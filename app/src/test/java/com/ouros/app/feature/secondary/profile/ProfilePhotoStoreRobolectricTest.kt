package com.ouros.app.feature.secondary.profile

import android.net.Uri
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProfilePhotoStoreRobolectricTest {

    @Test
    fun constructorWithContext_initializesCorrectly() {
        val context = RuntimeEnvironment.getApplication()
        val store = ProfilePhotoStore(context, 7777L)

        assertNull(store.localPhotoFile())
        assertNull(store.lastSyncedPhotoUrl())
        val avatar = store.peekRandomAvatar()
        assertNotNull(avatar)
    }

    @Test
    fun saveGalleryPhoto_withUri_delegatesGracefully() {
        val context = RuntimeEnvironment.getApplication()
        val store = ProfilePhotoStore(context, 7778L)
        val dummyUri = Uri.parse("content://dummy/nonexistent")

        // Non-existent content uri should return false gracefully
        val saved = store.saveGalleryPhoto(dummyUri)
        assertTrue(!saved)
    }
}
