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
    fun testAutomaticCirclePolygonContainsCenter() {
        val center = LatLngPoint(31.2200, 29.9500)
        val polygon = ZoneEngine.createCirclePolygon(center, radiusKm = 3.0)

        assertTrue(polygon.size >= 24)
        assertTrue(ZoneEngine.isPointInPolygon(center, polygon))
    }

    @Test
    fun testAutomaticCirclePolygonRejectsFarPoint() {
        val center = LatLngPoint(31.2200, 29.9500)
        val polygon = ZoneEngine.createCirclePolygon(center, radiusKm = 3.0)
        val farPoint = LatLngPoint(31.3200, 30.0500)

        assertFalse(ZoneEngine.isPointInPolygon(farPoint, polygon))
    }

    @Test
    fun testMultiPolygonRoundTrip() {
        val first = listOf(
            LatLngPoint(31.20, 29.90),
            LatLngPoint(31.21, 29.90),
            LatLngPoint(31.21, 29.91),
            LatLngPoint(31.20, 29.91)
        )
        val second = listOf(
            LatLngPoint(31.30, 30.00),
            LatLngPoint(31.31, 30.00),
            LatLngPoint(31.31, 30.01),
            LatLngPoint(31.30, 30.01)
        )

        val json = ZoneEngine.serializePolygonGroups(listOf(first, second))
        val parsed = ZoneEngine.parsePolygonGroups(json)

        assertTrue(parsed.size == 2)
        assertTrue(
            ZoneEngine.isPointInPolygonGroups(
                LatLngPoint(31.205, 29.905),
                parsed
            )
        )
        assertTrue(
            ZoneEngine.isPointInPolygonGroups(
                LatLngPoint(31.305, 30.005),
                parsed
            )
        )
    }

    @Test
    fun testOverlappingPolygonsMergeIntoOneBoundary() {
        val first = listOf(
            LatLngPoint(31.20, 29.90),
            LatLngPoint(31.24, 29.90),
            LatLngPoint(31.24, 29.94),
            LatLngPoint(31.20, 29.94)
        )
        val second = listOf(
            LatLngPoint(31.22, 29.92),
            LatLngPoint(31.26, 29.92),
            LatLngPoint(31.26, 29.96),
            LatLngPoint(31.22, 29.96)
        )

        val merged = ZoneEngine.mergePolygonGroups(listOf(first, second))

        assertTrue(merged.size == 1)
        assertTrue(
            ZoneEngine.isPointInPolygonGroups(
                LatLngPoint(31.205, 29.905),
                merged
            )
        )
        assertTrue(
            ZoneEngine.isPointInPolygonGroups(
                LatLngPoint(31.255, 29.955),
                merged
            )
        )
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
