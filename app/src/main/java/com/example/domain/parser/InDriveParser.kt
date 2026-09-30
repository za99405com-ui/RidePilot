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
        var hasCounterOfferPrompt = false
        var isWaitingResponse = false
        var isResponseRejected = false

        val quickOfferMap = mutableMapOf<Double, Rect>()
        var editButtonBounds: Rect? = null
        val editIconCandidates = mutableListOf<Rect>()
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

            if (text.contains("اعرض الأجرة المناسبة لك") || text.contains("اقترح أجرتك")) {
                // This text is also present on the normal details screen above the
                // quick-price chips. Do NOT classify the screen as a custom-input
                // screen from this prompt alone.
                hasCounterOfferPrompt = true
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

            // Explicit edit label or unlabeled image-button candidate. We choose the
            // actual pencil after the whole tree is scanned, using the quick-offer row.
            if (node.isClickable && text.contains("تعديل")) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                if (rect.width() in 40..240 && rect.height() in 40..240) {
                    editButtonBounds = rect
                }
            } else if (
                node.isClickable &&
                (node.className?.toString() == "android.widget.ImageButton" ||
                    node.className?.toString() == "android.widget.ImageView")
            ) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                if (rect.width() in 40..240 && rect.height() in 40..240) {
                    editIconCandidates.add(rect)
                }
            }

            // Recurse children
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                inspectNode(child)
            }
        }

        inspectNode(root)

        // Detect request cards even when this inDrive build does not expose the
        // "طلبات الركوب" heading through Accessibility. Previously this was
        // circular: cards were parsed only after the screen had already been
        // identified as REQUESTS_LIST.
        extractOrderCardsFromList(root, orderCards)
        if (orderCards.isNotEmpty()) {
            isRequestsList = true
        }

        // The pencil sits on the same horizontal row as the quick-price chips.
        // Pick the closest image button to that row instead of a random map/profile icon.
        if (editButtonBounds == null && quickOfferMap.isNotEmpty() && editIconCandidates.isNotEmpty()) {
            val chipCentersY = quickOfferMap.values.map { it.exactCenterY() }
            val avgChipY = chipCentersY.average().toFloat()
            editButtonBounds = editIconCandidates
                .filter { kotlin.math.abs(it.exactCenterY() - avgChipY) <= 140f }
                .minByOrNull { it.exactCenterX() }
        }

        // A custom-offer input screen is confirmed only when an editable field is
        // actually present. The normal details screen contains the same prompt text.
        val isCounterOfferInput =
            customOfferInputNode != null &&
                (submitButtonBounds != null || hasCounterOfferPrompt)

        // Some inDrive versions do not expose the "طلب ركوب" heading. The accept
        // button or quick-offer chips are enough to identify the details screen.
        if (acceptButtonBounds != null || quickOfferMap.isNotEmpty()) {
            isOrderDetails = true
        }

        val screenType = when {
            isWaitingResponse -> InDriveScreenType.WAITING_RESPONSE
            isCounterOfferInput -> InDriveScreenType.COUNTER_OFFER_INPUT
            isOrderDetails -> InDriveScreenType.ORDER_DETAILS
            isRequestsList -> InDriveScreenType.REQUESTS_LIST
            else -> InDriveScreenType.UNKNOWN
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
        val candidates = mutableListOf<InDriveOrderCard>()

        fun scan(node: AccessibilityNodeInfo) {
            val rect = Rect()
            node.getBoundsInScreen(rect)

            // Some inDrive builds expose the visual card container as non-clickable
            // while one of its ancestors handles the click. Detect by geometry/content
            // first and let ClickNode climb the parent chain when needed.
            val looksCardSized =
                rect.width() > 500 &&
                    rect.height() in 100..650 &&
                    node.childCount in 1..20

            if (looksCardSized) {
                val childTexts = mutableListOf<String>()

                fun collectChildTexts(childNode: AccessibilityNodeInfo) {
                    val t = (
                        childNode.text?.toString()
                            ?: childNode.contentDescription?.toString()
                        )?.trim()

                    if (!t.isNullOrBlank()) childTexts.add(t)

                    for (j in 0 until childNode.childCount) {
                        val child = childNode.getChild(j) ?: continue
                        collectChildTexts(child)
                    }
                }

                collectChildTexts(node)

                val priceText = childTexts.firstOrNull {
                    it.contains("EGP", ignoreCase = true) ||
                        it.contains("E£", ignoreCase = true) ||
                        it.contains("ج.م") ||
                        it.contains("جنيه")
                }

                val distText = childTexts.firstOrNull {
                    it.contains("كلم") ||
                        it.contains("كم") ||
                        it.contains("km", ignoreCase = true) ||
                        it.contains("متر")
                }

                if (priceText != null && distText != null) {
                    val price = ArabicNumberHelper.extractFirstDouble(priceText)
                    val dist = ArabicNumberHelper.extractDistanceKm(distText)

                    if (price != null && price > 0.0 && dist != null && dist > 0.0) {
                        val addressCandidates = childTexts.filter {
                            it != priceText &&
                                it != distText &&
                                !it.contains("EGP", ignoreCase = true) &&
                                !it.contains("E£", ignoreCase = true) &&
                                !it.contains("ج.م") &&
                                !it.contains("سداد") &&
                                !it.contains("★") &&
                                !it.contains("طلب ركوب") &&
                                it.length > 5
                        }

                        candidates.add(
                            InDriveOrderCard(
                                priceEgp = price,
                                distanceKm = dist,
                                pickupAddress = addressCandidates.getOrNull(0),
                                destinationAddress = addressCandidates.getOrNull(1),
                                bounds = Rect(rect),
                                node = node
                            )
                        )
                    }
                }
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                scan(child)
            }
        }

        scan(root)

        // Nested accessibility containers can describe the same visual request card.
        // Keep the smallest container per visual row to avoid opening one order twice.
        val unique = candidates
            .sortedWith(
                compareBy<InDriveOrderCard> { it.bounds.top }
                    .thenBy { it.bounds.width() * it.bounds.height() }
            )
            .fold(mutableListOf<InDriveOrderCard>()) { acc, card ->
                val duplicateIndex = acc.indexOfFirst { existing ->
                    kotlin.math.abs(
                        existing.bounds.exactCenterY() - card.bounds.exactCenterY()
                    ) < 32f &&
                        kotlin.math.abs(
                            (existing.priceEgp ?: 0.0) - (card.priceEgp ?: 0.0)
                        ) < 0.01
                }

                if (duplicateIndex < 0) {
                    acc.add(card)
                } else {
                    val existing = acc[duplicateIndex]
                    val existingArea = existing.bounds.width() * existing.bounds.height()
                    val candidateArea = card.bounds.width() * card.bounds.height()
                    if (candidateArea < existingArea) {
                        acc[duplicateIndex] = card
                    }
                }
                acc
            }
            .sortedBy { it.bounds.top }

        cardsOut.addAll(unique)
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

        // Prefer the actual passenger fare shown in the accept button.
        // Quick-offer chips also contain "EGP" and must not be mistaken for the fare.
        val acceptPriceText = texts.firstOrNull {
            it.contains("القبول مقابل") && (it.contains("EGP") || it.contains("E£") || it.contains("ج.م"))
        }
        val priceText = acceptPriceText ?: texts.firstOrNull {
            (it.contains("EGP") || it.contains("E£") || it.contains("ج.م")) &&
                !Regex("""^\s*\d+(?:[.,]\d+)?\s*EGP\s*$""", RegexOption.IGNORE_CASE)
                    .matches(ArabicNumberHelper.normalizeDigits(it))
        } ?: texts.firstOrNull { it.contains("EGP") || it.contains("E£") || it.contains("ج.م") }
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
