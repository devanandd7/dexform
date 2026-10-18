package com.dexform.app.core.imaging

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.Locale

object PdfWriter {
    private const val PAGE_W = 595.0 // A4 in points
    private const val PAGE_H = 842.0

    fun write(pages: List<JpegPage>, out: OutputStream) {
        var pos = 0L
        val offsets = ArrayList<Long>()
        fun put(b: ByteArray) {
            out.write(b)
            pos += b.size
        }
        fun put(s: String) = put(s.toByteArray(Charsets.ISO_8859_1))
        fun begin(num: Int) {
            while (offsets.size < num) offsets.add(0L)
            offsets[num - 1] = pos
        }
        fun f(d: Double) = String.format(Locale.US, "%.2f", d)

        put("%PDF-1.4\n")
        begin(1)
        put("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")
        val kids = pages.indices.joinToString(" ") { "${3 + 3 * it} 0 R" }
        begin(2)
        put("2 0 obj\n<< /Type /Pages /Kids [$kids] /Count ${pages.size} >>\nendobj\n")

        pages.forEachIndexed { i, p ->
            val pageObj = 3 + 3 * i
            val contentObj = 4 + 3 * i
            val imgObj = 5 + 3 * i
            val s = minOf(PAGE_W / p.w, PAGE_H / p.h)
            val dw = p.w * s
            val dh = p.h * s
            val x = (PAGE_W - dw) / 2
            val y = (PAGE_H - dh) / 2
            val content = "q ${f(dw)} 0 0 ${f(dh)} ${f(x)} ${f(y)} cm /Im0 Do Q\n"

            begin(pageObj)
            put("$pageObj 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] " +
                    "/Resources << /XObject << /Im0 $imgObj 0 R >> >> /Contents $contentObj 0 R >>\nendobj\n")
            begin(contentObj)
            put("$contentObj 0 obj\n<< /Length ${content.length} >>\nstream\n$content" + "endstream\nendobj\n")
            begin(imgObj)
            put("$imgObj 0 obj\n<< /Type /XObject /Subtype /Image /Width ${p.w} /Height ${p.h} " +
                    "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${p.jpeg.size} >>\nstream\n")
            put(p.jpeg)
            put("\nendstream\nendobj\n")
        }

        val count = offsets.size
        val xrefPos = pos
        put("xref\n0 ${count + 1}\n")
        put("0000000000 65535 f \n") // exactly 20 bytes
        offsets.forEach { put(String.format(Locale.US, "%010d 00000 n \n", it)) } // exactly 20 bytes
        put("trailer\n<< /Size ${count + 1} /Root 1 0 R >>\nstartxref\n$xrefPos\n%%EOF\n")
        out.flush()
    }
}

/** Images -> PDF. If maxBytes is set, each page gets an equal share of the budget. */
fun imagesToPdf(context: Context, uris: List<Uri>, maxBytes: Long?): ByteArray {
    val longSide = if (uris.size > 4) 1200 else 1600
    var share = 1.0 // shrinks by 10% per failed round
    repeat(6) {
        val pages = uris.map { uri ->
            val bmp = flattenToWhite(BitmapLoader.load(context, uri, longSide))
            if (maxBytes == null) {
                val jpeg = encodeJpeg(bmp, 85, ByteArrayOutputStream())
                JpegPage(jpeg, bmp.width, bmp.height)
            } else {
                val limit = (maxBytes * 0.97).toLong()
                val perPage = ((limit - 1500 - 600L * uris.size) / uris.size * share).toLong()
                val e = compressToMax(bmp, perPage.coerceAtLeast(2 * KB))
                JpegPage(e.bytes, e.width, e.height)
            }
        }
        val bos = ByteArrayOutputStream()
        PdfWriter.write(pages, bos)
        if (maxBytes == null || bos.size().toLong() <= (maxBytes * 0.97).toLong()) return bos.toByteArray()
        share *= 0.9
    }
    throw TargetNotReachable("Cannot fit these pages in ${maxBytes!! / KB} KB")
}

fun shrinkPdf(context: Context, uri: Uri, maxBytes: Long): ByteArray {
    val originalSize = context.querySize(uri)
    if (originalSize in 1..maxBytes) {
        context.contentResolver.openInputStream(uri)?.use { return it.readBytes() }
    }

    val limit = (maxBytes * 0.97).toLong()
    val pfd = context.contentResolver.openFileDescriptor(uri, "r")
        ?: throw IllegalArgumentException("Cannot open PDF")
    try {
        val renderer = try {
            PdfRenderer(pfd)
        } catch (e: SecurityException) {
            throw TargetNotReachable("This PDF is protected and cannot be shrunk")
        }
        try {
            for (jpegQuality in intArrayOf(70, 50)) {
                for (dpi in intArrayOf(150, 120, 100, 80, 60)) {
                    val pages = ArrayList<JpegPage>()
                    for (i in 0 until renderer.pageCount) {
                        val page = renderer.openPage(i)
                        try {
                            val w = (page.width * dpi / 72f).toInt().coerceAtLeast(1)
                            val h = (page.height * dpi / 72f).toInt().coerceAtLeast(1)
                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(Color.WHITE) // PdfRenderer bitmaps are transparent
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val jpeg = encodeJpeg(bmp, jpegQuality, ByteArrayOutputStream())
                            pages.add(JpegPage(jpeg, w, h))
                            bmp.recycle()
                        } finally {
                            page.close()
                        }
                    }
                    val bos = ByteArrayOutputStream()
                    PdfWriter.write(pages, bos)
                    if (bos.size().toLong() <= limit) return bos.toByteArray()
                }
            }
        } finally {
            renderer.close()
        }
    } finally {
        pfd.close()
    }
    throw TargetNotReachable("Cannot shrink this PDF to ${maxBytes / KB} KB")
}

fun pdfFirstPageToBitmap(context: Context, uri: Uri, dpi: Int = 150): Bitmap {
    val pfd = context.contentResolver.openFileDescriptor(uri, "r")
        ?: throw IllegalArgumentException("Cannot open PDF")
    pfd.use {
        val renderer = PdfRenderer(it)
        try {
            val page = renderer.openPage(0)
            try {
                val bmp = Bitmap.createBitmap(
                    (page.width * dpi / 72f).toInt(),
                    (page.height * dpi / 72f).toInt(),
                    Bitmap.Config.ARGB_8888
                )
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return bmp
            } finally {
                page.close()
            }
        } finally {
            renderer.close()
        }
    }
}
