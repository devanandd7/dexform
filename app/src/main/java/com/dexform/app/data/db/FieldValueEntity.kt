package com.dexform.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "field_values")
data class FieldValueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val fieldKey: String,
    val docId: Long? = null,                  // null = Bio (profile level), set = Sub-bio (belongs to doc)
    val valueEnc: String,                    // Encrypted if SENSITIVE
    val confidence: String = "HIGH",          // HIGH / MEDIUM / LOW
    val sourceDocId: Long? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
