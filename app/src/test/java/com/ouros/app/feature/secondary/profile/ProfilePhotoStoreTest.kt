package com.ouros.app.feature.secondary.profile

import android.content.SharedPreferences
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProfilePhotoStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Suppress("UNCHECKED_CAST")
    private class FakeSharedPreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = data
        override fun getString(key: String?, defValue: String?): String? = data[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            (data[key] as? MutableSet<String>) ?: defValues

        override fun getInt(key: String?, defValue: Int): Int = data[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = data[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = data[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun edit(): SharedPreferences.Editor = FakeEditor()
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        inner class FakeEditor : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private val removed = mutableSetOf<String>()

            override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
                key?.let { pending[it] = value }
            }

            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = apply {
                key?.let { pending[it] = values }
            }

            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
                key?.let { pending[it] = value }
            }

            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
                key?.let { pending[it] = value }
            }

            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
                key?.let { pending[it] = value }
            }

            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
                key?.let { pending[it] = value }
            }

            override fun remove(key: String?): SharedPreferences.Editor = apply {
                key?.let { removed.add(it) }
            }

            override fun clear(): SharedPreferences.Editor = apply {
                data.clear()
            }

            override fun commit(): Boolean {
                apply()
                return true
            }

            override fun apply() {
                removed.forEach { data.remove(it) }
                pending.forEach { (k, v) -> data[k] = v }
            }
        }
    }

    private fun createStore(
        ownerId: Long = 1001L,
        legacyClearAction: () -> Unit = {}
    ): Pair<ProfilePhotoStore, FakeSharedPreferences> {
        val prefs = FakeSharedPreferences()
        val store = ProfilePhotoStore(
            filesDir = temporaryFolder.root,
            preferences = prefs,
            ownerId = ownerId,
            legacyClearAction = legacyClearAction
        )
        return Pair(store, prefs)
    }

    @Test
    fun committingPhoto_persistsFileAndUpdatesSyncUrl() {
        val (store, _) = createStore(5101L)
        val source = temporaryFolder.newFile("new_avatar.jpg").apply {
            writeBytes(byteArrayOf(10, 20, 30))
        }

        assertNull(store.localPhotoFile())
        assertNull(store.lastSyncedPhotoUrl())

        val committed = store.commitPhoto(source, "https://cloudinary.com/test_user_photo.jpg")
        assertTrue(committed)

        val localFile = store.localPhotoFile()
        assertNotNull(localFile)
        assertArrayEquals(byteArrayOf(10, 20, 30), localFile?.readBytes())
        assertEquals("https://cloudinary.com/test_user_photo.jpg", store.lastSyncedPhotoUrl())
        assertFalse(store.shouldIgnoreRemotePhoto())
        assertFalse(store.isPhotoPendingUpload())
    }

    @Test
    fun committingPhoto_withEmptyOrMissingFile_returnsFalse() {
        val (store, _) = createStore(5102L)
        val nonExistent = File(temporaryFolder.root, "does_not_exist.jpg")
        assertFalse(store.commitPhoto(nonExistent))

        val emptyFile = temporaryFolder.newFile("empty.jpg")
        assertFalse(store.commitPhoto(emptyFile))
    }

    @Test
    fun committingPhotoRemoval_deletesFileAndMarksRemoved() {
        val (store, _) = createStore(5103L)
        val source = temporaryFolder.newFile("to_delete.jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3))
        }

        store.commitPhoto(source, "https://cloudinary.com/user_to_delete.jpg")
        assertNotNull(store.localPhotoFile())

        val avatarResource = store.commitPhotoRemoval()
        assertNotNull(avatarResource)
        assertNull(store.localPhotoFile())
        assertTrue(store.shouldIgnoreRemotePhoto())
        assertNull(store.lastSyncedPhotoUrl())
        assertFalse(store.isPhotoPendingUpload())
    }

    @Test
    fun syncWithRemote_whenRemoteUrlIsNull_clearsLocalPhotoAndResetsState() {
        val (store, _) = createStore(5104L)
        val source = temporaryFolder.newFile("orphan.jpg").apply {
            writeBytes(byteArrayOf(7, 8, 9))
        }

        store.commitPhoto(source, "https://cloudinary.com/orphan.jpg")
        assertNotNull(store.localPhotoFile())

        store.syncWithRemote(null)

        assertNull(store.localPhotoFile())
        assertNull(store.lastSyncedPhotoUrl())
        assertFalse(store.shouldIgnoreRemotePhoto())
    }

    @Test
    fun syncWithRemote_whenRemoteUrlChanges_invalidatesOutdatedLocalPhoto() {
        val (store, _) = createStore(5105L)
        val source = temporaryFolder.newFile("old.jpg").apply {
            writeBytes(byteArrayOf(4, 5, 6))
        }

        store.commitPhoto(source, "https://cloudinary.com/old_photo.jpg")
        assertNotNull(store.localPhotoFile())
        assertEquals("https://cloudinary.com/old_photo.jpg", store.lastSyncedPhotoUrl())

        // Remote has new URL
        store.syncWithRemote("https://cloudinary.com/new_photo.jpg")

        // Old local photo was invalidated
        assertNull(store.localPhotoFile())
        assertNull(store.lastSyncedPhotoUrl())
    }

    @Test
    fun syncWithRemote_whenRemoteUrlMatches_keepsLocalPhotoAndClearsRemovedFlag() {
        val (store, prefs) = createStore(5106L)
        val source = temporaryFolder.newFile("synced.jpg").apply {
            writeBytes(byteArrayOf(1, 1, 1))
        }

        store.commitPhoto(source, "https://cloudinary.com/same_photo.jpg")
        assertNotNull(store.localPhotoFile())

        prefs.edit().putBoolean("remote_photo_removed", true).apply()
        assertTrue(store.shouldIgnoreRemotePhoto())

        store.syncWithRemote("https://cloudinary.com/same_photo.jpg")

        assertNotNull(store.localPhotoFile())
        assertEquals("https://cloudinary.com/same_photo.jpg", store.lastSyncedPhotoUrl())
        assertFalse(store.shouldIgnoreRemotePhoto())
    }

    @Test
    fun uncommittedChanges_doNotAlterPermanentStorage() {
        val (store, _) = createStore(5107L)

        assertNull(store.localPhotoFile())

        val cameraFile = store.prepareCameraFile()
        cameraFile.writeBytes(byteArrayOf(99, 98, 97))

        assertNull(store.localPhotoFile())
        assertNull(store.lastSyncedPhotoUrl())
        assertFalse(store.shouldIgnoreRemotePhoto())

        store.cancelPendingCameraPhoto()
        assertFalse(cameraFile.exists())
    }

    @Test
    fun prepareCameraFile_cleansUpExistingPendingFile() {
        val (store, _) = createStore(5108L)
        val firstFile = store.prepareCameraFile()
        firstFile.writeBytes(byteArrayOf(1, 2))
        assertTrue(firstFile.exists())

        val secondFile = store.prepareCameraFile()
        assertTrue(secondFile.exists())
        assertEquals(0L, secondFile.length())
    }

    @Test
    fun pendingCameraFile_reflectsPreparedFileWithContent() {
        val (store, _) = createStore(5109L)
        assertNull(store.pendingCameraFile())

        val cameraFile = store.prepareCameraFile()
        assertNull(store.pendingCameraFile())

        cameraFile.writeBytes(byteArrayOf(1, 2, 3))
        assertNotNull(store.pendingCameraFile())
        assertEquals(cameraFile.absolutePath, store.pendingCameraFile()?.absolutePath)

        store.cancelPendingCameraPhoto()
        assertNull(store.pendingCameraFile())
    }

    @Test
    fun committingCameraPhoto_whenPendingExists_commitsAndMarksPendingUpload() {
        val (store, _) = createStore(5110L)
        val pendingPhoto = store.prepareCameraFile()
        pendingPhoto.writeBytes(byteArrayOf(5, 6, 7))

        assertTrue(store.commitCameraPhoto())
        assertFalse(pendingPhoto.exists())
        assertArrayEquals(byteArrayOf(5, 6, 7), store.localPhotoFile()?.readBytes())
        assertTrue(store.isPhotoPendingUpload())

        store.markPhotoUploadCompleted()
        assertFalse(store.isPhotoPendingUpload())
    }

    @Test
    fun committingCameraPhoto_whenPendingMissingOrEmpty_returnsFalse() {
        val (store, _) = createStore(5111L)
        assertFalse(store.commitCameraPhoto())

        store.prepareCameraFile()
        assertFalse(store.commitCameraPhoto())
    }

    @Test
    fun saveGalleryStream_successFlow() {
        val sampleBytes = byteArrayOf(9, 8, 7, 6)
        val (store, _) = createStore(ownerId = 5112L)

        assertTrue(store.saveGalleryStream { ByteArrayInputStream(sampleBytes) })
        assertArrayEquals(sampleBytes, store.localPhotoFile()?.readBytes())
        assertTrue(store.isPhotoPendingUpload())
    }

    @Test
    fun saveGalleryStream_whenStreamReturnsNull_returnsFalse() {
        val (store, _) = createStore(ownerId = 5113L)
        assertFalse(store.saveGalleryStream { null })
        assertNull(store.localPhotoFile())
    }

    @Test
    fun saveGalleryStream_whenStreamIsEmpty_returnsFalse() {
        val (store, _) = createStore(ownerId = 5114L)
        assertFalse(store.saveGalleryStream { ByteArrayInputStream(byteArrayOf()) })
        assertNull(store.localPhotoFile())
    }

    @Test
    fun saveGalleryStream_whenStreamThrows_returnsFalse() {
        val (store, _) = createStore(ownerId = 5115L)
        assertFalse(store.saveGalleryStream { throw IOException("Failed to read stream") })
        assertNull(store.localPhotoFile())
    }

    @Test
    fun deletePhotoAndSelectRandomAvatar_delegatesToCommitPhotoRemoval() {
        val (store, _) = createStore(5116L)
        val source = temporaryFolder.newFile("photo_to_random.jpg").apply {
            writeBytes(byteArrayOf(3, 2, 1))
        }
        store.commitPhoto(source)
        assertNotNull(store.localPhotoFile())

        val avatar = store.deletePhotoAndSelectRandomAvatar()
        assertNotNull(avatar)
        assertNull(store.localPhotoFile())
        assertTrue(store.shouldIgnoreRemotePhoto())
    }

    @Test
    fun peekRandomAvatar_returnsExistingOrNewWithoutPersisting() {
        val (store, prefs) = createStore(5117L)

        // When no avatar is stored
        val peeked1 = store.peekRandomAvatar()
        assertNotNull(peeked1)
        assertNull(prefs.getString("selected_avatar", null))

        // When an avatar was already saved
        val selected = store.selectedAvatarResource()
        assertNotNull(selected)
        val storedName = prefs.getString("selected_avatar", null)
        assertNotNull(storedName)

        val peeked2 = store.peekRandomAvatar()
        assertEquals(selected, peeked2)
    }

    @Test
    fun selectedAvatarResource_persistsWhenNotStored() {
        val (store, prefs) = createStore(5118L)
        assertNull(prefs.getString("selected_avatar", null))

        val avatar = store.selectedAvatarResource()
        assertNotNull(avatar)
        assertNotNull(prefs.getString("selected_avatar", null))

        // Calling again returns same avatar
        val avatarAgain = store.selectedAvatarResource()
        assertEquals(avatar, avatarAgain)
    }

    @Test
    fun legacyClearAction_isInvokedOnInit() {
        var legacyCleaned = false
        createStore(
            ownerId = 5119L,
            legacyClearAction = { legacyCleaned = true }
        )
        assertTrue(legacyCleaned)
    }
}
