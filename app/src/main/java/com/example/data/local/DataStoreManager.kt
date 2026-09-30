package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.NegotiationConfig
import com.example.data.model.PricingDistanceMode
import com.example.data.model.SwipeDirection
import com.example.data.model.ZoneVerificationMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "ridepilot_settings")

class DataStoreManager(private val context: Context) {

    private object PreferencesKeys {
        val AUTOMATION_ENABLED = booleanPreferencesKey("automation_enabled")
        val EMERGENCY_STOP = booleanPreferencesKey("emergency_stop")
        val OVERLAY_ENABLED = booleanPreferencesKey("overlay_enabled")
        val OCR_FALLBACK_ENABLED = booleanPreferencesKey("ocr_fallback_enabled")

        val PRICING_DISTANCE_MODE = stringPreferencesKey("pricing_distance_mode")
        val ZONE_VERIFICATION_MODE = stringPreferencesKey("zone_verification_mode")
        val SWIPE_DIRECTION = stringPreferencesKey("swipe_direction")

        val NEGOTIATION_START_MARGIN = doublePreferencesKey("negotiation_start_margin")
        val NEGOTIATION_STEP = doublePreferencesKey("negotiation_step")
        val NEGOTIATION_MAX_ATTEMPTS = intPreferencesKey("negotiation_max_attempts")
        val NEGOTIATION_ROUND_TO = doublePreferencesKey("negotiation_round_to")
        val NEGOTIATION_AUTO_ACCEPT = booleanPreferencesKey("negotiation_auto_accept")
        val NEGOTIATION_AUTO_COUNTER = booleanPreferencesKey("negotiation_auto_counter")

        val CONFIDENCE_THRESHOLD = intPreferencesKey("confidence_threshold")
        val V3_AUTOMATION_DEFAULTS_APPLIED = booleanPreferencesKey("v3_automation_defaults_applied")
    }

    val automationEnabled: Flow<Boolean> = context.dataStore.data.map {
        it[PreferencesKeys.AUTOMATION_ENABLED] ?: false
    }

    val emergencyStop: Flow<Boolean> = context.dataStore.data.map {
        it[PreferencesKeys.EMERGENCY_STOP] ?: false
    }

    val overlayEnabled: Flow<Boolean> = context.dataStore.data.map {
        it[PreferencesKeys.OVERLAY_ENABLED] ?: true
    }

    val ocrFallbackEnabled: Flow<Boolean> = context.dataStore.data.map {
        it[PreferencesKeys.OCR_FALLBACK_ENABLED] ?: false
    }

    val pricingDistanceMode: Flow<PricingDistanceMode> = context.dataStore.data.map {
        val name = it[PreferencesKeys.PRICING_DISTANCE_MODE] ?: PricingDistanceMode.PICKUP_PLUS_TRIP.name
        try { PricingDistanceMode.valueOf(name) } catch (e: Exception) { PricingDistanceMode.PICKUP_PLUS_TRIP }
    }

    val zoneVerificationMode: Flow<ZoneVerificationMode> = context.dataStore.data.map {
        val name = it[PreferencesKeys.ZONE_VERIFICATION_MODE] ?: ZoneVerificationMode.BOTH_PICKUP_AND_DESTINATION.name
        try { ZoneVerificationMode.valueOf(name) } catch (e: Exception) { ZoneVerificationMode.BOTH_PICKUP_AND_DESTINATION }
    }

    val swipeDirection: Flow<SwipeDirection> = context.dataStore.data.map {
        val name = it[PreferencesKeys.SWIPE_DIRECTION] ?: SwipeDirection.SWIPE_LEFT.name
        try { SwipeDirection.valueOf(name) } catch (e: Exception) { SwipeDirection.SWIPE_LEFT }
    }

    val confidenceThreshold: Flow<Int> = context.dataStore.data.map {
        it[PreferencesKeys.CONFIDENCE_THRESHOLD] ?: 70
    }

    val negotiationConfig: Flow<NegotiationConfig> = context.dataStore.data.map {
        NegotiationConfig(
            startMarginEgp = it[PreferencesKeys.NEGOTIATION_START_MARGIN] ?: 15.0,
            negotiationStepEgp = it[PreferencesKeys.NEGOTIATION_STEP] ?: 5.0,
            maxNegotiationAttempts = it[PreferencesKeys.NEGOTIATION_MAX_ATTEMPTS] ?: 3,
            roundToEgp = it[PreferencesKeys.NEGOTIATION_ROUND_TO] ?: 5.0,
            autoAccept = it[PreferencesKeys.NEGOTIATION_AUTO_ACCEPT] ?: true,
            autoCounterOffer = it[PreferencesKeys.NEGOTIATION_AUTO_COUNTER] ?: true
        )
    }

