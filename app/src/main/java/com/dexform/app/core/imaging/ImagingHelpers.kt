package com.dexform.app.core.imaging

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.io.File

const val KB = 1024L
const val MB = 1024L * 1024L

fun flattenToWhite(src: Bitmap): Bitmap {
    val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    Canvas(out).apply {
        drawColor(Color.WHITE)
        drawBitmap(src, 0f, 0f, null)
    }
    return out
}

fun encodeJpeg(bmp: Bitmap, quality: Int, bos: ByteArrayOutputStream): ByteArray {
    bos.reset()
    bmp.compress(Bitmap.CompressFormat.JPEG, quality, bos)
    return bos.toByteArray()
}

fun Context.querySize(uri: Uri): Long {
    if (uri.scheme == "file" && uri.path != null) {
        val f = File(uri.path!!)
        if (f.exists()) return f.length()
    }
    val queried = try {
        contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIdx >= 0) cursor.getLong(sizeIdx) else -1L
            } else -1L
        } ?: -1L
    } catch (_: Exception) {
        -1L
    }

    if (queried > 0) return queried

    val afdLength = try {
        contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
    } catch (_: Exception) {
        -1L
    }

    if (afdLength > 0) return afdLength

    return try {
        contentResolver.openInputStream(uri)?.use { stream ->
            stream.readBytes().size.toLong()
        } ?: -1L
    } catch (_: Exception) {
        -1L
    }
}

fun toJpeg(src: Bitmap, quality: Int = 90): ByteArray =
    encodeJpeg(flattenToWhite(src), quality, ByteArrayOutputStream())

fun toPng(src: Bitmap): ByteArray = ByteArrayOutputStream().also {
    src.compress(Bitmap.CompressFormat.PNG, 100, it)
}.toByteArray()

fun rotate(src: Bitmap, degrees: Float): Bitmap =
    Bitmap.createBitmap(src, 0, 0, src.width, src.height, Matrix().apply { postRotate(degrees) }, true)

fun readInfo(context: Context, uri: Uri): FileInfo {
    var size = context.querySize(uri)
    var mime = try {
        context.contentResolver.getType(uri) ?: ""
    } catch (_: Exception) {
        ""
    }

    val pathOrName = uri.path ?: uri.toString()
    if (mime.isBlank()) {
        mime = when {
            pathOrName.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
            pathOrName.endsWith(".png", ignoreCase = true) -> "image/png"
            pathOrName.endsWith(".jpg", ignoreCase = true) || pathOrName.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
            pathOrName.endsWith(".webp", ignoreCase = true) -> "image/webp"
            else -> ""
        }
    }

    var fmt = when {
        mime.contains("jpeg", ignoreCase = true) || mime.contains("jpg", ignoreCase = true) -> Fmt.JPG
        mime.contains("png", ignoreCase = true) -> Fmt.PNG
        mime.contains("pdf", ignoreCase = true) -> Fmt.PDF
        else -> Fmt.OTHER
    }

    if (fmt == Fmt.OTHER) {
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val header = ByteArray(4)
                val read = stream.read(header)
                if (read >= 2 && header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte()) {
                    fmt = Fmt.JPG
                } else if (read >= 4 && header[0] == 0x89.toByte() && header[1] == 'P'.code.toByte() && header[2] == 'N'.code.toByte() && header[3] == 'G'.code.toByte()) {
                    fmt = Fmt.PNG
                } else if (read >= 4 && header[0] == '%'.code.toByte() && header[1] == 'P'.code.toByte() && header[2] == 'D'.code.toByte() && header[3] == 'F'.code.toByte()) {
                    fmt = Fmt.PDF
                }
            }
        } catch (_: Exception) {}
    }

    var w: Int? = null
    var h: Int? = null
    if (fmt == Fmt.JPG || fmt == Fmt.PNG || fmt == Fmt.OTHER) {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, o)
            }
        } catch (_: Exception) {}
        if (o.outWidth > 0 && o.outHeight > 0) {
            w = o.outWidth
            h = o.outHeight
            if (fmt == Fmt.OTHER) fmt = Fmt.JPG
        }
    }

    if (size <= 0) {
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                size = stream.readBytes().size.toLong()
            }
        } catch (_: Exception) {}
    }

    return FileInfo(sizeBytes = size, format = fmt, width = w, height = h)
}

fun needsConversion(f: FileInfo, r: Requirement): Boolean {
    if (r.maxBytes != null && (f.sizeBytes <= 0 || f.sizeBytes > r.maxBytes)) return true
    if (r.minBytes != null && (f.sizeBytes <= 0 || f.sizeBytes < r.minBytes)) return true
    if (r.formats.isNotEmpty() && f.format !in r.formats) return true
    if (r.widthPx != null && (f.width == null || f.width != r.widthPx)) return true
    if (r.heightPx != null && (f.height == null || f.height != r.heightPx)) return true
    return false
}
