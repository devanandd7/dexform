package com.dexform.app.data.media

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MediaRepository(private val context: Context) {

    private val _mediaItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val mediaItems: StateFlow<List<MediaItem>> = _mediaItems.asStateFlow()

    private val _albums = MutableStateFlow<List<AlbumItem>>(emptyList())
    val albums: StateFlow<List<AlbumItem>> = _albums.asStateFlow()

    private val _dexFormAlbums = MutableStateFlow<List<AlbumItem>>(emptyList())
    val dexFormAlbums: StateFlow<List<AlbumItem>> = _dexFormAlbums.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO)

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            refresh()
        }
    }

    init {
        try {
            context.contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer
            )
            context.contentResolver.registerContentObserver(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer
            )
        } catch (_: Exception) {}
        refresh()
    }

    fun refresh() {
        scope.launch {
            val items = loadMediaFromMediaStore()
            _mediaItems.value = items

            val allAlbumList = groupIntoAlbums(items)
            _albums.value = allAlbumList

            // Filter ONLY DexForm project-related albums and cache outputs
            val dexFormItems = loadDexFormProjectMedia(items)
            val dexFormAlbumList = groupDexFormAlbums(dexFormItems)
            _dexFormAlbums.value = dexFormAlbumList
        }
    }

    private suspend fun loadMediaFromMediaStore(): List<MediaItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<MediaItem>()

        val projection = mutableListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.BUCKET_ID,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.MediaColumns.RELATIVE_PATH)
            }
        }.toTypedArray()

        // Images Query
        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.MediaColumns.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val widthCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
                val heightCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
                val dateTakenCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
                val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
                val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                val relPathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                } else -1

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val size = cursor.getLong(sizeCol)
                    val name = cursor.getString(nameCol) ?: continue
                    if (size <= 0) continue

                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    val mime = cursor.getString(mimeCol) ?: "image/jpeg"
                    val width = cursor.getInt(widthCol)
                    val height = cursor.getInt(heightCol)
                    val dateTaken = cursor.getLong(dateTakenCol)
                    val dateAdded = cursor.getLong(dateAddedCol)
                    val dateMs = if (dateTaken > 0) dateTaken else dateAdded * 1000L
                    val bucketId = cursor.getLong(bucketIdCol)
                    val bucketName = cursor.getString(bucketNameCol) ?: "Internal"
                    val relPath = if (relPathCol >= 0) cursor.getString(relPathCol) else null

                    results.add(
                        MediaItem(
                            id = id,
                            uri = uri,
                            displayName = name,
                            mimeType = mime,
                            sizeBytes = size,
                            width = width,
                            height = height,
                            dateTakenMs = dateMs,
                            isVideo = false,
                            durationMs = 0L,
                            bucketId = bucketId,
                            bucketName = bucketName,
                            relativePath = relPath
                        )
                    )
                }
            }
        } catch (_: Exception) {}

        // Video Query
        val videoProjection = projection.toMutableList().apply {
            add(MediaStore.Video.VideoColumns.DURATION)
        }.toTypedArray()

        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                videoProjection,
                null,
                null,
                "${MediaStore.MediaColumns.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val widthCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
                val heightCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
                val dateTakenCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
                val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
                val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                val durationCol = cursor.getColumnIndex(MediaStore.Video.VideoColumns.DURATION)
                val relPathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                } else -1

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val size = cursor.getLong(sizeCol)
                    val name = cursor.getString(nameCol) ?: continue
                    if (size <= 0) continue

                    val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    val mime = cursor.getString(mimeCol) ?: "video/mp4"
                    val width = cursor.getInt(widthCol)
                    val height = cursor.getInt(heightCol)
                    val dateTaken = cursor.getLong(dateTakenCol)
                    val dateAdded = cursor.getLong(dateAddedCol)
                    val dateMs = if (dateTaken > 0) dateTaken else dateAdded * 1000L
                    val bucketId = cursor.getLong(bucketIdCol)
                    val bucketName = cursor.getString(bucketNameCol) ?: "Internal"
                    val duration = if (durationCol >= 0) cursor.getLong(durationCol) else 0L
                    val relPath = if (relPathCol >= 0) cursor.getString(relPathCol) else null

                    results.add(
                        MediaItem(
                            id = id,
                            uri = uri,
                            displayName = name,
                            mimeType = mime,
                            sizeBytes = size,
                            width = width,
                            height = height,
                            dateTakenMs = dateMs,
                            isVideo = true,
                            durationMs = duration,
                            bucketId = bucketId,
                            bucketName = bucketName,
                            relativePath = relPath
                        )
                    )
                }
            }
        } catch (_: Exception) {}

        results.sortedByDescending { it.dateTakenMs }
    }

    private suspend fun loadDexFormProjectMedia(allMedia: List<MediaItem>): List<MediaItem> = withContext(Dispatchers.IO) {
        val dexFormItems = mutableListOf<MediaItem>()

        // 1. Filter MediaStore items belonging to DexForm
        for (item in allMedia) {
            val isDexForm = item.bucketName.equals("DexForm", ignoreCase = true) ||
                    (item.relativePath != null && item.relativePath.contains("DexForm", ignoreCase = true))
            if (isDexForm) {
                dexFormItems.add(item)
            }
        }

        // 2. Add files from cache/processed/ directory (App Tool Outputs)
        val processedDir = File(context.cacheDir, "processed")
        if (processedDir.exists()) {
            processedDir.listFiles()?.forEach { file ->
                if (file.isFile && file.length() > 0) {
                    val uri = try {
                        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    } catch (_: Exception) {
                        Uri.fromFile(file)
                    }
                    val isPdf = file.name.endsWith(".pdf", ignoreCase = true)
                    val isVideo = file.name.endsWith(".mp4", ignoreCase = true)
                    val mime = when {
                        isPdf -> "application/pdf"
                        file.name.endsWith(".png", ignoreCase = true) -> "image/png"
                        else -> "image/jpeg"
                    }
                    dexFormItems.add(
                        MediaItem(
                            id = file.hashCode().toLong(),
                            uri = uri,
                            displayName = file.name,
                            mimeType = mime,
                            sizeBytes = file.length(),
                            width = 0,
                            height = 0,
                            dateTakenMs = file.lastModified(),
                            isVideo = isVideo,
                            durationMs = 0L,
                            bucketId = -999L,
                            bucketName = "DexForm Processed Outputs",
                            relativePath = "cache/processed"
                        )
                    )
                }
            }
        }

        dexFormItems.sortedByDescending { it.dateTakenMs }
    }

    private fun groupIntoAlbums(items: List<MediaItem>): List<AlbumItem> {
        val grouped = items.groupBy { it.bucketId }
        val albumList = mutableListOf<AlbumItem>()

        for ((bucketId, bucketItems) in grouped) {
            val first = bucketItems.first()
            val name = first.bucketName
            val isDexForm = name.equals("DexForm", ignoreCase = true)
            albumList.add(
                AlbumItem(
                    bucketId = bucketId,
                    name = name,
                    count = bucketItems.size,
                    coverUri = first.uri,
                    isDexFormAlbum = isDexForm
                )
            )
        }

        return albumList.sortedWith(compareByDescending<AlbumItem> { it.isDexFormAlbum }.thenBy { it.name })
    }

    private fun groupDexFormAlbums(items: List<MediaItem>): List<AlbumItem> {
        if (items.isEmpty()) return emptyList()

        val grouped = items.groupBy { item ->
            when {
                item.mimeType.contains("pdf", ignoreCase = true) -> -100L // PDFs
                item.bucketId == -999L -> -999L // Tool Cache
                else -> -10L // DexForm Form Ready Photos
            }
        }

        val dexFormAlbumList = mutableListOf<AlbumItem>()

        for ((groupId, groupItems) in grouped) {
            val first = groupItems.first()
            val albumName = when (groupId) {
                -100L -> "DexForm PDFs & Documents"
                -999L -> "DexForm Tool Outputs"
                else -> "DexForm (Form Ready)"
            }

            dexFormAlbumList.add(
                AlbumItem(
                    bucketId = groupId,
                    name = albumName,
                    count = groupItems.size,
                    coverUri = first.uri,
                    isDexFormAlbum = true
                )
            )
        }

        return dexFormAlbumList.sortedBy { it.name }
    }

    fun getItemsForAlbum(bucketId: Long): List<MediaItem> {
        val currentDexForm = _mediaItems.value.filter {
            it.bucketName.equals("DexForm", ignoreCase = true) ||
                    (it.relativePath != null && it.relativePath.contains("DexForm", ignoreCase = true))
        }

        return when (bucketId) {
            -100L -> currentDexForm.filter { it.mimeType.contains("pdf", ignoreCase = true) }
            -999L -> {
                val processedDir = File(context.cacheDir, "processed")
                val cacheItems = mutableListOf<MediaItem>()
                if (processedDir.exists()) {
                    processedDir.listFiles()?.forEach { file ->
                        if (file.isFile && file.length() > 0) {
                            val uri = try {
                                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            } catch (_: Exception) {
                                Uri.fromFile(file)
                            }
                            val isPdf = file.name.endsWith(".pdf", ignoreCase = true)
                            val mime = if (isPdf) "application/pdf" else "image/jpeg"
                            cacheItems.add(
                                MediaItem(
                                    id = file.hashCode().toLong(),
                                    uri = uri,
                                    displayName = file.name,
                                    mimeType = mime,
                                    sizeBytes = file.length(),
                                    width = 0,
                                    height = 0,
                                    dateTakenMs = file.lastModified(),
                                    isVideo = false,
                                    durationMs = 0L,
                                    bucketId = -999L,
                                    bucketName = "DexForm Tool Outputs",
                                    relativePath = "cache/processed"
                                )
                            )
                        }
                    }
                }
                cacheItems
            }
            -10L -> currentDexForm.filter { !it.mimeType.contains("pdf", ignoreCase = true) }
            else -> _mediaItems.value.filter { it.bucketId == bucketId }
        }
    }
}
