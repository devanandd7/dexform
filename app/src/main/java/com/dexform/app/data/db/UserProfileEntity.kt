package com.dexform.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profiles")
data class UserProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val imageUri: String? = null,
    val isSelected: Boolean = false,

    // Extracted Personal Details
    val fatherName: String = "",
    val motherName: String = "",
    val dob: String = "",
    val gender: String = "",
    val category: String = "",
    val mobile: String = "",
    val email: String = "",
    val address: String = "",
    val idNumber: String = "", // Aadhaar / PAN

    // 10th Academic Info
    val tenthRollNo: String = "",
    val tenthRegNo: String = "",
    val tenthPassingYear: String = "",
    val tenthBoard: String = "",
    val tenthMarks: String = "",
    val tenthDivision: String = "",

    // 12th Academic Info
    val twelfthRollNo: String = "",
    val twelfthRegNo: String = "",
    val twelfthPassingYear: String = "",
    val twelfthBoard: String = "",
    val twelfthMarks: String = "",
    val twelfthDivision: String = "",

    val createdMs: Long = System.currentTimeMillis()
)
