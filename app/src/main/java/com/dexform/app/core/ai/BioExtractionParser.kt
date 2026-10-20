package com.dexform.app.core.ai

import com.dexform.app.data.db.Tier
import com.dexform.app.data.db.UserProfileEntity
import org.json.JSONObject
import java.util.Locale

object BioExtractionParser {

    private val blockedKeys = setOf(
        "note", "notes", "summary", "disclaimer", "description",
        "details", "step", "stage", "important", "http", "https",
        "image", "photo", "document", "document type", "doc type"
    )

    fun parse(
        aiText: String,
        currentProfile: UserProfileEntity?,
        imagePath: String? = null
    ): DocumentProposal? {
        val trimmed = aiText.trim()
        if (trimmed.isBlank()) return null

        var docType: String? = null
        val rawFields = mutableListOf<ParsedField>()

        // 1. Try extracting JSON if present
        val jsonMatch = Regex("""(?s)```(?:json)?\s*(\{.*?\})\s*```""").find(trimmed)
            ?: Regex("""(?s)(\{\s*"[a-zA-Z0-9_]+"\s*:.*?\})""").find(trimmed)

        if (jsonMatch != null) {
            try {
                val json = JSONObject(jsonMatch.groupValues[1])
                docType = json.optString("document_type").ifBlank {
                    json.optString("doc_type").ifBlank {
                        json.optString("document").ifBlank { null }
                    }
                }

                // Check nested primary_bio / sub_bio or flat keys
                val primaryObj = json.optJSONObject("primary_bio") ?: json.optJSONObject("bio")
                if (primaryObj != null) {
                    val keys = primaryObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val v = primaryObj.optString(k).trim()
                        if (v.isNotBlank() && !isIgnoredValue(v)) {
                            rawFields.add(ParsedField(k, v, explicitTier = Tier.BIO))
                        }
                    }
                }

                val subObj = json.optJSONObject("sub_bio") ?: json.optJSONObject("document_details")
                if (subObj != null) {
                    val keys = subObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val v = subObj.optString(k).trim()
                        if (v.isNotBlank() && !isIgnoredValue(v)) {
                            rawFields.add(ParsedField(k, v, explicitTier = Tier.SUB))
                        }
                    }
                }

                // Flat keys
                val flatKeys = json.keys()
                while (flatKeys.hasNext()) {
                    val k = flatKeys.next()
                    if (k.equals("document_type", true) || k.equals("doc_type", true) ||
                        k.equals("primary_bio", true) || k.equals("sub_bio", true) ||
                        k.equals("bio", true) || k.equals("document_details", true)) {
                        continue
                    }
                    val v = json.optString(k).trim()
                    if (v.isNotBlank() && !isIgnoredValue(v) && !rawFields.any { it.label.equals(k, true) }) {
                        rawFields.add(ParsedField(k, v, explicitTier = null))
                    }
                }
            } catch (_: Exception) {}
        }

        // 2. Line-by-line Markdown parsing
        var currentSectionTier: Tier? = null
        val lines = trimmed.lines()

        for (line in lines) {
            val cleanLine = line.trim()
            if (cleanLine.isBlank()) continue

            val lowerLine = cleanLine.lowercase()

            // Detect section headers
            if (lowerLine.contains("primary bio") || lowerLine.contains("personal details") || lowerLine.contains("identity details")) {
                currentSectionTier = Tier.BIO
                continue
            }
            if (lowerLine.contains("sub-bio") || lowerLine.contains("sub bio") || lowerLine.contains("document details") || lowerLine.contains("exam details") || lowerLine.contains("certificate details")) {
                currentSectionTier = Tier.SUB
                continue
            }

            // Detect Document Type / Title
            val docMatch = Regex("""^(?:[*-]?\s*)?(?:\*\*)?(?:Document(?:\s*Type)?|Title)(?:\*\*)?\s*[:=]\s*(?:\*\*)?([^\n\r]+?)(?:\*\*)?$""", RegexOption.IGNORE_CASE).find(cleanLine)
            if (docMatch != null && docType == null) {
                docType = cleanLabel(docMatch.groupValues[1])
                continue
            }

            // Key-Value matching
            val kvMatch = Regex("""^[*-]?\s*(?:\*\*)?([A-Za-z0-9\s/'’\(\)-]{2,40}?)(?:\*\*)?\s*[:=]\s*(?:\*\*)?([^\n\r]+?)(?:\*\*)?$""").find(cleanLine)
            if (kvMatch != null) {
                val rawKey = kvMatch.groupValues[1].trim()
                val rawVal = kvMatch.groupValues[2].trim().trim('*', '_', '"', '\'')

                val normKey = rawKey.lowercase().replace(Regex("""[^a-z0-9]"""), "")
                if (blockedKeys.contains(normKey) || isIgnoredValue(rawVal)) {
                    continue
                }

                if (!rawFields.any { it.label.equals(rawKey, ignoreCase = true) }) {
                    rawFields.add(ParsedField(rawKey, rawVal, explicitTier = currentSectionTier))
                }
            }
        }

        if (rawFields.isEmpty()) return null

        val detectedDocType = docType?.ifBlank { null } ?: inferDocType(trimmed, rawFields)
        val docTitle = formatDocTitle(detectedDocType)
        val isPermanent = isPermanentPersonalDoc(detectedDocType)

        // 3. Mathematical Reasoning & Auto-Calculations for Academic Marksheets
        performAcademicReasoning(detectedDocType, rawFields, trimmed)

        // 4. Convert to ProposedBioField with contextual label normalization and smart tiering
        val proposedFields = rawFields.map { parsed ->
            val cleanLabel = normalizeFieldLabel(parsed.label, detectedDocType)
            val tier = if (isPermanent) {
                // All fields of a permanent personal document (10th, 12th, Degree, PAN, Aadhaar) belong to Primary BIO
                Tier.BIO
            } else {
                // For temporary docs (Admit card, exam slip), determine based on field identity
                parsed.explicitTier ?: inferTemporaryDocTier(cleanLabel)
            }

            val coreMatch = mapToCoreProfile(cleanLabel, detectedDocType)

            val isDuplicate = if (coreMatch != null && currentProfile != null) {
                val existingVal = getCoreValue(currentProfile, coreMatch)
                existingVal.isNotBlank() && existingVal.equals(parsed.value, ignoreCase = true)
            } else false

            val slugKey = if (tier == Tier.BIO) {
                "bio." + slug(cleanLabel)
            } else {
                "${slug(detectedDocType)}.${slug(cleanLabel)}"
            }

            ProposedBioField(
                key = slugKey,
                label = cleanLabel,
                value = parsed.value,
                tier = tier,
                isCoreProfileField = coreMatch != null,
                coreFieldName = coreMatch,
                isSelected = !isDuplicate,
                isDuplicate = isDuplicate
            )
        }

        return DocumentProposal(
            docType = detectedDocType,
            docTitle = docTitle,
            fields = proposedFields,
            imagePath = imagePath,
            isSaved = false
        )
    }

    private data class ParsedField(
        var label: String,
        var value: String,
        val explicitTier: Tier?
    )

    private fun isIgnoredValue(v: String): Boolean {
        val lower = v.lowercase().trim()
        return lower.isBlank() || lower == "n/a" || lower == "null" || lower == "none" ||
                lower == "not mentioned" || lower == "not applicable" || lower == "unknown" ||
                lower.startsWith("http://") || lower.startsWith("https://")
    }

    private fun cleanLabel(label: String): String {
        return label.trim()
            .replace(Regex("""^[-*#\s]+"""), "")
            .replace(Regex("""[:=]+$"""), "")
            .trim()
            .split(" ")
            .joinToString(" ") { word ->
                word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
    }

    private fun inferDocType(text: String, fields: List<ParsedField>): String {
        val combined = (text + " " + fields.joinToString(" ") { "${it.label} ${it.value}" }).uppercase()
        return when {
            combined.contains("ADMIT CARD") || combined.contains("HALL TICKET") || combined.contains("CALL LETTER") -> "Admit Card"
            combined.contains("INCOME TAX") || combined.contains("PERMANENT ACCOUNT") || combined.contains("PAN CARD") -> "PAN Card"
            combined.contains("AADHAAR") || combined.contains("UIDAI") -> "Aadhaar Card"
            combined.contains("DRIVING LICENCE") || combined.contains("DRIVING LICENSE") -> "Driving License"
            combined.contains("VOTER") || combined.contains("ELECTION") || combined.contains("EPIC") -> "Voter ID Card"
            combined.contains("PASSPORT") -> "Passport"
            combined.contains("10TH") || combined.contains("MATRICULATION") || combined.contains("SECONDARY SCHOOL") || combined.contains("HIGH SCHOOL") -> "10th Marksheet"
            combined.contains("12TH") || combined.contains("INTERMEDIATE") || combined.contains("HIGHER SECONDARY") || combined.contains("SENIOR SECONDARY") -> "12th Marksheet"
            combined.contains("GRADUATION") || combined.contains("BACHELOR") || combined.contains("B.TECH") || combined.contains("B.SC") || combined.contains("B.COM") || combined.contains("B.A") -> "Degree Certificate"
            combined.contains("MASTER") || combined.contains("POST GRADUATION") || combined.contains("M.TECH") || combined.contains("M.SC") || combined.contains("M.COM") || combined.contains("M.A") -> "Master's Degree"
            combined.contains("DIPLOMA") || combined.contains("POLYTECHNIC") -> "Diploma Certificate"
            combined.contains("EXPERIENCE") || combined.contains("EMPLOYMENT") || combined.contains("RELIEVING") -> "Experience Certificate"
            combined.contains("DOMICILE") || combined.contains("RESIDENTIAL") -> "Domicile Certificate"
            combined.contains("CASTE") || combined.contains("COMMUNITY") -> "Caste Certificate"
            else -> "Document"
        }
    }

    /**
     * Permanent personal documents (Marksheets, Degrees, National IDs, Experience) are always Primary BIO.
     * Temporary documents (Admit Cards, Hall Tickets, Exam Slips) are Sub-Bio.
     */
    fun isPermanentPersonalDoc(docType: String): Boolean {
        val upper = docType.uppercase()
        if (upper.contains("ADMIT") || upper.contains("HALL TICKET") || upper.contains("CALL LETTER") || upper.contains("EXAM SLIP")) {
            return false
        }
        return true
    }

    /**
     * Normalizes generic labels into rich, context-aware labels.
     * e.g. "Number" on PAN Card -> "PAN Card Number"
     *      "Roll No" on 10th Marksheet -> "10th Roll Number"
     */
    fun normalizeFieldLabel(label: String, docType: String): String {
        val clean = cleanLabel(label)
        val norm = clean.lowercase().replace(Regex("""[^a-z0-9]"""), "")
        val docUpper = docType.uppercase()

        return when {
            docUpper.contains("PAN") -> {
                if (norm == "number" || norm == "no" || norm == "panno" || norm == "id" || norm == "pannumber") {
                    "PAN Card Number"
                } else clean
            }
            docUpper.contains("AADHAAR") -> {
                if (norm == "number" || norm == "no" || norm == "uid" || norm == "vid" || norm == "aadhaarno") {
                    "Aadhaar Number"
                } else clean
            }
            docUpper.contains("VOTER") -> {
                if (norm == "number" || norm == "no" || norm == "epic" || norm == "epicno" || norm == "voterid") {
                    "Voter ID (EPIC) Number"
                } else clean
            }
            docUpper.contains("DRIVING") || docUpper.contains("LICENSE") -> {
                if (norm == "number" || norm == "no" || norm == "dlno" || norm == "licenseno") {
                    "Driving License Number"
                } else clean
            }
            docUpper.contains("10TH") || docUpper.contains("MATRIC") -> {
                when {
                    norm.contains("roll") -> "10th Roll Number"
                    norm.contains("reg") || norm.contains("enroll") -> "10th Registration Number"
                    norm.contains("year") || norm.contains("passing") -> "10th Passing Year"
                    norm.contains("board") || norm.contains("council") -> "10th Board"
                    norm.contains("percent") -> "10th Percentage"
                    norm.contains("division") -> "10th Division"
                    norm.contains("marks") || norm.contains("score") || norm.contains("total") -> "10th Total Marks"
                    else -> clean
                }
            }
            docUpper.contains("12TH") || docUpper.contains("INTER") -> {
                when {
                    norm.contains("roll") -> "12th Roll Number"
                    norm.contains("reg") || norm.contains("enroll") -> "12th Registration Number"
                    norm.contains("year") || norm.contains("passing") -> "12th Passing Year"
                    norm.contains("board") || norm.contains("council") -> "12th Board"
                    norm.contains("percent") -> "12th Percentage"
                    norm.contains("division") -> "12th Division"
                    norm.contains("marks") || norm.contains("score") || norm.contains("total") -> "12th Total Marks"
                    else -> clean
                }
            }
            else -> clean
        }
    }

    /**
     * Performs math reasoning to compute Percentage and Division from Marks if not already present.
     */
    private fun performAcademicReasoning(docType: String, fields: MutableList<ParsedField>, fullText: String) {
        val docUpper = docType.uppercase()
        val isAcademic = docUpper.contains("10TH") || docUpper.contains("12TH") ||
                docUpper.contains("MATRIC") || docUpper.contains("INTER") ||
                docUpper.contains("MARKSHEET") || docUpper.contains("DEGREE")

        if (!isAcademic) return

        val prefix = if (docUpper.contains("12TH") || docUpper.contains("INTER")) "12th" else "10th"

        var hasPercentage = fields.any { it.label.lowercase().contains("percent") }
        var hasDivision = fields.any { it.label.lowercase().contains("division") }

        // Look for marks patterns: e.g. "425/500", "425 out of 500", or separate Total Marks and Maximum Marks
        var obtainedMarks: Double? = null
        var maxMarks: Double? = null

        // 1. Try finding fraction pattern in text (e.g. 425/500 or 425 / 500)
        val fractionMatch = Regex("""(\d{2,4})\s*(?:/|out of)\s*(\d{2,4})""", RegexOption.IGNORE_CASE).find(fullText)
        if (fractionMatch != null) {
            val num1 = fractionMatch.groupValues[1].toDoubleOrNull()
            val num2 = fractionMatch.groupValues[2].toDoubleOrNull()
            if (num1 != null && num2 != null && num2 > 0 && num1 <= num2) {
                obtainedMarks = num1
                maxMarks = num2
            }
        }

        // 2. Check extracted fields if not found via fraction
        if (obtainedMarks == null) {
            val obtainedField = fields.firstOrNull {
                val l = it.label.lowercase()
                (l.contains("obtained") || l.contains("total marks") || l.contains("marks secured")) && !l.contains("max")
            }
            val maxField = fields.firstOrNull {
                val l = it.label.lowercase()
                l.contains("max") || l.contains("full marks") || l.contains("total marks")
            }

            val num1 = obtainedField?.value?.replace(Regex("""[^0-9.]"""), "")?.toDoubleOrNull()
            val num2 = maxField?.value?.replace(Regex("""[^0-9.]"""), "")?.toDoubleOrNull()
            if (num1 != null && num2 != null && num2 > num1) {
                obtainedMarks = num1
                maxMarks = num2
            } else if (num1 != null && num1 in 200.0..600.0) {
                obtainedMarks = num1
                maxMarks = if (num1 <= 500.0) 500.0 else 600.0 // Standard Indian board total
            }
        }

        // 3. Calculate Percentage if missing
        var computedPct: Double? = null
        if (!hasPercentage && obtainedMarks != null && maxMarks != null && maxMarks > 0) {
            computedPct = (obtainedMarks / maxMarks) * 100.0
            val pctString = String.format(Locale.US, "%.2f%%", computedPct)
            fields.add(ParsedField("$prefix Percentage", pctString, explicitTier = Tier.BIO))
            hasPercentage = true
        } else if (hasPercentage) {
            val pctField = fields.firstOrNull { it.label.lowercase().contains("percent") }
            val rawNum = pctField?.value?.replace(Regex("""[^0-9.]"""), "")?.toDoubleOrNull()
            if (rawNum != null) computedPct = rawNum
        }

        // 4. Derive Division if missing and percentage is available
        if (!hasDivision && computedPct != null && computedPct in 1.0..100.0) {
            val divisionString = when {
                computedPct >= 60.0 -> "1st Division"
                computedPct >= 45.0 -> "2nd Division"
                computedPct >= 33.0 -> "3rd Division"
                else -> "Passed"
            }
            fields.add(ParsedField("$prefix Division", divisionString, explicitTier = Tier.BIO))
        }
    }

    private fun inferTemporaryDocTier(label: String): Tier {
        val norm = label.lowercase().replace(Regex("""[^a-z0-9]"""), "")
        // Identity details in temporary docs still map to personal bio
        if (norm.contains("name") || norm.contains("dob") || norm.contains("father") ||
            norm.contains("mother") || norm.contains("gender") || norm.contains("category") ||
            norm.contains("mobile") || norm.contains("email") || norm.contains("address")) {
            return Tier.BIO
        }
        return Tier.SUB
    }

    private fun mapToCoreProfile(label: String, docType: String): String? {
        val norm = label.lowercase().replace(Regex("""[^a-z0-9]"""), "")
        val docUpper = docType.uppercase()

        // 1. Direct personal matches
        if (norm == "name" || norm == "fullname" || norm == "candidatename" || norm == "applicantname") return "name"
        if (norm.contains("father")) return "fatherName"
        if (norm.contains("mother")) return "motherName"
        if (norm == "dob" || norm.contains("dateofbirth") || norm.contains("birthdate")) return "dob"
        if (norm == "gender" || norm == "sex") return "gender"
        if (norm == "category" || norm == "caste") return "category"
        if (norm == "mobile" || norm == "phone" || norm == "contact" || norm == "mobileno") return "mobile"
        if (norm == "email" || norm.contains("emailaddress")) return "email"
        if (norm == "address" || norm.contains("permanentaddress")) return "address"
        if (norm.contains("idnumber") || norm.contains("pannumber") || norm.contains("aadhaarnumber")) return "idNumber"

        // 2. 10th Academic
        if (norm.contains("10th") || docUpper.contains("10TH") || docUpper.contains("MATRIC")) {
            return when {
                norm.contains("roll") -> "tenthRollNo"
                norm.contains("reg") || norm.contains("enroll") -> "tenthRegNo"
                norm.contains("year") || norm.contains("passing") -> "tenthPassingYear"
                norm.contains("board") || norm.contains("council") -> "tenthBoard"
                norm.contains("marks") || norm.contains("score") || norm.contains("total") -> "tenthMarks"
                norm.contains("percent") -> "tenthMarks" // stored in tenthMarks if desired or dynamic bio
                norm.contains("division") -> "tenthDivision"
                else -> null
            }
        }

        // 3. 12th Academic
        if (norm.contains("12th") || docUpper.contains("12TH") || docUpper.contains("INTER")) {
            return when {
                norm.contains("roll") -> "twelfthRollNo"
                norm.contains("reg") || norm.contains("enroll") -> "twelfthRegNo"
                norm.contains("year") || norm.contains("passing") -> "twelfthPassingYear"
                norm.contains("board") || norm.contains("council") -> "twelfthBoard"
                norm.contains("marks") || norm.contains("score") || norm.contains("total") -> "twelfthMarks"
                norm.contains("percent") -> "twelfthMarks"
                norm.contains("division") -> "twelfthDivision"
                else -> null
            }
        }

        return null
    }

    private fun getCoreValue(profile: UserProfileEntity, fieldName: String): String {
        return when (fieldName) {
            "name" -> profile.name
            "fatherName" -> profile.fatherName
            "motherName" -> profile.motherName
            "dob" -> profile.dob
            "gender" -> profile.gender
            "category" -> profile.category
            "mobile" -> profile.mobile
            "email" -> profile.email
            "address" -> profile.address
            "idNumber" -> profile.idNumber
            "tenthRollNo" -> profile.tenthRollNo
            "tenthRegNo" -> profile.tenthRegNo
            "tenthPassingYear" -> profile.tenthPassingYear
            "tenthBoard" -> profile.tenthBoard
            "tenthMarks" -> profile.tenthMarks
            "tenthDivision" -> profile.tenthDivision
            "twelfthRollNo" -> profile.twelfthRollNo
            "twelfthRegNo" -> profile.twelfthRegNo
            "twelfthPassingYear" -> profile.twelfthPassingYear
            "twelfthBoard" -> profile.twelfthBoard
            "twelfthMarks" -> profile.twelfthMarks
            "twelfthDivision" -> profile.twelfthDivision
            else -> ""
        }
    }

    private fun formatDocTitle(docType: String): String {
        return docType.trim()
    }

    private fun slug(text: String): String {
        return text.lowercase()
            .replace(Regex("""[^a-z0-9]"""), "_")
            .trim('_')
            .ifBlank { "field" }
    }
}
