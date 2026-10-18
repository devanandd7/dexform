package com.dexform.app.core.requirement

import com.dexform.app.core.imaging.FileInfo
import com.dexform.app.core.imaging.Fmt
import com.dexform.app.core.imaging.KB
import com.dexform.app.core.imaging.MB
import com.dexform.app.core.imaging.Requirement
import com.dexform.app.core.imaging.needsConversion
import com.dexform.app.ui.browser.UploadProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RequirementParserTest {

    @Test
    fun testMax20KbJpg() {
        val req = RequirementParser.parse(null, listOf("Upload photo (max 20 KB, JPG)"))
        assertEquals(20 * KB, req.maxBytes)
        assertTrue(Fmt.JPG in req.formats)
    }

    @Test
    fun testRange10To50Kb() {
        val req = RequirementParser.parse(null, listOf("Size should be between 10 KB and 50 KB"))
        assertEquals(10 * KB, req.minBytes)
        assertEquals(50 * KB, req.maxBytes)
    }

    @Test
    fun testCmDimensionsIgnored() {
        val req = RequirementParser.parse(null, listOf("Photograph: 20kb-50kb, 3.5cm x 4.5cm"))
        assertEquals(20 * KB, req.minBytes)
        assertEquals(50 * KB, req.maxBytes)
        assertNull(req.widthPx)
        assertNull(req.heightPx)
    }

    @Test
    fun testPixelsAndMax30Kb() {
        val req = RequirementParser.parse(null, listOf("Signature 140 x 60 pixels, below 30KB"))
        assertEquals(140, req.widthPx)
        assertEquals(60, req.heightPx)
        assertEquals(30 * KB, req.maxBytes)
    }

    @Test
    fun testPdfNotMoreThan200Kb() {
        val req = RequirementParser.parse("application/pdf", listOf("Upload PDF not more than 200 KB"))
        assertEquals(200 * KB, req.maxBytes)
        assertTrue(Fmt.PDF in req.formats)
    }

    @Test
    fun testMax2Mb() {
        val req = RequirementParser.parse(null, listOf("Maximum file size: 2 MB"))
        assertEquals(2 * MB, req.maxBytes)
    }

    @Test
    fun testMin50Kb() {
        val req = RequirementParser.parse(null, listOf("Minimum 50 KB"))
        assertEquals(50 * KB, req.minBytes)
        assertNull(req.maxBytes)
    }

    @Test
    fun testHindiMax50Kb() {
        val req = RequirementParser.parse(null, listOf("फोटो अधिकतम 50 KB"))
        assertEquals(50 * KB, req.maxBytes)
    }

    @Test
    fun testHindi10KbSeKam() {
        val req = RequirementParser.parse(null, listOf("10 KB से कम"))
        assertEquals(10 * KB, req.maxBytes)
    }

    @Test
    fun testBare200Kb() {
        val req = RequirementParser.parse(null, listOf("200KB"))
        assertEquals(200 * KB, req.maxBytes)
        assertEquals(0.5f, req.confidence)
    }

    @Test
    fun testAcceptTypes() {
        val reqImage = RequirementParser.parse("image/jpeg,image/png", emptyList())
        assertTrue(Fmt.JPG in reqImage.formats)
        assertTrue(Fmt.PNG in reqImage.formats)

        val reqPdf = RequirementParser.parse(".pdf", emptyList())
        assertTrue(Fmt.PDF in reqPdf.formats)
    }

    @Test
    fun testFirstHintWinsForSize() {
        val req = RequirementParser.parse(null, listOf("Photo max 20KB", "Photo max 20KB Signature max 10KB"))
        assertEquals(20 * KB, req.maxBytes)
    }

    @Test
    fun testNeedsConversionWithDimensionsMismatch() {
        val req = Requirement(maxBytes = 50 * KB, widthPx = 200, heightPx = 230, formats = setOf(Fmt.JPG))
        
        // Mismatched dimensions (3072x4096)
        val infoMismatched = FileInfo(sizeBytes = 40 * KB, format = Fmt.JPG, width = 3072, height = 4096)
        assertTrue("Image with 3072x4096 must need conversion when 200x230 requested", needsConversion(infoMismatched, req))

        // Unknown size (-1)
        val infoUnknownSize = FileInfo(sizeBytes = -1L, format = Fmt.JPG, width = 200, height = 230)
        assertTrue("Unknown size file (-1) must need conversion", needsConversion(infoUnknownSize, req))

        // Matching
        val infoMatching = FileInfo(sizeBytes = 40 * KB, format = Fmt.JPG, width = 200, height = 230)
        assertFalse("Matching file should not need conversion", needsConversion(infoMatching, req))
    }

    @Test
    fun testPickTargetFormat() {
        val reqPdf = Requirement(formats = setOf(Fmt.PDF))
        assertEquals(Fmt.PDF, UploadProcessor.pickTarget(listOf(Fmt.JPG), reqPdf))

        val reqJpg = Requirement(formats = setOf(Fmt.JPG))
        assertEquals(Fmt.JPG, UploadProcessor.pickTarget(listOf(Fmt.PDF), reqJpg))
    }
}
