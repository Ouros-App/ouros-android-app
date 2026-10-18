package com.ouros.app.data.notification

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response

class NotificationRepository(
    private val api: NotificationApi = NotificationNetwork.api,
    private val enableMockFallback: Boolean = true,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun registerDeviceToken(
        token: String,
        platform: String = "ANDROID"
    ): Result<Unit> = withContext(ioDispatcher) {
        safeCall {
            val response = callApiWithFallback {
                api.registerDeviceToken(DeviceTokenRequest(token = token, platform = platform))
            }
            resolveUnitResponse(response, "Falha ao registrar token")
        }
    }

    suspend fun deleteDeviceToken(token: String): Result<Unit> = withContext(ioDispatcher) {
        safeCall {
            val response = callApiWithFallback { api.deleteDeviceToken(token) }
            resolveUnitResponse(response, "Falha ao remover token")
        }
    }

    suspend fun getNotifications(
        page: Int = 0,
        size: Int = 20
    ): Result<NotificationPageResponse> = withContext(ioDispatcher) {
        safeCall {
            val response = callApiWithFallback { api.getNotifications(page = page, size = size) }
            val body = response?.body()
            when {
                response?.isSuccessful == true && body != null -> Result.success(body)
                enableMockFallback -> Result.success(createMockPageResponse())
                else -> Result.failure(Exception("Falha ao carregar notificações: HTTP ${response?.code()}"))
            }
        }
    }

    suspend fun getUnreadCount(): Result<Int> = withContext(ioDispatcher) {
        safeCall {
            val response = callApiWithFallback { api.getUnreadCount() }
            val body = response?.body()
            when {
                response?.isSuccessful == true && body != null -> Result.success(body.count)
                enableMockFallback -> Result.success(mockNotifications.count { !it.isRead })
                else -> Result.failure(Exception("Falha ao obter contador de não lidas: HTTP ${response?.code()}"))
            }
        }
    }

    suspend fun markAsRead(id: Long): Result<Unit> = withContext(ioDispatcher) {
        safeCall {
            val response = callApiWithFallback { api.markAsRead(id) }
            updateMockItemReadStatus(id)
            resolveUnitResponse(response, "Falha ao marcar notificação como lida")
        }
    }

    suspend fun markAllAsRead(): Result<Unit> = withContext(ioDispatcher) {
        safeCall {
            val response = callApiWithFallback { api.markAllAsRead() }
            updateAllMockItemsReadStatus()
            resolveUnitResponse(response, "Falha ao marcar todas como lidas")
        }
    }

    private suspend fun <T> callApiWithFallback(call: suspend () -> Response<T>): Response<T>? {
        return try {
            call()
        } catch (e: Exception) {
            if (enableMockFallback) null else throw e
        }
    }

    private fun resolveUnitResponse(
        response: Response<Unit>?,
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
