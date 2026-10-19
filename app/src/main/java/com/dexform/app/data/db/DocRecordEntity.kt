package com.dexform.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "doc_records")
data class DocRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val docType: String,                     // e.g. "PAN", "AADHAAR", "ADMIT_CARD"
    val title: String,                       // e.g. "SSC CGL admit card"
    val tier: Tier,
    val createdAt: Long = System.currentTimeMillis(),
    val savedImageUri: String? = null
)
