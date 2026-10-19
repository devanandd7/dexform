package com.dexform.app.core.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object ImageAiHelper {
    private const val TAG = "ImageAiHelper"
    private const val MAX_DIMENSION_PX = 1280
    private const val JPEG_QUALITY = 92

    /**
     * Prepares an image from a content Uri:
     * 1. Copies the InputStream ONCE to a local temporary file in cacheDir/ai/ to avoid permission or non-seekable stream issues.
     * 2. Decodes image bounds from the local file.
     * 3. Calculates inSampleSize to ensure neither side exceeds 1280px (never upscales).
     * 4. Reads EXIF orientation from file path and rotates/flips accordingly.
     * 5. Decodes, transforms, and compresses as JPEG (quality ~92) to cacheDir/ai/
     * 6. Deletes the temporary raw file and returns the prepared File.
     */
    suspend fun prepareImageForAi(context: Context, uri: Uri): File = withContext(Dispatchers.IO) {
        val aiDir = File(context.cacheDir, "ai").apply {
            if (!exists()) mkdirs()
        }
        val tempRawFile = File(aiDir, "raw_${UUID.randomUUID()}.tmp")
        val targetFile = File(aiDir, "ai_${UUID.randomUUID()}.jpg")

        try {
            // Step 1: Copy InputStream ONCE to a local file
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempRawFile).use { output ->
                    input.copyTo(output)
                }
            } ?: throw IllegalArgumentException("Cannot open image stream from URI: $uri")

            if (!tempRawFile.exists() || tempRawFile.length() == 0L) {
                throw IllegalArgumentException("Selected image file is empty or unreadable")
            }

            // Step 2: Decode bounds from the local file
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(tempRawFile.absolutePath, boundsOptions)

            val rawWidth = boundsOptions.outWidth
            val rawHeight = boundsOptions.outHeight
            if (rawWidth <= 0 || rawHeight <= 0) {
                throw IllegalArgumentException("Invalid image dimensions (${rawWidth}x${rawHeight})")
            }

            // Step 3: Read EXIF orientation directly from file path
            var rotationDegrees = 0f
            var flipHorizontal = false
            try {
                val exif = ExifInterface(tempRawFile.absolutePath)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> rotationDegrees = 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> rotationDegrees = 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> rotationDegrees = 270f
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> flipHorizontal = true
                    ExifInterface.ORIENTATION_TRANSPOSE -> {
                        rotationDegrees = 90f
                        flipHorizontal = true
                    }
                    ExifInterface.ORIENTATION_TRANSVERSE -> {
                        rotationDegrees = 270f
                        flipHorizontal = true
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "EXIF parse error: ${e.message}")
            }

            // Step 4: Calculate inSampleSize (power of 2)
            var sampleSize = 1
            val maxOriginalDim = maxOf(rawWidth, rawHeight)
            if (maxOriginalDim > MAX_DIMENSION_PX) {
                sampleSize = 2
                while ((maxOriginalDim / sampleSize) > MAX_DIMENSION_PX) {
                    sampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val decodedBitmap = BitmapFactory.decodeFile(tempRawFile.absolutePath, decodeOptions)
                ?: throw IllegalStateException("Failed to decode image bitmap")

            // Step 5: Exact scaling down to MAX_DIMENSION_PX and EXIF Matrix
            val currentMax = maxOf(decodedBitmap.width, decodedBitmap.height)
            val scale = if (currentMax > MAX_DIMENSION_PX) {
                MAX_DIMENSION_PX.toFloat() / currentMax.toFloat()
            } else {
                1.0f // Never upscale
            }

            val matrix = Matrix()
            if (scale < 1.0f) {
                matrix.postScale(scale, scale)
            }
            if (flipHorizontal) {
                matrix.postScale(-1f, 1f)
            }
            if (rotationDegrees != 0f) {
                matrix.postRotate(rotationDegrees)
            }

            val finalBitmap = if (!matrix.isIdentity) {
                val transformed = Bitmap.createBitmap(
                    decodedBitmap,
                    0,
                    0,
                    decodedBitmap.width,
                    decodedBitmap.height,
                    matrix,
                    true
                )
                if (transformed != decodedBitmap) {
                    decodedBitmap.recycle()
                }
                transformed
            } else {
                decodedBitmap
            }

            // Step 6: Save to targetFile as JPEG 92
            FileOutputStream(targetFile).use { fos ->
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, fos)
                fos.flush()
            }
            finalBitmap.recycle()

            Log.i(TAG, "Prepared image for AI: ${targetFile.absolutePath} (${targetFile.length() / 1024} KB)")
            return@withContext targetFile
        } finally {
            if (tempRawFile.exists()) {
                tempRawFile.delete()
            }
        }
    }

    /**
     * Cleans up temporary AI images in cacheDir/ai/
     */
    fun clearAiCache(context: Context) {
        try {
            val aiDir = File(context.cacheDir, "ai")
            if (aiDir.exists()) {
                aiDir.listFiles()?.forEach { file ->
                    if (file.isFile && (file.name.startsWith("ai_") || file.name.startsWith("raw_"))) {
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed clearing AI cache: ${e.message}")
        }
    }
}
