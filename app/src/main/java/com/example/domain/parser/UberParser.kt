package com.example.domain.parser

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.example.data.model.AppTarget
import com.example.data.model.RideOffer

object UberParser {

    data class ParsedNode(
        val text: String,
        val bounds: Rect,
        val className: String
    )

    /**
     * Traverses the node hierarchy and extracts text items with bounding boxes.
     */
    fun extractNodes(root: AccessibilityNodeInfo?): List<ParsedNode> {
        val list = mutableListOf<ParsedNode>()
        if (root == null) return list

        fun traverse(node: AccessibilityNodeInfo) {
            val text = (node.text?.toString() ?: node.contentDescription?.toString())?.trim()
            if (!text.isNullOrBlank()) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                list.add(ParsedNode(text, rect, node.className?.toString() ?: ""))
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                traverse(child)
            }
        }

        traverse(root)
        return list
    }

    /**
     * Parses an Uber offer from the accessibility node list.
     */
    fun parseUberScreen(nodes: List<ParsedNode>): RideOffer? {
        if (nodes.isEmpty()) return null

        val allTexts = nodes.map { it.text }

        // Find main price (e.g., "٦٠,٦٠ ج.م.", "٦٠,٣٥ ج.م.", "60.60 EGP")
        var displayedPrice: Double? = null
        for (text in allTexts) {
            if (text.contains("ج.م") || text.contains("EGP") || text.contains("LE") || text.contains("L.E")) {
                val price = ArabicNumberHelper.extractFirstDouble(text)
                if (price != null && price > 5.0) {
                    displayedPrice = price
                    break
                }
            }
        }

        // Look for pickup & trip distances & times
        // In Uber screens:
        // Format 1: "11 دقيقة (4.4 كلم)"
        // Format 2: "4 دقيقة (1.1 كلم)"
        // Format 3: "17 د (10.3 كلم)"
        // Format 4: "19 د (11.3 كلم)"
        val timeDistancePattern = Regex(
            """(\d+)\s*(?:دقيقة|د|min|mins)?\s*\(\s*([\d.,]+)\s*(?:كلم|كم|km)\s*\)""",
            RegexOption.IGNORE_CASE
        )

        var pickupDistance: Double? = null
        var pickupTime: Int? = null
        var tripDistance: Double? = null
        var tripTime: Int? = null
        var pickupAddress: String? = null
        var destAddress: String? = null

        val matchedTimeDistanceIndices = mutableListOf<Int>()

        for (i in allTexts.indices) {
            val normalized = ArabicNumberHelper.normalizeDigits(allTexts[i])
            val match = timeDistancePattern.find(normalized)
            if (match != null) {
                matchedTimeDistanceIndices.add(i)
                val time = match.groupValues[1].toIntOrNull()
                val dist = match.groupValues[2].replace(",", ".").toDoubleOrNull()

                if (pickupDistance == null) {
                    pickupDistance = dist
                    pickupTime = time
                } else if (tripDistance == null) {
                    tripDistance = dist
                    tripTime = time
                }
            }
        }

        // Newer Uber cards often expose time and distance as separate nodes
        // (for example 2.6 km pickup and 11.2 km trip) instead of the old
        // "11 دقيقة (4.4 كلم)" combined string. Fall back to any distance nodes
        // in visual/accessibility order so the overlay can still be analysed.
        if (pickupDistance == null || tripDistance == null) {
            val distanceCandidates = allTexts.mapIndexedNotNull { index, text ->
                val looksLikeDistance =
                    text.contains("كلم") ||
                    text.contains("كم") ||
                    text.contains("km", ignoreCase = true) ||
                    text.contains("متر")
                if (!looksLikeDistance) {
                    null
                } else {
                    ArabicNumberHelper.extractDistanceKm(text)?.let { index to it }
                }
            }.filter { it.second > 0.0 }

            if (pickupDistance == null) {
                pickupDistance = distanceCandidates.getOrNull(0)?.second
            }
            if (tripDistance == null) {
                tripDistance = distanceCandidates
                    .firstOrNull { (_, value) ->
                        pickupDistance == null || kotlin.math.abs(value - pickupDistance!!) > 0.001
                    }
                    ?.second
            }

            if (matchedTimeDistanceIndices.size < 2) {
                distanceCandidates.forEach { (index, _) ->
                    if (index !in matchedTimeDistanceIndices) {
                        matchedTimeDistanceIndices.add(index)
                    }
                }
                matchedTimeDistanceIndices.sort()
            }
        }

        if (pickupTime == null || tripTime == null) {
            val timeCandidates = allTexts.mapNotNull { text ->
                val normalized = ArabicNumberHelper.normalizeDigits(text)
                val looksLikeTime =
                    normalized.contains("دقيقة") ||
                    Regex("""\b\d+\s*(?:د|min|mins)\b""", RegexOption.IGNORE_CASE)
                        .containsMatchIn(normalized)
                if (looksLikeTime) {
                    ArabicNumberHelper.extractFirstDouble(normalized)?.toInt()
                } else {
                    null
                }
            }.filter { it > 0 }

            if (pickupTime == null) pickupTime = timeCandidates.getOrNull(0)
            if (tripTime == null) tripTime = timeCandidates.getOrNull(1)
        }

        // Extract addresses: Typically placed right below or adjacent to the time/distance lines
        if (matchedTimeDistanceIndices.size >= 1) {
            val firstIdx = matchedTimeDistanceIndices[0]
            if (firstIdx + 1 < allTexts.size) {
                pickupAddress = allTexts[firstIdx + 1]
                // Sometimes address has second line (e.g. "السيدة عائشة 16")
                if (firstIdx + 2 < allTexts.size &&
                    (matchedTimeDistanceIndices.size < 2 || firstIdx + 2 < matchedTimeDistanceIndices[1])
                ) {
                    val candidate = allTexts[firstIdx + 2]
                    if (!candidate.contains("دقيقة") && !candidate.contains("كلم") && !candidate.contains("★")) {
                        pickupAddress += " - $candidate"
                    }
                }
            }
        }

        if (matchedTimeDistanceIndices.size >= 2) {
            val secondIdx = matchedTimeDistanceIndices[1]
            if (secondIdx + 1 < allTexts.size) {
                destAddress = allTexts[secondIdx + 1]
                if (secondIdx + 2 < allTexts.size) {
                    val candidate = allTexts[secondIdx + 2]
                    if (!candidate.contains("اقبل") && !candidate.contains("تطابق") && candidate.length > 3) {
                        destAddress += " - $candidate"
                    }
                }
            }
        }

        // Service type: Scooter, UberX, Comfort
        val rideType = allTexts.firstOrNull {
            it.equals("Scooter", ignoreCase = true) ||
            it.equals("UberX", ignoreCase = true) ||
            it.equals("سكوتر", ignoreCase = true) ||
            it.equals("Comfort", ignoreCase = true)
        }

        // Rating: e.g. "5.00" or "4.81"
        var rating: Double? = null
        val ratingPattern = Regex("""(?:\★\s*([\d.,]+)|([\d.,]+)\s*\★)""")
        for (text in allTexts) {
            val match = ratingPattern.find(text)
            if (match != null) {
                val raw = (match.groups[1] ?: match.groups[2])?.value
                rating = ArabicNumberHelper.extractFirstDouble(raw)
                if (rating != null) break
            }
        }

        // Calculate confidence
        var confidence = 0
        if (displayedPrice != null) confidence += 35
        if (pickupDistance != null) confidence += 20
        if (tripDistance != null) confidence += 25
        if (!pickupAddress.isNullOrBlank()) confidence += 10
        if (!destAddress.isNullOrBlank()) confidence += 10

        if (displayedPrice == null && tripDistance == null) {
            return null // Not an Uber request dialog
        }

        return RideOffer(
            app = AppTarget.UBER,
            displayedPrice = displayedPrice,
            pickupDistanceKm = pickupDistance,
            pickupTimeMinutes = pickupTime,
            tripDistanceKm = tripDistance,
            tripTimeMinutes = tripTime,
            pickupAddress = pickupAddress,
            destinationAddress = destAddress,
            rideType = rideType,
            passengerRating = rating,
            confidence = confidence,
            rawSource = "ACCESSIBILITY"
        )
    }
}
