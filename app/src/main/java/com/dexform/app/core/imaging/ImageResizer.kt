package com.dexform.app.core.imaging

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect

fun resizeExact(src: Bitmap, tw: Int, th: Int, mode: FitMode): Bitmap {
    val out = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
    val c = Canvas(out).apply { drawColor(Color.WHITE) }
    val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    when (mode) {
        FitMode.STRETCH ->
            c.drawBitmap(src, Rect(0, 0, src.width, src.height), Rect(0, 0, tw, th), paint)
        FitMode.CROP_FILL -> {
            val s = maxOf(tw.toFloat() / src.width, th.toFloat() / src.height)
            val sw = (tw / s).toInt().coerceAtMost(src.width)
            val sh = (th / s).toInt().coerceAtMost(src.height)
            val sx = (src.width - sw) / 2
            val sy = (src.height - sh) / 2
            c.drawBitmap(src, Rect(sx, sy, sx + sw, sy + sh), Rect(0, 0, tw, th), paint)
        }
        FitMode.FIT_WHITE_PADDING -> {
            val s = minOf(tw.toFloat() / src.width, th.toFloat() / src.height)
            val dw = (src.width * s).toInt()
            val dh = (src.height * s).toInt()
            val dx = (tw - dw) / 2
            val dy = (th - dh) / 2
            c.drawBitmap(src, null, Rect(dx, dy, dx + dw, dy + dh), paint)
        }
    }
    return out
}
