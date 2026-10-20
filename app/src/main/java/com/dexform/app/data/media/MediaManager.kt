package com.dexform.app.data.media

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.activity.result.IntentSenderRequest
import java.io.File

sealed class RenameResult {
    data class Success(val newName: String) : RenameResult()
    data class RequiresPermission(val request: IntentSenderRequest, val pendingAction: () -> Unit) : RenameResult()
    data class Error(val message: String) : RenameResult()
}

object MediaManager {

    /**
     * Prepares and executes file renaming.
     * Preserves extension if not specified in newBaseName.
     * Returns Success, RequiresPermission (Android 11+ system dialog), or Error.
     */
    fun prepareRenameFile(
        context: Context,
        item: MediaItem,
        newBaseName: String
    ): RenameResult {
        val trimmed = newBaseName.trim()
        if (trimmed.isEmpty()) {
            return RenameResult.Error("Name cannot be empty")
        }

        val dotIdx = item.displayName.lastIndexOf('.')
        val originalExt = if (dotIdx >= 0) item.displayName.substring(dotIdx + 1) else ""
        val newFullName = if (originalExt.isNotEmpty() && !trimmed.endsWith(".$originalExt", ignoreCase = true)) {
            "$trimmed.$originalExt"
        } else {
            trimmed
        }

        // Cache / Internal file handling
        if (item.bucketId == -999L || item.uri.scheme == "file") {
            return try {
                val processedDir = File(context.cacheDir, "processed")
                val oldFile = File(processedDir, item.displayName)
                if (oldFile.exists()) {
                    val newFile = File(processedDir, newFullName)
                    if (oldFile.renameTo(newFile)) {
                        RenameResult.Success(newFullName)
                    } else {
                        RenameResult.Error("Failed to rename file on disk")
                    }
                } else {
                    RenameResult.Error("Source file not found")
                }
            } catch (e: Exception) {
                RenameResult.Error(e.message ?: "Rename error")
            }
        }

        // Android MediaStore File
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, newFullName)
        }

        try {
            val rows = context.contentResolver.update(item.uri, values, null, null)
            if (rows > 0) {
                return RenameResult.Success(newFullName)
            }
        } catch (_: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                return try {
                    val pi = MediaStore.createWriteRequest(context.contentResolver, listOf(item.uri))
                    RenameResult.RequiresPermission(
                        request = IntentSenderRequest.Builder(pi.intentSender).build(),
                        pendingAction = {
                            try {
                                context.contentResolver.update(item.uri, values, null, null)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    )
                } catch (e: Exception) {
                    RenameResult.Error(e.message ?: "Permission request failed")
                }
            }
        } catch (e: Exception) {
            return RenameResult.Error(e.message ?: "Error updating file")
        }

        return RenameResult.Error("Unable to rename photo")
    }

    /**
     * Prepares and executes album/folder renaming.
     * Moves items to new RELATIVE_PATH on Android 10+.
     */
    fun prepareRenameAlbum(
        context: Context,
        items: List<MediaItem>,
        newFolderName: String
    ): RenameResult {
        val trimmed = newFolderName.trim()
        if (trimmed.isEmpty()) {
            return RenameResult.Error("Folder name cannot be empty")
        }
        if (items.isEmpty()) {
            return RenameResult.Error("Folder is empty")
        }

        if (items.all { it.bucketId == -999L }) {
            return RenameResult.Error("Tool cache folder cannot be renamed")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val uris = items.map { it.uri }

            fun performBatchMove() {
                for (item in items) {
                    try {
                        val newRelPath = computeNewRelativePath(item.relativePath, trimmed)
                        val values = ContentValues().apply {
                            put(MediaStore.MediaColumns.RELATIVE_PATH, newRelPath)
                        }
                        context.contentResolver.update(item.uri, values, null, null)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                return try {
                    val pi = MediaStore.createWriteRequest(context.contentResolver, uris)
                    RenameResult.RequiresPermission(
                        request = IntentSenderRequest.Builder(pi.intentSender).build(),
                        pendingAction = { performBatchMove() }
                    )
                } catch (e: Exception) {
                    RenameResult.Error(e.message ?: "Permission request failed")
                }
            } else {
                performBatchMove()
                return RenameResult.Success(trimmed)
            }
        } else {
            return RenameResult.Error("Folder rename requires Android 10 or newer")
        }
    }

    /**
     * Creates an IntentSenderRequest for deleting items via MediaStore (Android 11+).
     */
    fun createDeleteIntentRequest(
        context: Context,
        items: List<MediaItem>
    ): IntentSenderRequest? {
        val mediaStoreItems = items.filter { it.bucketId != -999L && it.uri.scheme != "file" }
        if (mediaStoreItems.isEmpty()) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return try {
                val uris = mediaStoreItems.map { it.uri }
                val pi = MediaStore.createDeleteRequest(context.contentResolver, uris)
                IntentSenderRequest.Builder(pi.intentSender).build()
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
        return null
    }

    /**
     * Direct delete for cache/internal files or pre-Android 11 devices.
     */
    fun directDelete(context: Context, items: List<MediaItem>): Int {
        var count = 0
        for (item in items) {
            try {
                if (item.bucketId == -999L || item.uri.scheme == "file") {
                    val processedDir = File(context.cacheDir, "processed")
                    val file = File(processedDir, item.displayName)
                    if (file.exists() && file.delete()) {
                        count++
                    }
                } else {
                    count += context.contentResolver.delete(item.uri, null, null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return count
    }

    fun computeNewRelativePath(currentRelPath: String?, newFolderName: String): String {
        val clean = currentRelPath?.trim('/') ?: ""
        if (clean.isEmpty()) {
            return "Pictures/$newFolderName/"
        }
        val parts = clean.split('/')
        if (parts.size <= 1) {
            return "$newFolderName/"
        }
        val parent = parts.dropLast(1).joinToString("/")
        return "$parent/$newFolderName/"
    }
}
