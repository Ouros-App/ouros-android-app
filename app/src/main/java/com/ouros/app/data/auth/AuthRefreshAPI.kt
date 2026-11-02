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

/**
 * Contrato Retrofit de auth. Declara rotas, parâmetros e formatos HTTP esperados; a interpretação das respostas fica no repositório.
 */
typealias AuthRefreshAPI = AuthRefreshApi
