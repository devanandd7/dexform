package com.dexform.app.data.media

import android.net.Uri

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val dateTakenMs: Long,
    val isVideo: Boolean,
    val durationMs: Long,
    val bucketId: Long,
    val bucketName: String,
    val relativePath: String?
)

data class AlbumItem(
    val bucketId: Long,
    val name: String,
    val count: Int,
    val coverUri: Uri,
    val isDexFormAlbum: Boolean = false
)
