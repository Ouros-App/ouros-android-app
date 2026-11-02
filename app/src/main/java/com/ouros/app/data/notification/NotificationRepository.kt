package com.ouros.app.data.notification

import java.io.IOException
import com.ouros.app.data.local.OfflineStore
import com.ouros.app.data.local.OfflineSynchronizer
import com.ouros.app.data.local.OfflineSyncRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response

/**
 * Acesso a dados de notification. Encapsula chamadas de rede e persistência local e traduz respostas para que a tela não dependa do transporte.
 */
class NotificationRepository(
    private val api: NotificationApi = NotificationNetwork.api,
    private val enableMockFallback: Boolean = false,
    private val offlineStore: OfflineStore? = null,
    private val ownerIdProvider: () -> Long? = { null },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : OfflineSynchronizer {

    init {
        if (offlineStore != null) OfflineSyncRegistry.register(this)
    }

    suspend fun registerDeviceToken(
        token: String,
        platform: String = "ANDROID"
    ): Result<Unit> = withContext(ioDispatcher) {
        safeCall {
            val response = callApiWithFallback {
                api.registerDeviceToken(DeviceTokenRequest(token = token, platform = platform))
            }
            resolveAnyResponse(response, "Falha ao registrar token")
        }
    }

    suspend fun deleteDeviceToken(token: String): Result<Unit> = withContext(ioDispatcher) {
        safeCall {
            val response = callApiWithFallback { api.deleteDeviceToken(token) }
            resolveAnyResponse(response, "Falha ao remover token")
        }
    }

    suspend fun getNotifications(
        page: Int = 0,
        size: Int = 20,
        unreadOnly: Boolean? = null
    ): Result<NotificationPageResponse> = withContext(ioDispatcher) {
        safeCall {
            val response = callApiOrNull {
                api.getNotifications(page = page, size = size, unreadOnly = unreadOnly)
            }
            val body = response?.body()
            when {
                response?.isSuccessful == true && body != null -> {
                    offlineStore?.cache(cacheKey(), body)
                    Result.success(body)
                }
                cachedPage() != null -> Result.success(cachedPage()!!)
                enableMockFallback -> Result.success(createMockPageResponse())
                response == null -> Result.failure(Exception(OFFLINE_MESSAGE))
                else -> Result.failure(Exception("Falha ao carregar notificações: HTTP ${response?.code()}"))
            }
        }
    }

    suspend fun getUnreadCount(): Result<Int> = withContext(ioDispatcher) {
        safeCall {
            val response = callApiOrNull { api.getUnreadCount() }
            val body = response?.body()
            when {
                response?.isSuccessful == true && body != null -> Result.success(body.count)
                cachedPage() != null -> Result.success(cachedPage()!!.content.count { !it.isRead })
                enableMockFallback -> Result.success(mockNotifications.count { !it.isRead })
                response == null -> Result.failure(Exception(OFFLINE_MESSAGE))
                else -> Result.failure(Exception("Falha ao obter contador de não lidas: HTTP ${response?.code()}"))
            }
        }
    }

    suspend fun markAsRead(id: Long): Result<NotificationItemResponse?> = withContext(ioDispatcher) {
        safeCall {
            val response = callApiOrNull { api.markAsRead(id) }
            when {
                response?.isSuccessful == true -> {
                    updateCachedItemReadStatus(id)
                    Result.success(response.body())
                }
                response == null && offlineStore != null -> {
                    val cachedItem = updateCachedItemReadStatus(id)
                    offlineStore.enqueueReplacing(
                        area = pendingArea(),
                        operation = MARK_READ,
                        entityKey = id.toString(),
                        value = PendingNotificationOperation(id)
                    )
                    Result.success(cachedItem)
                }
                enableMockFallback -> {
                    updateMockItemReadStatus(id)
                    Result.success(mockNotifications.find { it.id == id })
                }
                else -> Result.failure(Exception("Falha ao marcar notificação como lida: HTTP ${response?.code()}"))
            }
        }
    }

    suspend fun markAllAsRead(): Result<Unit> = withContext(ioDispatcher) {
        safeCall {
            val response = callApiOrNull { api.markAllAsRead() }
            when {
                response?.isSuccessful == true -> {
                    updateAllCachedItemsReadStatus()
                    Result.success(Unit)
                }
                response == null && offlineStore != null -> {
                    updateAllCachedItemsReadStatus()
                    offlineStore.enqueueReplacing(
                        area = pendingArea(),
                        operation = MARK_ALL_READ,
                        entityKey = "all",
                        value = PendingNotificationOperation()
                    )
                    Result.success(Unit)
                }
                enableMockFallback -> {
                    updateAllMockItemsReadStatus()
                    Result.success(Unit)
                }
                else -> Result.failure(Exception("Falha ao marcar todas como lidas: HTTP ${response?.code()}"))
            }
        }
    }

    override suspend fun synchronize() = withContext(ioDispatcher) {
        val store = offlineStore ?: return@withContext
        for (operation in store.pending(pendingArea())) {
            val successful = try {
                when (operation.operation) {
                    MARK_READ -> {
                        val pending = store.decode(
                            operation.payload,
                            PendingNotificationOperation::class.java
                        )
                        val id = pending.id
                        if (id == null) {
                            store.complete(operation.id)
                            continue
                        }
                        api.markAsRead(id).isSuccessful
                    }
                    MARK_ALL_READ -> api.markAllAsRead().isSuccessful
                    else -> true
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                false
            }
            if (!successful) return@withContext
            store.complete(operation.id)
        }
    }

    private suspend fun <T> callApiOrNull(call: suspend () -> Response<T>): Response<T>? =
        try {
            call()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: IOException) {
            null
        }

    private fun cachedPage(): NotificationPageResponse? = offlineStore?.cached(cacheKey())

    private fun cacheKey() = "$CACHE_KEY:${ownerIdProvider() ?: "anonymous"}"

    private fun pendingArea() = "$PENDING_AREA:${ownerIdProvider() ?: "anonymous"}"

    private fun updateCachedItemReadStatus(id: Long): NotificationItemResponse? {
        val page = cachedPage() ?: return null
        val updated = page.content.map { item ->
            if (item.id == id) item.copy(isRead = true) else item
        }
        offlineStore?.cache(cacheKey(), page.copy(content = updated))
        return updated.find { it.id == id }
    }

    private fun updateAllCachedItemsReadStatus() {
        val page = cachedPage() ?: return
        offlineStore?.cache(
            cacheKey(),
            page.copy(content = page.content.map { it.copy(isRead = true) })
        )
    }

    private suspend fun <T> callApiWithFallback(call: suspend () -> Response<T>): Response<T>? {
        return try {
            call()
        } catch (e: Exception) {
            if (enableMockFallback) null else throw e
        }
    }

    private fun <T> resolveAnyResponse(
        response: Response<T>?,
        errorMessage: String
    ): Result<Unit> {
        return when {
            response?.isSuccessful == true -> Result.success(Unit)
            enableMockFallback -> Result.success(Unit)
            else -> Result.failure(Exception("$errorMessage: HTTP ${response?.code()}"))
        }
    }

    private fun updateMockItemReadStatus(id: Long) {
        val index = mockNotifications.indexOfFirst { it.id == id }
        if (index != -1) {
            mockNotifications[index] = mockNotifications[index].copy(isRead = true)
        }
    }

    private fun updateAllMockItemsReadStatus() {
        mockNotifications.indices.forEach { index ->
            mockNotifications[index] = mockNotifications[index].copy(isRead = true)
        }
    }

    private fun createMockPageResponse(): NotificationPageResponse {
        return NotificationPageResponse(
            content = mockNotifications.toList(),
            totalElements = mockNotifications.size.toLong(),
            totalPages = 1,
            number = 0,
            last = true
        )
    }

    private inline fun <T> safeCall(block: () -> Result<T>): Result<T> {
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Result.failure(Exception("Sem conexão com o servidor. Verifique sua internet."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        private const val CACHE_KEY = "notifications:latest"
        private const val PENDING_AREA = "notifications"
        private const val MARK_READ = "mark-read"
        private const val MARK_ALL_READ = "mark-all-read"
        private const val OFFLINE_MESSAGE = "Sem conexão. Exibindo apenas notificações salvas neste aparelho."

        private data class PendingNotificationOperation(val id: Long? = null)

        private val mockNotifications = mutableListOf(
            NotificationItemResponse(
                id = 1,
                title = "Piiuu",
                message = "O midas está com saudade!!",
                type = "MIDAS_INFO",
                isRead = false
            ),
            NotificationItemResponse(
                id = 2,
                title = "Oiiiiiiiiiii, é o Midas!",
                message = "Você já testou o nosso incrível simulador?",
                type = "MIDAS_INFO",
                isRead = false
            ),
            NotificationItemResponse(
                id = 3,
                title = "Você viu?",
                message = "Daqui a pouco o novo ranking sai!",
                type = "RANK_UPDATE",
                isRead = false
            ),
            NotificationItemResponse(
                id = 4,
                title = "Toma cuidado,",
                message = "os seus pintos estão com sede!",
                type = "ALERT_WATER",
                idTip = 1L,
                isRead = false
            ),
            NotificationItemResponse(
                id = 5,
                title = "Dê uma olhada,",
                message = "a nova posição do ranking saiu",
                type = "RANK_UPDATE",
                isRead = true
            ),
            NotificationItemResponse(
                id = 6,
                title = "Eii,",
                message = "Uma nova tarefa foi atribuída a sua granja",
                type = "GOAL_ASSIGNED",
                isRead = true
            ),
            NotificationItemResponse(
                id = 7,
                title = "Vishh",
                message = "vai alimentar os seus pintos direito!",
                type = "ALERT_FEED",
                idTip = 1L,
                isRead = true
            )
        )
    }
}
