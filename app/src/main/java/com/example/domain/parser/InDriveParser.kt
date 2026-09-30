package com.example.domain.parser

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.example.data.model.AppTarget
import com.example.data.model.RideOffer

object InDriveParser {

    enum class InDriveScreenType {
        UNKNOWN,
        REQUESTS_LIST,
        ORDER_DETAILS,
        COUNTER_OFFER_INPUT,
        WAITING_RESPONSE
    }

    data class InDriveOrderCard(
        val priceEgp: Double?,
        val distanceKm: Double?,
        val pickupAddress: String?,
        val destinationAddress: String?,
        val bounds: Rect,
        val node: AccessibilityNodeInfo?
    )

    data class InDriveParsedScreen(
        val screenType: InDriveScreenType,
        val isOffline: Boolean,
        val offlineButtonBounds: Rect?,
        val orderCards: List<InDriveOrderCard>,
        val activeOffer: RideOffer?,
        val quickOfferButtons: Map<Double, Rect>,
        val customOfferEditButtonBounds: Rect?,
        val customOfferInputNode: AccessibilityNodeInfo?,
        val customOfferInputText: String?,
        val submitOfferButtonBounds: Rect?,
        val closeButtonBounds: Rect?,
        val acceptButtonBounds: Rect?,
        val isResponseRejected: Boolean,
        val confidence: Int
    )

    fun parseScreen(root: AccessibilityNodeInfo?): InDriveParsedScreen {
        if (root == null) {
            return InDriveParsedScreen(
                screenType = InDriveScreenType.UNKNOWN,
                isOffline = false,
                offlineButtonBounds = null,
                orderCards = emptyList(),
                activeOffer = null,
                quickOfferButtons = emptyMap(),
                customOfferEditButtonBounds = null,
                customOfferInputNode = null,
                customOfferInputText = null,
                submitOfferButtonBounds = null,
                closeButtonBounds = null,
                acceptButtonBounds = null,
                isResponseRejected = false,
                confidence = 0
            )
        }

        var isOffline = false
        var offlineButtonBounds: Rect? = null
        var isRequestsList = false
        var isOrderDetails = false
        var isCounterOfferInput = false
        var isWaitingResponse = false
        var isResponseRejected = false

        val quickOfferMap = mutableMapOf<Double, Rect>()
        var editButtonBounds: Rect? = null
        var customOfferInputNode: AccessibilityNodeInfo? = null
        var customOfferInputText: String? = null
        var submitButtonBounds: Rect? = null
        var closeButtonBounds: Rect? = null
        var acceptButtonBounds: Rect? = null

        val orderCards = mutableListOf<InDriveOrderCard>()

        // Find offline / online status
        fun inspectNode(node: AccessibilityNodeInfo) {
            val text = (node.text?.toString() ?: node.contentDescription?.toString())?.trim() ?: ""

            if (node.isEditable || node.className?.toString() == "android.widget.EditText") {
                customOfferInputNode = node
                customOfferInputText = text.ifBlank { null }
            }

            if (text.contains("غير متصل")) {
                isOffline = true
                val rect = Rect()
                node.getBoundsInScreen(rect)
                offlineButtonBounds = rect
            } else if (text == "متصل") {
                isOffline = false
                val rect = Rect()
                node.getBoundsInScreen(rect)
                offlineButtonBounds = rect
            }

            if (text.contains("طلبات الركوب")) {
                isRequestsList = true
            }

            if (text.contains("طلب ركوب") && !text.contains("طلبات")) {
                isOrderDetails = true
            }

            if (text.contains("اعرض الأجرة المناسبة لك")) {
                isCounterOfferInput = true
            }

            if (text.contains("جار عرض الأجرة المناسبة لك") || text.contains("انتظر الرد")) {
                isWaitingResponse = true
            }

            if (text.contains("لم يتم قبول عرضك")) {
                isWaitingResponse = true
                isResponseRejected = true
            }

            if (text.contains("تقديم عرض")) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                submitButtonBounds = rect
            }

            if (text == "إغلاق" || text == "Close") {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                closeButtonBounds = rect
            }

            if (text.contains("القبول مقابل")) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                acceptButtonBounds = rect
            }

