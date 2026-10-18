package com.dexform.app.core.imaging

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

suspend fun makeFormReady(
    context: Context,
    uri: Uri,
    req: Requirement
): File = withContext(Dispatchers.Default) {
    var bmp = BitmapLoader.load(context, uri, 2000)
    val lock = req.widthPx != null && req.heightPx != null
    if (lock) {
        bmp = resizeExact(bmp, req.widthPx!!, req.heightPx!!, FitMode.CROP_FILL)
    }

    val bytes = when {
        req.maxBytes != null -> {
            val e = compressToMax(bmp, req.maxBytes, lockDims = lock)
            if (req.minBytes != null && e.bytes.size < req.minBytes) {
                ensureMin(bmp, req.minBytes, req.maxBytes, lock)
            } else {
                e.bytes
            }
        }
        req.minBytes != null -> ensureMin(bmp, req.minBytes, Long.MAX_VALUE, lock)
        else -> toJpeg(bmp)
    }

    val dir = File(context.cacheDir, "processed").apply { mkdirs() }
    val maxKbName = (req.maxBytes ?: 0) / KB
    val fileName = if (maxKbName > 0) "photo_${maxKbName}kb.jpg" else "photo_processed.jpg"
    File(dir, fileName).apply { writeBytes(bytes) }
}
