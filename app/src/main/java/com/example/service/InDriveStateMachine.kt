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

class InDriveStateMachine(
    private val gestureDispatcher: suspend (GestureAction) -> Boolean,
    private val logger: suspend (action: String, reason: String, price: Double?, dist: Double?, conf: Int) -> Unit
) {

    companion object {
        private const val MIN_ACTION_CONFIDENCE = 80
        private const val OFFLINE_CONFIRMATION_GRACE_MS = 15_000L
    }

    sealed class GestureAction {
        data class Tap(val x: Float, val y: Float) : GestureAction()
        data class Swipe(val startX: Float, val startY: Float, val endX: Float, val endY: Float, val durationMs: Long = 300) : GestureAction()
        data class ClickNode(val node: AccessibilityNodeInfo) : GestureAction()
        data class SetText(val node: AccessibilityNodeInfo, val text: String) : GestureAction()
        object Back : GestureAction()
    }

    private val _currentState = MutableStateFlow(AutomationState.IDLE)
    val currentState: StateFlow<AutomationState> = _currentState.asStateFlow()

    private var currentAttemptCount = 0
    private var lastSwipedCardBounds: Rect? = null
    private var consecutiveErrors = 0
    private var pendingCounterPrice: Double? = null
    private var lastConfirmedOfflineAt: Long = 0L

    fun reset() {
        _currentState.value = AutomationState.IDLE
        currentAttemptCount = 0
        lastSwipedCardBounds = null
        consecutiveErrors = 0
        pendingCounterPrice = null
        lastConfirmedOfflineAt = 0L
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
                "تم إيقاف الأتمتة لهذه الشاشة لأن دقة التعرف ${parsed.confidence}% أقل من الحد الآمن ${MIN_ACTION_CONFIDENCE}%",
                null,
                null,
                parsed.confidence
            )
            return
        }

        val now = System.currentTimeMillis()

        // If the connection control is visible, it is authoritative.
        if (parsed.offlineButtonBounds != null) {
            if (!parsed.isOffline) {
                _currentState.value = AutomationState.ENSURE_OFFLINE
                logger("ENSURE_OFFLINE", "تم رصد inDrive في وضع 'متصل'، جاري الضغط لإعادته 'غير متصل'", null, null, parsed.confidence)
                val btn = parsed.offlineButtonBounds
                gestureDispatcher(GestureAction.Tap(btn.exactCenterX(), btn.exactCenterY()))
                return
            }
            lastConfirmedOfflineAt = now
        }

        // Detail / counter-offer screens sometimes hide the online/offline control from
        // Accessibility. Allow them only when Offline was confirmed moments earlier.
        val offlineRecentlyConfirmed = now - lastConfirmedOfflineAt <= OFFLINE_CONFIRMATION_GRACE_MS

        when (parsed.screenType) {
            InDriveParser.InDriveScreenType.REQUESTS_LIST -> {
                // Some inDrive builds draw the "غير متصل" label in a way Accessibility
                // cannot expose. If "متصل" was explicitly detected, the global guard
                // above already switched it back. Otherwise, allow request handling
                // without blocking on an unreadable status label.
                if (parsed.offlineButtonBounds != null && !parsed.isOffline) {
                    _currentState.value = AutomationState.ERROR_RECOVERY
                    logger(
                        "REQUESTS_ONLINE",
                        "تم رصد حالة متصل بشكل صريح؛ لن يتم التحكم قبل الرجوع لعدم الاتصال",
                        null,
                        null,
                        parsed.confidence
                    )
                    return
                }
                lastConfirmedOfflineAt = now

                _currentState.value = AutomationState.REQUESTS_PAGE
                currentAttemptCount = 0
                pendingCounterPrice = null

                if (parsed.orderCards.isNotEmpty()) {
                    _currentState.value = AutomationState.READING_ORDERS
                    for (card in parsed.orderCards) {
                        if (lastSwipedCardBounds == card.bounds) continue

                        _currentState.value = AutomationState.CHECKING_ZONE
                        val zoneResult = ZoneEngine.evaluateOffer(
                            context = context,
                            pickupAddress = card.pickupAddress,
                            destinationAddress = card.destinationAddress,
                            zones = zones,
                            mode = zoneVerificationMode
                        )

                        if (!zoneResult.isConclusive) {
                            // The compact request card may omit one of the addresses.
                            // Open details to get a richer parse, but do not negotiate yet.
                            _currentState.value = AutomationState.OPENING_ORDER
                            logger(
                                "ZONE_NEEDS_DETAILS",
                                "${zoneResult.reason} — فتح تفاصيل الطلب للتحقق من المنطقة قبل أي تفاوض",
                                card.priceEgp,
                                card.distanceKm,
                                parsed.confidence
                            )
                            val opened = if (card.node != null) {
                                gestureDispatcher(GestureAction.ClickNode(card.node))
                            } else {
                                false
                            }
                            if (!opened) {
                                gestureDispatcher(GestureAction.Tap(card.bounds.exactCenterX(), card.bounds.exactCenterY()))
                            }
                            return
                        }

                        if (!zoneResult.isAllowed) {
                            logger(
                                "SWIPE_OUT_OF_ZONE",
                                "الطلب خارج منطقة العمل (${zoneResult.reason})، جاري سحب البطاقة لإخفائها",
                                card.priceEgp,
                                card.distanceKm,
                                parsed.confidence
                            )
                            val b = card.bounds
                            val startX = if (swipeDirection == SwipeDirection.SWIPE_LEFT) b.right - 50f else b.left + 50f
                            val endX = if (swipeDirection == SwipeDirection.SWIPE_LEFT) b.left + 50f else b.right - 50f
                            val midY = b.exactCenterY()

                            lastSwipedCardBounds = Rect(card.bounds)
                            gestureDispatcher(GestureAction.Swipe(startX, midY, endX, midY, 350))
                            return
                        }

                        _currentState.value = AutomationState.OPENING_ORDER
                        logger(
                            "OPEN_ORDER",
                            "الطلب داخل الـZone؛ فتح التفاصيل للتحقق من السعر والمسافة قبل أي تفاوض",
                            card.priceEgp,
                            card.distanceKm,
                            parsed.confidence
                        )
                        val opened = if (card.node != null) {
                            gestureDispatcher(GestureAction.ClickNode(card.node))
                        } else {
                            false
                        }
                        if (!opened) {
                            gestureDispatcher(GestureAction.Tap(card.bounds.exactCenterX(), card.bounds.exactCenterY()))
                        }
                        return
                    }
                }
            }

            InDriveParser.InDriveScreenType.ORDER_DETAILS -> {
                if (!offlineRecentlyConfirmed) {
                    _currentState.value = AutomationState.ERROR_RECOVERY
                    logger(
                        "OFFLINE_NOT_RECENTLY_CONFIRMED",
                        "شاشة تفاصيل الطلب لا تعرض حالة الاتصال، وآخر تأكيد Offline قديم؛ لن يتم التفاوض",
                        parsed.activeOffer?.displayedPrice,
                        parsed.activeOffer?.tripDistanceKm,
                        parsed.confidence
                    )
                    return
                }

                val offer = parsed.activeOffer
                if (offer == null || offer.confidence < MIN_ACTION_CONFIDENCE) {
                    _currentState.value = AutomationState.ERROR_RECOVERY
                    logger(
                        "INCOMPLETE_ORDER",
                        "تعذر قراءة تفاصيل الطلب بدرجة ثقة كافية؛ لن يتم قبول أو تفاوض",
                        offer?.displayedPrice,
                        offer?.tripDistanceKm,
                        offer?.confidence ?: parsed.confidence
                    )
                    return
                }

                val detailZone = ZoneEngine.evaluateOffer(
                    context = context,
                    pickupAddress = offer.pickupAddress,
                    destinationAddress = offer.destinationAddress,
                    zones = zones,
                    mode = zoneVerificationMode
                )

                if (!detailZone.isConclusive) {
                    _currentState.value = AutomationState.ERROR_RECOVERY
                    logger(
                        "ZONE_STILL_UNCERTAIN",
                        "تعذر التحقق من منطقة الطلب حتى بعد فتح التفاصيل؛ لن يتم التفاوض",
                        offer.displayedPrice,
                        offer.tripDistanceKm,
                        offer.confidence
                    )
                    return
                }

                if (!detailZone.isAllowed) {
                    _currentState.value = AutomationState.RETURN_TO_REQUESTS
                    logger(
                        "DETAILS_OUT_OF_ZONE",
                        "الطلب خارج منطقة العمل بعد التحقق من التفاصيل؛ الرجوع لقائمة الطلبات",
                        offer.displayedPrice,
                        offer.tripDistanceKm,
                        offer.confidence
                    )
                    if (parsed.closeButtonBounds != null) {
                        gestureDispatcher(GestureAction.Tap(parsed.closeButtonBounds.exactCenterX(), parsed.closeButtonBounds.exactCenterY()))
                    } else {
                        gestureDispatcher(GestureAction.Back)
                    }
                    return
                }

                val passengerPrice = offer.displayedPrice
                if (passengerPrice == null || passengerPrice <= 0.0) {
                    _currentState.value = AutomationState.ERROR_RECOVERY
                    logger("MISSING_PRICE", "تعذر قراءة سعر العميل؛ لن يتم اتخاذ أي إجراء", null, null, offer.confidence)
                    return
                }

                val distance: Double? = when (pricingDistanceMode) {
                    PricingDistanceMode.TRIP_ONLY -> offer.tripDistanceKm
                    PricingDistanceMode.PICKUP_PLUS_TRIP -> {
                        val pickup = offer.pickupDistanceKm
                        val trip = offer.tripDistanceKm
                        when {
                            pickup != null && trip != null -> pickup + trip
                            trip != null -> {
                                logger(
                                    "TRIP_ONLY_DISTANCE_USED",
                                    "inDrive لم يعرض مسافة الوصول في هذه الشاشة؛ سيتم التسعير على مسافة الرحلة المقروءة فقط",
                                    passengerPrice,
                                    trip,
                                    offer.confidence
                                )
                                trip
                            }
                            else -> null
                        }
                    }
                }

                if (distance == null || distance <= 0.0) {
                    _currentState.value = AutomationState.ERROR_RECOVERY
                    logger(
                        "MISSING_DISTANCE",
                        "المسافة المطلوبة لوضع التسعير الحالي غير مكتملة؛ لن يتم استخدام قيمة افتراضية ولن يتم التفاوض",
                        passengerPrice,
                        distance,
                        offer.confidence
                    )
                    return
                }

                val calc = PricingEngine.calculateMinimumPrice(distance, bands)
                if (!calc.isValid) {
                    _currentState.value = AutomationState.ERROR_RECOVERY
                    logger("INVALID_PRICING_RULES", calc.explanation, passengerPrice, distance, offer.confidence)
                    return
                }

                _currentState.value = AutomationState.CALCULATING_PRICE
                val decision = NegotiationEngine.evaluatePriceAndNegotiate(
                    passengerPrice = passengerPrice,
                    floorMinimum = calc.minimumPrice,
                    attemptNumber = currentAttemptCount + 1,
                    config = negotiationConfig
                )

                if (decision.isAcceptableAsIs) {
                    pendingCounterPrice = null
                    logger("ORDER_ACCEPTABLE", decision.reason, passengerPrice, distance, offer.confidence)
                    if (negotiationConfig.autoAccept) {
                        val b = parsed.acceptButtonBounds
                        if (b != null) {
                            gestureDispatcher(GestureAction.Tap(b.exactCenterX(), b.exactCenterY()))
                        } else {
                            _currentState.value = AutomationState.ERROR_RECOVERY
                            logger("ACCEPT_BUTTON_NOT_FOUND", "السعر مناسب لكن زر القبول غير مؤكد؛ لم يتم الضغط", passengerPrice, distance, offer.confidence)
                        }
                    }
                } else if (decision.shouldCounterOffer && decision.counterPrice != null) {
                    _currentState.value = AutomationState.NEGOTIATING
                    currentAttemptCount++

                    val matchingChip = parsed.quickOfferButtons.entries.firstOrNull {
                        it.key >= decision.counterPrice && it.key <= decision.counterPrice + 2.0
                    }

                    if (matchingChip != null) {
                        pendingCounterPrice = null
                        logger("SUBMIT_QUICK_OFFER", "تقديم عرض سريع بمبلغ ${matchingChip.key} ج.م", passengerPrice, distance, offer.confidence)
                        gestureDispatcher(GestureAction.Tap(matchingChip.value.exactCenterX(), matchingChip.value.exactCenterY()))
                    } else if (parsed.customOfferEditButtonBounds != null) {
                        pendingCounterPrice = decision.counterPrice
                        logger("OPEN_CUSTOM_OFFER", "فتح إدخال عرض مخصص بقيمة ${decision.counterPrice} ج.م", passengerPrice, distance, offer.confidence)
                        val b = parsed.customOfferEditButtonBounds
                        gestureDispatcher(GestureAction.Tap(b.exactCenterX(), b.exactCenterY()))
                    } else {
                        _currentState.value = AutomationState.ERROR_RECOVERY
                        logger("COUNTER_CONTROL_NOT_FOUND", "لم يتم العثور بثقة على عرض سريع أو زر إدخال سعر مخصص؛ لم يتم الضغط", passengerPrice, distance, offer.confidence)
                    }
                } else {
                    pendingCounterPrice = null
                    _currentState.value = AutomationState.RETURN_TO_REQUESTS
                    logger("CANCEL_ORDER", "تعذر التفاوض أو تم بلوغ الحد الأقصى. الرجوع لقائمة الطلبات", passengerPrice, distance, offer.confidence)
                    if (parsed.closeButtonBounds != null) {
                        gestureDispatcher(GestureAction.Tap(parsed.closeButtonBounds.exactCenterX(), parsed.closeButtonBounds.exactCenterY()))
                    } else {
                        gestureDispatcher(GestureAction.Back)
                    }
                }
            }

            InDriveParser.InDriveScreenType.COUNTER_OFFER_INPUT -> {
                if (!offlineRecentlyConfirmed) {
                    _currentState.value = AutomationState.ERROR_RECOVERY
                    logger("OFFLINE_NOT_RECENTLY_CONFIRMED", "لن يتم إرسال عرض بدون تأكيد Offline حديث", pendingCounterPrice, null, parsed.confidence)
                    return
                }

                val target = pendingCounterPrice
                val inputNode = parsed.customOfferInputNode
                val submitBounds = parsed.submitOfferButtonBounds

                if (target == null || inputNode == null || submitBounds == null) {
                    _currentState.value = AutomationState.ERROR_RECOVERY
                    logger(
                        "CUSTOM_OFFER_NOT_READY",
                        "نافذة العرض المخصص غير مكتملة؛ لن يتم إرسال سعر قبل التأكد من حقل الإدخال وزر التقديم",
                        target,
                        null,
                        parsed.confidence
                    )
                    return
                }

                val textValue = if (target % 1.0 == 0.0) target.toInt().toString() else target.toString()
                val textSet = gestureDispatcher(GestureAction.SetText(inputNode, textValue))
                if (!textSet) {
                    _currentState.value = AutomationState.ERROR_RECOVERY
                    logger("SET_COUNTER_TEXT_FAILED", "فشل إدخال سعر العرض المخصص؛ لم يتم الضغط على تقديم عرض", target, null, parsed.confidence)
                    return
                }

                delay(200)
                logger("SUBMIT_COUNTER_OFFER", "تم إدخال $textValue ج.م بنجاح، جاري الضغط على 'تقديم عرض'", target, null, parsed.confidence)
                val submitted = gestureDispatcher(GestureAction.Tap(submitBounds.exactCenterX(), submitBounds.exactCenterY()))
                if (submitted) pendingCounterPrice = null
            }

            InDriveParser.InDriveScreenType.WAITING_RESPONSE -> {
                _currentState.value = AutomationState.WAITING_RESPONSE
                if (parsed.isResponseRejected) {
                    logger("RESPONSE_REJECTED", "تم رفض العرض من العميل أو انتهت الصلاحية", null, null, parsed.confidence)
                    if (currentAttemptCount >= negotiationConfig.maxNegotiationAttempts) {
                        pendingCounterPrice = null
                        // Return to requests
                        if (parsed.closeButtonBounds != null) {
                            gestureDispatcher(GestureAction.Tap(parsed.closeButtonBounds.exactCenterX(), parsed.closeButtonBounds.exactCenterY()))
                        } else {
                            gestureDispatcher(GestureAction.Back)
                        }
                    }
                }
            }

            InDriveParser.InDriveScreenType.UNKNOWN -> {
                // Not a recognized inDrive screen, do not dispatch gestures
            }
        }
    }
}
