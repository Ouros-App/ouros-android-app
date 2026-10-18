package com.ouros.app.data.auth

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthRefreshApi {

    @POST("v1/auth/token/refresh")
    fun refreshToken(
        @Body request: RefreshRequest
    ): Call<TokenResponse>
}