    /**
     * Low-level automation toggle. A hard-stopped session cannot be re-enabled
     * through this method; only startAutomationFromApp() may clear HARD STOP.
     */
    suspend fun setAutomationEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            if (enabled && prefs[PreferencesKeys.EMERGENCY_STOP] == true) {
                prefs[PreferencesKeys.AUTOMATION_ENABLED] = false
            } else {
                prefs[PreferencesKeys.AUTOMATION_ENABLED] = enabled
            }
        }
    }

    suspend fun setEmergencyStop(stopped: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.EMERGENCY_STOP] = stopped
            if (stopped) {
                prefs[PreferencesKeys.AUTOMATION_ENABLED] = false
                prefs[PreferencesKeys.OVERLAY_ENABLED] = false
            }
        }
    }

    /**
     * The only path that clears a hard stop and starts RidePilot again.
     * This method is intentionally called from the RidePilot app UI, never
     * from the floating overlay.
     */
    suspend fun startAutomationFromApp() {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.EMERGENCY_STOP] = false
            prefs[PreferencesKeys.OVERLAY_ENABLED] = true
            prefs[PreferencesKeys.AUTOMATION_ENABLED] = true
        }
    }

    /**
     * Temporary pause: keep the floating control available, but do not perform
     * any ride automation until resumed.
     */
    suspend fun pauseAutomation() {
        context.dataStore.edit { prefs ->
            if (prefs[PreferencesKeys.EMERGENCY_STOP] != true) {
                prefs[PreferencesKeys.AUTOMATION_ENABLED] = false
                prefs[PreferencesKeys.OVERLAY_ENABLED] = true
            }
        }
    }

    /**
     * Resume is allowed from the floating control only when HARD STOP is not set.
     */
    suspend fun resumeAutomationFromOverlay() {
        context.dataStore.edit { prefs ->
            if (prefs[PreferencesKeys.EMERGENCY_STOP] != true) {
                prefs[PreferencesKeys.OVERLAY_ENABLED] = true
                prefs[PreferencesKeys.AUTOMATION_ENABLED] = true
            }
        }
    }

    /**
     * Full STOP. The overlay is disabled too, so Accessibility events cannot
     * resurrect it. Restart requires startAutomationFromApp().
     */
    suspend fun hardStop() {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.EMERGENCY_STOP] = true
            prefs[PreferencesKeys.AUTOMATION_ENABLED] = false
            prefs[PreferencesKeys.OVERLAY_ENABLED] = false
        }
    }

    suspend fun setOverlayEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            if (enabled && prefs[PreferencesKeys.EMERGENCY_STOP] == true) {
                prefs[PreferencesKeys.OVERLAY_ENABLED] = false
            } else {
                prefs[PreferencesKeys.OVERLAY_ENABLED] = enabled
            }
        }
    }

    suspend fun setOcrFallbackEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.OCR_FALLBACK_ENABLED] = enabled }
    }

    suspend fun setPricingDistanceMode(mode: PricingDistanceMode) {
        context.dataStore.edit { it[PreferencesKeys.PRICING_DISTANCE_MODE] = mode.name }
    }

    suspend fun setZoneVerificationMode(mode: ZoneVerificationMode) {
        context.dataStore.edit { it[PreferencesKeys.ZONE_VERIFICATION_MODE] = mode.name }
    }

    suspend fun setSwipeDirection(dir: SwipeDirection) {
        context.dataStore.edit { it[PreferencesKeys.SWIPE_DIRECTION] = dir.name }
    }

    suspend fun setConfidenceThreshold(threshold: Int) {
        context.dataStore.edit { it[PreferencesKeys.CONFIDENCE_THRESHOLD] = threshold }
    }

    suspend fun applyV3AutomationDefaultsOnce() {
        context.dataStore.edit { prefs ->
            if (prefs[PreferencesKeys.V3_AUTOMATION_DEFAULTS_APPLIED] != true) {
                prefs[PreferencesKeys.NEGOTIATION_AUTO_ACCEPT] = true
                prefs[PreferencesKeys.NEGOTIATION_AUTO_COUNTER] = true
                prefs[PreferencesKeys.V3_AUTOMATION_DEFAULTS_APPLIED] = true
            }
        }
    }

    suspend fun updateNegotiationConfig(config: NegotiationConfig) {
        context.dataStore.edit {
            it[PreferencesKeys.NEGOTIATION_START_MARGIN] = config.startMarginEgp
            it[PreferencesKeys.NEGOTIATION_STEP] = config.negotiationStepEgp
            it[PreferencesKeys.NEGOTIATION_MAX_ATTEMPTS] = config.maxNegotiationAttempts
            it[PreferencesKeys.NEGOTIATION_ROUND_TO] = config.roundToEgp
            it[PreferencesKeys.NEGOTIATION_AUTO_ACCEPT] = config.autoAccept
            it[PreferencesKeys.NEGOTIATION_AUTO_COUNTER] = config.autoCounterOffer
        }
    }
}
