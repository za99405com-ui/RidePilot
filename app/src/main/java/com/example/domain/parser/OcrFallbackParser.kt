package com.example.domain.parser

import android.graphics.Bitmap
import com.example.data.model.AppTarget
import com.example.data.model.RideOffer
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object OcrFallbackParser {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun parseFromBitmap(bitmap: Bitmap, targetApp: AppTarget): RideOffer? {
        val image = InputImage.fromBitmap(bitmap, 0)

        val visionText: Text = suspendCancellableCoroutine<Text?> { cont ->
            recognizer.process(image)
                .addOnSuccessListener { result -> cont.resume(result) }
                .addOnFailureListener { _ -> cont.resume(null) }
        } ?: return null

        val lines = mutableListOf<String>()
        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                lines.add(line.text)
            }
        }

        // Find price
        var price: Double? = null
        for (line in lines) {
            if (line.contains("EGP") || line.contains("ج.م") || line.contains("E£")) {
                val p = ArabicNumberHelper.extractFirstDouble(line)
                if (p != null && p > 5.0) {
                    price = p
                    break
                }
            }
        }

        // Find distances
        val distances = mutableListOf<Double>()
        for (line in lines) {
            if (line.contains("كلم") || line.contains("km") || line.contains("كم")) {
                val d = ArabicNumberHelper.extractDistanceKm(line)
                if (d != null) distances.add(d)
            }
        }

        val pickupDistance = if (targetApp == AppTarget.UBER && distances.size >= 2) distances[0] else null
        val tripDistance = if (targetApp == AppTarget.UBER && distances.size >= 2) distances[1] else distances.firstOrNull()

        if (price == null && tripDistance == null) return null

        return RideOffer(
            app = targetApp,
            displayedPrice = price,
            pickupDistanceKm = pickupDistance,
            pickupTimeMinutes = null,
            tripDistanceKm = tripDistance,
            tripTimeMinutes = null,
            pickupAddress = null,
            destinationAddress = null,
            confidence = 65,
            rawSource = "OCR"
        )
    }
}
