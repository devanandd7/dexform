package com.dexform.app.core.ai

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.dexform.app.core.imaging.BitmapLoader
import com.dexform.app.core.imaging.Fmt
import com.dexform.app.core.imaging.Requirement
import com.dexform.app.core.imaging.readInfo
import com.dexform.app.core.ocr.OcrEngine
import com.dexform.app.core.ocr.OcrResult
import com.dexform.app.core.requirement.RequirementParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class StructuredFormRequirements(
    val fieldType: String,
    val maxKb: Long?,
    val minKb: Long?,
    val widthPx: Int?,
    val heightPx: Int?,
    val formats: List<Fmt>,
    val correctedText: String,
    val rawOcrText: String,
    val aiAnalysisSummary: String
)

object MultimodalFormAnalyzer {

    suspend fun analyzeFormImage(
        context: Context,
        imageUri: Uri,
        localAiManager: LocalAiManager
    ): StructuredFormRequirements = withContext(Dispatchers.Default) {
        // Path 1: Pass to ML Kit OCR and extract raw text blocks
        val ocrResult: OcrResult = try {
            OcrEngine.extractText(context, imageUri)
        } catch (e: Exception) {
            OcrResult(fullText = "", blocks = emptyList())
        }

        val rawText = ocrResult.fullText
        val imageInfo = readInfo(context, imageUri)

        // Path 2: AI Prompt Synthesis & Typo Correction
        val parsedReq: Requirement = RequirementParser.parse(null, listOf(rawText) + ocrResult.blocks)

        // Correction engine: Correct misspelled form terms (e.g. k.b -> KB, photograf -> Photo)
        val correctedText = rawText
            .replace(Regex("""(?i)\bk\.?\s?b\.?\b"""), "KB")
            .replace(Regex("""(?i)\bm\.?\s?b\.?\b"""), "MB")
            .replace(Regex("""(?i)\bphotograf|fotograf|fotograph\b"""), "Photo")
            .replace(Regex("""(?i)\bsignture|signatur\b"""), "Signature")
            .replace(Regex("""\s+"""), " ")
            .trim()

        val prompt = """
            Analyze Form Requirement Image:
            Raw OCR: "$rawText"
            Image File Info: Size=${imageInfo.sizeBytes / 1024} KB, Dims=${imageInfo.width}x${imageInfo.height}, Format=${imageInfo.format}
            Corrected Text: "$correctedText"
            Requirement: MaxKB=${parsedReq.maxBytes?.let { it / 1024 }}, MinKB=${parsedReq.minBytes?.let { it / 1024 }}, Dims=${parsedReq.widthPx}x${parsedReq.heightPx}, Formats=${parsedReq.formats}
        """.trimIndent()

        val aiSummary = try {
            localAiManager.generateResponse(prompt).first
        } catch (_: Exception) {
            "Form requirement parsed: $correctedText"
        }

        val detectedType = when {
            rawText.contains("sig", ignoreCase = true) -> "Signature"
            rawText.contains("photo", ignoreCase = true) || rawText.contains("फोटो", ignoreCase = true) -> "Photo"
            rawText.contains("pdf", ignoreCase = true) || rawText.contains("document", ignoreCase = true) -> "Document / PDF"
            else -> "Form Field"
        }

        StructuredFormRequirements(
            fieldType = detectedType,
            maxKb = parsedReq.maxBytes?.let { it / 1024 },
            minKb = parsedReq.minBytes?.let { it / 1024 },
            widthPx = parsedReq.widthPx,
            heightPx = parsedReq.heightPx,
            formats = parsedReq.formats.toList().ifEmpty { listOf(Fmt.JPG) },
            correctedText = if (correctedText.isBlank()) "No text detected" else correctedText,
            rawOcrText = if (rawText.isBlank()) "None" else rawText,
            aiAnalysisSummary = aiSummary
        )
    }
}
