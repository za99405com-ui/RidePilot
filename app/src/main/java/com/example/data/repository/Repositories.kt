package com.example.data.repository

import com.example.data.local.AppDatabase
import com.example.data.local.DataStoreManager
import com.example.data.model.AppLogEntry
import com.example.data.model.AppTarget
import com.example.data.model.NegotiationConfig
import com.example.data.model.PricingBand
import com.example.data.model.PricingDistanceMode
import com.example.data.model.SwipeDirection
import com.example.data.model.WorkZone
import com.example.data.model.ZoneVerificationMode
import kotlinx.coroutines.flow.Flow

class PricingRepository(private val db: AppDatabase) {
    fun getBands(app: AppTarget): Flow<List<PricingBand>> = db.pricingBandDao().getBandsForApp(app)
    suspend fun getBandsDirect(app: AppTarget): List<PricingBand> = db.pricingBandDao().getBandsForAppDirect(app)
    suspend fun getAllBandsDirect(): List<PricingBand> = db.pricingBandDao().getAllBandsDirect()
    suspend fun addBand(band: PricingBand) = db.pricingBandDao().insertBand(band)
    suspend fun addBands(bands: List<PricingBand>) = db.pricingBandDao().insertBands(bands)
    suspend fun clearBandsForApp(app: AppTarget) = db.pricingBandDao().clearBandsForApp(app)
    suspend fun updateBand(band: PricingBand) = db.pricingBandDao().updateBand(band)
    suspend fun deleteBand(band: PricingBand) = db.pricingBandDao().deleteBand(band)
}

class ZoneRepository(private val db: AppDatabase) {
    fun getAllZones(): Flow<List<WorkZone>> = db.workZoneDao().getAllZones()
    suspend fun getEnabledZones(): List<WorkZone> = db.workZoneDao().getEnabledZones()
    suspend fun getAllZonesDirect(): List<WorkZone> = db.workZoneDao().getAllZonesDirect()
    suspend fun addZone(zone: WorkZone) = db.workZoneDao().insertZone(zone)
    suspend fun addZones(zones: List<WorkZone>) = db.workZoneDao().insertZones(zones)
    suspend fun clearAllZones() = db.workZoneDao().clearAllZones()
    suspend fun updateZone(zone: WorkZone) = db.workZoneDao().updateZone(zone)
    suspend fun deleteZone(zone: WorkZone) = db.workZoneDao().deleteZone(zone)
    suspend fun deleteZoneById(id: Long) = db.workZoneDao().deleteZoneById(id)
}

class LogRepository(private val db: AppDatabase) {
    fun getRecentLogs(): Flow<List<AppLogEntry>> = db.appLogDao().getRecentLogs()
    suspend fun getAllLogs(): List<AppLogEntry> = db.appLogDao().getAllLogsDirect()
    suspend fun log(entry: AppLogEntry) = db.appLogDao().insertLog(entry)
    suspend fun clearLogs() = db.appLogDao().clearAllLogs()
}

class SettingsRepository(private val dataStoreManager: DataStoreManager) {
    val automationEnabled: Flow<Boolean> = dataStoreManager.automationEnabled
    val emergencyStop: Flow<Boolean> = dataStoreManager.emergencyStop
    val overlayEnabled: Flow<Boolean> = dataStoreManager.overlayEnabled
    val ocrFallbackEnabled: Flow<Boolean> = dataStoreManager.ocrFallbackEnabled
    val pricingDistanceMode: Flow<PricingDistanceMode> = dataStoreManager.pricingDistanceMode
    val zoneVerificationMode: Flow<ZoneVerificationMode> = dataStoreManager.zoneVerificationMode
    val swipeDirection: Flow<SwipeDirection> = dataStoreManager.swipeDirection
    val confidenceThreshold: Flow<Int> = dataStoreManager.confidenceThreshold
    val maxPickupDistanceKm: Flow<Double> = dataStoreManager.maxPickupDistanceKm
    val negotiationConfig: Flow<NegotiationConfig> = dataStoreManager.negotiationConfig

    suspend fun setAutomationEnabled(enabled: Boolean) = dataStoreManager.setAutomationEnabled(enabled)
    suspend fun setEmergencyStop(stopped: Boolean) = dataStoreManager.setEmergencyStop(stopped)
    suspend fun startAutomationFromApp() = dataStoreManager.startAutomationFromApp()
    suspend fun pauseAutomation() = dataStoreManager.pauseAutomation()
    suspend fun resumeAutomationFromOverlay() = dataStoreManager.resumeAutomationFromOverlay()
    suspend fun hardStop() = dataStoreManager.hardStop()
    suspend fun setOverlayEnabled(enabled: Boolean) = dataStoreManager.setOverlayEnabled(enabled)
    suspend fun setOcrFallbackEnabled(enabled: Boolean) = dataStoreManager.setOcrFallbackEnabled(enabled)
    suspend fun setPricingDistanceMode(mode: PricingDistanceMode) = dataStoreManager.setPricingDistanceMode(mode)
    suspend fun setZoneVerificationMode(mode: ZoneVerificationMode) = dataStoreManager.setZoneVerificationMode(mode)
    suspend fun setSwipeDirection(dir: SwipeDirection) = dataStoreManager.setSwipeDirection(dir)
    suspend fun setConfidenceThreshold(threshold: Int) = dataStoreManager.setConfidenceThreshold(threshold)
    suspend fun setMaxPickupDistanceKm(distanceKm: Double) = dataStoreManager.setMaxPickupDistanceKm(distanceKm)
    suspend fun applyV3AutomationDefaultsOnce() = dataStoreManager.applyV3AutomationDefaultsOnce()
    suspend fun updateNegotiationConfig(config: NegotiationConfig) = dataStoreManager.updateNegotiationConfig(config)
}
