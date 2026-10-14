package com.ouros.app.data.session

import android.content.Context

class FarmSessionStore(context: Context) {

    private val preferences = context.applicationContext
        .getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )

    fun saveFarmId(farmId: Long) {
        preferences.edit()
            .putLong(KEY_FARM_ID, farmId)
            .apply()
    }

    fun farmId(): Long? {
        if (!preferences.contains(KEY_FARM_ID)) {
            return null
        }

        return preferences
            .getLong(KEY_FARM_ID, INVALID_FARM_ID)
            .takeIf { it > INVALID_FARM_ID }
    }

    fun clearFarmId() {
        preferences.edit()
            .remove(KEY_FARM_ID)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "ouros_farm_session"
        const val KEY_FARM_ID = "farm_id"
        const val INVALID_FARM_ID = 0L
    }
}