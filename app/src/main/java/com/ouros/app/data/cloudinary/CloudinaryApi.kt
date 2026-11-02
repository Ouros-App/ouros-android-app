package com.ouros.app.data.cloudinary

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

/**
 * Contrato Retrofit de cloudinary. Declara rotas, parâmetros e formatos HTTP esperados; a interpretação das respostas fica no repositório.
 */
interface CloudinaryApi {

    @Multipart
    @POST("v1_1/{cloudName}/image/upload")
    suspend fun uploadImage(
        @Path("cloudName")
        cloudName: String,

        @Part
        file: MultipartBody.Part,

        @Part("upload_preset")
        uploadPreset: RequestBody,

        @Part("folder")
        folder: RequestBody? = null
    ): Response<CloudinaryUploadResponse>
}
