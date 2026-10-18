package com.dexform.app.core.ocr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
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
        val inputImage = try {
            InputImage.fromFilePath(context, uri)
        } catch (e: Exception) {
            throw IllegalArgumentException("Cannot load image for OCR: ${e.message}")
        }
        processImage(inputImage)
    }

    suspend fun extractText(bitmap: Bitmap): OcrResult = withContext(Dispatchers.Default) {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        processImage(inputImage)
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
