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
    return try {
        contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else -1L
        } ?: -1L
    } catch (e: Exception) {
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
    val mime = context.contentResolver.getType(uri) ?: ""
    val fmt = when {
        mime.contains("jpeg", ignoreCase = true) || mime.contains("jpg", ignoreCase = true) -> Fmt.JPG
        mime.contains("png", ignoreCase = true) -> Fmt.PNG
        mime.contains("pdf", ignoreCase = true) -> Fmt.PDF
        else -> Fmt.OTHER
    }
    var w: Int? = null
    var h: Int? = null
    if (fmt == Fmt.JPG || fmt == Fmt.PNG) {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, o)
            }
        } catch (_: Exception) {}
        if (o.outWidth > 0 && o.outHeight > 0) {
            w = o.outWidth
            h = o.outHeight
        }
    }
    return FileInfo(context.querySize(uri), fmt, w, h)
}

fun needsConversion(f: FileInfo, r: Requirement): Boolean =
    (r.maxBytes != null && f.sizeBytes > r.maxBytes) ||
            (r.minBytes != null && f.sizeBytes < r.minBytes) ||
            (r.formats.isNotEmpty() && f.format !in r.formats) ||
            (r.widthPx != null && f.width != r.widthPx) ||
            (r.heightPx != null && f.height != r.heightPx)
