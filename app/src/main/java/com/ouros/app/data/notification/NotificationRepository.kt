package com.ouros.app.data.notification

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NotificationRepository(
    private val api: NotificationApi = NotificationNetwork.api,
    private val enableMockFallback: Boolean = true
) {

    suspend fun registerDeviceToken(
        token: String,
        platform: String = "ANDROID"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        safeCall {
            val response = try {
                api.registerDeviceToken(DeviceTokenRequest(token = token, platform = platform))
            } catch (e: Exception) {
                if (enableMockFallback) null else throw e
            }

            if (response != null && response.isSuccessful) {
                Result.success(Unit)
            } else if (enableMockFallback) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Falha ao registrar token: HTTP ${response?.code()}"))
            }
        }
    }

    suspend fun deleteDeviceToken(token: String): Result<Unit> = withContext(Dispatchers.IO) {
        safeCall {
            val response = try {
                api.deleteDeviceToken(token)
            } catch (e: Exception) {
                if (enableMockFallback) null else throw e
            }

            if (response != null && response.isSuccessful) {
                Result.success(Unit)
            } else if (enableMockFallback) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Falha ao remover token: HTTP ${response?.code()}"))
            }
        }
    }

    suspend fun getNotifications(
        page: Int = 0,
        size: Int = 20
    ): Result<NotificationPageResponse> = withContext(Dispatchers.IO) {
        safeCall {
            val response = try {
                api.getNotifications(page = page, size = size)
            } catch (e: Exception) {
                if (enableMockFallback) null else throw e
            }

            val body = response?.body()
            if (response != null && response.isSuccessful && body != null) {
                Result.success(body)
            } else if (enableMockFallback) {
                Result.success(
                    NotificationPageResponse(
                        content = mockNotifications.toList(),
                        totalElements = mockNotifications.size.toLong(),
                        totalPages = 1,
                        number = 0,
                        last = true
                    )
                )
            } else {
                Result.failure(Exception("Falha ao carregar notificações: HTTP ${response?.code()}"))
            }
        }
    }

    suspend fun getUnreadCount(): Result<Int> = withContext(Dispatchers.IO) {
        safeCall {
            val response = try {
                api.getUnreadCount()
            } catch (e: Exception) {
                if (enableMockFallback) null else throw e
            }

            val body = response?.body()
            if (response != null && response.isSuccessful && body != null) {
                Result.success(body.count)
            } else if (enableMockFallback) {
                Result.success(mockNotifications.count { !it.isRead })
            } else {
                Result.failure(Exception("Falha ao obter contador de não lidas: HTTP ${response?.code()}"))
            }
        }
    }

    suspend fun markAsRead(id: Long): Result<Unit> = withContext(Dispatchers.IO) {
        safeCall {
            val response = try {
                api.markAsRead(id)
            } catch (e: Exception) {
                if (enableMockFallback) null else throw e
            }

            val index = mockNotifications.indexOfFirst { it.id == id }
            if (index != -1) {
                mockNotifications[index] = mockNotifications[index].copy(isRead = true)
            }

            if (response != null && response.isSuccessful) {
                Result.success(Unit)
            } else if (enableMockFallback) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Falha ao marcar notificação como lida: HTTP ${response?.code()}"))
            }
        }
    }

    suspend fun markAllAsRead(): Result<Unit> = withContext(Dispatchers.IO) {
        safeCall {
            val response = try {
                api.markAllAsRead()
            } catch (e: Exception) {
                if (enableMockFallback) null else throw e
            }

            mockNotifications.indices.forEach { index ->
                mockNotifications[index] = mockNotifications[index].copy(isRead = true)
            }

            if (response != null && response.isSuccessful) {
                Result.success(Unit)
            } else if (enableMockFallback) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Falha ao marcar todas como lidas: HTTP ${response?.code()}"))
            }
        }
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
        private val mockNotifications = mutableListOf(
            NotificationItemResponse(
                id = 1,
                title = "Toma cuidado,",
                message = "os seus pintos estão com sede!",
                type = "ALERT_WATER",
                isRead = false,
                createdAt = "Hoje às 09:15"
            ),
            NotificationItemResponse(
                id = 2,
                title = "Vishh",
                message = "vai alimentar os seus pintos direito!",
                type = "ALERT_FEED",
                isRead = false,
                createdAt = "Hoje às 08:40"
            ),
            NotificationItemResponse(
                id = 3,
                title = "Como diminuir a conta de água",
                message = "Veja a recomendação técnica operacional para reduzir em até 50% o consumo.",
                type = "TIP_CREATED",
                idTip = 1,
                isRead = false,
                createdAt = "Hoje às 08:00"
            ),
            NotificationItemResponse(
                id = 4,
                title = "Eii,",
                message = "Uma nova tarefa foi atribuída a sua granja",
                type = "GOAL_ASSIGNED",
                isRead = false,
                createdAt = "Ontem"
            ),
            NotificationItemResponse(
                id = 5,
                title = "Você viu?",
                message = "Daqui a pouco o novo ranking sai!",
                type = "RANK_UPDATE",
                isRead = true,
                createdAt = "Ontem"
            ),
            NotificationItemResponse(
                id = 6,
                title = "Piiuu",
                message = "O midas está com saudade!!",
                type = "MIDAS_INFO",
                isRead = true,
                createdAt = "14/10/2026"
            ),
            NotificationItemResponse(
                id = 7,
                title = "Dê uma olhada,",
                message = "a nova posição do ranking saiu",
                type = "RANK_UPDATE",
                isRead = true,
                createdAt = "12/10/2026"
            )
        )
    }
}
