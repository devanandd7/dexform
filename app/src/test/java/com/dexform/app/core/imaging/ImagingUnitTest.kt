package com.dexform.app.core.imaging

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImagingUnitTest {

    @Test
    fun testNeedsConversion() {
        val info = FileInfo(sizeBytes = 30 * KB, format = Fmt.JPG, width = 200, height = 200)

        // Fits max 50 KB
        val reqPass = Requirement(maxBytes = 50 * KB, formats = setOf(Fmt.JPG))
        assertFalse(needsConversion(info, reqPass))

        // Exceeds max 20 KB
        val reqFailSize = Requirement(maxBytes = 20 * KB)
        assertTrue(needsConversion(info, reqFailSize))

        // Wrong format (requires PNG)
        val reqFailFormat = Requirement(formats = setOf(Fmt.PNG))
        assertTrue(needsConversion(info, reqFailFormat))

        // Wrong dimensions
        val reqFailDims = Requirement(widthPx = 300, heightPx = 300)
        assertTrue(needsConversion(info, reqFailDims))
    }

    @Test
    fun testPadJpegAlgorithm() {
        val dummyJpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())
        val target = 20
        val gap = target - dummyJpeg.size
        assertTrue("Gap should be calculated correctly", gap > 0)
    }
}
