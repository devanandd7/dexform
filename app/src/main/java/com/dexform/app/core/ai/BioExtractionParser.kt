package com.dexform.app.core.ai

import com.dexform.app.data.db.Tier
import com.dexform.app.data.db.UserProfileEntity
import org.json.JSONObject

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

        // Convert to ProposedBioField objects
        val proposedFields = rawFields.map { parsed ->
            val cleanLabel = cleanLabel(parsed.label)
            val tier = parsed.explicitTier ?: inferTier(cleanLabel, parsed.value)
            val coreMatch = mapToCoreProfile(cleanLabel)

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
        val label: String,
        val value: String,
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
            combined.contains("EXPERIENCE") || combined.contains("EMPLOYMENT") || combined.contains("RELIEVING") -> "Experience Certificate"
            combined.contains("INCOME TAX") || combined.contains("PERMANENT ACCOUNT") || combined.contains("PAN CARD") -> "PAN Card"
            combined.contains("AADHAAR") || combined.contains("UIDAI") -> "Aadhaar Card"
            combined.contains("DRIVING LICENCE") || combined.contains("DRIVING LICENSE") -> "Driving License"
            combined.contains("VOTER") || combined.contains("ELECTION") || combined.contains("EPIC") -> "Voter ID Card"
            combined.contains("PASSPORT") -> "Passport"
            combined.contains("ADMIT CARD") || combined.contains("HALL TICKET") -> "Admit Card"
            combined.contains("12TH") || combined.contains("INTERMEDIATE") || combined.contains("HIGHER SECONDARY") -> "12th Marksheet"
            combined.contains("10TH") || combined.contains("MATRICULATION") || combined.contains("SECONDARY SCHOOL") -> "10th Marksheet"
            combined.contains("DEGREE") || combined.contains("GRADUATION") || combined.contains("CONVOCATION") -> "Degree Certificate"
            combined.contains("BONAFIDE") -> "Bonafide Certificate"
            combined.contains("DOMICILE") || combined.contains("RESIDENTIAL") -> "Domicile Certificate"
            combined.contains("CASTE") || combined.contains("COMMUNITY") -> "Caste Certificate"
            else -> "Document"
        }
    }

    private fun formatDocTitle(docType: String): String {
        return docType.trim()
    }

    /**
     * Identifies if a field is Primary Bio (Permanent/Personal/Career) vs Sub-Bio (Document Instance)
     */
    fun inferTier(label: String, value: String): Tier {
        val norm = label.lowercase().replace(Regex("""[^a-z0-9]"""), "")

        // Sub-Bio indicators: Document / Exam instance-specific
        if (norm.contains("certificateno") || norm.contains("certificatenumber") || norm.contains("certno") ||
            norm.contains("serialno") || norm.contains("serialnumber") || norm.contains("referenceno") ||
            norm.contains("issuedate") || norm.contains("dateofissue") || norm.contains("issuedon") ||
            norm.contains("expirydate") || norm.contains("validtill") || norm.contains("validupto") ||
            norm.contains("examdate") || norm.contains("examcenter") || norm.contains("venue") ||
            norm.contains("rollno") || norm.contains("hallticket") || norm.contains("seatno") ||
            norm.contains("applicationno") || norm.contains("registrationno") || norm.contains("regno") ||
            norm.contains("enrollmentno") || norm.contains("marks") || norm.contains("score") ||
            norm.contains("grade") || norm.contains("cgpa") || norm.contains("percentage") ||
            norm.contains("session") || norm.contains("shift") || norm.contains("reportingtime")) {
            return Tier.SUB
        }

        // Primary Bio indicators: Identity, Person, Career Profile
        if (norm.contains("name") || norm.contains("dob") || norm.contains("birth") || norm.contains("age") ||
            norm.contains("father") || norm.contains("mother") || norm.contains("gender") || norm.contains("sex") ||
            norm.contains("category") || norm.contains("caste") || norm.contains("religion") || norm.contains("nationality") ||
            norm.contains("mobile") || norm.contains("phone") || norm.contains("contact") || norm.contains("email") ||
            norm.contains("address") || norm.contains("pincode") || norm.contains("city") || norm.contains("state") ||
            norm.contains("pan") || norm.contains("aadhaar") || norm.contains("voter") || norm.contains("passport") ||
            norm.contains("blood") || norm.contains("marital") ||
            // Professional / Career Bio details:
            norm.contains("company") || norm.contains("employer") || norm.contains("organization") ||
            norm.contains("industry") || norm.contains("experience") || norm.contains("designation") ||
            norm.contains("role") || norm.contains("position") || norm.contains("jobtitle") ||
            norm.contains("skill") || norm.contains("qualification") || norm.contains("degree") ||
            norm.contains("department") || norm.contains("domain")) {
            return Tier.BIO
        }

        return Tier.BIO
    }

    private fun mapToCoreProfile(label: String): String? {
        val norm = label.lowercase().replace(Regex("""[^a-z0-9]"""), "")
        return when {
            norm == "name" || norm == "fullname" || norm == "candidatename" -> "name"
            norm.contains("father") -> "fatherName"
            norm.contains("mother") -> "motherName"
            norm == "dob" || norm.contains("dateofbirth") || norm.contains("birthdate") -> "dob"
            norm == "gender" || norm == "sex" -> "gender"
            norm == "category" || norm == "caste" -> "category"
            norm == "mobile" || norm == "phone" || norm == "contact" || norm == "mobileno" -> "mobile"
            norm == "email" || norm.contains("emailaddress") -> "email"
            norm == "address" || norm.contains("permanentaddress") -> "address"
            norm.contains("idnumber") || norm.contains("pannumber") || norm.contains("aadhaarnumber") -> "idNumber"
            norm.contains("10th") && norm.contains("roll") -> "tenthRollNo"
            norm.contains("10th") && norm.contains("reg") -> "tenthRegNo"
            norm.contains("10th") && (norm.contains("year") || norm.contains("passing")) -> "tenthPassingYear"
            norm.contains("10th") && norm.contains("board") -> "tenthBoard"
            norm.contains("10th") && (norm.contains("marks") || norm.contains("percent")) -> "tenthMarks"
            norm.contains("10th") && norm.contains("division") -> "tenthDivision"
            norm.contains("12th") && norm.contains("roll") -> "twelfthRollNo"
            norm.contains("12th") && norm.contains("reg") -> "twelfthRegNo"
            norm.contains("12th") && (norm.contains("year") || norm.contains("passing")) -> "twelfthPassingYear"
            norm.contains("12th") && norm.contains("board") -> "twelfthBoard"
            norm.contains("12th") && (norm.contains("marks") || norm.contains("percent")) -> "twelfthMarks"
            norm.contains("12th") && norm.contains("division") -> "twelfthDivision"
            else -> null
        }
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

    private fun slug(text: String): String {
        return text.lowercase()
            .replace(Regex("""[^a-z0-9]"""), "_")
            .trim('_')
            .ifBlank { "field" }
    }
}
