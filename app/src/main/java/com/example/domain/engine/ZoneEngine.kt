package com.example.domain.engine

import android.content.Context
import android.location.Address
import android.location.Geocoder
import com.example.data.model.LatLngPoint
import com.example.data.model.WorkZone
import com.example.data.model.ZoneVerificationMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object ZoneEngine {

    // ConcurrentHashMap does not allow null values. Keep successful and failed
    // geocoding results separately so a failed lookup never crashes automation.
    private val addressCache = ConcurrentHashMap<String, LatLngPoint>()
    private val failedAddressCache = ConcurrentHashMap.newKeySet<String>()

    data class ZoneCheckResult(
        val isAllowed: Boolean,
        val matchedZone: WorkZone?,
        val pickupInside: Boolean,
        val destinationInside: Boolean,
        val reason: String,
        val isConclusive: Boolean = true
    )

    /**
     * Creates an approximately circular polygon around a center point.
     *
     * The stored WorkZone format is polygon-based, so automatic zones can use this
     * without a database migration. Radius is expressed in kilometers.
     */
    fun createCirclePolygon(
        center: LatLngPoint,
        radiusKm: Double,
        segments: Int = 48
    ): List<LatLngPoint> {
        val safeRadius = radiusKm.coerceAtLeast(0.1)
        val safeSegments = segments.coerceIn(12, 120)
        val earthRadiusKm = 6371.0088
        val angularDistance = safeRadius / earthRadiusKm
        val lat1 = Math.toRadians(center.latitude)
        val lon1 = Math.toRadians(center.longitude)

        return (0 until safeSegments).map { index ->
            val bearing = 2.0 * Math.PI * index / safeSegments
            val lat2 = kotlin.math.asin(
                kotlin.math.sin(lat1) * kotlin.math.cos(angularDistance) +
                    kotlin.math.cos(lat1) * kotlin.math.sin(angularDistance) * kotlin.math.cos(bearing)
            )
            val lon2 = lon1 + kotlin.math.atan2(
                kotlin.math.sin(bearing) * kotlin.math.sin(angularDistance) * kotlin.math.cos(lat1),
                kotlin.math.cos(angularDistance) - kotlin.math.sin(lat1) * kotlin.math.sin(lat2)
            )
            LatLngPoint(
                latitude = Math.toDegrees(lat2),
                longitude = Math.toDegrees(lon2)
            )
        }
    }

    /**
     * Checks if a point (lat, lng) lies inside a polygon using the Ray-Casting algorithm.
     */
    fun isPointInPolygon(point: LatLngPoint, polygon: List<LatLngPoint>): Boolean {
        if (polygon.size < 3) return false

        var intersectCount = 0
        val n = polygon.size

        for (i in 0 until n) {
            val p1 = polygon[i]
            val p2 = polygon[(i + 1) % n]

            // Check if horizontal ray from point crosses line segment (p1, p2)
            if ((p1.longitude > point.longitude) != (p2.longitude > point.longitude)) {
                val slope = (p2.latitude - p1.latitude) / (p2.longitude - p1.longitude)
                val testLat = p1.latitude + slope * (point.longitude - p1.longitude)
                if (point.latitude < testLat) {
                    intersectCount++
                }
            }
        }

        return (intersectCount % 2) == 1
    }

    /**
     * Parse polygon points from JSON string.
     */
    fun parsePolygonJson(json: String): List<LatLngPoint> {
        val points = mutableListOf<LatLngPoint>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val lat = obj.optDouble("latitude", obj.optDouble("lat", 0.0))
                val lng = obj.optDouble("longitude", obj.optDouble("lng", 0.0))
                points.add(LatLngPoint(lat, lng))
            }
        } catch (e: Exception) {
            // fallback empty
        }
        return points
    }

    /**
     * Checks text against zone's allowed keywords list (fallback when geocoding is unavailable or off-line).
     */
    fun matchesKeywords(addressText: String?, zone: WorkZone): Boolean {
        if (addressText.isNullOrBlank() || zone.allowedKeywords.isBlank()) return false
        val keywords = zone.allowedKeywords.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        val normalizedAddress = addressText.lowercase()
        return keywords.any { kw -> normalizedAddress.contains(kw) }
    }

    /**
     * Geocode an address to LatLngPoint using Android Geocoder with caching.
     */
    suspend fun geocodeAddress(context: Context, addressText: String?): LatLngPoint? {
        if (addressText.isNullOrBlank()) return null
        val cleanKey = addressText.trim().replace("\\s+".toRegex(), " ")

        addressCache[cleanKey]?.let { return it }
        if (failedAddressCache.contains(cleanKey)) return null

        return withContext(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(context, Locale("ar", "EG"))
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocationName(cleanKey, 1) ?: emptyList()
                val result = addresses.firstOrNull()?.let {
                    LatLngPoint(it.latitude, it.longitude)
                }

                if (result != null) {
                    addressCache[cleanKey] = result
                } else {
                    failedAddressCache.add(cleanKey)
                }
                result
            } catch (_: Exception) {
                failedAddressCache.add(cleanKey)
                null
            }
        }
    }

    /**
     * Evaluates whether a ride offer satisfies the user's active zones according to verification mode.
     */
    suspend fun evaluateOffer(
        context: Context,
        pickupAddress: String?,
        destinationAddress: String?,
        zones: List<WorkZone>,
        mode: ZoneVerificationMode
    ): ZoneCheckResult {
        val enabledZones = zones.filter { it.isEnabled }
        if (enabledZones.isEmpty()) {
            // If no active zones exist, accept all areas by default
            return ZoneCheckResult(
                isAllowed = true,
                matchedZone = null,
                pickupInside = true,
                destinationInside = true,
                reason = "لا توجد مناطق عمل مفعلة (مسموح بالكامل)"
            )
        }

        // Fast path: explicit zone keywords can often decide the ride immediately
        // without waiting for Android Geocoder.
        for (zone in enabledZones) {
            if (zone.allowedKeywords.isBlank()) continue
            val pickupKeywordOk = matchesKeywords(pickupAddress, zone)
            val destinationKeywordOk = matchesKeywords(destinationAddress, zone)
            val allowedByKeywords = when (mode) {
                ZoneVerificationMode.PICKUP_ONLY -> pickupKeywordOk
                ZoneVerificationMode.DESTINATION_ONLY -> destinationKeywordOk
                ZoneVerificationMode.BOTH_PICKUP_AND_DESTINATION -> pickupKeywordOk && destinationKeywordOk
            }
            if (allowedByKeywords) {
                return ZoneCheckResult(
                    isAllowed = true,
                    matchedZone = zone,
                    pickupInside = pickupKeywordOk,
                    destinationInside = destinationKeywordOk,
                    reason = "✅ داخل منطقة [${zone.name}] حسب الكلمات المفتاحية"
                )
            }
        }

        // Resolve pickup and destination in parallel; sequential geocoding doubled
        // the response time on first-seen addresses.
        val (pickupPoint, destPoint) = coroutineScope {
            val pickupDeferred = async { geocodeAddress(context, pickupAddress) }
            val destinationDeferred = async { geocodeAddress(context, destinationAddress) }
            pickupDeferred.await() to destinationDeferred.await()
        }

        // A failed geocode must not be treated as "outside the zone".
        // Keyword fallback is considered authoritative only when every active zone
        // has configured keywords for the required address.
        val pickupConclusive = pickupPoint != null ||
            (!pickupAddress.isNullOrBlank() && enabledZones.all { it.allowedKeywords.isNotBlank() })
        val destinationConclusive = destPoint != null ||
            (!destinationAddress.isNullOrBlank() && enabledZones.all { it.allowedKeywords.isNotBlank() })

        val requiredDataConclusive = when (mode) {
            ZoneVerificationMode.PICKUP_ONLY -> pickupConclusive
            ZoneVerificationMode.DESTINATION_ONLY -> destinationConclusive
            ZoneVerificationMode.BOTH_PICKUP_AND_DESTINATION -> pickupConclusive && destinationConclusive
        }

        // Check each zone
        for (zone in enabledZones) {
            val polygon = parsePolygonJson(zone.polygonJson)

            val pickupOk = when {
                pickupPoint != null && polygon.size >= 3 -> isPointInPolygon(pickupPoint, polygon)
                else -> matchesKeywords(pickupAddress, zone)
            }

            val destOk = when {
                destPoint != null && polygon.size >= 3 -> isPointInPolygon(destPoint, polygon)
                else -> matchesKeywords(destinationAddress, zone)
            }

            val allowedForThisZone = when (mode) {
                ZoneVerificationMode.PICKUP_ONLY -> pickupOk
                ZoneVerificationMode.DESTINATION_ONLY -> destOk
                ZoneVerificationMode.BOTH_PICKUP_AND_DESTINATION -> pickupOk && destOk
            }

            if (allowedForThisZone) {
                val modeDesc = when (mode) {
                    ZoneVerificationMode.PICKUP_ONLY -> "نقطة الركوب داخل Zone"
                    ZoneVerificationMode.DESTINATION_ONLY -> "الوجهة داخل Zone"
                    ZoneVerificationMode.BOTH_PICKUP_AND_DESTINATION -> "الركوب والوجهة كلاهما داخل Zone"
                }
                return ZoneCheckResult(
                    isAllowed = true,
                    matchedZone = zone,
                    pickupInside = pickupOk,
                    destinationInside = destOk,
                    reason = "✅ داخل منطقة [${zone.name}] ($modeDesc)"
                )
            }
        }

        if (!requiredDataConclusive) {
            return ZoneCheckResult(
                isAllowed = false,
                matchedZone = null,
                pickupInside = false,
                destinationInside = false,
                reason = "⚠️ تعذر التحقق من الـZone بشكل موثوق (عنوان أو Geocoding غير متاح)",
                isConclusive = false
            )
        }

        val failReason = when (mode) {
            ZoneVerificationMode.PICKUP_ONLY -> "❌ نقطة الركوب خارج مناطق العمل المفعلة"
            ZoneVerificationMode.DESTINATION_ONLY -> "❌ الوجهة خارج مناطق العمل المفعلة"
            ZoneVerificationMode.BOTH_PICKUP_AND_DESTINATION -> "❌ الركوب أو الوجهة خارج مناطق العمل المفعلة"
        }

        return ZoneCheckResult(
            isAllowed = false,
            matchedZone = null,
            pickupInside = false,
            destinationInside = false,
            reason = failReason,
            isConclusive = true
        )
    }
}
