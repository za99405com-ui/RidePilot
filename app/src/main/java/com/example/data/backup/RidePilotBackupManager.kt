package com.example.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.example.RidePilotApplication
import com.example.data.model.AppTarget
import com.example.data.model.NegotiationConfig
import com.example.data.model.PricingBand
import com.example.data.model.PricingDistanceMode
import com.example.data.model.SwipeDirection
import com.example.data.model.WorkZone
import com.example.data.model.ZoneVerificationMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object RidePilotBackupManager {

    private const val SCHEMA_VERSION = 1
    private const val BACKUP_TYPE = "RidePilotFullBackup"

    data class RestoreResult(
        val zones: Int,
        val pricingBands: Int
    )

    suspend fun exportToUri(
        context: Context,
        uri: Uri
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val app = RidePilotApplication.instance
            val settings = app.settingsRepository

            val zones = app.zoneRepository.getAllZonesDirect()
            val bands = app.pricingRepository.getAllBandsDirect()

            val negotiation = settings.negotiationConfig.first()

            val root = JSONObject()
                .put("type", BACKUP_TYPE)
                .put("schemaVersion", SCHEMA_VERSION)
                .put("createdAt", System.currentTimeMillis())
                .put(
                    "settings",
                    JSONObject()
                        .put("pricingDistanceMode", settings.pricingDistanceMode.first().name)
                        .put("zoneVerificationMode", settings.zoneVerificationMode.first().name)
                        .put("swipeDirection", settings.swipeDirection.first().name)
                        .put("confidenceThreshold", settings.confidenceThreshold.first())
                        .put("maxPickupDistanceKm", settings.maxPickupDistanceKm.first())
                        .put("ocrFallbackEnabled", settings.ocrFallbackEnabled.first())
                        .put(
                            "negotiation",
                            JSONObject()
                                .put("startMarginEgp", negotiation.startMarginEgp)
                                .put("negotiationStepEgp", negotiation.negotiationStepEgp)
                                .put("maxNegotiationAttempts", negotiation.maxNegotiationAttempts)
                                .put("roundToEgp", negotiation.roundToEgp)
                                .put("autoAccept", negotiation.autoAccept)
                                .put("autoCounterOffer", negotiation.autoCounterOffer)
                        )
                )
                .put("zones", zonesToJson(zones))
                .put("pricingBands", bandsToJson(bands))

            context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                stream.writer(Charsets.UTF_8).use { writer ->
                    writer.write(root.toString(2))
                }
            } ?: error("تعذر فتح ملف النسخة الاحتياطية للكتابة")
        }
    }

    suspend fun restoreFromUri(
        context: Context,
        uri: Uri
    ): Result<RestoreResult> = withContext(Dispatchers.IO) {
        runCatching {
            val jsonText = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.reader(Charsets.UTF_8).use { it.readText() }
            } ?: error("تعذر فتح ملف النسخة الاحتياطية")

            val root = JSONObject(jsonText)

            require(root.optString("type") == BACKUP_TYPE) {
                "الملف ليس نسخة احتياطية صالحة لـ RidePilot"
            }

            val version = root.optInt("schemaVersion", -1)
            require(version in 1..SCHEMA_VERSION) {
                "إصدار النسخة الاحتياطية غير مدعوم"
            }

            val restoredZones = parseZones(root.optJSONArray("zones") ?: JSONArray())
            val restoredBands = parseBands(root.optJSONArray("pricingBands") ?: JSONArray())
            val settingsJson = root.optJSONObject("settings") ?: JSONObject()

            val app = RidePilotApplication.instance

            // Safety first: restoration never starts automation by itself.
            app.settingsRepository.setAutomationEnabled(false)

            app.database.withTransaction {
                app.database.workZoneDao().clearAllZones()
                if (restoredZones.isNotEmpty()) {
                    app.database.workZoneDao().insertZones(restoredZones)
                }

                app.database.pricingBandDao().clearBandsForApp(AppTarget.INDRIVE)
                app.database.pricingBandDao().clearBandsForApp(AppTarget.UBER)
                if (restoredBands.isNotEmpty()) {
                    app.database.pricingBandDao().insertBands(restoredBands)
                }
            }

            restoreSettings(app, settingsJson)

            RestoreResult(
                zones = restoredZones.size,
                pricingBands = restoredBands.size
            )
        }
    }

    private suspend fun restoreSettings(
        app: RidePilotApplication,
        json: JSONObject
    ) {
        val pricingMode = runCatching {
            PricingDistanceMode.valueOf(
                json.optString(
                    "pricingDistanceMode",
                    PricingDistanceMode.PICKUP_PLUS_TRIP.name
                )
            )
        }.getOrDefault(PricingDistanceMode.PICKUP_PLUS_TRIP)

        val zoneMode = runCatching {
            ZoneVerificationMode.valueOf(
                json.optString(
                    "zoneVerificationMode",
                    ZoneVerificationMode.BOTH_PICKUP_AND_DESTINATION.name
                )
            )
        }.getOrDefault(ZoneVerificationMode.BOTH_PICKUP_AND_DESTINATION)

        val swipeDirection = runCatching {
            SwipeDirection.valueOf(
                json.optString(
                    "swipeDirection",
                    SwipeDirection.SWIPE_LEFT.name
                )
            )
        }.getOrDefault(SwipeDirection.SWIPE_LEFT)

        app.settingsRepository.setPricingDistanceMode(pricingMode)
        app.settingsRepository.setZoneVerificationMode(zoneMode)
        app.settingsRepository.setSwipeDirection(swipeDirection)
        app.settingsRepository.setConfidenceThreshold(
            json.optInt("confidenceThreshold", 70).coerceIn(40, 100)
        )
        app.settingsRepository.setMaxPickupDistanceKm(
            json.optDouble("maxPickupDistanceKm", 5.0).coerceIn(0.5, 30.0)
        )
        app.settingsRepository.setOcrFallbackEnabled(
            json.optBoolean("ocrFallbackEnabled", false)
        )

        val negotiationJson = json.optJSONObject("negotiation") ?: JSONObject()
        app.settingsRepository.updateNegotiationConfig(
            NegotiationConfig(
                startMarginEgp = negotiationJson.optDouble("startMarginEgp", 15.0),
                negotiationStepEgp = negotiationJson.optDouble("negotiationStepEgp", 5.0),
                maxNegotiationAttempts =
                    negotiationJson.optInt("maxNegotiationAttempts", 3).coerceIn(1, 10),
                roundToEgp = negotiationJson.optDouble("roundToEgp", 5.0),
                autoAccept = negotiationJson.optBoolean("autoAccept", true),
                autoCounterOffer = negotiationJson.optBoolean("autoCounterOffer", true)
            )
        )
    }

    private fun zonesToJson(zones: List<WorkZone>): JSONArray =
        JSONArray().apply {
            zones.forEach { zone ->
                put(
                    JSONObject()
                        .put("name", zone.name)
                        .put("polygonJson", zone.polygonJson)
                        .put("isEnabled", zone.isEnabled)
                        .put("allowedKeywords", zone.allowedKeywords)
                )
            }
        }

    private fun bandsToJson(bands: List<PricingBand>): JSONArray =
        JSONArray().apply {
            bands.forEach { band ->
                put(
                    JSONObject()
                        .put("appTarget", band.appTarget.name)
                        .put("minKm", band.minKm)
                        .put("maxKm", band.maxKm)
                        .put("pricePerKm", band.pricePerKm)
                        .put("sortOrder", band.sortOrder)
                )
            }
        }

    private fun parseZones(array: JSONArray): List<WorkZone> =
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val name = obj.optString("name").trim()
                val polygonJson = obj.optString("polygonJson").trim()

                if (name.isBlank() || polygonJson.isBlank()) continue

                add(
                    WorkZone(
                        id = 0,
                        name = name,
                        polygonJson = polygonJson,
                        isEnabled = obj.optBoolean("isEnabled", true),
                        allowedKeywords = obj.optString("allowedKeywords")
                    )
                )
            }
        }

    private fun parseBands(array: JSONArray): List<PricingBand> =
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val target = runCatching {
                    AppTarget.valueOf(obj.optString("appTarget"))
                }.getOrNull() ?: continue

                val minKm = obj.optDouble("minKm", Double.NaN)
                val maxKm = obj.optDouble("maxKm", Double.NaN)
                val pricePerKm = obj.optDouble("pricePerKm", Double.NaN)

                if (
                    minKm.isNaN() ||
                    maxKm.isNaN() ||
                    pricePerKm.isNaN() ||
                    maxKm <= minKm ||
                    pricePerKm <= 0.0
                ) {
                    continue
                }

                add(
                    PricingBand(
                        id = 0,
                        appTarget = target,
                        minKm = minKm,
                        maxKm = maxKm,
                        pricePerKm = pricePerKm,
                        sortOrder = obj.optInt("sortOrder", i)
                    )
                )
            }
        }
}
