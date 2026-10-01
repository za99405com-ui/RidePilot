package com.example

import com.example.domain.parser.ArabicNumberHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArabicNumberHelperTest {

    @Test
    fun combinedTimeAndDistanceReadsDistanceUnitValue() {
        assertEquals(
            9.2,
            ArabicNumberHelper.extractDistanceKm("21 دقيقة 9,2 كم"),
            0.0001
        )
    }

    @Test
    fun metersAreConvertedToKilometers() {
        assertEquals(
            0.922,
            ArabicNumberHelper.extractDistanceKm("~922 متر"),
            0.0001
        )
    }

    @Test
    fun timeOnlyLabelIsNotDistance() {
        assertNull(
            ArabicNumberHelper.extractDistanceKm("21 دقيقة")
        )
    }
}
