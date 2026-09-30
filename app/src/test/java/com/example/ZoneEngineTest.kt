package com.example

import com.example.data.model.LatLngPoint
import com.example.data.model.WorkZone
import com.example.domain.engine.ZoneEngine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoneEngineTest {

    // Simple square polygon around (31.20, 29.90) to (31.30, 30.00)
    private val squarePolygon = listOf(
        LatLngPoint(31.20, 29.90),
        LatLngPoint(31.30, 29.90),
        LatLngPoint(31.30, 30.00),
        LatLngPoint(31.20, 30.00)
    )

    @Test
    fun testPointInsidePolygon() {
        val insidePoint = LatLngPoint(31.25, 29.95)
        assertTrue(ZoneEngine.isPointInPolygon(insidePoint, squarePolygon))
    }

    @Test
    fun testPointOutsidePolygon() {
        val outsidePoint = LatLngPoint(31.10, 29.80)
        assertFalse(ZoneEngine.isPointInPolygon(outsidePoint, squarePolygon))

        val farAway = LatLngPoint(30.05, 31.23) // Cairo
        assertFalse(ZoneEngine.isPointInPolygon(farAway, squarePolygon))
    }

    @Test
    fun testKeywordMatchingFallback() {
        val zone = WorkZone(
            name = "المنتزه",
            polygonJson = "[]",
            isEnabled = true,
            allowedKeywords = "سيدي بشر,ميامي,العصافرة"
        )

        assertTrue(ZoneEngine.matchesKeywords("قسم ثان المنتزة سيدي بشر بحري", zone))
        assertTrue(ZoneEngine.matchesKeywords("شارع العصافرة قبلي", zone))
        assertFalse(ZoneEngine.matchesKeywords("محطة مصر الدقي الجيزة", zone))
    }
}
