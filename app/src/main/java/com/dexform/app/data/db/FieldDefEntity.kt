package com.dexform.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class Tier { BIO, SUB }
enum class Sensitivity { NORMAL, SENSITIVE }
enum class FieldType { TEXT, DATE, NUMBER, ID, PHONE, EMAIL, PINCODE, AMOUNT }
enum class Origin { SYSTEM, AI, USER }

@Entity(tableName = "field_defs")
data class FieldDefEntity(
    @PrimaryKey val key: String,             // e.g. "person.father_name", "identity.pan_number"
    val label: String,                       // e.g. "Father's Name"
    val group: String,                       // e.g. "Personal", "Identity", "Bank", "Education", "Address"
    val tier: Tier,
    val type: FieldType,
    val sensitivity: Sensitivity,
    val validator: String? = null,
    val synonymsJson: String = "[]",
    val origin: Origin = Origin.SYSTEM,
    val approved: Boolean = true
)
