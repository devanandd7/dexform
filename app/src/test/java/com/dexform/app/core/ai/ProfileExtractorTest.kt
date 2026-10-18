package com.dexform.app.core.ai

import com.dexform.app.data.db.UserProfileEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileExtractorTest {

    @Test
    fun testUserProfileEntityDefaults() {
        val prof = UserProfileEntity(name = "Devanand")
        assertEquals("Devanand", prof.name)
        assertEquals("", prof.fatherName)
        assertEquals("", prof.tenthRollNo)
        assertEquals("", prof.twelfthRollNo)
    }
}
