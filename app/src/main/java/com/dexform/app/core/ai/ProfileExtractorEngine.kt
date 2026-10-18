package com.dexform.app.core.ai

import android.content.Context
import android.net.Uri
import com.dexform.app.core.ocr.OcrEngine
import com.dexform.app.data.db.UserProfileDao
import com.dexform.app.data.db.UserProfileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ExtractionSummary(
    val updatedProfile: UserProfileEntity,
    val newFieldsAdded: List<String>,
    val duplicatesSkipped: List<String>,
    val aiResponseText: String
)

object ProfileExtractorEngine {

    suspend fun extractAndMergeDocuments(
        context: Context,
        imageUris: List<Uri>,
        targetProfile: UserProfileEntity,
        userQuery: String?,
        profileDao: UserProfileDao,
        localAiManager: LocalAiManager
    ): ExtractionSummary = withContext(Dispatchers.Default) {
        // ==========================================
        // STAGE 1: High-Precision OCR Extraction
        // ==========================================
        val ocrResults = imageUris.take(3).map { uri ->
            try {
                OcrEngine.extractText(context, uri).fullText
            } catch (_: Exception) {
                ""
            }
        }

        val rawText = ocrResults.joinToString("\n---\n")

        // Clean & normalize text lines
        val lines = rawText.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val newFields = mutableListOf<String>()
        val duplicates = mutableListOf<String>()

        var father = targetProfile.fatherName
        var mother = targetProfile.motherName
        var dob = targetProfile.dob
        var idNum = targetProfile.idNumber

        var tenthRoll = targetProfile.tenthRollNo
        var tenthReg = targetProfile.tenthRegNo
        var tenthYear = targetProfile.tenthPassingYear
        var tenthBoard = targetProfile.tenthBoard
        var tenthMarks = targetProfile.tenthMarks
        var tenthDiv = targetProfile.tenthDivision

        var twelfthRoll = targetProfile.twelfthRollNo
        var twelfthReg = targetProfile.twelfthRegNo
        var twelfthYear = targetProfile.twelfthPassingYear
        var twelfthBoard = targetProfile.twelfthBoard
        var twelfthMarks = targetProfile.twelfthMarks
        var twelfthDiv = targetProfile.twelfthDivision

        // ==========================================
        // STAGE 2: Deep Contextual Pattern Extraction
        // ==========================================
        val is12thDoc = rawText.contains("12th", true) ||
                rawText.contains("intermediate", true) ||
                rawText.contains("senior secondary", true) ||
                rawText.contains("higher secondary", true)

        // Regex candidate matchers
        val rollCandidate = findByRegex(lines, listOf(
            """(?i)\b(?:roll\s?code\s?/\s?no|roll\s?no|roll\s?code|roll|kramank)\D{0,6}([0-9A-Z/-]{5,15})\b""",
            """\b(\d{7,12})\b"""
        ))

        val regCandidate = findByRegex(lines, listOf(
            """(?i)\b(?:reg|registration|enrollment)\D{0,6}([0-9A-Z/-]{6,20})\b"""
        ))

        val yearCandidate = findYear(lines)

        val fatherCandidate = findPersonName(lines, listOf("father", "pita", "s/o", "d/o", "father's name"))
        val motherCandidate = findPersonName(lines, listOf("mother", "mata", "mother's name"))
        val dobCandidate = findDob(lines)

        val boardCandidate = findBoardName(rawText)
        val divCandidate = findDivision(rawText)
        val marksCandidate = findMarks(lines)
        val idCandidate = findIdNumber(rawText)

        // ==========================================
        // STAGE 3: Logical Reasoning & Duplicate Check
        // ==========================================

        // Father's Name Reasoning
        if (!fatherCandidate.isNullOrBlank()) {
            if (father.equals(fatherCandidate, ignoreCase = true)) {
                duplicates.add("Father's Name ($father)")
            } else if (father.isBlank()) {
                father = fatherCandidate
                newFields.add("Father's Name: $father")
            }
        }

        // Mother's Name Reasoning
        if (!motherCandidate.isNullOrBlank()) {
            if (mother.equals(motherCandidate, ignoreCase = true)) {
                duplicates.add("Mother's Name ($mother)")
            } else if (mother.isBlank()) {
                mother = motherCandidate
                newFields.add("Mother's Name: $mother")
            }
        }

        // DOB Reasoning
        if (!dobCandidate.isNullOrBlank()) {
            if (dob.equals(dobCandidate, ignoreCase = true)) {
                duplicates.add("DOB ($dob)")
            } else if (dob.isBlank()) {
                dob = dobCandidate
                newFields.add("DOB: $dob")
            }
        }

        // ID Number Reasoning
        if (!idCandidate.isNullOrBlank()) {
            if (idNum.equals(idCandidate, ignoreCase = true)) {
                duplicates.add("ID Number ($idNum)")
            } else if (idNum.isBlank()) {
                idNum = idCandidate
                newFields.add("ID Number: $idNum")
            }
        }

        // Educational Academic Data Reasoning (10th vs 12th)
        if (is12thDoc) {
            if (!rollCandidate.isNullOrBlank()) {
                if (twelfthRoll == rollCandidate) duplicates.add("12th Roll No ($rollCandidate)")
                else if (twelfthRoll.isBlank()) { twelfthRoll = rollCandidate; newFields.add("12th Roll No: $rollCandidate") }
            }
            if (!regCandidate.isNullOrBlank()) {
                if (twelfthReg == regCandidate) duplicates.add("12th Reg No ($regCandidate)")
                else if (twelfthReg.isBlank()) { twelfthReg = regCandidate; newFields.add("12th Reg No: $regCandidate") }
            }
            if (!yearCandidate.isNullOrBlank()) {
                if (twelfthYear == yearCandidate) duplicates.add("12th Passing Year ($yearCandidate)")
                else if (twelfthYear.isBlank()) { twelfthYear = yearCandidate; newFields.add("12th Passing Year: $yearCandidate") }
            }
            if (!boardCandidate.isNullOrBlank() && twelfthBoard.isBlank()) {
                twelfthBoard = boardCandidate
                newFields.add("12th Board: $boardCandidate")
            }
            if (!divCandidate.isNullOrBlank() && twelfthDiv.isBlank()) {
                twelfthDiv = divCandidate
                newFields.add("12th Division: $divCandidate")
            }
            if (!marksCandidate.isNullOrBlank() && twelfthMarks.isBlank()) {
                twelfthMarks = marksCandidate
                newFields.add("12th Marks: $marksCandidate")
            }
        } else {
            if (!rollCandidate.isNullOrBlank()) {
                if (tenthRoll == rollCandidate) duplicates.add("10th Roll No ($rollCandidate)")
                else if (tenthRoll.isBlank()) { tenthRoll = rollCandidate; newFields.add("10th Roll No: $rollCandidate") }
            }
            if (!regCandidate.isNullOrBlank()) {
                if (tenthReg == regCandidate) duplicates.add("10th Reg No ($regCandidate)")
                else if (tenthReg.isBlank()) { tenthReg = regCandidate; newFields.add("10th Reg No: $rollCandidate") }
            }
            if (!yearCandidate.isNullOrBlank()) {
                if (tenthYear == yearCandidate) duplicates.add("10th Passing Year ($yearCandidate)")
                else if (tenthYear.isBlank()) { tenthYear = yearCandidate; newFields.add("10th Passing Year: $yearCandidate") }
            }
            if (!boardCandidate.isNullOrBlank() && tenthBoard.isBlank()) {
                tenthBoard = boardCandidate
                newFields.add("10th Board: $boardCandidate")
            }
            if (!divCandidate.isNullOrBlank() && tenthDiv.isBlank()) {
                tenthDiv = divCandidate
                newFields.add("10th Division: $divCandidate")
            }
            if (!marksCandidate.isNullOrBlank() && tenthMarks.isBlank()) {
                tenthMarks = marksCandidate
                newFields.add("10th Marks: $marksCandidate")
            }
        }

        val updatedProfile = targetProfile.copy(
            fatherName = father,
            motherName = mother,
            dob = dob,
            idNumber = idNum,
            tenthRollNo = tenthRoll,
            tenthRegNo = tenthReg,
            tenthPassingYear = tenthYear,
            tenthBoard = tenthBoard,
            tenthMarks = tenthMarks,
            tenthDivision = tenthDiv,
            twelfthRollNo = twelfthRoll,
            twelfthRegNo = twelfthReg,
            twelfthPassingYear = twelfthYear,
            twelfthBoard = twelfthBoard,
            twelfthMarks = twelfthMarks,
            twelfthDivision = twelfthDiv
        )

        profileDao.updateProfile(updatedProfile)

        // Stage 3 Cross-Verification Prompt
        val reasoningPrompt = """
            Perform 3-Stage Logical Reasoning on Extracted Bio-Data for Profile '${targetProfile.name}':
            Raw Document Text: "$rawText"
            Identified Document Type: ${if (is12thDoc) "12th / Intermediate" else "10th / Matriculation"}
            New Verified Fields: $newFields
            Duplicate Skipped Fields: $duplicates
            User Prompt: "$userQuery"
            Provide a clean, verified reasoning breakdown of extracted data.
        """.trimIndent()

        val aiSummary = localAiManager.generateResponse(reasoningPrompt)

        ExtractionSummary(
            updatedProfile = updatedProfile,
            newFieldsAdded = newFields,
            duplicatesSkipped = duplicates,
            aiResponseText = aiSummary
        )
    }