            // Quick offer chips (e.g. "32 EGP", "35 EGP", "38 EGP")
            if (text.endsWith("EGP") && text.length <= 10) {
                val price = ArabicNumberHelper.extractFirstDouble(text)
                if (price != null && price in 15.0..1000.0) {
                    val rect = Rect()
                    node.getBoundsInScreen(rect)
                    // If it's a clickable pill or button
                    if (node.isClickable || rect.height() in 40..250) {
                        quickOfferMap[price] = rect
                    }
                }
            }

            // Edit button (pencil)
            if (node.isClickable && (text.contains("تعديل") || node.className == "android.widget.ImageButton" || node.className == "android.widget.ImageView")) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                if (rect.width() in 40..200 && rect.height() in 40..200 && rect.top > 800) {
                    editButtonBounds = rect
                }
            }

            // Recurse children
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                inspectNode(child)
            }
        }

        inspectNode(root)

        val screenType = when {
            isCounterOfferInput -> InDriveScreenType.COUNTER_OFFER_INPUT
            isWaitingResponse -> InDriveScreenType.WAITING_RESPONSE
            isOrderDetails -> InDriveScreenType.ORDER_DETAILS
            isRequestsList -> InDriveScreenType.REQUESTS_LIST
            else -> InDriveScreenType.UNKNOWN
        }

        // Parse order cards if in requests list
        if (screenType == InDriveScreenType.REQUESTS_LIST) {
            extractOrderCardsFromList(root, orderCards)
        }

        // Parse active order if in order details
        var activeOffer: RideOffer? = null
        if (screenType == InDriveScreenType.ORDER_DETAILS || screenType == InDriveScreenType.COUNTER_OFFER_INPUT) {
            activeOffer = extractActiveOrderDetails(root)
        }

        var confidence = 50
        if (screenType != InDriveScreenType.UNKNOWN) confidence += 30
        if (isOffline) confidence += 10
        if (orderCards.isNotEmpty() || activeOffer != null) confidence += 10

        return InDriveParsedScreen(
            screenType = screenType,
            isOffline = isOffline,
            offlineButtonBounds = offlineButtonBounds,
            orderCards = orderCards,
            activeOffer = activeOffer,
            quickOfferButtons = quickOfferMap,
            customOfferEditButtonBounds = editButtonBounds,
            customOfferInputNode = customOfferInputNode,
            customOfferInputText = customOfferInputText,
            submitOfferButtonBounds = submitButtonBounds,
            closeButtonBounds = closeButtonBounds,
            acceptButtonBounds = acceptButtonBounds,
            isResponseRejected = isResponseRejected,
            confidence = confidence
        )
    }

    private fun extractOrderCardsFromList(root: AccessibilityNodeInfo, cardsOut: MutableList<InDriveOrderCard>) {
        fun scan(node: AccessibilityNodeInfo) {
            val rect = Rect()
            node.getBoundsInScreen(rect)

            // Look for nodes that encapsulate an order: has price (EGP) and distance
            val childTexts = mutableListOf<String>()
            fun collectChildTexts(childNode: AccessibilityNodeInfo) {
                val t = (childNode.text?.toString() ?: childNode.contentDescription?.toString())?.trim()
                if (!t.isNullOrBlank()) childTexts.add(t)
                for (j in 0 until childNode.childCount) {
                    val c = childNode.getChild(j) ?: continue
                    collectChildTexts(c)
                }
            }

            if (node.isClickable && rect.height() in 100..600 && rect.width() > 500) {
                collectChildTexts(node)
                val priceText = childTexts.firstOrNull { it.contains("EGP") || it.contains("ج.م") }
                val distText = childTexts.firstOrNull { it.contains("كلم") || it.contains("كم") || it.contains("متر") }

                if (priceText != null && distText != null) {
                    val price = ArabicNumberHelper.extractFirstDouble(priceText)
                    val dist = ArabicNumberHelper.extractDistanceKm(distText)

                    // Pick addresses from remaining texts
                    val addressCandidates = childTexts.filter {
                        it != priceText && it != distText &&
                        !it.contains("EGP") && !it.contains("سداد") && !it.contains("★") && it.length > 5
                    }
                    val pickup = addressCandidates.getOrNull(0)
                    val dest = addressCandidates.getOrNull(1)

                    cardsOut.add(
                        InDriveOrderCard(
                            priceEgp = price,
                            distanceKm = dist,
                            pickupAddress = pickup,
                            destinationAddress = dest,
                            bounds = rect,
                            node = node
                        )
                    )
                }
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                scan(child)
            }
        }

        scan(root)
    }

    private fun extractActiveOrderDetails(root: AccessibilityNodeInfo): RideOffer? {
        val texts = mutableListOf<String>()
        fun collect(node: AccessibilityNodeInfo) {
            val t = (node.text?.toString() ?: node.contentDescription?.toString())?.trim()
            if (!t.isNullOrBlank()) texts.add(t)
            for (i in 0 until node.childCount) {
                val c = node.getChild(i) ?: continue
                collect(c)
            }
        }
        collect(root)

        val priceText = texts.firstOrNull { it.contains("EGP") || it.contains("E£") || it.contains("ج.م") }
        val price = ArabicNumberHelper.extractFirstDouble(priceText)

        val distanceTexts = texts.filter {
            it.contains("كلم") || it.contains("كم") || it.contains("km", ignoreCase = true) || it.contains("متر")
        }
        val distances = distanceTexts.mapNotNull { ArabicNumberHelper.extractDistanceKm(it) }

        val timeTexts = texts.filter {
            it.contains("دقيقة") || Regex("""\b\d+\s*د\b""").containsMatchIn(ArabicNumberHelper.normalizeDigits(it))
        }
        val times = timeTexts.mapNotNull { ArabicNumberHelper.extractFirstDouble(it)?.toInt() }

        val pickupDistance = if (distances.size >= 2) distances[0] else null
        val tripDistance = when {
            distances.size >= 2 -> distances[1]
            distances.size == 1 -> distances[0]
            else -> null
        }
        val pickupTime = if (times.size >= 2) times[0] else times.firstOrNull()
        val tripTime = if (times.size >= 2) times[1] else null

        val excluded = (distanceTexts + timeTexts + listOfNotNull(priceText)).toSet()

        fun isAddressLike(text: String): Boolean {
            if (text in excluded) return false
            if (text.length < 6) return false
            val blocked = listOf(
                "طلب ركوب", "سداد", "تقديم عرض", "القبول مقابل", "اعرض الأجرة",
                "اقترح أجرتك", "السعر العادل", "إغلاق", "حصري", "نقدي"
            )
            if (blocked.any { text.contains(it, ignoreCase = true) }) return false
            if (text.contains("★")) return false
            if (Regex("""^\s*\d+(?:[.,]\d+)?\s*EGP\s*$""", RegexOption.IGNORE_CASE)
                    .matches(ArabicNumberHelper.normalizeDigits(text))) return false
            return true
        }

        fun joinAddressRange(startExclusive: Int, endExclusive: Int): String? {
            if (startExclusive < 0 || endExclusive <= startExclusive + 1) return null
            val candidates = texts
                .subList(startExclusive + 1, endExclusive.coerceAtMost(texts.size))
                .filter(::isAddressLike)
                .take(3)
            return candidates.takeIf { it.isNotEmpty() }?.joinToString(" ")
        }

        val markerA = texts.indexOfFirst { it.trim().equals("A", ignoreCase = true) }
        val markerB = texts.indexOfFirst { it.trim().equals("B", ignoreCase = true) }

        val pickupFromMarkers = if (markerA >= 0 && markerB > markerA) {
            joinAddressRange(markerA, markerB)
        } else null

        val actionBoundary = if (markerB >= 0) {
            ((markerB + 1) until texts.size).firstOrNull { i ->
                val t = texts[i]
                t.contains("القبول مقابل") ||
                    t.contains("اقترح أجرتك") ||
                    t.contains("إغلاق")
            } ?: texts.size
        } else texts.size

        val destinationFromMarkers = if (markerB >= 0) {
            joinAddressRange(markerB, actionBoundary)
        } else null

        val fallbackAddresses = texts.filter(::isAddressLike)

        return RideOffer(
            app = AppTarget.INDRIVE,
            displayedPrice = price,
            pickupDistanceKm = pickupDistance,
            pickupTimeMinutes = pickupTime,
            tripDistanceKm = tripDistance,
            tripTimeMinutes = tripTime,
            pickupAddress = pickupFromMarkers ?: fallbackAddresses.getOrNull(0),
            destinationAddress = destinationFromMarkers ?: fallbackAddresses.getOrNull(1),
            confidence = when {
                price != null && tripDistance != null &&
                    (pickupFromMarkers != null || fallbackAddresses.isNotEmpty()) &&
                    (destinationFromMarkers != null || fallbackAddresses.size >= 2) -> 95
                price != null && tripDistance != null -> 85
                else -> 50
            },
            rawSource = "ACCESSIBILITY"
        )
    }

}
