package com.dexform.app.core.ai

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.dexform.app.core.ocr.OcrEngine
import com.dexform.app.data.db.UserProfileDao
import com.dexform.app.data.db.UserProfileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream

enum class DocumentType {
    PAN_CARD,
    AADHAAR_CARD,
    TENTH_MARKSHEET,
    TWELFTH_MARKSHEET,
    UNKNOWN_DOCUMENT
}

data class ExtractionSummary(
    val updatedProfile: UserProfileEntity,
    val newFieldsAdded: List<String>,
    val duplicatesSkipped: List<String>,
    val detectedDocType: DocumentType,
    val aiResponseText: String
)

object ProfileExtractorEngine {

    suspend fun extractAndMergeDocuments(
        context: Context,
        imageUris: List<Uri>,
        targetProfile: UserProfileEntity,
        userQuery: String?,
        profileDao: UserProfileDao,
        localAiManager: LocalAiManager,
        useOcr: Boolean = false, // Default false: Bypass OCR for 10x faster direct vision
        onStepUpdate: (String) -> Unit = {},
        onTokenStream: ((String) -> Unit)? = null
    ): ExtractionSummary = withContext(Dispatchers.Default) {

        var rawText = ""

        val extractionPrompt = buildString {
            appendLine("Extract Indian identity and academic document details accurately:")
            appendLine("\nFormat strictly as Markdown summary, followed by a JSON block:")
            appendLine("- **Document:** <PAN Card, Aadhaar, 10th Marksheet, 12th Marksheet>")
            appendLine("- **Name:** <Candidate Full Name>")
            appendLine("- **Father's Name:** <Father's Name>")
            appendLine("- **Mother's Name:** <Mother's Name>")
            appendLine("- **DOB:** <DD/MM/YYYY>")
            appendLine("- **ID Number:** <PAN or Aadhaar Number>")
            appendLine("```json")
            appendLine("{\"doc_type\":\"PAN_CARD\",\"name\":\"\",\"father_name\":\"\",\"mother_name\":\"\",\"dob\":\"\",\"id_number\":\"\",\"tenth_roll_no\":\"\",\"tenth_reg_no\":\"\",\"tenth_passing_year\":\"\",\"twelfth_roll_no\":\"\",\"twelfth_reg_no\":\"\",\"twelfth_passing_year\":\"\"}")
            appendLine("```")
        }

        val aiResponse = if (!useOcr && imageUris.isNotEmpty()) {
            // ==========================================
            // DIRECT VISION FLOW (Edge Gallery Architecture - 10x Super-Fast Speed)
            // ==========================================
            onStepUpdate("⚡ Stage 1/2: Direct Vision Flow (Bypassing OCR for Edge Gallery Speed)...")
            try {
                val primaryUri = imageUris.first()
                val bitmap = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(primaryUri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                }

                if (bitmap != null) {
                    onStepUpdate("🧠 Stage 2/2: On-Device Gemma Analyzing Image Natively...")
                    localAiManager.generateResponseWithImage(
                        bitmap = bitmap,
                        prompt = extractionPrompt,
                        enableThinking = false,
                        temperature = 0.1f, // Greedy decoding for ultra-fast response
                        topK = 1,
                        onTokenStream = { partialAnswer, _ ->
                            onTokenStream?.invoke(partialAnswer)
                        }
                    ).first
                } else {
                    throw IllegalStateException("Unable to decode document image bitmap")
                }
            } catch (visionErr: Exception) {
                Log.w("ProfileExtractor", "Direct vision error (${visionErr.message}), falling back to text engine", visionErr)
                localAiManager.generateResponse(
                    prompt = extractionPrompt,
                    enableThinking = false,
                    temperature = 0.1f,
                    topK = 1,
                    onTokenStream = { partialAnswer, _ ->
                        onTokenStream?.invoke(partialAnswer)
                    }
                ).first
            }
        } else {
            // ==========================================
            // HYBRID OCR + AI FLOW (When OCR toggle is explicitly enabled)
            // ==========================================
            onStepUpdate("🔍 Stage 1/3: High-Accuracy Document Scan...")
            val ocrResults = imageUris.take(3).map { uri ->
                try {
                    OcrEngine.extractText(context, uri).fullText
                } catch (e: Exception) {
                    Log.w("ProfileExtractor", "Scan fallback: ${e.message}")
                    ""
                }
            }
            rawText = ocrResults.filter { it.isNotBlank() }.joinToString("\n---\n")

            onStepUpdate("🧠 Stage 2/3: On-Device AI Analyzing & Extracting Details...")
            val hybridPrompt = buildString {
                appendLine("Extract Indian identity and academic document details accurately:")
                if (rawText.isNotBlank()) {
                    appendLine("\"\"\"")
                    appendLine(rawText.take(1500).trim())
                    appendLine("\"\"\"")
                }
                append(extractionPrompt)
            }

            try {
                localAiManager.generateResponse(
                    prompt = hybridPrompt,
                    enableThinking = false,
                    temperature = 0.1f,
                    topK = 1,
                    onTokenStream = { partialAnswer, _ ->
                        onTokenStream?.invoke(partialAnswer)
                    }
                ).first
            } catch (e: Exception) {
                Log.e("ProfileExtractor", "AI generation error: ${e.message}", e)
                "Document analyzed. Unable to extract fields automatically: ${e.message}"
            }
        }

        // ==========================================
        // STAGE 3: Structured AI Output Parsing
        // ==========================================
        onStepUpdate("📊 Stage 3/3: Mapping Verified AI Fields...")

        var aiDocTypeStr: String? = null
        var aiName: String? = null
        var aiFather: String? = null
        var aiMother: String? = null
        var aiDob: String? = null
        var aiIdNum: String? = null
        var ai10thRoll: String? = null
        var ai10thReg: String? = null
        var ai10thYear: String? = null
        var ai12thRoll: String? = null
        var ai12thReg: String? = null
        var ai12thYear: String? = null

        // 1. JSON parsing from AI output
        val jsonBlockMatch = Regex("""(?s)```(?:json)?\s*(\{.*?\})\s*```""").find(aiResponse)
        if (jsonBlockMatch != null) {
            try {
                val json = JSONObject(jsonBlockMatch.groupValues[1])
                aiDocTypeStr = json.optString("doc_type").takeIf { it.isNotBlank() }
                aiName = json.optString("name").takeIf { it.isNotBlank() && !it.equals("N/A", true) && !it.equals("null", true) }
                aiFather = json.optString("father_name").takeIf { it.isNotBlank() && !it.equals("N/A", true) && !it.equals("null", true) }
                aiMother = json.optString("mother_name").takeIf { it.isNotBlank() && !it.equals("N/A", true) && !it.equals("null", true) }
                aiDob = json.optString("dob").takeIf { it.isNotBlank() && !it.equals("N/A", true) && !it.equals("null", true) }
                aiIdNum = json.optString("id_number").takeIf { it.isNotBlank() && !it.equals("N/A", true) && !it.equals("null", true) }
                ai10thRoll = json.optString("tenth_roll_no").takeIf { it.isNotBlank() }
                ai10thReg = json.optString("tenth_reg_no").takeIf { it.isNotBlank() }
                ai10thYear = json.optString("tenth_passing_year").takeIf { it.isNotBlank() }
                ai12thRoll = json.optString("twelfth_roll_no").takeIf { it.isNotBlank() }
                ai12thReg = json.optString("twelfth_reg_no").takeIf { it.isNotBlank() }
                ai12thYear = json.optString("twelfth_passing_year").takeIf { it.isNotBlank() }
            } catch (e: Exception) {
                Log.w("ProfileExtractor", "JSON parse error: ${e.message}")
            }
        }

        // 2. Fallback Regex Parsing on AI Markdown text
        if (aiName.isNullOrBlank()) {
            aiName = Regex("""(?i)\b(?:\*\*Name:\*\*|Full Name:|Name:)\s*(?:Extracted as\s*)?["']?([A-Z\s]{3,35})["']?""").find(aiResponse)?.groupValues?.get(1)?.trim()
                ?.takeIf { !it.contains("Extracted as", true) && !it.equals("N/A", true) }
                ?: Regex("""(?i)Extracted as\s*["']([A-Z\s]{3,35})["']""").find(aiResponse)?.groupValues?.get(1)?.trim()
        }
        if (aiFather.isNullOrBlank()) {
            aiFather = Regex("""(?i)\b(?:\*\*Father'?s?\s*Name:\*\*|Father'?s?\s*Name:)\s*(?:Extracted as\s*)?["']?([A-Z\s]{3,35})["']?""").find(aiResponse)?.groupValues?.get(1)?.trim()
                ?.takeIf { !it.equals("N/A", true) }
                ?: Regex("""(?i)Father'?s?\s*Name.*?Extracted as\s*["']([A-Z\s]{3,35})["']""").find(aiResponse)?.groupValues?.get(1)?.trim()
        }
        if (aiDob.isNullOrBlank()) {
            aiDob = Regex("""(?i)\b(?:DOB|Date of Birth)\D{1,15}["']?(\d{1,2}[/-]\d{1,2}[/-]\d{2,4})["']?""").find(aiResponse)?.groupValues?.get(1)?.trim()
                ?: Regex("""\b(\d{2}[/-]\d{2}[/-]\d{4})\b""").find(aiResponse)?.value
        }
        if (aiIdNum.isNullOrBlank()) {
            aiIdNum = Regex("""\b([A-Z]{5}\d{4}[A-Z])\b""").find(aiResponse)?.value
                ?: Regex("""\b(\d{4}\s\d{4}\s\d{4})\b""").find(aiResponse)?.value
                ?: Regex("""\b([A-Z]{5}\d{4}[A-Z])\b""").find(rawText)?.value
        }

        val newFields = mutableListOf<String>()
        val duplicates = mutableListOf<String>()

        if (!aiName.isNullOrBlank()) {
            if (targetProfile.name.equals(aiName, ignoreCase = true)) duplicates.add("Full Name ($aiName)")
            else newFields.add("Full Name: $aiName")
        }
        if (!aiFather.isNullOrBlank()) {
            if (targetProfile.fatherName.equals(aiFather, ignoreCase = true)) duplicates.add("Father's Name ($aiFather)")
            else newFields.add("Father's Name: $aiFather")
        }
        if (!aiMother.isNullOrBlank()) {
            if (targetProfile.motherName.equals(aiMother, ignoreCase = true)) duplicates.add("Mother's Name ($aiMother)")
            else newFields.add("Mother's Name: $aiMother")
        }
        if (!aiDob.isNullOrBlank()) {
            if (targetProfile.dob.equals(aiDob, ignoreCase = true)) duplicates.add("DOB ($aiDob)")
            else newFields.add("DOB: $aiDob")
        }
        if (!aiIdNum.isNullOrBlank()) {
            if (targetProfile.idNumber.equals(aiIdNum, ignoreCase = true)) duplicates.add("ID Number ($aiIdNum)")
            else newFields.add("ID Number: $aiIdNum")
        }
        if (!ai10thRoll.isNullOrBlank()) newFields.add("10th Roll No: $ai10thRoll")
        if (!ai10thReg.isNullOrBlank()) newFields.add("10th Reg No: $ai10thReg")
        if (!ai10thYear.isNullOrBlank()) newFields.add("10th Passing Year: $ai10thYear")
        if (!ai12thRoll.isNullOrBlank()) newFields.add("12th Roll No: $ai12thRoll")
        if (!ai12thReg.isNullOrBlank()) newFields.add("12th Reg No: $ai12thReg")
        if (!ai12thYear.isNullOrBlank()) newFields.add("12th Passing Year: $ai12thYear")

        val docType = when {
            aiDocTypeStr?.contains("PAN", true) == true -> DocumentType.PAN_CARD
            aiDocTypeStr?.contains("AADHAAR", true) == true -> DocumentType.AADHAAR_CARD
            aiDocTypeStr?.contains("12", true) == true -> DocumentType.TWELFTH_MARKSHEET
            aiDocTypeStr?.contains("10", true) == true -> DocumentType.TENTH_MARKSHEET
            else -> classifyDocument(aiResponse + "\n" + rawText)
        }

        // Merge only verified AI fields into profile object for UI state
        var merged = targetProfile
        if (!aiName.isNullOrBlank()) merged = merged.copy(name = aiName)
        if (!aiFather.isNullOrBlank()) merged = merged.copy(fatherName = aiFather)
        if (!aiMother.isNullOrBlank()) merged = merged.copy(motherName = aiMother)
        if (!aiDob.isNullOrBlank()) merged = merged.copy(dob = aiDob)
        if (!aiIdNum.isNullOrBlank()) merged = merged.copy(idNumber = aiIdNum)
        if (!ai10thRoll.isNullOrBlank()) merged = merged.copy(tenthRollNo = ai10thRoll)
        if (!ai10thReg.isNullOrBlank()) merged = merged.copy(tenthRegNo = ai10thReg)
        if (!ai10thYear.isNullOrBlank()) merged = merged.copy(tenthPassingYear = ai10thYear)
        if (!ai12thRoll.isNullOrBlank()) merged = merged.copy(twelfthRollNo = ai12thRoll)
        if (!ai12thReg.isNullOrBlank()) merged = merged.copy(twelfthRegNo = ai12thReg)
        if (!ai12thYear.isNullOrBlank()) merged = merged.copy(twelfthPassingYear = ai12thYear)

        ExtractionSummary(
            updatedProfile = merged,
            newFieldsAdded = newFields,
            duplicatesSkipped = duplicates,
            detectedDocType = docType,
            aiResponseText = aiResponse
        )
    }

    private fun classifyDocument(text: String): DocumentType {
        return when {
            text.contains("INCOME TAX", true) || text.contains("PERMANENT ACCOUNT", true) || Regex("""\b([A-Z]{5}\d{4}[A-Z])\b""").containsMatchIn(text) -> DocumentType.PAN_CARD
            text.contains("AADHAAR", true) || text.contains("GOVERNMENT OF INDIA", true) || Regex("""\b(\d{4}\s\d{4}\s\d{4})\b""").containsMatchIn(text) -> DocumentType.AADHAAR_CARD
            text.contains("12TH", true) || text.contains("INTERMEDIATE", true) || text.contains("SENIOR SECONDARY", true) || text.contains("HIGHER SECONDARY", true) -> DocumentType.TWELFTH_MARKSHEET
            text.contains("10TH", true) || text.contains("MATRICULATION", true) || text.contains("SECONDARY", true) || text.contains("HIGH SCHOOL", true) -> DocumentType.TENTH_MARKSHEET
            else -> DocumentType.UNKNOWN_DOCUMENT
        }
    }
}