    private fun findByRegex(lines: List<String>, patterns: List<String>): String? {
        for (pattern in patterns) {
            val regex = Regex(pattern)
            for (line in lines) {
                val match = regex.find(line)
                if (match != null && match.groupValues.size > 1) {
                    val candidate = match.groupValues[1].trim()
                    if (candidate.length >= 3) return candidate
                }
            }
        }
        return null
    }

    private fun findYear(lines: List<String>): String? {
        val yearRegex = Regex("""\b(19[8-9][0-9]|20[0-2][0-9])\b""")
        for (line in lines) {
            if (line.contains("year", true) || line.contains("pass", true) || line.contains("session", true) || line.contains("exam", true)) {
                val match = yearRegex.find(line)
                if (match != null) return match.value
            }
        }
        // Fallback search across all lines
        for (line in lines) {
            val match = yearRegex.find(line)
            if (match != null) return match.value
        }
        return null
    }

    private fun findPersonName(lines: List<String>, keywords: List<String>): String? {
        for (i in lines.indices) {
            val line = lines[i]
            for (kw in keywords) {
                if (line.contains(kw, ignoreCase = true)) {
                    val cleaned = line
                        .replace(Regex("""(?i)\b(?:father|mother|pita|mata|father's|mother's|name|s/o|d/o|w/o|mr|mrs|shri|smt)\b[:.-]?"""), "")
                        .trim()
                    if (cleaned.length in 3..35 && !cleaned.contains("board", true) && !cleaned.contains("roll", true)) {
                        return cleaned
                    }
                    if (i + 1 < lines.size) {
                        val nextLine = lines[i + 1].trim()
                        if (nextLine.length in 3..35 && !nextLine.contains("roll", true) && !nextLine.contains("code", true)) {
                            return nextLine
                        }
                    }
                }
            }
        }
        return null
    }

