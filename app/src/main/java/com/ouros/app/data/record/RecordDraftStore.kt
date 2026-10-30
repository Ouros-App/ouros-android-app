package com.ouros.app.data.record

import android.content.Context
import com.google.gson.Gson

class RecordDraftStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )
    private val gson = Gson()

    fun load(): RecordDraft? {
        val json = preferences.getString(KEY_DRAFT, null) ?: return null
        return runCatching {
            gson.fromJson(json, RecordDraft::class.java)
        }.getOrNull()
    }

    fun save(draft: RecordDraft) {
        preferences.edit()
            .putString(
                KEY_DRAFT,
                gson.toJson(
                    draft.copy(
                        updatedAt = System.currentTimeMillis()
                    )
                )
            )
            .apply()
    }

    fun clear() {
        preferences.edit()
            .remove(KEY_DRAFT)
            .commit()
    }

    private companion object {
        const val PREFERENCES_NAME = "ouros_record_draft"
        const val KEY_DRAFT = "draft"
    }
}