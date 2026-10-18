package com.dexform.app.core.imaging

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

/** @param lockDims true when exact pixel size is required: only quality may change. */
fun compressToMax(src: Bitmap, maxBytes: Long, lockDims: Boolean = false): Encoded {
    val limit = (maxBytes * 0.97).toLong()
    val base = flattenToWhite(src)
    val scales = if (lockDims) listOf(1.0) else listOf(1.0, 0.9, 0.8, 0.7, 0.6, 0.5, 0.4, 0.3)
    val bos = ByteArrayOutputStream()

    for (s in scales) {
        val bmp = if (s == 1.0) base else
            Bitmap.createScaledBitmap(base, (base.width * s).toInt(), (base.height * s).toInt(), true)
        if (maxOf(bmp.width, bmp.height) < 120) {
            if (bmp !== base) bmp.recycle()
            break
        }

        var lo = 15
        var hi = 95
        var best: ByteArray? = null
        var bestQ = 0
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            val bytes = encodeJpeg(bmp, mid, bos)
            if (bytes.size <= limit) {
                best = bytes
                bestQ = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        if (best != null) return Encoded(best, bestQ, bmp.width, bmp.height)
        if (bmp !== base) bmp.recycle()
    }
    throw TargetNotReachable("Cannot reach ${maxBytes / KB} KB without making the image unreadable")
}

fun ensureMin(src: Bitmap, minBytes: Long, maxBytes: Long, lockDims: Boolean): ByteArray {
    val base = flattenToWhite(src)
    val bos = ByteArrayOutputStream()
    var bytes = encodeJpeg(base, 95, bos)
    if (!lockDims) {
        var scale = 1.0
        while (bytes.size < minBytes && scale < 2.0) {
            scale *= 1.15
            val up = Bitmap.createScaledBitmap(base, (base.width * scale).toInt(), (base.height * scale).toInt(), true)
            bytes = encodeJpeg(up, 90, bos)
            up.recycle()
        }
    }
    if (bytes.size < minBytes) {
        val target = minOf(maxBytes, (minBytes * 1.02).toLong()).toInt()
        bytes = padJpeg(bytes, target)
    }
    if (bytes.size > maxBytes) throw TargetNotReachable("Cannot fit between min and max size")
    return bytes
}

/** Adds JPEG comment (COM) segments after SOI so the file reaches `target` bytes and still decodes. */
fun padJpeg(jpeg: ByteArray, target: Int): ByteArray {
    require(jpeg.size > 2 && jpeg[0] == 0xFF.toByte() && jpeg[1] == 0xD8.toByte()) { "Not a JPEG" }
    var gap = target - jpeg.size
    if (gap <= 0) return jpeg
    if (gap < 4) gap = 4 // a COM segment needs at least 4 bytes
    val out = ByteArrayOutputStream(jpeg.size + gap)
    out.write(jpeg, 0, 2) // SOI
    while (gap > 0) {
        val seg = when {
            gap <= 65537 -> gap
            gap - 65537 in 1..3 -> 65537 - 4
            else -> 65537
        }
        val payload = seg - 4
        out.write(0xFF)
        out.write(0xFE)
        out.write((payload + 2) shr 8)
        out.write((payload + 2) and 0xFF)
        repeat(payload) { out.write(0x20) }
        gap -= seg
    }
    out.write(jpeg, 2, jpeg.size - 2)
    val result = out.toByteArray()
    // Verify decodability
    checkNotNull(BitmapFactory.decodeByteArray(result, 0, result.size)) {
        "Padded JPEG failed to decode"
    }
    return result
}