    private fun findDob(lines: List<String>): String? {
        val dobRegex = Regex("""\b(\d{1,2}[/-]\d{1,2}[/-]\d{2,4})\b""")
        for (line in lines) {
            if (line.contains("dob", true) || line.contains("birth", true) || line.contains("date of birth", true)) {
                val match = dobRegex.find(line)
                if (match != null) return match.value
            }
        }
        return null
    }

    private fun findBoardName(text: String): String? {
        return when {
            text.contains("CBSE", true) || text.contains("CENTRAL BOARD", true) -> "CBSE"
            text.contains("BSEB", true) || text.contains("BIHAR SCHOOL EXAMINATION", true) -> "BSEB"
            text.contains("ICSE", true) || text.contains("CISCE", true) -> "ICSE"
            text.contains("UP BOARD", true) -> "UP Board"
            text.contains("MP BOARD", true) -> "MP Board"
            else -> null
        }
    }

    private fun findDivision(text: String): String? {
        return when {
            text.contains("FIRST", true) || text.contains("1ST DIV", true) || text.contains("DISTINCTION", true) -> "1st Division"
            text.contains("SECOND", true) || text.contains("2ND DIV", true) -> "2nd Division"
            text.contains("THIRD", true) || text.contains("3RD DIV", true) -> "3rd Division"
            text.contains("PASS", true) -> "Passed"
            else -> null
        }
    }

    private fun findMarks(lines: List<String>): String? {
        val marksRegex = Regex("""(?i)\b(?:marks|total|aggregate)\D{0,6}(\d{3}\s?/\s?\d{3}|\d{2,3}%)\b""")
        for (line in lines) {
            val match = marksRegex.find(line)
            if (match != null && match.groupValues.size > 1) {
                return match.groupValues[1]
            }
        }
        return null
    }

    private fun findIdNumber(text: String): String? {
        val aadhaarMatch = Regex("""\b(\d{4}\s\d{4}\s\d{4})\b""").find(text)?.value
        if (aadhaarMatch != null) return aadhaarMatch
        val panMatch = Regex("""\b([A-Z]{5}\d{4}[A-Z])\b""").find(text)?.value
        if (panMatch != null) return panMatch
        return null
    }
}
