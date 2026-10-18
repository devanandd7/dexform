package com.dexform.app.core.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import com.dexform.app.core.imaging.BitmapLoader
import com.dexform.app.core.imaging.rotate
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class OcrResult(
    val fullText: String,
    val blocks: List<String>
)

object OcrEngine {

    suspend fun extractText(context: Context, uri: Uri): OcrResult = withContext(Dispatchers.Default) {
        val bitmap = try {
            BitmapLoader.load(context, uri)
        } catch (e: Exception) {
            throw IllegalArgumentException("Cannot load image for OCR: ${e.message}")
        }
        extractTextSmart(bitmap)
    }

    suspend fun extractText(bitmap: Bitmap): OcrResult = withContext(Dispatchers.Default) {
        extractTextSmart(bitmap)
    }

    private suspend fun extractTextSmart(bitmap: Bitmap): OcrResult = withContext(Dispatchers.Default) {
        val enhancedBmp = enhanceImageForOcr(bitmap)

        // Pass 1: Standard orientation
        var result = processImage(InputImage.fromBitmap(enhancedBmp, 0))

        // Pass 2: Multi-Orientation check if initial text is sparse
        if (result.fullText.trim().length < 20) {
            val angles = listOf(90f, 180f, 270f)
            for (angle in angles) {
                val rotated = rotate(enhancedBmp, angle)
                val testRes = processImage(InputImage.fromBitmap(rotated, 0))
                if (testRes.fullText.length > result.fullText.length) {
                    result = testRes
                }
            }
        }

        result
    }

    private fun enhanceImageForOcr(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val dest = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dest)
        val paint = Paint()

        // Enhance contrast (1.3x) and brightness for crisp OCR text
        val cm = ColorMatrix(
            floatArrayOf(
                1.3f, 0f, 0f, 0f, -20f,
                0f, 1.3f, 0f, 0f, -20f,
                0f, 0f, 1.3f, 0f, -20f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return dest
    }

    private suspend fun processImage(inputImage: InputImage): OcrResult = suspendCancellableCoroutine { continuation ->
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                val fullText = visionText.text
                val blocks = visionText.textBlocks.map { it.text }
                continuation.resume(OcrResult(fullText = fullText, blocks = blocks))
            }
            .addOnFailureListener { exception ->
                continuation.resumeWithException(exception)
            }
    }
}
