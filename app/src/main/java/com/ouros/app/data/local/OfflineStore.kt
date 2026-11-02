package com.ouros.app.data.local

import android.content.Context
import com.google.gson.Gson
import java.lang.reflect.Type

class OfflineStore(context: Context) {
    private val database = OurosOfflineDatabase(context)
    private val gson = Gson()

    fun <T> cache(key: String, value: T) = database.putCache(key, gson.toJson(value))

    fun <T> cached(key: String, type: Type): T? = database.getCache(key)?.let {
        runCatching { gson.fromJson<T>(it, type) }.getOrNull()
    }

    inline fun <reified T> cached(key: String): T? = cached(key, object :
        com.google.gson.reflect.TypeToken<T>() {}.type)

    fun enqueue(area: String, operation: String, entityKey: String?, value: Any): Long =
        database.enqueue(area, operation, entityKey, gson.toJson(value))

    fun enqueueReplacing(area: String, operation: String, entityKey: String, value: Any): Long =
        database.enqueueReplacing(area, operation, entityKey, gson.toJson(value))

    fun pending(area: String): List<OurosOfflineDatabase.PendingOperation> = database.pending(area)

    fun <T> decode(payload: String, type: Class<T>): T = gson.fromJson(payload, type)

    fun complete(id: Long) = database.complete(id)

    fun markAttempt(id: Long) = database.markAttempt(id)

    fun saveMapping(area: String, localId: Long, remoteId: Long) =
        database.saveMapping(area, localId, remoteId)

    fun mappedId(area: String, localId: Long): Long? = database.mappedId(area, localId)
}
