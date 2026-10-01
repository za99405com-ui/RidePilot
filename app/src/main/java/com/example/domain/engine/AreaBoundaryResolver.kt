package com.example.domain.engine

import com.example.data.model.LatLngPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Resolves a tapped map point to a real OpenStreetMap/Nominatim area boundary.
 * Runs only on explicit user actions and is not used by ride automation itself.
 */
object AreaBoundaryResolver {

    data class RecognizedArea(
        val name: String,
        val displayName: String,
        val osmKey: String,
        val polygons: List<List<LatLngPoint>>
    )

    private val client = OkHttpClient.Builder()
        .callTimeout(10, TimeUnit.SECONDS)
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val namedAreaCache = ConcurrentHashMap<String, RecognizedArea>()

    suspend fun resolve(latitude: Double, longitude: Double): Result<RecognizedArea> =
        withContext(Dispatchers.IO) {
            runCatching {
                for (zoom in listOf(14, 13, 12, 11)) {
                    val json = reverse(latitude, longitude, zoom) ?: continue
                    parseResult(json, latitude, longitude)?.let {
                        return@runCatching it
                    }

                    searchBoundaryFromAddress(json, latitude, longitude)?.let {
                        return@runCatching it
                    }
                }

                error("تعذر العثور على حدود منطقة واضحة عند هذه النقطة")
            }
        }

    /**
     * Resolves a known Alexandria neighbourhood by name and returns only real
     * polygon geometry. No rectangle/viewport fallback is ever generated.
     */
    suspend fun resolveByName(areaName: String): Result<RecognizedArea> =
        withContext(Dispatchers.IO) {
            runCatching {
                val key = areaName.trim()
                namedAreaCache[key]?.let { return@runCatching it }

                val query = "${key}, الإسكندرية, مصر"
                val url = HttpUrl.Builder()
                    .scheme("https")
                    .host("nominatim.openstreetmap.org")
                    .addPathSegment("search")
                    .addQueryParameter("format", "jsonv2")
                    .addQueryParameter("q", query)
                    .addQueryParameter("limit", "10")
                    .addQueryParameter("addressdetails", "1")
                    .addQueryParameter("polygon_geojson", "1")
                    .addQueryParameter("accept-language", "ar,en")
                    .build()

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "RidePilot/1.0 Android")
                    .header("Accept", "application/json")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        error("تعذر الاتصال بمصدر الحدود")
                    }

                    val body = response.body?.string().orEmpty()
                    if (body.isBlank()) error("لم يتم العثور على بيانات")

                    val arr = JSONArray(body)
                    val candidates = mutableListOf<RecognizedArea>()

                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val display = obj.optString("display_name")
                        if (
                            !display.contains("الإسكندرية", ignoreCase = true) &&
                            !display.contains("Alexandria", ignoreCase = true)
                        ) {
                            continue
                        }

