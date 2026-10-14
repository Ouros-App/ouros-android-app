package com.ouros.app.data.dashboard

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class DashboardCache(
    context: Context,
    private val gson: Gson = Gson()
) {

    private val cacheFile =
        File(
            context.applicationContext.filesDir,
            CACHE_FILE_NAME
        )

    suspend fun save(
        dashboard: DashboardResponse
    ) {
        withContext(Dispatchers.IO) {
            runCatching {
                val temporaryFile =
                    File(
                        cacheFile.parentFile,
                        TEMPORARY_FILE_NAME
                    )

                temporaryFile.writeText(
                    gson.toJson(dashboard),
                    Charsets.UTF_8
                )

                if (cacheFile.exists()) {
                    cacheFile.delete()
                }

                if (
                    !temporaryFile.renameTo(
                        cacheFile
                    )
                ) {
                    cacheFile.writeText(
                        temporaryFile.readText(
                            Charsets.UTF_8
                        ),
                        Charsets.UTF_8
                    )

                    temporaryFile.delete()
                }
            }
        }
    }

    suspend fun read():
            DashboardResponse? {
        return withContext(Dispatchers.IO) {
            runCatching {
                if (!cacheFile.exists()) {
                    return@runCatching null
                }

                gson.fromJson(
                    cacheFile.readText(
                        Charsets.UTF_8
                    ),
                    DashboardResponse::class.java
                )
            }.getOrNull()
        }
    }

    private companion object {
        const val CACHE_FILE_NAME =
            "dashboard_cache.json"

        const val TEMPORARY_FILE_NAME =
            "dashboard_cache.tmp"
    }
}