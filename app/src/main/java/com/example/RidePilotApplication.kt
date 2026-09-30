package com.example

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.data.local.DataStoreManager
import com.example.data.repository.LogRepository
import com.example.data.repository.PricingRepository
import com.example.data.repository.SettingsRepository
import com.example.data.repository.ZoneRepository
import com.example.data.model.WorkZone
import com.example.domain.engine.AlexandriaZoneCatalog
import com.example.domain.engine.ZoneEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration

class RidePilotApplication : Application() {

    companion object {
        lateinit var instance: RidePilotApplication
            private set
    }

    private val applicationScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val database by lazy { AppDatabase.getDatabase(this, applicationScope) }
    val dataStoreManager by lazy { DataStoreManager(this) }

    val pricingRepository by lazy { PricingRepository(database) }
    val zoneRepository by lazy { ZoneRepository(database) }
    val logRepository by lazy { LogRepository(database) }
    val settingsRepository by lazy { SettingsRepository(dataStoreManager) }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize OSMDroid configuration for OpenStreetMap tiles
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid_prefs", MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName

        applicationScope.launch {
            val existingZones = zoneRepository.getAllZones().first()
            if (existingZones.isEmpty()) {
                val preset = AlexandriaZoneCatalog.find("selected_scope")
                val polygon = preset?.fixedPolygon
                if (!polygon.isNullOrEmpty()) {
                    zoneRepository.addZone(
                        WorkZone(
                            name = "الإسكندرية",
                            polygonJson = ZoneEngine.serializePolygonGroups(listOf(polygon)),
                            isEnabled = true,
                            allowedKeywords = "الإسكندرية,Alexandria"
                        )
                    )
                }
            }
        }
    }
}
