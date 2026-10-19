package com.dexform.app.core.ai

import com.dexform.app.data.db.FieldDefEntity
import com.dexform.app.data.db.FieldType
import com.dexform.app.data.db.Origin
import com.dexform.app.data.db.Sensitivity
import com.dexform.app.data.db.Tier

data class CatalogEntry(
    val docType: String,
    val displayName: String,
    val keywords: List<String>,
    val defaultTier: Tier,
    val fields: List<FieldDefEntity>
)

object DocumentCatalog {

    val systemFields = listOf(
        FieldDefEntity("person.full_name", "Full Name", "Personal", Tier.BIO, FieldType.TEXT, Sensitivity.NORMAL, synonymsJson = "[\"full name\",\"candidate name\",\"नाम\"]"),
        FieldDefEntity("person.father_name", "Father's Name", "Personal", Tier.BIO, FieldType.TEXT, Sensitivity.NORMAL, synonymsJson = "[\"father's name\",\"father name\",\"पिता का नाम\",\"s/o\",\"d/o\"]"),
        FieldDefEntity("person.mother_name", "Mother's Name", "Personal", Tier.BIO, FieldType.TEXT, Sensitivity.NORMAL, synonymsJson = "[\"mother's name\",\"mother name\",\"माता का नाम\"]"),
        FieldDefEntity("person.dob", "Date of Birth", "Personal", Tier.BIO, FieldType.DATE, Sensitivity.NORMAL, validator = "dob", synonymsJson = "[\"date of birth\",\"dob\",\"जन्म तिथि\"]"),
        FieldDefEntity("person.gender", "Gender", "Personal", Tier.BIO, FieldType.TEXT, Sensitivity.NORMAL, synonymsJson = "[\"gender\",\"sex\",\"लिंग\"]"),
        FieldDefEntity("person.category", "Category", "Personal", Tier.BIO, FieldType.TEXT, Sensitivity.NORMAL, synonymsJson = "[\"category\",\"caste\",\"वर्ग\"]"),

        FieldDefEntity("identity.pan_number", "PAN Number", "Identity", Tier.BIO, FieldType.ID, Sensitivity.SENSITIVE, validator = "pan", synonymsJson = "[\"pan\",\"pan number\",\"permanent account number\"]"),
        FieldDefEntity("identity.aadhaar_last4", "Aadhaar (Last 4)", "Identity", Tier.BIO, FieldType.ID, Sensitivity.SENSITIVE, validator = "aadhaar", synonymsJson = "[\"aadhaar\",\"aadhaar number\"]"),
        FieldDefEntity("identity.voter_id", "Voter ID (EPIC)", "Identity", Tier.BIO, FieldType.ID, Sensitivity.NORMAL, validator = "voter", synonymsJson = "[\"voter id\",\"epic number\"]"),
        FieldDefEntity("identity.dl_number", "Driving License No", "Identity", Tier.BIO, FieldType.ID, Sensitivity.NORMAL, synonymsJson = "[\"dl number\",\"driving licence\"]"),
        FieldDefEntity("identity.passport_number", "Passport Number", "Identity", Tier.BIO, FieldType.ID, Sensitivity.SENSITIVE, validator = "passport", synonymsJson = "[\"passport number\"]"),

        FieldDefEntity("bank.holder_name", "Account Holder Name", "Bank", Tier.BIO, FieldType.TEXT, Sensitivity.NORMAL, synonymsJson = "[\"account holder\",\"holder name\"]"),
        FieldDefEntity("bank.account_no", "Account Number", "Bank", Tier.BIO, FieldType.NUMBER, Sensitivity.SENSITIVE, validator = "bank_acc", synonymsJson = "[\"account number\",\"acc no\",\"account no\"]"),
        FieldDefEntity("bank.ifsc", "IFSC Code", "Bank", Tier.BIO, FieldType.ID, Sensitivity.NORMAL, validator = "ifsc", synonymsJson = "[\"ifsc\",\"ifsc code\"]"),
        FieldDefEntity("bank.bank_name", "Bank Name", "Bank", Tier.BIO, FieldType.TEXT, Sensitivity.NORMAL, synonymsJson = "[\"bank name\",\"bank\"]"),

        FieldDefEntity("edu.10th.board", "10th Board", "Education", Tier.BIO, FieldType.TEXT, Sensitivity.NORMAL, synonymsJson = "[\"10th board\",\"matric board\"]"),
        FieldDefEntity("edu.10th.roll_no", "10th Roll No", "Education", Tier.BIO, FieldType.ID, Sensitivity.NORMAL, synonymsJson = "[\"10th roll no\",\"matric roll\"]"),
        FieldDefEntity("edu.10th.reg_no", "10th Registration No", "Education", Tier.BIO, FieldType.ID, Sensitivity.NORMAL, synonymsJson = "[\"10th reg no\",\"10th registration\"]"),
        FieldDefEntity("edu.10th.passing_year", "10th Passing Year", "Education", Tier.BIO, FieldType.NUMBER, Sensitivity.NORMAL, synonymsJson = "[\"10th passing year\",\"year of passing\"]"),
        FieldDefEntity("edu.10th.division", "10th Division", "Education", Tier.BIO, FieldType.TEXT, Sensitivity.NORMAL, synonymsJson = "[\"10th division\",\"10th result\"]"),

        FieldDefEntity("edu.12th.board", "12th Board", "Education", Tier.BIO, FieldType.TEXT, Sensitivity.NORMAL, synonymsJson = "[\"12th board\",\"inter board\"]"),
        FieldDefEntity("edu.12th.roll_no", "12th Roll No", "Education", Tier.BIO, FieldType.ID, Sensitivity.NORMAL, synonymsJson = "[\"12th roll no\",\"inter roll\"]"),
        FieldDefEntity("edu.12th.reg_no", "12th Registration No", "Education", Tier.BIO, FieldType.ID, Sensitivity.NORMAL, synonymsJson = "[\"12th reg no\",\"12th registration\"]"),
        FieldDefEntity("edu.12th.passing_year", "12th Passing Year", "Education", Tier.BIO, FieldType.NUMBER, Sensitivity.NORMAL, synonymsJson = "[\"12th passing year\",\"inter passing year\"]"),
        FieldDefEntity("edu.12th.division", "12th Division", "Education", Tier.BIO, FieldType.TEXT, Sensitivity.NORMAL, synonymsJson = "[\"12th division\",\"12th result\"]")
    )

