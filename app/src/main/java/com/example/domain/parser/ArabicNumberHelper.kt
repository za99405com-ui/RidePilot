package com.example.domain.parser

object ArabicNumberHelper {

    private val easternToArabicMap = mapOf(
        '٠' to '0', '١' to '1', '٢' to '2', '٣' to '3', '٤' to '4',
        '٥' to '5', '٦' to '6', '٧' to '7', '٨' to '8', '٩' to '9',
        '٫' to '.', '،' to '.'
    )

    fun normalizeDigits(input: String): String {
        val sb = StringBuilder()
        for (ch in input) {
            val mapped = easternToArabicMap[ch]
            if (mapped != null) {
                sb.append(mapped)
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    /**
     * Extracts double value from strings like "٦٠,٦٠ ج.م.", "60.60 EGP", "4.4 كلم", "E£45", "~3,6 كلم"
     */
    fun extractFirstDouble(rawText: String?): Double? {
        if (rawText.isNullOrBlank()) return null
        val normalized = normalizeDigits(rawText)
            .replace(",", ".")
            .replace("~", "")

        val regex = Regex("""\b\d+(?:\.\d+)?\b""")
        val match = regex.find(normalized) ?: return null
        return match.value.toDoubleOrNull()
    }

    /**
     * Extracts distance in kilometers, converting meters (e.g. "871 متر") to km (0.871 km).
     */
    fun extractDistanceKm(rawText: String?): Double? {
        if (rawText.isNullOrBlank()) return null
        val normalized = normalizeDigits(rawText)
            .replace(",", ".")
            .replace("~", "")

        // Match the number attached to the distance UNIT, not the first number in
        // the string. inDrive frequently exposes combined labels such as:
        // "21 دقيقة 9.2 كم". The old parser incorrectly returned 21 km.
        val metersRegex = Regex(
            """(\d+(?:\.\d+)?)\s*(?:متر|meters?|metres?|m)\b""",
            RegexOption.IGNORE_CASE
        )
        metersRegex.find(normalized)?.groupValues?.getOrNull(1)
            ?.toDoubleOrNull()
            ?.let { return it / 1000.0 }

        val kmRegex = Regex(
            """(\d+(?:\.\d+)?)\s*(?:كم|كلم|كيلومتر|kilometers?|kilometres?|km)\b""",
            RegexOption.IGNORE_CASE
        )
        kmRegex.find(normalized)?.groupValues?.getOrNull(1)
            ?.toDoubleOrNull()
            ?.let { return it }

        // Conservative fallback for legacy labels that contain only one numeric
        // value. Never use this fallback for labels that include time units.
        if (
            normalized.contains("دقيقة") ||
            normalized.contains("min", ignoreCase = true)
        ) {
            return null
        }

        val values = Regex("""\b\d+(?:\.\d+)?\b""")
            .findAll(normalized)
            .mapNotNull { it.value.toDoubleOrNull() }
            .toList()

        return values.singleOrNull()
    }
}
