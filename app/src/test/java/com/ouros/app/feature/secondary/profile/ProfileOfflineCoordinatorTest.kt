package com.ouros.app.feature.secondary.profile

import android.app.Application
import com.ouros.app.data.auth.AppSession
import com.ouros.app.data.auth.AuthResult
import com.ouros.app.data.farmowner.FarmOwnerResponse
import com.ouros.app.data.farmowner.FarmOwnerUpdateRequest
import com.ouros.app.data.local.OfflineStore
import com.ouros.app.data.local.OurosOfflineDatabase
import com.ouros.app.data.session.FarmSessionStore
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProfileOfflineCoordinatorTest {
    private val context: Application
        get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        context.deleteDatabase("ouros_offline.db")
        File(context.filesDir, "profile_photos").deleteRecursively()
        AppSession.initialize(context)
    }

    @Test
    fun savePending_keepCurrentCachesOptimisticProfileAndQueuesUpdate() {
        val coordinator = ProfileOfflineCoordinator(context)
        val store = OfflineStore(context)
        val current = profile(1L, photoUrl = "https://server/photo.jpg")

        val optimistic = coordinator.savePending(
            ownerId = 1L,
            currentProfile = current,
            request = request("Nome novo"),
            photoChange = ProfilePhotoChange.KeepCurrent
        )

        assertEquals("Nome novo", optimistic.name)
        assertEquals(current.photoUrl, optimistic.photoUrl)
        assertEquals(optimistic, store.cached<FarmOwnerResponse>("farm-owner:1"))
        assertEquals("update", store.pending("profile").single().operation)
    }

    @Test
    fun savePending_removePhotoMarksRemovalAndReplacesPreviousOwnerUpdate() {
        val coordinator = ProfileOfflineCoordinator(context)
        val store = OfflineStore(context)
        val current = profile(2L, photoUrl = "https://server/photo.jpg")

        coordinator.savePending(2L, current, request("Primeiro"), ProfilePhotoChange.KeepCurrent)
        val optimistic = coordinator.savePending(2L, current, request("Segundo"), ProfilePhotoChange.Remove)

        assertEquals("", optimistic.photoUrl)
        assertEquals(1, store.pending("profile").size)
        assertEquals("", store.cached<FarmOwnerResponse>("farm-owner:2")?.photoUrl)
    }

    @Test
    fun savePending_uploadStagesLocalPhoto() {
        val coordinator = ProfileOfflineCoordinator(context)
        val source = File(context.cacheDir, "new-profile-photo.jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }

        coordinator.savePending(
            3L,
            profile(3L, photoUrl = "old-url"),
            request("Com foto"),
            ProfilePhotoChange.UploadNew(source)
        )

        val photoStore = ProfilePhotoStore(context, 3L)
        assertNotNull(photoStore.localPhotoFile())
        assertTrue(photoStore.isPhotoPendingUpload())
    }

    @Test
    fun synchronize_discardsMalformedPayloadAndSkipsAnotherOwner() = runBlocking {
        val coordinator = ProfileOfflineCoordinator(context)
        val store = OfflineStore(context)
        val database = OurosOfflineDatabase(context)
        database.enqueue("profile", "update", "bad", "not-json")
        FarmSessionStore(context).saveAccount(10L, 20L)
        store.enqueue(
            "profile",
            "update",
            "11",
            mapOf(
                "ownerId" to 11L,
                "request" to request("Outro usuário"),
                "photoOperation" to "KEEP"
            )
        )

        coordinator.synchronize()

        val pending = store.pending("profile")
        assertEquals(1, pending.size)
        assertEquals("11", pending.single().entityKey)
        assertFalse(pending.single().attempts > 0)
    }

    @Test
    fun synchronize_keepCurrentUpdatesBackendAndCompletesQueue() = runBlocking {
        val store = OfflineStore(context)
        val farmStore = FarmSessionStore(context).apply { saveAccount(4L, 20L) }
        var sentRequest: FarmOwnerUpdateRequest? = null
        val coordinator = ProfileOfflineCoordinator(
            context,
            offlineStore = store,
            farmSessionStore = farmStore,
            uploadImage = { error("upload should not run") },
            profileUpdater = { _, request ->
                sentRequest = request
                AuthResult.Success(profile(4L, request.photoUrl))
            }
        )
        coordinator.savePending(4L, profile(4L, "remote"), request("Sincronizado"), ProfilePhotoChange.KeepCurrent)

        coordinator.synchronize()

        assertTrue(store.pending("profile").isEmpty())
        assertEquals("Sincronizado", sentRequest?.name)
    }

    @Test
    fun synchronize_uploadSendsUrlAndMarksPhotoAsCompleted() = runBlocking {
        val store = OfflineStore(context)
        val farmStore = FarmSessionStore(context).apply { saveAccount(5L, 20L) }
        var uploadedFile: File? = null
        var sentUrl: String? = null
        val coordinator = ProfileOfflineCoordinator(
            context,
            offlineStore = store,
            farmSessionStore = farmStore,
            uploadImage = { file ->
                uploadedFile = file
                Result.success("https://cloud/new.jpg")
            },
            profileUpdater = { _, request ->
                sentUrl = request.photoUrl
                AuthResult.Success(profile(5L, request.photoUrl))
            }
        )
        val source = File(context.cacheDir, "upload-profile.jpg").apply { writeBytes(byteArrayOf(9, 8, 7)) }
        coordinator.savePending(5L, profile(5L, "old"), request("Upload"), ProfilePhotoChange.UploadNew(source))

        coordinator.synchronize()

        assertNotNull(uploadedFile)
        assertEquals("https://cloud/new.jpg", sentUrl)
        assertFalse(ProfilePhotoStore(context, 5L).isPhotoPendingUpload())
        assertTrue(store.pending("profile").isEmpty())
    }

    @Test
    fun synchronize_removeSendsBlankUrlAndBackendErrorKeepsQueue() = runBlocking {
        val store = OfflineStore(context)
        val farmStore = FarmSessionStore(context).apply { saveAccount(6L, 20L) }
        var sentUrl: String? = null
        var succeeds = false
        val coordinator = ProfileOfflineCoordinator(
            context,
            offlineStore = store,
            farmSessionStore = farmStore,
            uploadImage = { error("upload should not run") },
            profileUpdater = { _, request ->
                sentUrl = request.photoUrl
                if (succeeds) AuthResult.Success(profile(6L, request.photoUrl))
                else AuthResult.Error("offline")
            }
        )
        coordinator.savePending(6L, profile(6L, "remote"), request("Remover"), ProfilePhotoChange.Remove)

        coordinator.synchronize()
        assertEquals("", sentUrl)
        assertEquals(1, store.pending("profile").single().attempts)

        succeeds = true
        coordinator.synchronize()
        assertTrue(store.pending("profile").isEmpty())
    }

    private fun profile(ownerId: Long, photoUrl: String?) = FarmOwnerResponse(
        id = ownerId,
        name = "Nome atual",
        documentNumber = "123",
        email = "atual@example.com",
        telephone = "11999999999",
        farmId = 20L,
        firstAccess = false,
        photoUrl = photoUrl
    )

    private fun request(name: String) = FarmOwnerUpdateRequest(
        name = name,
        email = "novo@example.com",
        telephone = "11888888888"
    )
}