    fun classifyByKeywords(ocrText: String): CatalogEntry? {
        val textUpper = ocrText.uppercase()
        return when {
            textUpper.contains("INCOME TAX") || textUpper.contains("PERMANENT ACCOUNT") -> CatalogEntry("PAN", "PAN Card", listOf("INCOME TAX", "PERMANENT ACCOUNT"), Tier.BIO, systemFields)
            textUpper.contains("AADHAAR") || textUpper.contains("UNIQUE IDENTIFICATION") -> CatalogEntry("AADHAAR", "Aadhaar Card", listOf("AADHAAR", "UNIQUE IDENTIFICATION"), Tier.BIO, systemFields)
            textUpper.contains("ELECTION COMMISSION") || textUpper.contains("ELECTORAL") -> CatalogEntry("VOTER_ID", "Voter ID Card", listOf("ELECTION COMMISSION", "ELECTORAL"), Tier.BIO, systemFields)
            textUpper.contains("DRIVING LICENCE") || textUpper.contains("DRIVING LICENSE") -> CatalogEntry("DRIVING_LICENCE", "Driving License", listOf("DRIVING LICENCE"), Tier.BIO, systemFields)
            textUpper.contains("PASSPORT") || textUpper.contains("REPUBLIC OF INDIA") -> CatalogEntry("PASSPORT", "Passport", listOf("PASSPORT"), Tier.BIO, systemFields)
            textUpper.contains("IFSC") || textUpper.contains("BANK") || textUpper.contains("PASSBOOK") -> CatalogEntry("BANK_DOCUMENT", "Bank Passbook / Cheque", listOf("IFSC", "BANK"), Tier.BIO, systemFields)
            textUpper.contains("ADMIT CARD") || textUpper.contains("HALL TICKET") -> CatalogEntry("ADMIT_CARD", "Admit Card", listOf("ADMIT CARD", "HALL TICKET"), Tier.SUB, systemFields)
            textUpper.contains("12TH") || textUpper.contains("INTERMEDIATE") || textUpper.contains("HIGHER SECONDARY") -> CatalogEntry("MARKSHEET_12", "12th Marksheet", listOf("12TH", "INTERMEDIATE"), Tier.BIO, systemFields)
            textUpper.contains("10TH") || textUpper.contains("MATRICULATION") || textUpper.contains("HIGH SCHOOL") -> CatalogEntry("MARKSHEET_10", "10th Marksheet", listOf("10TH", "MATRICULATION"), Tier.BIO, systemFields)
            else -> null
        }
    }
}
