package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pricing_bands")
data class PricingBand(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val appTarget: AppTarget,
    val minKm: Double,
    val maxKm: Double,
    val pricePerKm: Double,
    val sortOrder: Int = 0
)

@Entity(tableName = "work_zones")
data class WorkZone(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val polygonJson: String, // JSON array of LatLngPoint
    val isEnabled: Boolean = true,
    val allowedKeywords: String = "" // comma separated keywords
)

@Entity(tableName = "app_logs")
data class AppLogEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val app: AppTarget,
    val screen: String,
    val detectedPrice: Double? = null,
    val detectedDistance: Double? = null,
    val zoneResult: String,
    val minCalculated: Double? = null,
    val action: String,
    val reason: String,
    val confidence: Int
)

data class NegotiationConfig(
    val startMarginEgp: Double = 15.0,
    val negotiationStepEgp: Double = 5.0,
    val maxNegotiationAttempts: Int = 3,
    val roundToEgp: Double = 5.0,
    val autoAccept: Boolean = false,
    val autoCounterOffer: Boolean = true
)

enum class AutomationState {
    IDLE,
    ENSURE_OFFLINE,
    REQUESTS_PAGE,
    READING_ORDERS,
    OPENING_ORDER,
    CHECKING_ZONE,
    CALCULATING_PRICE,
    NEGOTIATING,
    WAITING_RESPONSE,
    RETURN_TO_REQUESTS,
    PAUSED,
    ERROR_RECOVERY
}

data class RecognitionProfile(
    val deviceModel: String = "Samsung Galaxy A35",
    val language: String = "ar",
    val isRtl: Boolean = true,
    val isDarkMode: Boolean = true,
    val fontScale: Float = 1.0f
)
