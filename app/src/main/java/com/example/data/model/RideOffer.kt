package com.example.data.model

enum class AppTarget {
    UBER,
    INDRIVE
}

enum class PricingDistanceMode {
    TRIP_ONLY,
    PICKUP_PLUS_TRIP
}

enum class ZoneVerificationMode {
    PICKUP_ONLY,
    DESTINATION_ONLY,
    BOTH_PICKUP_AND_DESTINATION
}

enum class SwipeDirection {
    SWIPE_LEFT,
    SWIPE_RIGHT
}

data class LatLngPoint(
    val latitude: Double,
    val longitude: Double
)

data class RideOffer(
    val app: AppTarget,
    val displayedPrice: Double?,
    val pickupDistanceKm: Double?,
    val pickupTimeMinutes: Int?,
    val tripDistanceKm: Double?,
    val tripTimeMinutes: Int?,
    val pickupAddress: String?,
    val destinationAddress: String?,
    val rideType: String? = null,
    val paymentType: String? = null,
    val passengerRating: Double? = null,
    val screenTimestamp: Long = System.currentTimeMillis(),
    val confidence: Int = 0,
    val rawSource: String = "ACCESSIBILITY" // ACCESSIBILITY or OCR
)

data class RideAnalysis(
    val offer: RideOffer,
    val totalPricingDistanceKm: Double,
    val applicableBand: PricingBand?,
    val minRequiredPrice: Double,
    val pricePerKm: Double,
    val isPriceViable: Boolean,
    val isInsideZone: Boolean,
    val zoneStatusReason: String,
    val pricingReason: String,
    val actionRecommended: String,
    val counterOfferPrice: Double? = null
)
