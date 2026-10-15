package com.ouros.app.data.cloudinary

import kotlinx.coroutines.runBlocking
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.Response
import java.io.File
import java.io.IOException

class CloudinaryRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun uploadImage_success_returnsSecureUrl() = runBlocking {
        val testFile = temporaryFolder.newFile("photo.jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }

        var capturedCloudName: String? = null
        val fakeApi = object : CloudinaryApi {
            override suspend fun uploadImage(
                cloudName: String,
                file: MultipartBody.Part,
                uploadPreset: RequestBody,
                folder: RequestBody?
            ): Response<CloudinaryUploadResponse> {
                capturedCloudName = cloudName
                return Response.success(
                    CloudinaryUploadResponse(
                        secureUrl = "https://res.cloudinary.com/test_cloud/image/upload/v1234/ouros/profiles/sample.jpg",
                        publicId = "ouros/profiles/sample"
                    )
                )
            }
        }

        val repository = CloudinaryRepository(
            api = fakeApi,
            cloudName = "test_cloud",
            uploadPreset = "test_preset",
            folder = "ouros/profiles"
        )

        val result = repository.uploadImage(testFile)

        assertTrue(result.isSuccess)
        assertEquals("test_cloud", capturedCloudName)
        assertEquals(
            "https://res.cloudinary.com/test_cloud/image/upload/v1234/ouros/profiles/sample.jpg",
            result.getOrNull()
        )
    }

    @Test
    fun uploadImage_emptyOrMissingFile_returnsFailure() = runBlocking {
        val missingFile = File(temporaryFolder.root, "missing.jpg")
        val fakeApi = object : CloudinaryApi {
            override suspend fun uploadImage(
                cloudName: String,
                file: MultipartBody.Part,
                uploadPreset: RequestBody,
                folder: RequestBody?
            ): Response<CloudinaryUploadResponse> = error("Should not be called")
        }

        val repository = CloudinaryRepository(api = fakeApi)
        val result = repository.uploadImage(missingFile)

        assertTrue(result.isFailure)
    }

    @Test
    fun uploadImage_apiErrorResponse_returnsFailureWithParsedMessage() = runBlocking {
        val testFile = temporaryFolder.newFile("photo.jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }

        val fakeApi = object : CloudinaryApi {
            override suspend fun uploadImage(
                cloudName: String,
                file: MultipartBody.Part,
                uploadPreset: RequestBody,
                folder: RequestBody?
            ): Response<CloudinaryUploadResponse> {
                val errorJson = """{"error":{"message":"Upload preset not found"}}"""
                return Response.error(400, errorJson.toResponseBody())
            }
        }

        val repository = CloudinaryRepository(api = fakeApi)
        val result = repository.uploadImage(testFile)

        assertTrue(result.isFailure)
        assertEquals("Upload preset not found", result.exceptionOrNull()?.message)
    }

    @Test
    fun uploadImage_networkException_returnsFriendlyErrorMessage() = runBlocking {
        val testFile = temporaryFolder.newFile("photo.jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }

        val fakeApi = object : CloudinaryApi {
            override suspend fun uploadImage(
                cloudName: String,
                file: MultipartBody.Part,
                uploadPreset: RequestBody,
                folder: RequestBody?
            ): Response<CloudinaryUploadResponse> {
                throw IOException("Network timeout")
            }
        }

        val repository = CloudinaryRepository(api = fakeApi)
        val result = repository.uploadImage(testFile)

        assertTrue(result.isFailure)
        assertEquals("Sem conexão ao enviar foto. Verifique sua internet.", result.exceptionOrNull()?.message)
    }

    @Test
    fun uploadImage_malformedErrorResponse_usesStatusFallback() = runBlocking {
        val testFile = temporaryFolder.newFile("photo.jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }
        val fakeApi = object : CloudinaryApi {
            override suspend fun uploadImage(
                cloudName: String,
                file: MultipartBody.Part,
                uploadPreset: RequestBody,
                folder: RequestBody?
            ): Response<CloudinaryUploadResponse> =
                Response.error(502, "not-json".toResponseBody())
        }

        val result = CloudinaryRepository(api = fakeApi).uploadImage(testFile)

        assertTrue(result.isFailure)
        assertEquals("Erro no upload da foto (código 502).", result.exceptionOrNull()?.message)
    }

    @Test
    fun uploadImage_unexpectedExceptionWithoutMessage_usesFallback() = runBlocking {
        val testFile = temporaryFolder.newFile("photo.jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }
        val fakeApi = object : CloudinaryApi {
            override suspend fun uploadImage(
                cloudName: String,
                file: MultipartBody.Part,
                uploadPreset: RequestBody,
                folder: RequestBody?
            ): Response<CloudinaryUploadResponse> = throw Exception()
        }

        val result = CloudinaryRepository(api = fakeApi).uploadImage(testFile)

        assertTrue(result.isFailure)
        assertEquals("Erro inesperado ao enviar foto.", result.exceptionOrNull()?.message)
    }

    @Test
    fun uploadImage_cancellationIsPropagated() = runBlocking {
        val testFile = temporaryFolder.newFile("photo.jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }
        val cancellation = kotlinx.coroutines.CancellationException("cancelled")
        val fakeApi = object : CloudinaryApi {
            override suspend fun uploadImage(
                cloudName: String,
                file: MultipartBody.Part,
                uploadPreset: RequestBody,
                folder: RequestBody?
            ): Response<CloudinaryUploadResponse> = throw cancellation
        }

        try {
            CloudinaryRepository(api = fakeApi).uploadImage(testFile)
            throw AssertionError("Cancellation must propagate")
        } catch (actual: kotlinx.coroutines.CancellationException) {
            assertEquals(cancellation, actual)
        }
    }
}
