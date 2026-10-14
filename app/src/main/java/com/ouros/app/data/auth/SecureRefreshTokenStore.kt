package com.ouros.app.data.auth

import android.content.Context
import android.util.Base64
import androidx.core.content.edit
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

class SecureRefreshTokenStore(context: Context) {

    private val preferences = context
        .applicationContext
        .getSharedPreferences(PREFERENCES_NAME,Context.MODE_PRIVATE)

    fun saveRefreshToken(token: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(token.toByteArray(StandardCharsets.UTF_8))

        preferences.edit {
            putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            putString(KEY_VALUE, Base64.encodeToString(encrypted, Base64.NO_WRAP))
        }
    }

    fun getRefreshToken(): String? {
        val encodedIv = preferences.getString(KEY_IV, null)
            ?: return null

        val encodedValue = preferences.getString(KEY_VALUE, null)
            ?: return null

        return try {
            val iv = Base64.decode(encodedIv, Base64.NO_WRAP)
            val encrypted = Base64.decode(encodedValue, Base64.NO_WRAP)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            )

            String(
                cipher.doFinal(encrypted),
                StandardCharsets.UTF_8
            )

        } catch (_: Exception) {
            clear()
            null
        }
    }

    fun clear() {
        preferences.edit { clear() }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        val existing = keyStore.getKey(KEY_ALIAS, null)
        if (existing is SecretKey) {
            return existing
        }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or
                           KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false)
                .build()
        )

        return generator.generateKey()
    }

    private companion object {
        const val PREFERENCES_NAME = "ouros_secure_auth"
        const val KEY_ALIAS = "ouros_refresh_token_key"
        const val KEY_IV = "refresh_token_iv"
        const val KEY_VALUE = "refresh_token_value"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
    }
}