package com.mimir.translate.ocr

import android.graphics.Bitmap
import android.util.Log
import com.mimir.translate.BuildConfig
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class TextRecognizer {

    companion object {
        private const val TAG = "Mimir"
    }

    private val recognizer = TextRecognition.getClient(
        JapaneseTextRecognizerOptions.Builder().build()
    )

    private fun downsample(bitmap: Bitmap): Bitmap {
        val maxWidth = 1080
        if (bitmap.width <= maxWidth) return bitmap
        val ratio = maxWidth.toFloat() / bitmap.width
        return Bitmap.createScaledBitmap(bitmap, maxWidth, (bitmap.height * ratio).toInt(), true)
    }

    suspend fun recognizeText(bitmap: Bitmap): String? {
        // NASA Rule 5: precondition assertions
        require(bitmap.width > 0 && bitmap.height > 0) { "Bitmap must have positive dimensions" }
        return suspendCancellableCoroutine { continuation ->
            val image = InputImage.fromBitmap(downsample(bitmap), 0)

            if (BuildConfig.DEBUG) Log.d(TAG, "OCR: Starting text recognition on ${bitmap.width}x${bitmap.height} image")

            recognizer.process(image)
                .addOnSuccessListener { result ->
                    val text = result.text.trim()
                    if (BuildConfig.DEBUG) Log.d(TAG, "OCR: Recognized ${result.textBlocks.size} blocks, text length=${text.length}")
                    if (text.isNotEmpty()) {
                        if (BuildConfig.DEBUG) Log.d(TAG, "OCR: Text = $text")
                        continuation.resume(text)
                    } else {
                        if (BuildConfig.DEBUG) Log.d(TAG, "OCR: No text found")
                        continuation.resume(null)
                    }
                }
                .addOnFailureListener { e ->
                    if (BuildConfig.DEBUG) Log.e(TAG, "OCR: Recognition failed", e)
                    continuation.resume(null)
                }
        }
    }

    suspend fun recognizeTextBlocks(bitmap: Bitmap): List<String>? {
        // NASA Rule 5: precondition assertions
        require(bitmap.width > 0 && bitmap.height > 0) { "Bitmap must have positive dimensions" }
        return suspendCancellableCoroutine { continuation ->
            val image = InputImage.fromBitmap(downsample(bitmap), 0)

            recognizer.process(image)
                .addOnSuccessListener { result ->
                    val blocks = result.textBlocks
                        .map { it.text.trim() }
                        .filter { it.isNotEmpty() }
                    if (blocks.isNotEmpty()) {
                        continuation.resume(blocks)
                    } else {
                        continuation.resume(null)
                    }
                }
                .addOnFailureListener {
                    continuation.resume(null)
                }
        }
    }

    fun close() {
        recognizer.close()
    }
}
