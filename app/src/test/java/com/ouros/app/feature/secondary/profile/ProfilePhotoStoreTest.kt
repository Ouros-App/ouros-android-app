package com.ouros.app.feature.secondary.profile

import android.net.Uri
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TemporaryFolder
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ProfilePhotoStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

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
