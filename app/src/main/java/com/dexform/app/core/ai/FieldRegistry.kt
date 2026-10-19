package com.dexform.app.core.ai

import com.dexform.app.data.db.FieldDefEntity
import com.dexform.app.data.db.FieldType
import com.dexform.app.data.db.Origin
import com.dexform.app.data.db.Sensitivity
import com.dexform.app.data.db.Tier

sealed interface ResolvedField {
    data class Existing(val key: String, val fieldDef: FieldDefEntity) : ResolvedField
    data class Proposed(val def: FieldDefEntity) : ResolvedField
}

object FieldRegistry {

    private val blockedLabels = setOf("cvv", "pin", "password", "otp", "secret", "cvv2")

    fun resolve(
        label: String,
        value: String,
        docType: String,
        tierHint: Tier?,
        existingDefs: List<FieldDefEntity>
    ): ResolvedField {
        val normLabel = normalizeLabel(label)

        if (blockedLabels.contains(normLabel)) {
            throw IllegalArgumentException("Blocked sensitive label '$label' cannot be saved.")
        }

        // 1. Exact or Synonym match
        val existing = existingDefs.firstOrNull { def ->
            def.label.equals(label, ignoreCase = true) ||
            def.key.equals(label, ignoreCase = true) ||
            def.synonymsJson.lowercase().contains(normLabel)
        }

        if (existing != null) {
            return ResolvedField.Existing(existing.key, existing)
        }

        // 2. Propose new AI Field
        val inferredType = inferType(value)
        val sensitivity = if (isSensitive(normLabel, value)) Sensitivity.SENSITIVE else Sensitivity.NORMAL
        val tier = tierHint ?: if (isIdentityOrPerson(normLabel)) Tier.BIO else Tier.SUB
        val slugKey = "${slug(docType)}.${slug(label)}"

        val proposedDef = FieldDefEntity(
            key = slugKey,
            label = label.trim(),
            group = docType.ifBlank { "Other" },
            tier = tier,
            type = inferredType,
            sensitivity = sensitivity,
            synonymsJson = "[\"$normLabel\"]",
            origin = Origin.AI,
            approved = false
        )

        return ResolvedField.Proposed(proposedDef)
    }

    private fun normalizeLabel(label: String): String {
        return label.lowercase().replace(Regex("""[^a-z0-9\s]"""), "").trim()
    }

    private fun inferType(value: String): FieldType {
        return when {
            value.matches(Regex("""\d{1,2}[/-]\d{1,2}[/-]\d{2,4}""")) -> FieldType.DATE
            value.matches(Regex("""\d{10}""")) -> FieldType.PHONE
            value.matches(Regex("""\d{6}""")) -> FieldType.PINCODE
            value.contains("@") && value.contains(".") -> FieldType.EMAIL
            value.matches(Regex("""\d+""")) -> FieldType.NUMBER
            value.matches(Regex("""[A-Z0-9/-]{5,}""")) -> FieldType.ID
            else -> FieldType.TEXT
        }
    }

    private fun isSensitive(normLabel: String, value: String): Boolean {
        return normLabel.contains("account") || normLabel.contains("pan") || normLabel.contains("aadhaar") || normLabel.contains("passport")
    }

    private fun isIdentityOrPerson(normLabel: String): Boolean {
        return normLabel.contains("name") || normLabel.contains("dob") || normLabel.contains("father") || normLabel.contains("mother") || normLabel.contains("address")
    }

    private fun slug(s: String): String {
        return s.lowercase().replace(Regex("""[^a-z0-9]"""), "_").trim('_')
    }
}
