package com.ouros.app.data.farmowner

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.Path

interface FarmOwnerApi {

    @GET("farm-owners/me")
    suspend fun getMe(
        @Header("Authorization")
        authorization: String
    ): Response<FarmOwnerResponse>

    @PATCH("farm-owners/{id}")
    suspend fun update(
        @Path("id")
        id: Long,

        @Header("Authorization")
        authorization: String,

        @Body
        request: FarmOwnerUpdateRequest
    ): Response<FarmOwnerResponse>
}