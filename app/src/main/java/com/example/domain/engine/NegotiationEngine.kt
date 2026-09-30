package com.example.domain.engine

import com.example.data.model.NegotiationConfig
import kotlin.math.ceil

object NegotiationEngine {

    data class NegotiationDecision(
        val isAcceptableAsIs: Boolean,
        val shouldCounterOffer: Boolean,
        val counterPrice: Double?,
        val currentAttempt: Int,
        val floorMinimum: Double,
        val reason: String
    )

    /**
     * Determines whether the passenger's offered price is acceptable, or computes the appropriate counter-offer.
     * Enforces the hard rule: counter-offer can NEVER go below floorMinimum.
     */
    fun evaluatePriceAndNegotiate(
        passengerPrice: Double,
        floorMinimum: Double,
        attemptNumber: Int,
        config: NegotiationConfig
    ): NegotiationDecision {
        if (passengerPrice >= floorMinimum) {
            return NegotiationDecision(
                isAcceptableAsIs = true,
                shouldCounterOffer = false,
                counterPrice = null,
                currentAttempt = attemptNumber,
                floorMinimum = floorMinimum,
                reason = "✅ عرض العميل ($passengerPrice ج.م) أعلى من أو يساوي الحد الأدنى ($floorMinimum ج.م)"
            )
        }

        if (!config.autoCounterOffer) {
            return NegotiationDecision(
                isAcceptableAsIs = false,
                shouldCounterOffer = false,
                counterPrice = null,
                currentAttempt = attemptNumber,
                floorMinimum = floorMinimum,
                reason = "❌ عرض العميل ($passengerPrice ج.م) أقل من الحد الأدنى ($floorMinimum ج.م)، والتفاوض التلقائي معطل"
            )
        }

        if (attemptNumber > config.maxNegotiationAttempts) {
            return NegotiationDecision(
                isAcceptableAsIs = false,
                shouldCounterOffer = false,
                counterPrice = null,
                currentAttempt = attemptNumber,
                floorMinimum = floorMinimum,
                reason = "❌ تم بلوغ الحد الأقصى لمحاولات التفاوض (${config.maxNegotiationAttempts})"
            )
        }

        // Calculate initial target: floor + margin - (attempt - 1) * step
        val baseTarget = floorMinimum + config.startMarginEgp - ((attemptNumber - 1) * config.negotiationStepEgp)

        // Apply strict floor: cannot be below floorMinimum
        val effectiveTarget = maxOf(floorMinimum, baseTarget)

        // Apply rounding
        val roundedPrice = roundUpTo(effectiveTarget, config.roundToEgp)

        // Final check against floor
        val finalCounter = maxOf(floorMinimum, roundedPrice)

        return NegotiationDecision(
            isAcceptableAsIs = false,
            shouldCounterOffer = true,
            counterPrice = finalCounter,
            currentAttempt = attemptNumber,
            floorMinimum = floorMinimum,
            reason = "تفاوض محاولة $attemptNumber: تقديم عرض $finalCounter ج.م (الحد الأدنى $floorMinimum ج.م)"
        )
    }

    private fun roundUpTo(value: Double, step: Double): Double {
        if (step <= 1.0) return ceil(value)
        return ceil(value / step) * step
    }
}
