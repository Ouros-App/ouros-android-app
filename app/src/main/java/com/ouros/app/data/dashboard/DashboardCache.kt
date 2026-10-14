package com.ouros.app.data.dashboard

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class DashboardCache(
    private val cacheDirectory: File,
    private val gson: Gson = Gson()
) {

    constructor(
        context: Context,
        gson: Gson = Gson()
    ) : this(
        context.applicationContext.filesDir,
        gson
    )

    init {
        File(cacheDirectory, LEGACY_CACHE_FILE_NAME).delete()
    }

    suspend fun save(
        farmId: Long,
        dashboard: DashboardResponse
    ) {
        withContext(Dispatchers.IO) {
            runCatching {
                val cacheFile = cacheFile(farmId)
                val temporaryFile =
                    File(
                        cacheDirectory,
                        "dashboard_cache_$farmId.tmp"
                    )

                temporaryFile.writeText(
                    gson.toJson(dashboard),
                    Charsets.UTF_8
                )

                if (cacheFile.exists()) {
                    cacheFile.delete()
                }

                if (!temporaryFile.renameTo(cacheFile)) {
                    cacheFile.writeText(
                        temporaryFile.readText(Charsets.UTF_8),
                        Charsets.UTF_8
                    )
                    temporaryFile.delete()
                }
            }
        }
    }

    suspend fun read(farmId: Long): DashboardResponse? {
        return withContext(Dispatchers.IO) {
            runCatching {
                val cacheFile = cacheFile(farmId)
                if (!cacheFile.exists()) {
                    return@runCatching null
                }

                gson.fromJson(
                    cacheFile.readText(Charsets.UTF_8),
                    DashboardResponse::class.java
                )
            }.getOrNull()
        }
    }

    private fun cacheFile(farmId: Long) =
        File(cacheDirectory, "dashboard_cache_$farmId.json")

    private companion object {
        const val LEGACY_CACHE_FILE_NAME = "dashboard_cache.json"
    }
}