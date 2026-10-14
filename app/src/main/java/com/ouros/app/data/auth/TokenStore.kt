package com.ouros.app.data.auth

import android.content.Context

class TokenStore(
    context: Context
) {

    private val preferences =
        context.applicationContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )

    fun save(
        token: TokenResponse
    ) {
        val editor = preferences.edit()
            .putString(
                KEY_ACCESS_TOKEN,
                token.accessToken
            )
            .putString(
                KEY_TOKEN_TYPE,
                token.tokenType.ifBlank {
                    DEFAULT_TOKEN_TYPE
                }
            )
            .putLong(
                KEY_ACCESS_EXPIRES_AT,
                System.currentTimeMillis() +
                        token.expiresIn * 1_000L
            )

        token.refreshToken
            ?.takeIf(String::isNotBlank)
            ?.let {
                editor.putString(
                    KEY_REFRESH_TOKEN,
                    it
                )
            }

        editor.apply()
    }

    fun accessToken(): String? {
        return preferences.getString(
            KEY_ACCESS_TOKEN,
            null
        )
    }

    fun refreshToken(): String? {
        return preferences.getString(
            KEY_REFRESH_TOKEN,
            null
        )
    }

    fun authorizationHeader(): String? {
        val accessToken =
            accessToken() ?: return null

        val tokenType =
            preferences.getString(
                KEY_TOKEN_TYPE,
                DEFAULT_TOKEN_TYPE
            ) ?: DEFAULT_TOKEN_TYPE

        return "$tokenType $accessToken"
    }

    /*
     * Mantido para compatibilidade caso algum código antigo
     * esteja chamando authorization().
     */
    fun authorization(): String? {
        return authorizationHeader()
    }

    fun hasValidAccessToken(): Boolean {
        val expiresAt =
            preferences.getLong(
                KEY_ACCESS_EXPIRES_AT,
                0L
            )

        return !accessToken().isNullOrBlank() &&
                System.currentTimeMillis() <
                expiresAt - EXPIRATION_MARGIN_MILLIS
    }

    fun clearAccessToken() {
        preferences.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_TOKEN_TYPE)
            .remove(KEY_ACCESS_EXPIRES_AT)
            .apply()
    }

    fun clear() {
        preferences.edit()
            .clear()
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME =
            "ouros_auth"

        const val KEY_ACCESS_TOKEN =
            "access_token"

        const val KEY_REFRESH_TOKEN =
            "refresh_token"

        const val KEY_TOKEN_TYPE =
            "token_type"

        const val KEY_ACCESS_EXPIRES_AT =
            "access_token_expires_at"

        const val DEFAULT_TOKEN_TYPE =
            "Bearer"

        const val EXPIRATION_MARGIN_MILLIS =
            30_000L
    }
}