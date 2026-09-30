package com.example

import com.example.data.model.NegotiationConfig
import com.example.domain.engine.NegotiationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NegotiationEngineTest {

    private val config = NegotiationConfig(
        startMarginEgp = 15.0,
        negotiationStepEgp = 5.0,
        maxNegotiationAttempts = 3,
        roundToEgp = 5.0,
        autoAccept = false,
        autoCounterOffer = true
    )

    @Test
    fun testOfferAboveFloorIsAcceptable() {
        val decision = NegotiationEngine.evaluatePriceAndNegotiate(
            passengerPrice = 85.0,
            floorMinimum = 72.0,
            attemptNumber = 1,
            config = config
        )
        assertTrue(decision.isAcceptableAsIs)
        assertFalse(decision.shouldCounterOffer)
    }

    @Test
    fun testFirstCounterOfferCalculation() {
        // Floor = 72, margin = 15 -> 87. Rounded to 5 -> 90.
        val decision = NegotiationEngine.evaluatePriceAndNegotiate(
            passengerPrice = 50.0,
            floorMinimum = 72.0,
            attemptNumber = 1,
            config = config
        )
        assertFalse(decision.isAcceptableAsIs)
        assertTrue(decision.shouldCounterOffer)
        assertEquals(90.0, decision.counterPrice!!, 0.01)
    }

    @Test
    fun testSubsequentAttemptsStepDown() {
        // Attempt 2: base = 72 + 15 - 5 = 82. Rounded to 5 -> 85.
        val d2 = NegotiationEngine.evaluatePriceAndNegotiate(50.0, 72.0, 2, config)
        assertEquals(85.0, d2.counterPrice!!, 0.01)

        // Attempt 3: base = 72 + 15 - 10 = 77. Rounded to 5 -> 80.
        val d3 = NegotiationEngine.evaluatePriceAndNegotiate(50.0, 72.0, 3, config)
        assertEquals(80.0, d3.counterPrice!!, 0.01)
    }

    @Test
    fun testCounterNeverGoesBelowFloor() {
        // Even if step decrements want to drop, floor 72 is absolute minimum
        val dLargeStep = NegotiationEngine.evaluatePriceAndNegotiate(
            passengerPrice = 50.0,
            floorMinimum = 72.0,
            attemptNumber = 5,
            config = config.copy(maxNegotiationAttempts = 10, negotiationStepEgp = 30.0)
        )
        assertTrue(dLargeStep.counterPrice!! >= 72.0)
    }

    @Test
    fun testExceedingMaxAttemptsStops() {
        val decision = NegotiationEngine.evaluatePriceAndNegotiate(
            passengerPrice = 50.0,
            floorMinimum = 72.0,
            attemptNumber = 4,
            config = config // max is 3
        )
        assertFalse(decision.isAcceptableAsIs)
        assertFalse(decision.shouldCounterOffer)
    }
}
