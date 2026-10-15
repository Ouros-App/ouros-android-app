package com.ouros.app.data.cloudinary

import com.google.gson.Gson
import com.ouros.app.BuildConfig
import kotlinx.coroutines.CancellationException
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import java.io.File
import java.io.IOException

class CloudinaryRepository(
    private val api: CloudinaryApi = CloudinaryNetwork.api,
    private val cloudName: String = BuildConfig.CLOUDINARY_CLOUD_NAME,
    private val uploadPreset: String = BuildConfig.CLOUDINARY_UPLOAD_PRESET,
    private val folder: String = BuildConfig.CLOUDINARY_FOLDER,
    private val gson: Gson = Gson()
) {

    suspend fun uploadImage(file: File): Result<String> {
        if (!file.exists() || file.length() == 0L) {
            return Result.failure(IllegalArgumentException("Arquivo de imagem não encontrado ou vazio."))
        }

        return try {
            val mediaType = "image/jpeg".toMediaTypeOrNull()
            val requestFile = file.asRequestBody(mediaType)
            val filePart = MultipartBody.Part.createFormData("file", file.name, requestFile)
            val presetPart = uploadPreset.toRequestBody("text/plain".toMediaTypeOrNull())
            val folderPart = folder.takeIf { it.isNotBlank() }
                ?.toRequestBody("text/plain".toMediaTypeOrNull())

            val response = api.uploadImage(
                cloudName = cloudName,
                file = filePart,
                uploadPreset = presetPart,
                folder = folderPart
            )

            if (response.isSuccessful) {
                val body = response.body()
                val secureUrl = body?.secureUrl

                if (!secureUrl.isNullOrBlank()) {
                    Result.success(secureUrl)
                } else {
                    val message = body?.error?.message ?: "Falha ao obter URL da imagem enviada."
                    Result.failure(Exception(message))
                }
            } else {
                val errorMessage = extractErrorMessage(response)
                Result.failure(Exception(errorMessage))
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (ioe: IOException) {
            Result.failure(IOException("Sem conexão ao enviar foto. Verifique sua internet.", ioe))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Erro inesperado ao enviar foto.", e))
        }
    }

    private fun extractErrorMessage(response: Response<CloudinaryUploadResponse>): String {
        return runCatching {
            val errorBodyString = response.errorBody()?.string()
            if (!errorBodyString.isNullOrBlank()) {
                val errorResponse = gson.fromJson(errorBodyString, CloudinaryUploadResponse::class.java)
                errorResponse?.error?.message
            } else null
        }.getOrNull() ?: "Erro no upload da foto (código ${response.code()})."
    }
}
