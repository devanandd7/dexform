package com.dexform.app.core.ai

import android.content.Context
import android.util.Log
import com.dexform.app.AppContainer
import com.dexform.app.data.db.DocRecordEntity
import com.dexform.app.data.db.FieldDefEntity
import com.dexform.app.data.db.FieldType
import com.dexform.app.data.db.FieldValueEntity
import com.dexform.app.data.db.Origin
import com.dexform.app.data.db.Sensitivity
import com.dexform.app.data.db.Tier
import com.dexform.app.data.db.UserProfileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class SaveResult(
    val profileId: Long,
    val updatedCoreFields: Int,
    val savedBioFields: Int,
    val savedSubFields: Int,
    val docRecordId: Long?
)

object SaveToBioEngine {
    private const val TAG = "SaveToBioEngine"

    suspend fun saveProposal(
        context: Context,
        container: AppContainer,
        proposal: DocumentProposal,
        explicitProfileId: Long? = null,
        sourceImageFile: File? = null
    ): SaveResult = withContext(Dispatchers.IO) {
        val profileDao = container.userProfileDao
        val fieldDefDao = container.fieldDefDao
        val fieldValueDao = container.fieldValueDao
        val docRecordDao = container.docRecordDao

        // 1. Get or create active profile
        val existingProfile: UserProfileEntity? = if (explicitProfileId != null) {
            profileDao.getSelectedProfile()?.takeIf { it.id == explicitProfileId }
        } else {
            profileDao.getSelectedProfile()
        }

        val baseProfile: UserProfileEntity = if (existingProfile == null) {
            val candidateName = proposal.fields.firstOrNull { it.isCoreProfileField && it.coreFieldName == "name" }?.value
                ?: "My Profile"
            val newProfile = UserProfileEntity(
                name = candidateName,
                isSelected = true
            )
            val newId = profileDao.insertProfile(newProfile)
            Log.i(TAG, "Created new active profile id=$newId with name=$candidateName")
            newProfile.copy(id = newId)
        } else {
            existingProfile
        }

        val profileId = baseProfile.id
        var updatedProfile: UserProfileEntity = baseProfile
        var coreCount = 0

        // 2. Update core profile fields
        for (field in proposal.fields) {
            if (field.isSelected && field.isCoreProfileField && field.coreFieldName != null) {
                val fieldVal = field.value.trim()
                if (fieldVal.isNotBlank()) {
                    val prevProfile = updatedProfile
                    updatedProfile = when (field.coreFieldName) {
                        "name" -> updatedProfile.copy(name = fieldVal)
                        "fatherName" -> updatedProfile.copy(fatherName = fieldVal)
                        "motherName" -> updatedProfile.copy(motherName = fieldVal)
                        "dob" -> updatedProfile.copy(dob = fieldVal)
                        "gender" -> updatedProfile.copy(gender = fieldVal)
                        "category" -> updatedProfile.copy(category = fieldVal)
                        "mobile" -> updatedProfile.copy(mobile = fieldVal)
                        "email" -> updatedProfile.copy(email = fieldVal)
                        "address" -> updatedProfile.copy(address = fieldVal)
                        "idNumber" -> updatedProfile.copy(idNumber = fieldVal)
                        "tenthRollNo" -> updatedProfile.copy(tenthRollNo = fieldVal)
                        "tenthRegNo" -> updatedProfile.copy(tenthRegNo = fieldVal)
                        "tenthPassingYear" -> updatedProfile.copy(tenthPassingYear = fieldVal)
                        "tenthBoard" -> updatedProfile.copy(tenthBoard = fieldVal)
                        "tenthMarks" -> updatedProfile.copy(tenthMarks = fieldVal)
                        "tenthDivision" -> updatedProfile.copy(tenthDivision = fieldVal)
                        "twelfthRollNo" -> updatedProfile.copy(twelfthRollNo = fieldVal)
                        "twelfthRegNo" -> updatedProfile.copy(twelfthRegNo = fieldVal)
                        "twelfthPassingYear" -> updatedProfile.copy(twelfthPassingYear = fieldVal)
                        "twelfthBoard" -> updatedProfile.copy(twelfthBoard = fieldVal)
                        "twelfthMarks" -> updatedProfile.copy(twelfthMarks = fieldVal)
                        "twelfthDivision" -> updatedProfile.copy(twelfthDivision = fieldVal)
                        else -> updatedProfile
                    }
                    if (prevProfile != updatedProfile) {
                        coreCount++
                    }
                }
            }
        }

        if (coreCount > 0) {
            profileDao.updateProfile(updatedProfile)
            Log.i(TAG, "Updated $coreCount core profile fields on profile id=$profileId")
        }

        // 3. Save Custom Primary Bio fields (Tier.BIO)
        var bioCount = 0
        val bioFields = proposal.fields.filter { it.isSelected && it.tier == Tier.BIO && !it.isCoreProfileField }
        for (field in bioFields) {
            val def = FieldDefEntity(
                key = field.key,
                label = field.label,
                group = proposal.docType.ifBlank { "Personal/Career" },
                tier = Tier.BIO,
                type = inferFieldType(field.value),
                sensitivity = inferSensitivity(field.label),
                synonymsJson = "[\"${field.label.lowercase()}\"]",
                origin = Origin.AI,
                approved = true
            )
            fieldDefDao.insertDef(def)

            val valEntity = FieldValueEntity(
                profileId = profileId,
                fieldKey = def.key,
                docId = null, // null represents Primary Bio
                valueEnc = field.value,
                confidence = "HIGH",
                updatedAt = System.currentTimeMillis()
            )
            fieldValueDao.insertValue(valEntity)
            bioCount++
        }
        Log.i(TAG, "Saved $bioCount dynamic primary bio fields")

        // 4. Save Sub-Bio fields (Tier.SUB) and Document Record
        var subCount = 0
        var createdDocId: Long? = null
        val subFields = proposal.fields.filter { it.isSelected && it.tier == Tier.SUB }

        if (subFields.isNotEmpty() || sourceImageFile != null) {
            var persistentImageUri: String? = null
            if (sourceImageFile != null && sourceImageFile.exists()) {
                try {
                    val docsDir = File(context.filesDir, "saved_documents").apply { if (!exists()) mkdirs() }
                    val targetImg = File(docsDir, "doc_${System.currentTimeMillis()}_${sourceImageFile.name}")
                    sourceImageFile.copyTo(targetImg, overwrite = true)
                    persistentImageUri = targetImg.absolutePath
                } catch (e: Exception) {
                    Log.w(TAG, "Failed copying image to persistent storage: ${e.message}")
                }
            }

            val docRecord = DocRecordEntity(
                profileId = profileId,
                docType = proposal.docType,
                title = proposal.docTitle.ifBlank { proposal.docType },
                tier = Tier.SUB,
                createdAt = System.currentTimeMillis(),
                savedImageUri = persistentImageUri
            )
            val docId = docRecordDao.insertDoc(docRecord)
            createdDocId = docId

            for (field in subFields) {
                val def = FieldDefEntity(
                    key = field.key,
                    label = field.label,
                    group = proposal.docType,
                    tier = Tier.SUB,
                    type = inferFieldType(field.value),
                    sensitivity = inferSensitivity(field.label),
                    synonymsJson = "[\"${field.label.lowercase()}\"]",
                    origin = Origin.AI,
                    approved = true
                )
                fieldDefDao.insertDef(def)

                val valEntity = FieldValueEntity(
                    profileId = profileId,
                    fieldKey = def.key,
                    docId = docId, // Tied to the created document record
                    valueEnc = field.value,
                    confidence = "HIGH",
                    sourceDocId = docId,
                    updatedAt = System.currentTimeMillis()
                )
                fieldValueDao.insertValue(valEntity)
                subCount++
            }
            Log.i(TAG, "Saved $subCount sub-bio fields for docId=$docId")
        }

        SaveResult(
            profileId = profileId,
            updatedCoreFields = coreCount,
            savedBioFields = bioCount,
            savedSubFields = subCount,
            docRecordId = createdDocId
        )
    }

    private fun inferFieldType(value: String): FieldType {
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

    private fun inferSensitivity(label: String): Sensitivity {
        val lower = label.lowercase()
        return if (lower.contains("pan") || lower.contains("aadhaar") || lower.contains("account") || lower.contains("passport")) {
            Sensitivity.SENSITIVE
        } else {
            Sensitivity.NORMAL
        }
    }
}