                        parseResult(
                            obj,
                            obj.optDouble("lat", 0.0),
                            obj.optDouble("lon", 0.0)
                        )?.let { candidates.add(it) }
                    }

                    val exact = candidates.firstOrNull {
                        normalizeName(it.name) == normalizeName(key)
                    }

                    val chosen = exact ?: candidates.firstOrNull()
                        ?: error("المنطقة موجودة بالاسم لكن لا توجد لها حدود Polygon متاحة")

                    namedAreaCache[key] = chosen
                    chosen
                }
            }
        }

    private fun normalizeName(value: String): String =
        value
            .trim()
            .lowercase()
            .replace("أ", "ا")
            .replace("إ", "ا")
            .replace("آ", "ا")
            .replace("ة", "ه")
            .replace("\\s+".toRegex(), " ")

    private fun reverse(lat: Double, lon: Double, zoom: Int): JSONObject? {
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("nominatim.openstreetmap.org")
            .addPathSegment("reverse")
            .addQueryParameter("format", "jsonv2")
            .addQueryParameter("lat", lat.toString())
            .addQueryParameter("lon", lon.toString())
            .addQueryParameter("zoom", zoom.toString())
            .addQueryParameter("addressdetails", "1")
            .addQueryParameter("polygon_geojson", "1")
            .addQueryParameter("accept-language", "ar,en")
            .build()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "RidePilot/1.0 Android")
            .header("Accept", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return null
            return JSONObject(body)
        }
    }

    private fun searchBoundaryFromAddress(
        reverseJson: JSONObject,
        lat: Double,
        lon: Double
    ): RecognizedArea? {
        val address = reverseJson.optJSONObject("address") ?: return null
        val candidateName = firstNonBlank(
            reverseJson.optString("name"),
            address.optString("neighbourhood"),
            address.optString("suburb"),
            address.optString("city_district"),
            address.optString("quarter"),
            address.optString("town"),
            address.optString("city")
        ) ?: return null

        val city = firstNonBlank(
            address.optString("city"),
            address.optString("town"),
            address.optString("county")
        )
        val state = address.optString("state")
        val country = address.optString("country")

        val query = listOfNotNull(
            candidateName,
            city?.takeIf { !it.equals(candidateName, ignoreCase = true) },
            state.takeIf { it.isNotBlank() },
            country.takeIf { it.isNotBlank() }
        ).joinToString(", ")

        val url = HttpUrl.Builder()
            .scheme("https")
            .host("nominatim.openstreetmap.org")
            .addPathSegment("search")
            .addQueryParameter("format", "jsonv2")
            .addQueryParameter("q", query)
            .addQueryParameter("limit", "6")
            .addQueryParameter("addressdetails", "1")
            .addQueryParameter("polygon_geojson", "1")
            .addQueryParameter("accept-language", "ar,en")
            .build()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "RidePilot/1.0 Android")
            .header("Accept", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val arr = JSONArray(response.body?.string().orEmpty())

            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val area = parseResult(obj, lat, lon) ?: continue
                val clicked = LatLngPoint(lat, lon)
                if (area.polygons.any { ZoneEngine.isPointInPolygon(clicked, it) }) {
                    return area
                }
            }

            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                parseResult(obj, lat, lon)?.let { return it }
            }
        }

        return null
    }

    private fun parseResult(
        json: JSONObject,
        lat: Double,
        lon: Double
    ): RecognizedArea? {
        val geo = json.optJSONObject("geojson") ?: return null
        val polygons = parseGeoJson(geo)
            .map { simplifyBoundary(it, maxPoints = 64) }
            .filter { it.size >= 3 }

        if (polygons.isEmpty()) return null

        val address = json.optJSONObject("address")
        val name = firstNonBlank(
            json.optString("name"),
            address?.optString("neighbourhood"),
            address?.optString("suburb"),
            address?.optString("city_district"),
            address?.optString("quarter"),
            address?.optString("town"),
            address?.optString("city")
        ) ?: "منطقة محددة"

        val display = json.optString("display_name").ifBlank { name }
        val osmType = json.optString("osm_type").ifBlank { "area" }
        val osmId = json.optLong("osm_id", 0L)
        val key = if (osmId != 0L) "${osmType}:${osmId}" else "${name}:${lat}:${lon}"

        return RecognizedArea(
            name = name,
            displayName = display,
            osmKey = key,
            polygons = polygons
        )
    }

    private fun parseGeoJson(geo: JSONObject): List<List<LatLngPoint>> {
        val type = geo.optString("type")
        val coordinates = geo.optJSONArray("coordinates") ?: return emptyList()

        return when (type) {
            "Polygon" -> {
                val outer = coordinates.optJSONArray(0) ?: return emptyList()
                listOf(parseRing(outer))
            }

            "MultiPolygon" -> {
                buildList {
                    for (i in 0 until coordinates.length()) {
                        val polygon = coordinates.optJSONArray(i) ?: continue
                        val outer = polygon.optJSONArray(0) ?: continue
                        val ring = parseRing(outer)
                        if (ring.size >= 3) add(ring)
                    }
                }
            }

            else -> emptyList()
        }
    }

    private fun parseRing(ring: JSONArray): List<LatLngPoint> {
        val points = mutableListOf<LatLngPoint>()
        for (i in 0 until ring.length()) {
            val pair = ring.optJSONArray(i) ?: continue
            if (pair.length() < 2) continue
            val lon = pair.optDouble(0, Double.NaN)
            val lat = pair.optDouble(1, Double.NaN)
            if (!lat.isNaN() && !lon.isNaN()) {
                points.add(LatLngPoint(lat, lon))
            }
        }

        if (
            points.size > 1 &&
            points.first().latitude == points.last().latitude &&
            points.first().longitude == points.last().longitude
        ) {
            points.removeAt(points.lastIndex)
        }

        return points
    }

    private fun simplifyBoundary(
        input: List<LatLngPoint>,
        maxPoints: Int
    ): List<LatLngPoint> {
        if (input.size <= maxPoints) return input

        var epsilon = 0.00004
        var simplified = douglasPeucker(input, epsilon)

        while (simplified.size > maxPoints && epsilon < 0.003) {
            epsilon *= 1.6
            simplified = douglasPeucker(input, epsilon)
        }

        if (simplified.size <= maxPoints) return simplified

        val stride = input.size.toDouble() / maxPoints
        return (0 until maxPoints).map { index ->
            input[(index * stride).toInt().coerceAtMost(input.lastIndex)]
        }
    }

    private fun douglasPeucker(
        points: List<LatLngPoint>,
        epsilon: Double
    ): List<LatLngPoint> {
        if (points.size < 3) return points

        var maxDistance = 0.0
        var index = 0

        for (i in 1 until points.lastIndex) {
            val distance = perpendicularDistance(
                points[i],
                points.first(),
                points.last()
            )
            if (distance > maxDistance) {
                index = i
                maxDistance = distance
            }
        }

        if (maxDistance <= epsilon) {
            return listOf(points.first(), points.last())
        }

        val left = douglasPeucker(points.subList(0, index + 1), epsilon)
        val right = douglasPeucker(points.subList(index, points.size), epsilon)

        return left.dropLast(1) + right
    }

    private fun perpendicularDistance(
        point: LatLngPoint,
        start: LatLngPoint,
        end: LatLngPoint
    ): Double {
        val x = point.longitude
        val y = point.latitude
        val x1 = start.longitude
        val y1 = start.latitude
        val x2 = end.longitude
        val y2 = end.latitude

        val dx = x2 - x1
        val dy = y2 - y1
        if (abs(dx) < 1e-12 && abs(dy) < 1e-12) {
            return sqrt((x - x1) * (x - x1) + (y - y1) * (y - y1))
        }

        val numerator = abs(dy * x - dx * y + x2 * y1 - y2 * x1)
        val denominator = sqrt(dx * dx + dy * dy)
        return numerator / denominator
    }

    private fun firstNonBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() }?.trim()
}
