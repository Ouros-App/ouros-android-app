package com.ouros.app.data.auth

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {

    @POST("v1/auth/login/start")
    suspend fun startLogin(
        @Body request: LoginStartRequest
    ): Response<LoginStartResponse>

    @POST("v1/auth/login/verify")
    suspend fun verifyLogin(
        @Body request: LoginVerifyRequest
    ): Response<TokenResponse>

    @POST("v1/auth/token/refresh")
    suspend fun refreshToken(
        @Body request: RefreshRequest
    ): Response<TokenResponse>

    @POST("v1/auth/password/reset/start")
    suspend fun startPasswordReset(
        @Body request: PasswordResetStartRequest
    ): Response<PasswordResetStartResponse>

    @POST("v1/auth/password/reset/verify")
    suspend fun verifyPasswordReset(
        @Body request: PasswordResetVerifyRequest
    ): Response<PasswordResetVerifyResponse>

    @POST("v1/auth/password/reset/confirm")
    suspend fun confirmPasswordReset(
        @Body request: PasswordResetConfirmRequest
    ): Response<PasswordResetConfirmResponse>
}