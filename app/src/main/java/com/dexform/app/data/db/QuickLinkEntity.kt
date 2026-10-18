package com.dexform.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quick_links")
data class QuickLinkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val createdMs: Long = System.currentTimeMillis()
)
