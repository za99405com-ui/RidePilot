package com.example.domain.engine

import com.example.data.model.PricingBand
import kotlin.math.roundToInt

object PricingEngine {

    data class CalculationResult(
        val distanceKm: Double,
        val matchedBand: PricingBand?,
        val pricePerKm: Double,
        val minimumPrice: Double,
        val isValid: Boolean,
        val explanation: String
    )

    /**
     * Calculates the minimum required price based on non-progressive band matching.
     * Boundary rules:
     * Band matches if: minKm <= distanceKm < maxKm (or <= maxKm if it is the highest band).
     * Formula: minimumPrice = distanceKm * pricePerKm
     */
    fun calculateMinimumPrice(
        distanceKm: Double,
        bands: List<PricingBand>
    ): CalculationResult {
        if (distanceKm <= 0.0) {
            return CalculationResult(
                distanceKm = 0.0,
                matchedBand = null,
                pricePerKm = 0.0,
                minimumPrice = 0.0,
                isValid = false,
                explanation = "المسافة صفر أو غير صالحة"
            )
        }

        if (bands.isEmpty()) {
            return CalculationResult(
                distanceKm = distanceKm,
                matchedBand = null,
                pricePerKm = 0.0,
                minimumPrice = 0.0,
                isValid = false,
                explanation = "لا توجد شرائح تسعير مفعلة؛ لن يتم اتخاذ أي إجراء تلقائي"
            )
        }

        val sortedBands = bands.sortedBy { it.minKm }
        val maxBand = sortedBands.maxByOrNull { it.maxKm }

        // Find band with boundary condition: minKm <= distance < maxKm
        var matched = sortedBands.firstOrNull { band ->
            distanceKm >= band.minKm && distanceKm < band.maxKm
        }

        // If distance exactly equals or exceeds highest band maxKm, use the highest band
        if (matched == null && maxBand != null && distanceKm >= maxBand.maxKm) {
            matched = maxBand
        }

        val pricePerKm = matched?.pricePerKm ?: sortedBands.last().pricePerKm
        val calculatedMin = roundToTwoDecimals(distanceKm * pricePerKm)

        val bandDesc = if (matched != null) {
            "شريحة [${matched.minKm} - ${matched.maxKm} كم] بمعدل ${matched.pricePerKm} ج.م/كم"
        } else {
            "أعلى شريحة متاحة بمعدل $pricePerKm ج.م/كم"
        }

        return CalculationResult(
            distanceKm = distanceKm,
            matchedBand = matched,
            pricePerKm = pricePerKm,
            minimumPrice = calculatedMin,
            isValid = true,
            explanation = "$bandDesc: الحد الأدنى المطلوب = $calculatedMin ج.م ($distanceKm كم × $pricePerKm)"
        )
    }

    fun roundToTwoDecimals(value: Double): Double {
        return (value * 100.0).roundToInt() / 100.0
    }
}
