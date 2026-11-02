package com.ouros.app.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** SQLite storage for server snapshots and durable outbound operations. */
class OurosOfflineDatabase(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    override fun onCreate(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE cache_entries (
                cache_key TEXT PRIMARY KEY NOT NULL,
                payload TEXT NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE TABLE pending_operations (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                area TEXT NOT NULL,
                operation TEXT NOT NULL,
                entity_key TEXT,
                payload TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                attempts INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        database.execSQL(
            "CREATE INDEX pending_operations_area_idx ON pending_operations(area, id)"
        )
        database.execSQL(
            """
            CREATE TABLE id_mappings (
                area TEXT NOT NULL,
                local_id INTEGER NOT NULL,
                remote_id INTEGER NOT NULL,
                PRIMARY KEY (area, local_id)
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun putCache(key: String, payload: String) {
        writableDatabase.insertWithOnConflict(
            "cache_entries",
            null,
            ContentValues().apply {
                put("cache_key", key)
                put("payload", payload)
                put("updated_at", System.currentTimeMillis())
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun getCache(key: String): String? = readableDatabase.query(
        "cache_entries",
        arrayOf("payload"),
        "cache_key = ?",
        arrayOf(key),
        null,
        null,
        null
    ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    fun enqueue(area: String, operation: String, entityKey: String?, payload: String): Long =
        writableDatabase.insertOrThrow(
            "pending_operations",
            null,
            ContentValues().apply {
                put("area", area)
                put("operation", operation)
                put("entity_key", entityKey)
                put("payload", payload)
                put("created_at", System.currentTimeMillis())
            }
        )

    fun enqueueReplacing(area: String, operation: String, entityKey: String, payload: String): Long {
        writableDatabase.delete(
            "pending_operations",
            "area = ? AND operation = ? AND entity_key = ?",
            arrayOf(area, operation, entityKey)
        )
        return enqueue(area, operation, entityKey, payload)
    }

    fun pending(area: String): List<PendingOperation> = readableDatabase.query(
        "pending_operations",
        arrayOf("id", "operation", "entity_key", "payload", "attempts"),
        "area = ?",
        arrayOf(area),
        null,
        null,
        "id ASC"
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    PendingOperation(
                        id = cursor.getLong(0),
                        operation = cursor.getString(1),
                        entityKey = cursor.getString(2),
                        payload = cursor.getString(3),
                        attempts = cursor.getInt(4)
                    )
                )
            }
        }
    }

    fun complete(id: Long) {
        writableDatabase.delete("pending_operations", "id = ?", arrayOf(id.toString()))
    }

    fun markAttempt(id: Long) {
        writableDatabase.execSQL(
            "UPDATE pending_operations SET attempts = attempts + 1 WHERE id = ?",
            arrayOf(id)
        )
    }

    fun saveMapping(area: String, localId: Long, remoteId: Long) {
        writableDatabase.insertWithOnConflict(
            "id_mappings",
            null,
            ContentValues().apply {
                put("area", area)
                put("local_id", localId)
                put("remote_id", remoteId)
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun mappedId(area: String, localId: Long): Long? = readableDatabase.query(
        "id_mappings",
        arrayOf("remote_id"),
        "area = ? AND local_id = ?",
        arrayOf(area, localId.toString()),
        null,
        null,
        null
    ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }

    data class PendingOperation(
        val id: Long,
        val operation: String,
        val entityKey: String?,
        val payload: String,
        val attempts: Int
    )

    private companion object {
        const val DATABASE_NAME = "ouros_offline.db"
        const val DATABASE_VERSION = 1
    }
}
