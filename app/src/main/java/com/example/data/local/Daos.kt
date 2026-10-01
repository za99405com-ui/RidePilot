package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.AppLogEntry
import com.example.data.model.AppTarget
import com.example.data.model.PricingBand
import com.example.data.model.WorkZone
import kotlinx.coroutines.flow.Flow

@Dao
interface PricingBandDao {
    @Query("SELECT * FROM pricing_bands WHERE appTarget = :appTarget ORDER BY minKm ASC")
    fun getBandsForApp(appTarget: AppTarget): Flow<List<PricingBand>>

    @Query("SELECT * FROM pricing_bands WHERE appTarget = :appTarget ORDER BY minKm ASC")
    suspend fun getBandsForAppDirect(appTarget: AppTarget): List<PricingBand>

    @Query("SELECT * FROM pricing_bands ORDER BY appTarget, minKm ASC")
    suspend fun getAllBandsDirect(): List<PricingBand>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBand(band: PricingBand): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBands(bands: List<PricingBand>)

    @Update
    suspend fun updateBand(band: PricingBand)

    @Delete
    suspend fun deleteBand(band: PricingBand)

    @Query("DELETE FROM pricing_bands WHERE appTarget = :appTarget")
    suspend fun clearBandsForApp(appTarget: AppTarget)
}

@Dao
interface WorkZoneDao {
    @Query("SELECT * FROM work_zones ORDER BY id DESC")
    fun getAllZones(): Flow<List<WorkZone>>

    @Query("SELECT * FROM work_zones WHERE isEnabled = 1")
    suspend fun getEnabledZones(): List<WorkZone>

    @Query("SELECT * FROM work_zones ORDER BY id ASC")
    suspend fun getAllZonesDirect(): List<WorkZone>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertZone(zone: WorkZone): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertZones(zones: List<WorkZone>)

    @Update
    suspend fun updateZone(zone: WorkZone)

    @Delete
    suspend fun deleteZone(zone: WorkZone)

    @Query("DELETE FROM work_zones WHERE id = :id")
    suspend fun deleteZoneById(id: Long)

    @Query("DELETE FROM work_zones")
    suspend fun clearAllZones()
}

@Dao
interface AppLogDao {
    @Query("SELECT * FROM app_logs ORDER BY timestamp DESC LIMIT 200")
    fun getRecentLogs(): Flow<List<AppLogEntry>>

    @Query("SELECT * FROM app_logs ORDER BY timestamp DESC")
    suspend fun getAllLogsDirect(): List<AppLogEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(entry: AppLogEntry)

    @Query("DELETE FROM app_logs")
    suspend fun clearAllLogs()
}
