package com.example.domain.engine

import android.content.Context
import android.location.Geocoder
import com.example.data.model.LatLngPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object InDriveRoutePreviewResolver {

    data class Result(
        val pickupPoint: LatLngPoint,
        val destinationPoint: LatLngPoint,
        val routeDistanceKm: Double
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()

    private val geocodeCache = ConcurrentHashMap<String, LatLngPoint>()

    suspend fun resolve(
        context: Context,
        pickupAddress: String,
        destinationAddress: String
    ): Result? = withContext(Dispatchers.IO) {
        val pickup = geocode(context, pickupAddress) ?: return@withContext null
        val destination = geocode(context, destinationAddress) ?: return@withContext null

        val route = routeDistance(pickup, destination)
            ?: haversineKm(pickup, destination)

        Result(
            pickupPoint = pickup,
            destinationPoint = destination,
            routeDistanceKm = (route * 10.0).toInt() / 10.0
        )
    }

    private fun geocode(context: Context, raw: String): LatLngPoint? {
        val key = raw.trim().replace("\\s+".toRegex(), " ")
        geocodeCache[key]?.let { return it }

        val androidResult = try {
            val geocoder = Geocoder(context, Locale("ar", "EG"))
            @Suppress("DEPRECATION")
            val results = geocoder.getFromLocationName(key, 3).orEmpty()
            results.firstOrNull {
                it.latitude in AlexandriaZoneCatalog.alexandriaSouth..AlexandriaZoneCatalog.alexandriaNorth &&
                    it.longitude in AlexandriaZoneCatalog.alexandriaWest..AlexandriaZoneCatalog.alexandriaEast
            }?.let { LatLngPoint(it.latitude, it.longitude) }
        } catch (_: Exception) {
            null
        }

        if (androidResult != null) {
            geocodeCache[key] = androidResult
            return androidResult
        }

        val query = if (
            key.contains("alexandria", ignoreCase = true) ||
            key.contains("الإسكندرية")
        ) key else "${key}, Alexandria, Egypt"

        val url = HttpUrl.Builder()
            .scheme("https")
            .host("nominatim.openstreetmap.org")
            .addPathSegment("search")
            .addQueryParameter("format", "jsonv2")
            .addQueryParameter("q", query)
            .addQueryParameter("limit", "5")
            .addQueryParameter("countrycodes", "eg")
            .addQueryParameter("bounded", "1")
            .addQueryParameter(
                "viewbox",
                "${AlexandriaZoneCatalog.alexandriaWest}," +
                    "${AlexandriaZoneCatalog.alexandriaNorth}," +
                    "${AlexandriaZoneCatalog.alexandriaEast}," +
                    "${AlexandriaZoneCatalog.alexandriaSouth}"
            )
            .addQueryParameter("accept-language", "ar,en")
            .build()

        return try {
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
                    val lat = obj.optString("lat").toDoubleOrNull() ?: continue
                    val lon = obj.optString("lon").toDoubleOrNull() ?: continue

                    if (
                        lat in AlexandriaZoneCatalog.alexandriaSouth..AlexandriaZoneCatalog.alexandriaNorth &&
                        lon in AlexandriaZoneCatalog.alexandriaWest..AlexandriaZoneCatalog.alexandriaEast
                    ) {
                        val point = LatLngPoint(lat, lon)
                        geocodeCache[key] = point
                        return point
                    }
                }
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun routeDistance(a: LatLngPoint, b: LatLngPoint): Double? {
        val coordinates =
            "${a.longitude},${a.latitude};${b.longitude},${b.latitude}"

        val url = HttpUrl.Builder()
            .scheme("https")
            .host("router.project-osrm.org")
            .addPathSegment("route")
            .addPathSegment("v1")
            .addPathSegment("driving")
            .addPathSegment(coordinates)
            .addQueryParameter("overview", "false")
            .addQueryParameter("alternatives", "false")
            .addQueryParameter("steps", "false")
            .build()

        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "RidePilot/1.0 Android")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val root = JSONObject(response.body?.string().orEmpty())
                val routes = root.optJSONArray("routes") ?: return null
                val distanceMeters = routes.optJSONObject(0)?.optDouble("distance")
                    ?: return null
                distanceMeters / 1000.0
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun haversineKm(a: LatLngPoint, b: LatLngPoint): Double {
        val earthRadius = 6371.0088
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.longitude - a.longitude)

        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1) * cos(lat2) *
            sin(dLon / 2) * sin(dLon / 2)

        return earthRadius * 2 * atan2(sqrt(h), sqrt(1 - h))
    }
}
