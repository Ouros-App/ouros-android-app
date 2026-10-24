package com.ouros.app.feature.secondary.profile

import android.net.Uri
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TemporaryFolder
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProfilePhotoStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun committingPhoto_persistsFileAndUpdatesSyncUrl() {
        val store = ProfilePhotoStore(RuntimeEnvironment.getApplication(), 5101L)
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
    fun committingPhotoRemoval_deletesFileAndMarksRemoved() {
        val store = ProfilePhotoStore(RuntimeEnvironment.getApplication(), 5102L)
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
        val store = ProfilePhotoStore(RuntimeEnvironment.getApplication(), 5103L)
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
        val store = ProfilePhotoStore(RuntimeEnvironment.getApplication(), 5104L)
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
    fun syncWithRemote_whenRemoteUrlMatches_keepsLocalPhoto() {
        val store = ProfilePhotoStore(RuntimeEnvironment.getApplication(), 5105L)
        val source = temporaryFolder.newFile("synced.jpg").apply {
            writeBytes(byteArrayOf(1, 1, 1))
        }

        store.commitPhoto(source, "https://cloudinary.com/same_photo.jpg")
        assertNotNull(store.localPhotoFile())

        store.syncWithRemote("https://cloudinary.com/same_photo.jpg")

        assertNotNull(store.localPhotoFile())
        assertEquals("https://cloudinary.com/same_photo.jpg", store.lastSyncedPhotoUrl())
    }

    @Test
    fun uncommittedChanges_doNotAlterPermanentStorage() {
        val store = ProfilePhotoStore(RuntimeEnvironment.getApplication(), 5106L)

        assertNull(store.localPhotoFile())

        // Prepare camera file but do NOT commit
        val cameraFile = store.prepareCameraFile()
        cameraFile.writeBytes(byteArrayOf(99, 98, 97))

        assertNull(store.localPhotoFile())
        assertNull(store.lastSyncedPhotoUrl())
        assertFalse(store.shouldIgnoreRemotePhoto())

        store.cancelPendingCameraPhoto()
        assertFalse(cameraFile.exists())
    }

    @Test
    fun peekRandomAvatar_doesNotAlterStateUntilCommitted() {
        val store = ProfilePhotoStore(RuntimeEnvironment.getApplication(), 5107L)

        val avatar = store.peekRandomAvatar()
        assertNotNull(avatar)
        assertFalse(store.shouldIgnoreRemotePhoto())
        assertNull(store.lastSyncedPhotoUrl())
    }

    @Test
    fun pendingCameraFile_reflectsPreparedFileWithContent() {
        val store = ProfilePhotoStore(RuntimeEnvironment.getApplication(), 5108L)
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
    fun savingGalleryPhotoMarksUploadPendingUntilMarkedComplete() {
        val store = ProfilePhotoStore(RuntimeEnvironment.getApplication(), 4101L)
        val source = temporaryFolder.newFile("gallery.jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }

        assertFalse(store.isPhotoPendingUpload())
        assertTrue(store.saveGalleryPhoto(Uri.fromFile(source)))

        val savedPhoto: File = store.localPhotoFile() ?: error("Saved photo must be available locally")
        assertArrayEquals(source.readBytes(), savedPhoto.readBytes())
        assertTrue(store.isPhotoPendingUpload())

        store.markPhotoUploadCompleted()

        assertFalse(store.isPhotoPendingUpload())
    }

    @Test
    fun committingCameraPhotoMarksItPendingAndRemovesTemporaryFile() {
        val store = ProfilePhotoStore(RuntimeEnvironment.getApplication(), 4102L)
        val pendingPhoto = store.prepareCameraFile()
        pendingPhoto.writeBytes(byteArrayOf(5, 6, 7))

        assertTrue(store.commitCameraPhoto())
        assertFalse(pendingPhoto.exists())
        assertArrayEquals(byteArrayOf(5, 6, 7), store.localPhotoFile()?.readBytes())
        assertTrue(store.isPhotoPendingUpload())
    }
}
