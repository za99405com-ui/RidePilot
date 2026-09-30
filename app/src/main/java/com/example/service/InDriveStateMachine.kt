package com.example.service

import android.graphics.Rect
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class InDriveStateMachine(
    private val gestureDispatcher: suspend (GestureAction) -> Boolean,
    private val logger: suspend (action: String, reason: String, price: Double?, dist: Double?, conf: Int) -> Unit
) {

    sealed class GestureAction {
        data class Tap(val x: Float, val y: Float) : GestureAction()
        data class Swipe(val startX: Float, val startY: Float, val endX: Float, val endY: Float, val durationMs: Long = 300) : GestureAction()
        object Back : GestureAction()
    }

    private val _currentState = MutableStateFlow(AutomationState.IDLE)
    val currentState: StateFlow<AutomationState> = _currentState.asStateFlow()

    private var currentAttemptCount = 0
    private var lastSwipedCardBounds: Rect? = null
    private var consecutiveErrors = 0

    fun reset() {
        _currentState.value = AutomationState.IDLE
        currentAttemptCount = 0
        lastSwipedCardBounds = null
        consecutiveErrors = 0
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

        // Rule 1: Always enforce OFFLINE
        if (!parsed.isOffline && parsed.offlineButtonBounds != null) {
            _currentState.value = AutomationState.ENSURE_OFFLINE
            logger("ENSURE_OFFLINE", "تم رصد inDrive في وضع 'متصل'، جاري الضغط لإعادته 'غير متصل'", null, null, parsed.confidence)
            val btn = parsed.offlineButtonBounds
            gestureDispatcher(GestureAction.Tap(btn.exactCenterX(), btn.exactCenterY()))
            return
        }

        when (parsed.screenType) {
            InDriveParser.InDriveScreenType.REQUESTS_LIST -> {
                _currentState.value = AutomationState.REQUESTS_PAGE
                currentAttemptCount = 0

                // Examine order cards
                if (parsed.orderCards.isNotEmpty()) {
                    _currentState.value = AutomationState.READING_ORDERS
                    for (card in parsed.orderCards) {
                        // Skip if already swiped this exact card recently
                        if (lastSwipedCardBounds == card.bounds) continue

                        _currentState.value = AutomationState.CHECKING_ZONE
                        val zoneResult = ZoneEngine.evaluateOffer(
                            context = context,
                            pickupAddress = card.pickupAddress,
                            destinationAddress = card.destinationAddress,
                            zones = zones,
                            mode = zoneVerificationMode
                        )

                        if (!zoneResult.isAllowed) {
                            // OUT OF ZONE: Execute Swipe out of zone!
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

                            lastSwipedCardBounds = card.bounds
                            gestureDispatcher(GestureAction.Swipe(startX, midY, endX, midY, 350))
                            return // wait for next event
                        } else {
                            // INSIDE ZONE: Check pricing
                            _currentState.value = AutomationState.CALCULATING_PRICE
                            val distance = card.distanceKm ?: 5.0
                            val calcResult = PricingEngine.calculateMinimumPrice(distance, bands)

                            val passengerPrice = card.priceEgp ?: 0.0
                            if (passengerPrice < calcResult.minimumPrice) {
                                // Open order for negotiation!
                                _currentState.value = AutomationState.OPENING_ORDER
                                logger(
                                    "OPEN_ORDER",
                                    "الطلب داخل الـZone ولكن السعر ($passengerPrice) < الحد الأدنى (${calcResult.minimumPrice}). فتح الطلب للتفاوض",
                                    passengerPrice,
                                    distance,
                                    parsed.confidence
                                )
                                gestureDispatcher(GestureAction.Tap(card.bounds.exactCenterX(), card.bounds.exactCenterY()))
                                return
                            } else {
                                // Acceptable as is
                                logger(
                                    "PRICE_OK",
                                    "الطلب مناسب سعرياً (${passengerPrice} >= ${calcResult.minimumPrice} ج.م)",
                                    passengerPrice,
                                    distance,
                                    parsed.confidence
                                )
                                if (negotiationConfig.autoAccept) {
                                    gestureDispatcher(GestureAction.Tap(card.bounds.exactCenterX(), card.bounds.exactCenterY()))
                                }
                                return
                            }
                        }
                    }
                }
            }

            InDriveParser.InDriveScreenType.ORDER_DETAILS -> {
                val offer = parsed.activeOffer
                val distance = offer?.tripDistanceKm ?: 5.0
                val calc = PricingEngine.calculateMinimumPrice(distance, bands)
                val passengerPrice = offer?.displayedPrice ?: 0.0

                val decision = NegotiationEngine.evaluatePriceAndNegotiate(
                    passengerPrice = passengerPrice,
                    floorMinimum = calc.minimumPrice,
                    attemptNumber = currentAttemptCount + 1,
                    config = negotiationConfig
                )

                if (decision.isAcceptableAsIs) {
                    logger("ORDER_ACCEPTABLE", decision.reason, passengerPrice, distance, parsed.confidence)
                    if (negotiationConfig.autoAccept && parsed.acceptButtonBounds != null) {
                        val b = parsed.acceptButtonBounds
                        gestureDispatcher(GestureAction.Tap(b.exactCenterX(), b.exactCenterY()))
                    }
                } else if (decision.shouldCounterOffer && decision.counterPrice != null) {
                    _currentState.value = AutomationState.NEGOTIATING
                    currentAttemptCount++

                    // Check if one of the quick chips matches the counter price
                    val matchingChip = parsed.quickOfferButtons.entries.firstOrNull {
                        it.key >= decision.counterPrice && it.key <= decision.counterPrice + 2.0
                    }

                    if (matchingChip != null) {
                        logger("SUBMIT_QUICK_OFFER", "تقديم عرض سريع بمبلغ ${matchingChip.key} ج.م", passengerPrice, distance, parsed.confidence)
                        gestureDispatcher(GestureAction.Tap(matchingChip.value.exactCenterX(), matchingChip.value.exactCenterY()))
                    } else if (parsed.customOfferEditButtonBounds != null) {
                        logger("OPEN_CUSTOM_OFFER", "الضغط على زر القلم لتقديم عرض مخصص ${decision.counterPrice} ج.م", passengerPrice, distance, parsed.confidence)
                        val b = parsed.customOfferEditButtonBounds
                        gestureDispatcher(GestureAction.Tap(b.exactCenterX(), b.exactCenterY()))
                    }
                } else {
                    // Cannot negotiate further, return to requests list safely
                    _currentState.value = AutomationState.RETURN_TO_REQUESTS
                    logger("CANCEL_ORDER", "تعذر التفاوض أو تم بلوغ الحد الأقصى. الرجوع لقائمة الطلبات", passengerPrice, distance, parsed.confidence)
                    if (parsed.closeButtonBounds != null) {
                        gestureDispatcher(GestureAction.Tap(parsed.closeButtonBounds.exactCenterX(), parsed.closeButtonBounds.exactCenterY()))
                    } else {
                        gestureDispatcher(GestureAction.Back)
                    }
                }
            }

            InDriveParser.InDriveScreenType.COUNTER_OFFER_INPUT -> {
                // If custom offer dialog is open, submit offer if button visible
                if (parsed.submitOfferButtonBounds != null) {
                    val b = parsed.submitOfferButtonBounds
                    logger("SUBMIT_COUNTER_OFFER", "الضغط على زر 'تقديم عرض'", null, null, parsed.confidence)
                    gestureDispatcher(GestureAction.Tap(b.exactCenterX(), b.exactCenterY()))
                }
            }

            InDriveParser.InDriveScreenType.WAITING_RESPONSE -> {
                _currentState.value = AutomationState.WAITING_RESPONSE
                if (parsed.isResponseRejected) {
                    logger("RESPONSE_REJECTED", "تم رفض العرض من العميل أو انتهت الصلاحية", null, null, parsed.confidence)
                    if (currentAttemptCount >= negotiationConfig.maxNegotiationAttempts) {
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
