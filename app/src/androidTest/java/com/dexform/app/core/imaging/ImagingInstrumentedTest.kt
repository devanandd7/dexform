package com.dexform.app.core.imaging

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class ImagingInstrumentedTest {

    @Test
    fun testFlattenToWhite() {
        val src = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val flattened = flattenToWhite(src)
        assertEquals(100, flattened.width)
        assertEquals(100, flattened.height)
    }

    @Test
    fun testResizeExactModes() {
        val src = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)

        val cropFill = resizeExact(src, 100, 100, FitMode.CROP_FILL)
        assertEquals(100, cropFill.width)
        assertEquals(100, cropFill.height)

        val fitPadding = resizeExact(src, 100, 100, FitMode.FIT_WHITE_PADDING)
        assertEquals(100, fitPadding.width)
        assertEquals(100, fitPadding.height)

        val stretch = resizeExact(src, 100, 100, FitMode.STRETCH)
        assertEquals(100, stretch.width)
        assertEquals(100, stretch.height)
    }

    @Test
    fun testCompressToMax() {
        val src = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        val result = compressToMax(src, 20 * KB)
        assertTrue("Compressed size should be <= 20 KB limit", result.bytes.size <= 20 * KB)
        assertTrue("Compressed quality should be > 0", result.quality > 0)
    }

    @Test
    fun testPadJpeg() {
        val src = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val jpeg = toJpeg(src, 50)
        val targetSize = jpeg.size + 1024
        val padded = padJpeg(jpeg, targetSize)

        assertEquals("Padded size should match target", targetSize, padded.size)
        assertEquals(0xFF.toByte(), padded[0])
        assertEquals(0xD8.toByte(), padded[1])
    }

    @Test
    fun testPdfWriter() {
        val src = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val jpeg = toJpeg(src, 80)
        val pages = listOf(JpegPage(jpeg, 100, 100))

        val bos = ByteArrayOutputStream()
        PdfWriter.write(pages, bos)
        val pdfBytes = bos.toByteArray()

        assertTrue("PDF header should start with %PDF-1.4", pdfBytes.isNotEmpty())
        val pdfString = String(pdfBytes, Charsets.ISO_8859_1)
        assertTrue(pdfString.startsWith("%PDF-1.4"))
        assertTrue(pdfString.contains("%%EOF"))
    }
}
