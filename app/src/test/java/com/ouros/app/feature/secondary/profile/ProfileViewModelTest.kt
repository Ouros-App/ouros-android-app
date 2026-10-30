package com.ouros.app.feature.secondary.profile

import com.ouros.app.data.auth.AuthResult
import com.ouros.app.data.auth.SessionManager
import com.ouros.app.data.cloudinary.CloudinaryApi
import com.ouros.app.data.cloudinary.CloudinaryRepository
import com.ouros.app.data.cloudinary.CloudinaryUploadResponse
import com.ouros.app.data.farmowner.FarmOwnerApi
import com.ouros.app.data.farmowner.FarmOwnerRepository
import com.ouros.app.data.farmowner.FarmOwnerResponse
import com.ouros.app.data.farmowner.FarmOwnerUpdateRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MultipartBody
import okhttp3.RequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.Response
import java.io.File
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun saveProfile_withNewPhoto_uploadsToCloudinaryAndSavesSecureUrl() = runTest {
        val photoFile = temporaryFolder.newFile("avatar.jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }

        var uploadedFile: MultipartBody.Part? = null
        val fakeCloudinaryApi = object : CloudinaryApi {
            override suspend fun uploadImage(
                cloudName: String,
                file: MultipartBody.Part,
                uploadPreset: RequestBody,
                folder: RequestBody?
            ): Response<CloudinaryUploadResponse> {
                uploadedFile = file
                return Response.success(
                    CloudinaryUploadResponse(
                        secureUrl = "https://res.cloudinary.com/test_cloud/image/upload/v1/ouros/profiles/avatar.jpg",
                        publicId = "ouros/profiles/avatar"
                    )
                )
            }
        }
        val cloudinaryRepository = CloudinaryRepository(api = fakeCloudinaryApi)

        var capturedUpdateRequest: FarmOwnerUpdateRequest? = null
        val fakeFarmOwnerApi = object : FarmOwnerApi {
            override suspend fun getMe(authorization: String): Response<FarmOwnerResponse> {
                return Response.success(sampleProfile(photoUrl = null))
            }

            override suspend fun update(
                id: Long,
                authorization: String,
                request: FarmOwnerUpdateRequest
            ): Response<FarmOwnerResponse> {
                capturedUpdateRequest = request
                return Response.success(
                    sampleProfile(photoUrl = request.photoUrl)
                )
            }
        }

        val sessionManager = SessionManager().apply {
            setAccessToken("valid-token", expiresInSeconds = 3600)
        }

        val farmOwnerRepository = FarmOwnerRepository(
            api = fakeFarmOwnerApi,
            sessionManager = sessionManager,
            refreshSession = { error("Not needed") },
            clearSession = {}
        )

        val viewModel = ProfileViewModel(
            farmOwnerRepository = farmOwnerRepository,
            cloudinaryRepository = cloudinaryRepository
        )

        val listener = TestProfileListener()
        viewModel.setListener(listener)
        viewModel.loadProfile()

        viewModel.saveProfile(
            name = "Produtor Rural",
            email = "produtor@fazenda.com",
            telephone = "11987654321",
            photoChange = ProfilePhotoChange.UploadNew(photoFile)
        )

        assertEquals(
            "https://res.cloudinary.com/test_cloud/image/upload/v1/ouros/profiles/avatar.jpg",
            capturedUpdateRequest?.photoUrl
        )
        assertNull(capturedUpdateRequest?.documentNumber)
        assertNull(capturedUpdateRequest?.password)
        assertEquals(
            "https://res.cloudinary.com/test_cloud/image/upload/v1/ouros/profiles/avatar.jpg",
            listener.savedProfile?.photoUrl
        )
        assertNull(listener.errorMessage)
    }

    @Test
    fun saveProfile_whenCloudinaryUploadFails_abortsProfileUpdateAndReportsError() = runTest {
        val photoFile = temporaryFolder.newFile("avatar.jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }

        val fakeCloudinaryApi = object : CloudinaryApi {
            override suspend fun uploadImage(
                cloudName: String,
                file: MultipartBody.Part,
                uploadPreset: RequestBody,
                folder: RequestBody?
            ): Response<CloudinaryUploadResponse> {
                throw IOException("Network failure")
            }
        }
        val cloudinaryRepository = CloudinaryRepository(api = fakeCloudinaryApi)

        var updateCalled = false
        val fakeFarmOwnerApi = object : FarmOwnerApi {
            override suspend fun getMe(authorization: String): Response<FarmOwnerResponse> {
                return Response.success(sampleProfile(photoUrl = null))
            }

            override suspend fun update(
                id: Long,
                authorization: String,
                request: FarmOwnerUpdateRequest
            ): Response<FarmOwnerResponse> {
                updateCalled = true
                return Response.success(sampleProfile())
            }
        }

        val sessionManager = SessionManager().apply {
            setAccessToken("valid-token", expiresInSeconds = 3600)
        }
        val farmOwnerRepository = FarmOwnerRepository(
            api = fakeFarmOwnerApi,
            sessionManager = sessionManager,
            refreshSession = { error("Not needed") },
            clearSession = {}
        )

        val viewModel = ProfileViewModel(
            farmOwnerRepository = farmOwnerRepository,
            cloudinaryRepository = cloudinaryRepository
        )

        val listener = TestProfileListener()
        viewModel.setListener(listener)
        viewModel.loadProfile()

        viewModel.saveProfile(
            name = "Produtor Rural",
            email = "produtor@fazenda.com",
            telephone = "11987654321",
            photoChange = ProfilePhotoChange.UploadNew(photoFile)
        )

        assertEquals(false, updateCalled)
        assertEquals(
            "Sem conexão ao enviar foto. Verifique sua internet.",
            listener.errorMessage
        )
        assertNull(listener.savedProfile)
    }

    @Test
    fun saveProfile_withRemovePhoto_sendsEmptyStringPhotoUrl() = runTest {
        val fakeCloudinaryApi = object : CloudinaryApi {
            override suspend fun uploadImage(
                cloudName: String,
                file: MultipartBody.Part,
                uploadPreset: RequestBody,
                folder: RequestBody?
            ): Response<CloudinaryUploadResponse> = error("Cloudinary should not be called")
        }
        val cloudinaryRepository = CloudinaryRepository(api = fakeCloudinaryApi)

        var capturedUpdateRequest: FarmOwnerUpdateRequest? = null
        val fakeFarmOwnerApi = object : FarmOwnerApi {
            override suspend fun getMe(authorization: String): Response<FarmOwnerResponse> {
                return Response.success(sampleProfile(photoUrl = "https://existing.com/photo.jpg"))
            }

            override suspend fun update(
                id: Long,
                authorization: String,
                request: FarmOwnerUpdateRequest
            ): Response<FarmOwnerResponse> {
                capturedUpdateRequest = request
                return Response.success(sampleProfile(photoUrl = request.photoUrl))
            }
        }

        val sessionManager = SessionManager().apply {
            setAccessToken("valid-token", expiresInSeconds = 3600)
        }
        val farmOwnerRepository = FarmOwnerRepository(
            api = fakeFarmOwnerApi,
            sessionManager = sessionManager,
            refreshSession = { error("Not needed") },
            clearSession = {}
        )

        val viewModel = ProfileViewModel(
            farmOwnerRepository = farmOwnerRepository,
            cloudinaryRepository = cloudinaryRepository
        )

        val listener = TestProfileListener()
        viewModel.setListener(listener)
        viewModel.loadProfile()

        viewModel.saveProfile(
            name = "Produtor Rural",
            email = "produtor@fazenda.com",
            telephone = "11987654321",
            photoChange = ProfilePhotoChange.Remove
        )

        assertEquals("", capturedUpdateRequest?.photoUrl)
        assertEquals("", listener.savedProfile?.photoUrl)
    }

    @Test
    fun saveProfile_withKeepCurrent_preservesExistingPhotoUrl() = runTest {
        val fakeCloudinaryApi = object : CloudinaryApi {
            override suspend fun uploadImage(
                cloudName: String,
                file: MultipartBody.Part,
                uploadPreset: RequestBody,
                folder: RequestBody?
            ): Response<CloudinaryUploadResponse> = error("Cloudinary should not be called")
        }
        val cloudinaryRepository = CloudinaryRepository(api = fakeCloudinaryApi)

        var capturedUpdateRequest: FarmOwnerUpdateRequest? = null
        val fakeFarmOwnerApi = object : FarmOwnerApi {
            override suspend fun getMe(authorization: String): Response<FarmOwnerResponse> {
                return Response.success(sampleProfile(photoUrl = "https://existing.com/photo.jpg"))
            }

            override suspend fun update(
                id: Long,
                authorization: String,
                request: FarmOwnerUpdateRequest
            ): Response<FarmOwnerResponse> {
                capturedUpdateRequest = request
                return Response.success(sampleProfile(photoUrl = request.photoUrl))
            }
        }

        val sessionManager = SessionManager().apply {
            setAccessToken("valid-token", expiresInSeconds = 3600)
        }
        val farmOwnerRepository = FarmOwnerRepository(
            api = fakeFarmOwnerApi,
            sessionManager = sessionManager,
            refreshSession = { error("Not needed") },
            clearSession = {}
        )

        val viewModel = ProfileViewModel(
            farmOwnerRepository = farmOwnerRepository,
            cloudinaryRepository = cloudinaryRepository
        )

        val listener = TestProfileListener()
        viewModel.setListener(listener)
        viewModel.loadProfile()

        viewModel.saveProfile(
            name = "Produtor Rural",
            email = "produtor@fazenda.com",
            telephone = "11987654321",
            photoChange = ProfilePhotoChange.KeepCurrent
        )

        assertEquals("https://existing.com/photo.jpg", capturedUpdateRequest?.photoUrl)
        assertEquals("https://existing.com/photo.jpg", listener.savedProfile?.photoUrl)
    }

    private fun sampleProfile(photoUrl: String? = null) = FarmOwnerResponse(
        id = 100L,
        name = "Produtor Rural",
        documentNumber = "12345678901",
        email = "produtor@fazenda.com",
        telephone = "11987654321",
        farmId = 1L,
        firstAccess = false,
        photoUrl = photoUrl
    )

    private class TestProfileListener : ProfileViewModel.Listener {
        var isLoading: Boolean = false
        var loadedProfile: FarmOwnerResponse? = null
        var savedProfile: FarmOwnerResponse? = null
        var errorMessage: String? = null
        var sessionExpired: Boolean = false

        override fun onProfileLoadingChanged(isLoading: Boolean) {
            this.isLoading = isLoading
        }

        override fun onProfileLoaded(profile: FarmOwnerResponse) {
            this.loadedProfile = profile
        }

        override fun onProfileErrors(
            nameMessage: String?,
            emailMessage: String?,
            telephoneMessage: String?
        ) = Unit

        override fun onProfileSaved(profile: FarmOwnerResponse) {
            this.savedProfile = profile
        }

        override fun onProfileError(message: String) {
            this.errorMessage = message
        }

        override fun onProfileSessionExpired() {
            this.sessionExpired = true
        }
    }
}
