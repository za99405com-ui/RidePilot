package com.example.service

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.example.data.model.AutomationState
import com.example.data.model.NegotiationConfig
import com.example.data.model.PricingBand
import com.example.data.model.PricingDistanceMode
import com.example.data.model.SwipeDirection
import com.example.data.model.WorkZone
import com.example.data.model.ZoneVerificationMode
import com.example.domain.engine.NegotiationEngine
import com.example.domain.engine.PricingEngine
import com.example.domain.engine.ZoneEngine
import com.example.domain.parser.InDriveParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

class InDriveStateMachine(
    private val gestureDispatcher: suspend (GestureAction) -> Boolean,
    private val logger: suspend (action: String, reason: String, price: Double?, dist: Double?, conf: Int) -> Unit
) {

    companion object {
        private const val MIN_ACTION_CONFIDENCE = 80
        private const val REPEAT_ACTION_GUARD_MS = 1_200L
    }

    sealed class GestureAction {
        data class Tap(val x: Float, val y: Float) : GestureAction()
        data class Swipe(
            val startX: Float,
            val startY: Float,
            val endX: Float,
            val endY: Float,
            val durationMs: Long = 210
        ) : GestureAction()
        data class ClickNode(val node: AccessibilityNodeInfo) : GestureAction()
        data class SetText(val node: AccessibilityNodeInfo, val text: String) : GestureAction()
        object Back : GestureAction()
    }

    private val _currentState = MutableStateFlow(AutomationState.IDLE)
    val currentState: StateFlow<AutomationState> = _currentState.asStateFlow()

    private var currentAttemptCount = 0
    private var pendingCounterPrice: Double? = null
    private var openedCardBounds: Rect? = null
    private var pendingSwipeAfterReturn = false
    private var lastOrderKey: String? = null
    private var lastOpenedAt = 0L
    private var lastActionSignature: String? = null
    private var lastActionAt = 0L

    fun reset() {
        _currentState.value = AutomationState.IDLE
        currentAttemptCount = 0
        pendingCounterPrice = null
        openedCardBounds = null
        pendingSwipeAfterReturn = false
        lastOrderKey = null
        lastOpenedAt = 0L
        lastActionSignature = null
        lastActionAt = 0L
    }

    suspend fun processScreen(
        parsed: InDriveParser.InDriveParsedScreen,
        automationEnabled: Boolean,
        emergencyStop: Boolean,
        swipeDirection: SwipeDirection,
        pricingDistanceMode: PricingDistanceMode,
        zoneVerificationMode: ZoneVerificationMode,
        bands: List<PricingBand>,
        zones: List<WorkZone>,
        negotiationConfig: NegotiationConfig,
        context: android.content.Context
    ) {
        if (emergencyStop || !automationEnabled) {
            _currentState.value = if (emergencyStop) AutomationState.PAUSED else AutomationState.IDLE
            return
        }

        if (parsed.confidence < MIN_ACTION_CONFIDENCE) {
            _currentState.value = AutomationState.ERROR_RECOVERY
            logger(
                "LOW_CONFIDENCE",
                "القراءة غير كافية للتنفيذ (${parsed.confidence}%)",
                parsed.activeOffer?.displayedPrice,
                parsed.activeOffer?.tripDistanceKm,
                parsed.confidence
            )
            return
        }

        if (parsed.offlineButtonBounds != null && !parsed.isOffline) {
            _currentState.value = AutomationState.ENSURE_OFFLINE
            if (!canDispatch("ENSURE_OFFLINE", 1_500L)) return
            logger("ENSURE_OFFLINE", "إرجاع inDrive إلى وضع غير متصل", null, null, parsed.confidence)
            val b = parsed.offlineButtonBounds
            gestureDispatcher(GestureAction.Tap(b.exactCenterX(), b.exactCenterY()))
            return
        }

        when (parsed.screenType) {
            InDriveParser.InDriveScreenType.REQUESTS_LIST -> {
                _currentState.value = AutomationState.REQUESTS_PAGE
                pendingCounterPrice = null

                if (pendingSwipeAfterReturn && openedCardBounds != null && parsed.orderCards.isNotEmpty()) {
                    val previous = openedCardBounds!!
                    val target = parsed.orderCards.minByOrNull {
                        abs(it.bounds.exactCenterY() - previous.exactCenterY())
                    }
                    if (target != null) {
                        pendingSwipeAfterReturn = false
                        openedCardBounds = null
                        swipeCard(target.bounds, swipeDirection)
                        logger(
                            "HIDE_ORDER",
                            "إخفاء الطلب من القائمة",
                            target.priceEgp,
                            target.distanceKm,
                            parsed.confidence
                        )
                        return
                    }
                }

                val card = parsed.orderCards.firstOrNull() ?: return
                val key = orderKey(card)

                val now = System.currentTimeMillis()
                if (key == lastOrderKey && now - lastOpenedAt < 650L) return

                if (key != lastOrderKey) {
                    currentAttemptCount = 0
                    lastOrderKey = key
                }

                openedCardBounds = Rect(card.bounds)
                lastOpenedAt = now
                _currentState.value = AutomationState.OPENING_ORDER
                logger(
                    "OPEN_ORDER",
                    "فتح الطلب للتحليل",
                    card.priceEgp,
                    card.distanceKm,
                    parsed.confidence
                )

                val opened = card.node?.let {
                    gestureDispatcher(GestureAction.ClickNode(it))
                } ?: false

                if (!opened) {
                    gestureDispatcher(
                        GestureAction.Tap(
                            card.bounds.exactCenterX(),
                            card.bounds.exactCenterY()
                        )
                    )
                }
            }

            InDriveParser.InDriveScreenType.ORDER_DETAILS -> {
                val offer = parsed.activeOffer
                if (offer == null || offer.confidence < MIN_ACTION_CONFIDENCE) {
                    _currentState.value = AutomationState.ERROR_RECOVERY
                    logger(
                        "INCOMPLETE_ORDER",
                        "تفاصيل الطلب غير مكتملة",
                        offer?.displayedPrice,
                        offer?.tripDistanceKm,
                        offer?.confidence ?: parsed.confidence
                    )
                    return
                }

                _currentState.value = AutomationState.CHECKING_ZONE
                val zone = ZoneEngine.evaluateOffer(
                    context = context,
                    pickupAddress = offer.pickupAddress,
                    destinationAddress = offer.destinationAddress,
                    zones = zones,
                    mode = zoneVerificationMode
                )

                if (!zone.isConclusive) {
                    logger(
                        "ZONE_UNKNOWN",
                        "تعذر تحديد المنطقة؛ رجوع بدون تفاوض",
                        offer.displayedPrice,
                        offer.tripDistanceKm,
                        offer.confidence
                    )
                    returnToRequests(parsed)
                    return
                }

                if (!zone.isAllowed) {
                    pendingSwipeAfterReturn = openedCardBounds != null
                    logger(
                        "OUT_OF_ZONE",
                        "خارج منطقة العمل",
                        offer.displayedPrice,
                        offer.tripDistanceKm,
                        offer.confidence
                    )
                    returnToRequests(parsed)
                    return
                }

                val passengerPrice = offer.displayedPrice
                if (passengerPrice == null || passengerPrice <= 0.0) {
                    logger("MISSING_PRICE", "لم يتم قراءة السعر", null, offer.tripDistanceKm, offer.confidence)
                    return
                }

                val pricingDistance = when (pricingDistanceMode) {
                    PricingDistanceMode.TRIP_ONLY -> offer.tripDistanceKm
                    PricingDistanceMode.PICKUP_PLUS_TRIP -> {
                        val pickup = offer.pickupDistanceKm
                        val trip = offer.tripDistanceKm
                        when {
                            pickup != null && trip != null -> pickup + trip
                            trip != null -> trip
                            else -> null
                        }
                    }
                }

                if (pricingDistance == null || pricingDistance <= 0.0) {
                    logger("MISSING_DISTANCE", "لم يتم قراءة مسافة الرحلة", passengerPrice, null, offer.confidence)
                    return
                }

                _currentState.value = AutomationState.CALCULATING_PRICE
                val calc = PricingEngine.calculateMinimumPrice(pricingDistance, bands)
                if (!calc.isValid) {
                    logger("INVALID_PRICING", calc.explanation, passengerPrice, pricingDistance, offer.confidence)
                    return
                }

                val decision = NegotiationEngine.evaluatePriceAndNegotiate(
                    passengerPrice = passengerPrice,
                    floorMinimum = calc.minimumPrice,
                    attemptNumber = currentAttemptCount + 1,
                    config = negotiationConfig
                )

                if (decision.isAcceptableAsIs) {
                    pendingCounterPrice = null
                    logger(
                        "ACCEPT",
                        "قبول ${passengerPrice.toInt()} ج — الحد الأدنى ${calc.minimumPrice.toInt()} ج",
                        passengerPrice,
                        pricingDistance,
                        offer.confidence
                    )

                    if (negotiationConfig.autoAccept) {
                        val b = parsed.acceptButtonBounds
                        if (b != null) {
                            if (canDispatch("ACCEPT:${lastOrderKey ?: "unknown"}", 1_500L)) {
                                gestureDispatcher(GestureAction.Tap(b.exactCenterX(), b.exactCenterY()))
                            }
                        } else {
                            logger(
                                "ACCEPT_BUTTON_NOT_FOUND",
                                "السعر مناسب لكن زر القبول غير ظاهر",
                                passengerPrice,
                                pricingDistance,
                                offer.confidence
                            )
                        }
                    }
                    return
                }

                if (decision.shouldCounterOffer && decision.counterPrice != null) {
                    _currentState.value = AutomationState.NEGOTIATING
                    if (!canDispatch("COUNTER:${lastOrderKey ?: "unknown"}")) return
                    currentAttemptCount++

                    val target = decision.counterPrice
                    val eligible = parsed.quickOfferButtons
                        .filterKeys { it >= calc.minimumPrice }

                    // Use a preset only when it is essentially the price calculated by
                    // the negotiation engine. Otherwise open the pencil/custom offer;
                    // choosing a much lower preset would defeat Start Margin.
                    val quick = eligible.entries
                        .minByOrNull { kotlin.math.abs(it.key - target) }
                        ?.takeIf { kotlin.math.abs(it.key - target) <= 2.0 }

                    if (quick != null) {
                        pendingCounterPrice = null
                        logger(
                            "COUNTER_QUICK",
                            "جار عرض الأجرة المناسبة لك — ${quick.key.toInt()} ج",
                            passengerPrice,
                            pricingDistance,
                            offer.confidence
                        )
                        gestureDispatcher(
                            GestureAction.Tap(
                                quick.value.exactCenterX(),
                                quick.value.exactCenterY()
                            )
                        )
                        return
                    }

                    val editBounds = parsed.customOfferEditButtonBounds
                    if (editBounds != null) {
                        pendingCounterPrice = target
                        logger(
                            "COUNTER_CUSTOM",
                            "تجهيز عرض ${target.toInt()} ج",
                            passengerPrice,
                            pricingDistance,
                            offer.confidence
                        )
                        gestureDispatcher(
                            GestureAction.Tap(
                                editBounds.exactCenterX(),
                                editBounds.exactCenterY()
                            )
                        )
                        return
                    }

                    logger(
                        "COUNTER_CONTROL_NOT_FOUND",
                        "لم يتم العثور على زر تفاوض",
                        passengerPrice,
                        pricingDistance,
                        offer.confidence
                    )
                    return
                }

                pendingSwipeAfterReturn = openedCardBounds != null
                logger(
                    "NEGOTIATION_FINISHED",
                    "انتهت محاولات التفاوض",
                    passengerPrice,
                    pricingDistance,
                    offer.confidence
                )
                returnToRequests(parsed)
            }

            InDriveParser.InDriveScreenType.COUNTER_OFFER_INPUT -> {
                val target = pendingCounterPrice
                val inputNode = parsed.customOfferInputNode
                val submit = parsed.submitOfferButtonBounds

                if (target == null || inputNode == null || submit == null) {
                    logger("CUSTOM_OFFER_NOT_READY", "تعذر تجهيز العرض المخصص", target, null, parsed.confidence)
                    return
                }

                val textValue = if (target % 1.0 == 0.0) target.toInt().toString() else target.toString()
                val textSet = gestureDispatcher(GestureAction.SetText(inputNode, textValue))
                if (!textSet) {
                    logger("SET_COUNTER_FAILED", "فشل إدخال سعر العرض", target, null, parsed.confidence)
                    return
                }

                delay(55)
                logger("SUBMIT_COUNTER", "إرسال عرض $textValue ج — انتظر الرد", target, null, parsed.confidence)
                gestureDispatcher(
                    GestureAction.Tap(
                        submit.exactCenterX(),
                        submit.exactCenterY()
                    )
                )
                pendingCounterPrice = null
            }

            InDriveParser.InDriveScreenType.WAITING_RESPONSE -> {
                _currentState.value = AutomationState.WAITING_RESPONSE

                if (parsed.isResponseRejected) {
                    logger(
                        "REJECTED",
                        "لم يتم قبول العرض — المحاولة التالية",
                        null,
                        null,
                        parsed.confidence
                    )
                    returnToRequests(parsed)
                }
            }

            InDriveParser.InDriveScreenType.UNKNOWN -> {
                _currentState.value = AutomationState.ERROR_RECOVERY
            }
        }
    }

    private suspend fun swipeCard(bounds: Rect, direction: SwipeDirection) {
        val margin = 40f
        val startX = if (direction == SwipeDirection.SWIPE_LEFT) bounds.right - margin else bounds.left + margin
        val endX = if (direction == SwipeDirection.SWIPE_LEFT) bounds.left + margin else bounds.right - margin
        gestureDispatcher(
            GestureAction.Swipe(
                startX = startX,
                startY = bounds.exactCenterY(),
                endX = endX,
                endY = bounds.exactCenterY()
            )
        )
    }

    private suspend fun returnToRequests(parsed: InDriveParser.InDriveParsedScreen) {
        _currentState.value = AutomationState.RETURN_TO_REQUESTS
        if (!canDispatch("RETURN:${lastOrderKey ?: "unknown"}:${parsed.screenType}", 900L)) return
        val close = parsed.closeButtonBounds
        if (close != null) {
            gestureDispatcher(GestureAction.Tap(close.exactCenterX(), close.exactCenterY()))
        } else {
            gestureDispatcher(GestureAction.Back)
        }
    }

    private fun canDispatch(signature: String, cooldownMs: Long = REPEAT_ACTION_GUARD_MS): Boolean {
        val now = System.currentTimeMillis()
        if (signature == lastActionSignature && now - lastActionAt < cooldownMs) {
            return false
        }
        lastActionSignature = signature
        lastActionAt = now
        return true
    }

    private fun orderKey(card: InDriveParser.InDriveOrderCard): String =
        "${card.priceEgp}|${card.distanceKm}|${card.pickupAddress}|${card.destinationAddress}"
}
