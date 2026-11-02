package com.ouros.app.data.chatbot

import android.content.Context
import java.util.UUID

/**
 * Armazenamento local de chatbot. Centraliza leitura e gravação para que telas não manipulem diretamente os detalhes do armazenamento.
 */
internal class ChatbotSessionStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    @Synchronized
    fun threadId(ownerId: Long?): String {
        val key = ownerId?.let { "thread_$it" } ?: ANONYMOUS_THREAD
        return preferences.getString(key, null) ?: defaultThreadId(ownerId).also {
            preferences.edit().putString(key, it).apply()
        }
    }

    fun saveThreadId(ownerId: Long?, threadId: String) {
        if (threadId.isBlank()) return
        val key = ownerId?.let { "thread_$it" } ?: ANONYMOUS_THREAD
        preferences.edit().putString(key, threadId).apply()
    }

    private fun defaultThreadId(ownerId: Long?) =
        ownerId?.let { "ouros_thread_user_$it" } ?: UUID.randomUUID().toString()

    private companion object {
        const val PREFERENCES_NAME = "ouros_chatbot_session"
        const val ANONYMOUS_THREAD = "thread_anonymous"
    }
}
