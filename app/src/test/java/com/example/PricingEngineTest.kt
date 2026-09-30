package com.example

import com.example.data.model.AppTarget
import com.example.data.model.PricingBand
import com.example.domain.engine.PricingEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PricingEngineTest {

    private val sampleBands = listOf(
        PricingBand(appTarget = AppTarget.INDRIVE, minKm = 0.0, maxKm = 10.0, pricePerKm = 9.0),
        PricingBand(appTarget = AppTarget.INDRIVE, minKm = 10.0, maxKm = 20.0, pricePerKm = 7.0),
        PricingBand(appTarget = AppTarget.INDRIVE, minKm = 20.0, maxKm = 30.0, pricePerKm = 6.0)
    )

    @Test
    fun testTripInsideFirstBand() {
        // 8 km in 0-10km band (9 EGP/km) -> 8 * 9 = 72 EGP
        val result = PricingEngine.calculateMinimumPrice(8.0, sampleBands)
        assertEquals(72.0, result.minimumPrice, 0.01)
        assertEquals(9.0, result.pricePerKm, 0.01)
    }

    @Test
    fun testTripInsideSecondBand_NonProgressive() {
        // 15 km in 10-20km band (7 EGP/km)
        // MUST BE 15 * 7 = 105 EGP
        // MUST NOT BE progressive (10*9 + 5*7 = 125)
        val result = PricingEngine.calculateMinimumPrice(15.0, sampleBands)
        assertEquals(105.0, result.minimumPrice, 0.01)
        assertEquals(7.0, result.pricePerKm, 0.01)
    }

    @Test
    fun testBoundaries() {
        // 9.99 km -> inside 0-10 km (9 EGP/km)
        val r999 = PricingEngine.calculateMinimumPrice(9.99, sampleBands)
        assertEquals(9.0, r999.pricePerKm, 0.01)
        assertEquals(89.91, r999.minimumPrice, 0.01)

        // 10.0 km -> matches 10-20 km (7 EGP/km)
        val r10 = PricingEngine.calculateMinimumPrice(10.0, sampleBands)
        assertEquals(7.0, r10.pricePerKm, 0.01)
        assertEquals(70.0, r10.minimumPrice, 0.01)

        // 10.01 km -> inside 10-20 km (7 EGP/km)
        val r1001 = PricingEngine.calculateMinimumPrice(10.01, sampleBands)
        assertEquals(7.0, r1001.pricePerKm, 0.01)
        assertEquals(70.07, r1001.minimumPrice, 0.01)

        // 20.0 km -> matches 20-30 km (6 EGP/km)
        val r20 = PricingEngine.calculateMinimumPrice(20.0, sampleBands)
        assertEquals(6.0, r20.pricePerKm, 0.01)
        assertEquals(120.0, r20.minimumPrice, 0.01)
    }

    @Test
    fun testDistanceBeyondMaxBand() {
        // 35 km -> exceeds 30km, uses highest band rate (6 EGP/km)
        val result = PricingEngine.calculateMinimumPrice(35.0, sampleBands)
        assertEquals(210.0, result.minimumPrice, 0.01)
        assertEquals(6.0, result.pricePerKm, 0.01)
    }
}
