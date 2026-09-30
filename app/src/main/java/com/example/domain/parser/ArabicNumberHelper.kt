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

        // Check if string contains "متر" (meters)
        if (normalized.contains("متر") || normalized.contains(" m") || normalized.contains("m ")) {
            val regex = Regex("""\b\d+(?:\.\d+)?\b""")
            val match = regex.find(normalized) ?: return null
            val meters = match.value.toDoubleOrNull() ?: return null
            return (meters / 1000.0)
        }

        // Default km extraction
        val regex = Regex("""\b\d+(?:\.\d+)?\b""")
        val match = regex.find(normalized) ?: return null
        return match.value.toDoubleOrNull()
    }
}
