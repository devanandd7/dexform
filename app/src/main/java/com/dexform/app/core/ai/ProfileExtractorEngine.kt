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
        val ocrResults = imageUris.take(3).map { uri ->
            try {
                OcrEngine.extractText(context, uri).fullText
            } catch (_: Exception) {
                ""
            }
        }

        val combinedText = ocrResults.joinToString("\n---\n")
        val newFields = mutableListOf<String>()
        val duplicates = mutableListOf<String>()

        var father = targetProfile.fatherName
        var mother = targetProfile.motherName
        val dob = targetProfile.dob
        var idNum = targetProfile.idNumber

        var tenthRoll = targetProfile.tenthRollNo
        var tenthReg = targetProfile.tenthRegNo
        var tenthYear = targetProfile.tenthPassingYear
        val tenthBoard = targetProfile.tenthBoard
        val tenthMarks = targetProfile.tenthMarks
        val tenthDiv = targetProfile.tenthDivision

        var twelfthRoll = targetProfile.twelfthRollNo
        var twelfthReg = targetProfile.twelfthRegNo
        var twelfthYear = targetProfile.twelfthPassingYear
        val twelfthBoard = targetProfile.twelfthBoard
        val twelfthMarks = targetProfile.twelfthMarks
        val twelfthDiv = targetProfile.twelfthDivision

        // Regex parsing helper
        val rollMatch = Regex("""(?i)\b(?:roll\s?no|roll)\D{0,5}(\d{5,12})\b""").find(combinedText)?.groupValues?.get(1)
        val regMatch = Regex("""(?i)\b(?:reg|registration)\D{0,5}([a-z0-9/-]{6,16})\b""").find(combinedText)?.groupValues?.get(1)
        val yearMatch = Regex("""\b(20[0-2][0-9]|19[8-9][0-9])\b""").find(combinedText)?.groupValues?.get(1)
        val fatherMatch = Regex("""(?i)\b(?:father|father's\s?name|s/o|d/o)\D{0,10}([a-z\s]{3,30})\b""").find(combinedText)?.groupValues?.get(1)?.trim()
        val motherMatch = Regex("""(?i)\b(?:mother|mother's\s?name)\D{0,10}([a-z\s]{3,30})\b""").find(combinedText)?.groupValues?.get(1)?.trim()
        val idMatch = Regex("""\b(\d{4}\s?\d{4}\s?\d{4}|[A-Z]{5}\d{4}[A-Z])\b""").find(combinedText)?.groupValues?.get(1)

        val is12th = combinedText.contains("12th", true) || combinedText.contains("intermediate", true) || combinedText.contains("senior secondary", true)

        // Duplicate Check & Merge Logic
        if (fatherMatch != null) {
            if (father.equals(fatherMatch, true)) {
                duplicates.add("Father's Name ($father)")
            } else if (father.isBlank()) {
                father = fatherMatch
                newFields.add("Father's Name: $father")
            }
        }

        if (motherMatch != null) {
            if (mother.equals(motherMatch, true)) {
                duplicates.add("Mother's Name ($mother)")
            } else if (mother.isBlank()) {
                mother = motherMatch
                newFields.add("Mother's Name: $mother")
            }
        }

        if (idMatch != null) {
            if (idNum.equals(idMatch, true)) {
                duplicates.add("ID Number ($idNum)")
            } else if (idNum.isBlank()) {
                idNum = idMatch
                newFields.add("ID Number: $idNum")
            }
        }

        if (is12th) {
            if (rollMatch != null) {
                if (twelfthRoll == rollMatch) duplicates.add("12th Roll No ($rollMatch)")
                else if (twelfthRoll.isBlank()) { twelfthRoll = rollMatch; newFields.add("12th Roll No: $rollMatch") }
            }
            if (regMatch != null) {
                if (twelfthReg == regMatch) duplicates.add("12th Reg No ($regMatch)")
                else if (twelfthReg.isBlank()) { twelfthReg = regMatch; newFields.add("12th Reg No: $regMatch") }
            }
            if (yearMatch != null) {
                if (twelfthYear == yearMatch) duplicates.add("12th Passing Year ($yearMatch)")
                else if (twelfthYear.isBlank()) { twelfthYear = yearMatch; newFields.add("12th Passing Year: $yearMatch") }
            }
        } else {
            if (rollMatch != null) {
                if (tenthRoll == rollMatch) duplicates.add("10th Roll No ($rollMatch)")
                else if (tenthRoll.isBlank()) { tenthRoll = rollMatch; newFields.add("10th Roll No: $rollMatch") }
            }
            if (regMatch != null) {
                if (tenthReg == regMatch) duplicates.add("10th Reg No ($regMatch)")
                else if (tenthReg.isBlank()) { tenthReg = regMatch; newFields.add("10th Reg No: $regMatch") }
            }
            if (yearMatch != null) {
                if (tenthYear == yearMatch) duplicates.add("10th Passing Year ($yearMatch)")
                else if (tenthYear.isBlank()) { tenthYear = yearMatch; newFields.add("10th Passing Year: $yearMatch") }
            }
        }

        val updated = targetProfile.copy(
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

        profileDao.updateProfile(updated)

        val prompt = "Synthesize extracted details for profile '${targetProfile.name}'. New fields: $newFields. Duplicates skipped: $duplicates. User Query: '$userQuery'"
        val aiSummary = localAiManager.generateResponse(prompt)

        ExtractionSummary(
            updatedProfile = updated,
            newFieldsAdded = newFields,
            duplicatesSkipped = duplicates,
            aiResponseText = aiSummary
        )
    }
}
