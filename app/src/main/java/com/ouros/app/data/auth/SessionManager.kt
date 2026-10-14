package com.ouros.app.data.auth

class SessionManager {

    @Volatile
    private var accessToken: String? = null

    @Volatile
    private var accessTokenExpiresAt: Long = 0L

    fun setAccessToken(token: String, expiresInSeconds: Long) {
        accessToken = token
        accessTokenExpiresAt = System.currentTimeMillis() + expiresInSeconds * 1_000L
    }

    fun accessToken(): String? {
        val token = accessToken ?: return null

        return if (hasValidAccessToken()) {
            token

        } else {
            null
        }
    }

    fun hasValidAccessToken(): Boolean {
        return accessToken != null && System.currentTimeMillis() < accessTokenExpiresAt - SAFETY_MARGIN_MILLIS
    }

    fun clear() {
        accessToken = null
        accessTokenExpiresAt = 0L
    }

    private companion object {
        const val SAFETY_MARGIN_MILLIS = 30_000L
    }
}