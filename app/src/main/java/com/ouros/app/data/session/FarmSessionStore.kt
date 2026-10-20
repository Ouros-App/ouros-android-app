package com.ouros.app.data.session

import android.content.Context
import android.content.SharedPreferences

open class FarmSessionStore(
    context: Context? = null,
    private val preferences: SharedPreferences? = context?.applicationContext
        ?.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )
) {

    open fun saveAccount(ownerId: Long?, farmId: Long?) {
        preferences?.edit()?.apply {
            if (ownerId != null) {
                putLong(KEY_OWNER_ID, ownerId)
            } else {
                remove(KEY_OWNER_ID)
            }

            if (farmId != null) {
                putLong(KEY_FARM_ID, farmId)
            } else {
                remove(KEY_FARM_ID)
            }
        }?.apply()
    }

    open fun ownerId(): Long? = storedId(KEY_OWNER_ID)

    open fun farmId(): Long? = storedId(KEY_FARM_ID)

    open fun clear() {
        preferences?.edit()
            ?.remove(KEY_OWNER_ID)
            ?.remove(KEY_FARM_ID)
            ?.apply()
    }

    private fun storedId(key: String): Long? {
        val prefs = preferences ?: return null
        if (!prefs.contains(key)) {
            return null
        }

        return prefs.getLong(key, INVALID_ID)
            .takeIf { it > INVALID_ID }
    }

    private companion object {
        const val PREFERENCES_NAME = "ouros_farm_session"
        const val KEY_OWNER_ID = "owner_id"
        const val KEY_FARM_ID = "farm_id"
        const val INVALID_ID = 0L
    }
}
