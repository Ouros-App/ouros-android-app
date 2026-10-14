package com.ouros.app.feature.secondary.profile

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProfilePhotoScopeTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun differentOwnersUseSeparatePhotoFilesAndPreferences() {
        val filesDir = temporaryFolder.root
        val firstOwner = ProfilePhotoScope.forOwner(filesDir, 101L)
        assertEquals(
            File(filesDir, "profile_accounts/101"),
            firstOwner.directory
        )
        val secondOwner = ProfilePhotoScope.forOwner(filesDir, 202L)
        assertNotEquals(
            File(firstOwner.directory, "profile_photo.jpg"),
            File(secondOwner.directory, "profile_photo.jpg")
        )
        assertNotEquals(firstOwner.directory, secondOwner.directory)
        assertNotEquals(firstOwner.preferencesName, secondOwner.preferencesName)
    }
}
