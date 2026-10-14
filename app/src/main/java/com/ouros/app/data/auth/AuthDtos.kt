package com.ouros.app.data.auth

import com.google.gson.annotations.SerializedName

data class LoginStartRequest(
    val email: String,
    val password: String
)

data class LoginStartResponse(
    @SerializedName("challenge_id")
    val challengeId: String,

    @SerializedName("expires_in")
    val expiresIn: Long,

    @SerializedName("masked_email")
    val maskedEmail: String
)

data class LoginVerifyRequest(
    @SerializedName("challenge_id")
    val challengeId: String,

    val email: String,
    val password: String,
    val code: String
)

data class RefreshRequest(
    @SerializedName("refresh_token")
    val refreshToken: String
)

data class TokenResponse(
    @SerializedName("access_token")
    val accessToken: String,

    @SerializedName("expires_in")
    val expiresIn: Long,

    @SerializedName("refresh_expires_in")
    val refreshExpiresIn: Long?,

    @SerializedName("refresh_token")
    val refreshToken: String?,

    @SerializedName("token_type")
    val tokenType: String,

    val scope: String?
)

data class PendingLogin(
    val email: String,
    val password: String,
    val challengeId: String,
    val maskedEmail: String,
    val expiresAtMillis: Long
)

data class PasswordResetStartRequest(
    val email: String
)

data class PasswordResetStartResponse(
    @SerializedName("challenge_id")
    val challengeId: String,

    @SerializedName("masked_email")
    val maskedEmail: String,

    @SerializedName("expires_in")
    val expiresIn: Long
)

data class PasswordResetVerifyRequest(
    @SerializedName("challenge_id")
    val challengeId: String,

    val email: String,
    val code: String
)

data class PasswordResetVerifyResponse(
    @SerializedName("reset_token")
    val resetToken: String,

    @SerializedName("expires_in")
    val expiresIn: Long
)

data class PasswordResetConfirmRequest(
    @SerializedName("reset_token")
    val resetToken: String,

    @SerializedName("new_password")
    val newPassword: String
)

data class PasswordResetConfirmResponse(
    val message: String
)

data class ApiErrorResponse(
    val detail: String?
)