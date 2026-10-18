package com.dexform.app.data.media

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

object MediaSaver {

    suspend fun saveToDexFormAlbum(
        context: Context,
        file: File,
        displayName: String? = null
    ): Uri? = withContext(Dispatchers.IO) {
        val name = displayName ?: file.name
        val isPdf = name.endsWith(".pdf", ignoreCase = true)
        val isPng = name.endsWith(".png", ignoreCase = true)
        val mimeType = when {
            isPdf -> "application/pdf"
            isPng -> "image/png"
            else -> "image/jpeg"
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentUri = if (isPdf) {
                MediaStore.Downloads.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

            val relativePath = if (isPdf) "Download/DexForm" else "Pictures/DexForm"

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(contentUri, contentValues) ?: return@withContext null

            try {
                resolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(file).use { input ->
                        input.copyTo(out)
                    }
                }
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
                return@withContext uri
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                return@withContext null
            }
        } else {
            // API 26 - 28
            val subDir = if (isPdf) Environment.DIRECTORY_DOCUMENTS else Environment.DIRECTORY_PICTURES
            val targetFolder = File(
                Environment.getExternalStoragePublicDirectory(subDir),
                "DexForm"
            ).apply { if (!exists()) mkdirs() }

            val targetFile = File(targetFolder, name)
            try {
                file.copyTo(targetFile, overwrite = true)
                var scannedUri: Uri? = null
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(targetFile.absolutePath),
                    arrayOf(mimeType)
                ) { _, uri ->
                    scannedUri = uri
                }
                return@withContext scannedUri ?: Uri.fromFile(targetFile)
            } catch (e: Exception) {
                return@withContext null
            }
        }
    }
}
