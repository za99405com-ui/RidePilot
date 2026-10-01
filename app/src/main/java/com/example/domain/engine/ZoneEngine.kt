package com.example.domain.engine

import android.content.Context
import com.example.data.model.LatLngPoint
import com.example.data.model.WorkZone
import com.example.data.model.ZoneVerificationMode
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryCollection
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.MultiPolygon
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.operation.union.UnaryUnionOp
import org.locationtech.jts.simplify.TopologyPreservingSimplifier
import java.util.concurrent.ConcurrentHashMap

object ZoneEngine {

    // Cache successful resolutions only. A failed lookup is allowed to retry
    // because inDrive sometimes exposes a partial address on the first read.
    private val addressCache = ConcurrentHashMap<String, LatLngPoint>()

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
     * Parses both the legacy single-polygon JSON and the newer multi-polygon JSON.
     *
     * Legacy:
     *   [ {lat/lng...}, {lat/lng...} ]
     *
     * Multi:
     *   [ [ {lat/lng...}, ... ], [ {lat/lng...}, ... ] ]
     */
    fun parsePolygonGroups(json: String): List<List<LatLngPoint>> {
        return try {
            val root = JSONArray(json)
            if (root.length() == 0) return emptyList()

            val first = root.opt(0)
            if (first is JSONObject) {
                listOf(parsePointArray(root))
            } else {
                buildList {
                    for (i in 0 until root.length()) {
                        val group = root.optJSONArray(i) ?: continue
                        val points = parsePointArray(group)
                        if (points.size >= 3) add(points)
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun parsePolygonJson(json: String): List<LatLngPoint> =
        parsePolygonGroups(json).firstOrNull().orEmpty()

    fun serializePolygonGroups(groups: List<List<LatLngPoint>>): String {
        val cleanGroups = groups.filter { it.size >= 3 }
        if (cleanGroups.size == 1) {
            return pointArrayToJson(cleanGroups.first()).toString()
        }

        val root = JSONArray()
        cleanGroups.forEach { root.put(pointArrayToJson(it)) }
        return root.toString()
    }

    fun isPointInPolygonGroups(
        point: LatLngPoint,
        groups: List<List<LatLngPoint>>
    ): Boolean = groups.any { it.size >= 3 && isPointInPolygon(point, it) }

    /**
     * Geometric union used by the UI's "دمج" action.
     * Adjacent/overlapping areas become one outline. Truly separated areas remain
     * multiple polygon parts but are still stored as one WorkZone.
     */
    fun mergePolygonGroups(groups: List<List<LatLngPoint>>): List<List<LatLngPoint>> {
        val geometryFactory = GeometryFactory()
        val polygons = groups.mapNotNull { points ->
            if (points.size < 3) return@mapNotNull null

            val coordinates = points.map {
                Coordinate(it.longitude, it.latitude)
            }.toMutableList()

            if (
                coordinates.first().x != coordinates.last().x ||
                coordinates.first().y != coordinates.last().y
            ) {
                coordinates.add(Coordinate(coordinates.first()))
            }

            try {
                geometryFactory.createPolygon(coordinates.toTypedArray()).let { polygon ->
                    if (polygon.isValid) polygon else polygon.buffer(0.0)
                }
            } catch (_: Exception) {
                null
            }
        }

        if (polygons.isEmpty()) return emptyList()

        return try {
            val unioned = UnaryUnionOp.union(polygons)
            val simplified = TopologyPreservingSimplifier.simplify(unioned, 0.00003)
            geometryToPolygonGroups(simplified)
        } catch (_: Exception) {
            groups
        }
    }

    private fun parsePointArray(arr: JSONArray): List<LatLngPoint> {
        val points = mutableListOf<LatLngPoint>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val lat = obj.optDouble("latitude", obj.optDouble("lat", Double.NaN))
            val lng = obj.optDouble("longitude", obj.optDouble("lng", Double.NaN))
            if (!lat.isNaN() && !lng.isNaN()) {
                points.add(LatLngPoint(lat, lng))
            }
        }
        return points
    }

    private fun pointArrayToJson(points: List<LatLngPoint>): JSONArray {
        val arr = JSONArray()
        points.forEach { point ->
            arr.put(
                JSONObject()
                    .put("latitude", point.latitude)
                    .put("longitude", point.longitude)
            )
        }
        return arr
    }

    private fun geometryToPolygonGroups(geometry: Geometry): List<List<LatLngPoint>> {
        val result = mutableListOf<List<LatLngPoint>>()

        fun addPolygon(polygon: Polygon) {
            val coords = polygon.exteriorRing.coordinates
            if (coords.size < 4) return

            val points = coords
                .dropLast(1)
                .map { LatLngPoint(it.y, it.x) }

            if (points.size >= 3) result.add(points)
        }

        when (geometry) {
            is Polygon -> addPolygon(geometry)
            is MultiPolygon -> {
                for (i in 0 until geometry.numGeometries) {
                    val polygon = geometry.getGeometryN(i) as? Polygon ?: continue
                    addPolygon(polygon)
                }
            }
            is GeometryCollection -> {
                for (i in 0 until geometry.numGeometries) {
                    result.addAll(geometryToPolygonGroups(geometry.getGeometryN(i)))
                }
            }
        }

        return result
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

        val result = InDriveRoutePreviewResolver.resolveAddress(
            context = context,
            address = cleanKey
        )

        if (result != null) {
            addressCache[cleanKey] = result
        }
        return result
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

        // A failed geocode is UNKNOWN, never OUTSIDE. Keyword matches were already
        // accepted above as a positive fast-path, but a keyword miss is not proof
        // that the address lies outside the polygon.
        val pickupConclusive = pickupPoint != null
        val destinationConclusive = destPoint != null

        val requiredDataConclusive = when (mode) {
            ZoneVerificationMode.PICKUP_ONLY -> pickupConclusive
            ZoneVerificationMode.DESTINATION_ONLY -> destinationConclusive
            ZoneVerificationMode.BOTH_PICKUP_AND_DESTINATION -> pickupConclusive && destinationConclusive
        }

        // Check each zone. One WorkZone may contain multiple merged polygon parts.
        for (zone in enabledZones) {
            val polygonGroups = parsePolygonGroups(zone.polygonJson)

            val pickupOk = when {
                pickupPoint != null && polygonGroups.isNotEmpty() ->
                    isPointInPolygonGroups(pickupPoint, polygonGroups)
                else -> matchesKeywords(pickupAddress, zone)
            }

            val destOk = when {
                destPoint != null && polygonGroups.isNotEmpty() ->
                    isPointInPolygonGroups(destPoint, polygonGroups)
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
