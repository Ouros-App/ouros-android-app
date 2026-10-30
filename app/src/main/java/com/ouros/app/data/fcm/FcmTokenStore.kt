package com.ouros.app.data.fcm

import android.content.Context
import android.content.SharedPreferences

class FcmTokenStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("ouros_fcm_prefs", Context.MODE_PRIVATE)

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_FCM_TOKEN, token).apply()
    }

    fun getToken(): String? {
        return prefs.getString(KEY_FCM_TOKEN, null)
    }

    fun clear() {
        prefs.edit().remove(KEY_FCM_TOKEN).apply()
    }

    companion object {
        private const val KEY_FCM_TOKEN = "fcm_token"
    }
}
