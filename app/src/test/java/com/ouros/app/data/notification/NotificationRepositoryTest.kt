package com.ouros.app.data.notification

import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class NotificationRepositoryTest {

    @Test
    fun registerDeviceToken_success() = runBlocking {
        var recordedToken: String? = null
        val fakeApi = object : NotificationApi {
            override suspend fun registerDeviceToken(request: DeviceTokenRequest): Response<Unit> {
                recordedToken = request.token
                return Response.success(Unit)
            }
            override suspend fun deleteDeviceToken(token: String): Response<Unit> = error("unused")
            override suspend fun getNotifications(page: Int, size: Int): Response<NotificationPageResponse> = error("unused")
            override suspend fun getUnreadCount(): Response<UnreadCountResponse> = error("unused")
            override suspend fun markAsRead(id: Long): Response<Unit> = error("unused")
            override suspend fun markAllAsRead(): Response<Unit> = error("unused")
        }

        val repository = NotificationRepository(api = fakeApi, enableMockFallback = false)
        val result = repository.registerDeviceToken("token_abc")

        assertTrue(result.isSuccess)
        assertEquals("token_abc", recordedToken)
    }

    @Test
    fun registerDeviceToken_error_returnsFailureWhenFallbackDisabled() = runBlocking {
        val fakeApi = object : NotificationApi {
            override suspend fun registerDeviceToken(request: DeviceTokenRequest): Response<Unit> {
                return Response.error(400, "Bad Request".toResponseBody(null))
            }
            override suspend fun deleteDeviceToken(token: String): Response<Unit> = error("unused")
            override suspend fun getNotifications(page: Int, size: Int): Response<NotificationPageResponse> = error("unused")
            override suspend fun getUnreadCount(): Response<UnreadCountResponse> = error("unused")
            override suspend fun markAsRead(id: Long): Response<Unit> = error("unused")
            override suspend fun markAllAsRead(): Response<Unit> = error("unused")
        }

        val repository = NotificationRepository(api = fakeApi, enableMockFallback = false)
        val result = repository.registerDeviceToken("token_err")

        assertTrue(result.isFailure)
    }

    @Test
    fun deleteDeviceToken_success() = runBlocking {
        var deletedToken: String? = null
        val fakeApi = object : NotificationApi {
            override suspend fun registerDeviceToken(request: DeviceTokenRequest): Response<Unit> = error("unused")
            override suspend fun deleteDeviceToken(token: String): Response<Unit> {
                deletedToken = token
                return Response.success(Unit)
            }
            override suspend fun getNotifications(page: Int, size: Int): Response<NotificationPageResponse> = error("unused")
            override suspend fun getUnreadCount(): Response<UnreadCountResponse> = error("unused")
            override suspend fun markAsRead(id: Long): Response<Unit> = error("unused")
            override suspend fun markAllAsRead(): Response<Unit> = error("unused")
        }

        val repository = NotificationRepository(api = fakeApi, enableMockFallback = false)
        val result = repository.deleteDeviceToken("token_del")

        assertTrue(result.isSuccess)
        assertEquals("token_del", deletedToken)
    }

    @Test
    fun deleteDeviceToken_error_returnsFailureWhenFallbackDisabled() = runBlocking {
        val fakeApi = object : NotificationApi {
            override suspend fun registerDeviceToken(request: DeviceTokenRequest): Response<Unit> = error("unused")
            override suspend fun deleteDeviceToken(token: String): Response<Unit> {
                return Response.error(404, "Not Found".toResponseBody(null))
            }
            override suspend fun getNotifications(page: Int, size: Int): Response<NotificationPageResponse> = error("unused")
            override suspend fun getUnreadCount(): Response<UnreadCountResponse> = error("unused")
            override suspend fun markAsRead(id: Long): Response<Unit> = error("unused")
            override suspend fun markAllAsRead(): Response<Unit> = error("unused")
        }

        val repository = NotificationRepository(api = fakeApi, enableMockFallback = false)
        val result = repository.deleteDeviceToken("token_del_err")

        assertTrue(result.isFailure)
    }

    @Test
    fun getNotifications_success() = runBlocking {
        val fakeApi = object : NotificationApi {
            override suspend fun registerDeviceToken(request: DeviceTokenRequest): Response<Unit> = error("unused")
            override suspend fun deleteDeviceToken(token: String): Response<Unit> = error("unused")
            override suspend fun getNotifications(page: Int, size: Int): Response<NotificationPageResponse> {
                return Response.success(
                    NotificationPageResponse(
                        content = listOf(NotificationItemResponse(id = 1L, title = "Titulo", message = "Msg", type = "GENERAL", isRead = false)),
                        totalElements = 1,
                        totalPages = 1,
                        number = 0,
                        last = true
                    )
                )
            }
            override suspend fun getUnreadCount(): Response<UnreadCountResponse> = error("unused")
            override suspend fun markAsRead(id: Long): Response<Unit> = error("unused")
            override suspend fun markAllAsRead(): Response<Unit> = error("unused")
        }

        val repository = NotificationRepository(api = fakeApi, enableMockFallback = false)
        val result = repository.getNotifications()

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.content?.size)
    }

    @Test
    fun getNotifications_failure_returnsFailureWhenFallbackDisabled() = runBlocking {
        val fakeApi = object : NotificationApi {
            override suspend fun registerDeviceToken(request: DeviceTokenRequest): Response<Unit> = error("unused")
            override suspend fun deleteDeviceToken(token: String): Response<Unit> = error("unused")
            override suspend fun getNotifications(page: Int, size: Int): Response<NotificationPageResponse> {
                return Response.error(500, "Error".toResponseBody(null))
            }
            override suspend fun getUnreadCount(): Response<UnreadCountResponse> = error("unused")
            override suspend fun markAsRead(id: Long): Response<Unit> = error("unused")
            override suspend fun markAllAsRead(): Response<Unit> = error("unused")
        }

        val repository = NotificationRepository(api = fakeApi, enableMockFallback = false)
        val result = repository.getNotifications()

        assertTrue(result.isFailure)
    }

    @Test
    fun getNotifications_withMockFallback_returnsMockData() = runBlocking {
        val fakeApi = object : NotificationApi {
            override suspend fun registerDeviceToken(request: DeviceTokenRequest): Response<Unit> = error("unused")
            override suspend fun deleteDeviceToken(token: String): Response<Unit> = error("unused")
            override suspend fun getNotifications(page: Int, size: Int): Response<NotificationPageResponse> {
                return Response.error(404, "Not Found".toResponseBody("application/json".toMediaTypeOrNull()))
            }
            override suspend fun getUnreadCount(): Response<UnreadCountResponse> = error("unused")
            override suspend fun markAsRead(id: Long): Response<Unit> = error("unused")
            override suspend fun markAllAsRead(): Response<Unit> = error("unused")
        }

        val repository = NotificationRepository(api = fakeApi, enableMockFallback = true)
        val result = repository.getNotifications()

        assertTrue(result.isSuccess)
        val page = result.getOrNull()
        assertTrue((page?.content?.size ?: 0) > 0)
    }

    @Test
    fun getUnreadCount_returnsCount() = runBlocking {
        val fakeApi = object : NotificationApi {
            override suspend fun registerDeviceToken(request: DeviceTokenRequest): Response<Unit> = error("unused")
            override suspend fun deleteDeviceToken(token: String): Response<Unit> = error("unused")
            override suspend fun getNotifications(page: Int, size: Int): Response<NotificationPageResponse> = error("unused")
            override suspend fun getUnreadCount(): Response<UnreadCountResponse> {
                return Response.success(UnreadCountResponse(unreadCountSnake = 4))
            }
            override suspend fun markAsRead(id: Long): Response<Unit> = error("unused")
            override suspend fun markAllAsRead(): Response<Unit> = error("unused")
        }

        val repository = NotificationRepository(api = fakeApi, enableMockFallback = false)
        val result = repository.getUnreadCount()

        assertTrue(result.isSuccess)
        assertEquals(4, result.getOrNull())
    }

    @Test
    fun getUnreadCount_error_returnsFailureWhenFallbackDisabled() = runBlocking {
        val fakeApi = object : NotificationApi {
            override suspend fun registerDeviceToken(request: DeviceTokenRequest): Response<Unit> = error("unused")
            override suspend fun deleteDeviceToken(token: String): Response<Unit> = error("unused")
            override suspend fun getNotifications(page: Int, size: Int): Response<NotificationPageResponse> = error("unused")
            override suspend fun getUnreadCount(): Response<UnreadCountResponse> {
                return Response.error(500, "Error".toResponseBody(null))
            }
            override suspend fun markAsRead(id: Long): Response<Unit> = error("unused")
            override suspend fun markAllAsRead(): Response<Unit> = error("unused")
        }

        val repository = NotificationRepository(api = fakeApi, enableMockFallback = false)
        val result = repository.getUnreadCount()

        assertTrue(result.isFailure)
    }

    @Test
    fun markAsRead_success() = runBlocking {
        val fakeApi = object : NotificationApi {
            override suspend fun registerDeviceToken(request: DeviceTokenRequest): Response<Unit> = error("unused")
            override suspend fun deleteDeviceToken(token: String): Response<Unit> = error("unused")
            override suspend fun getNotifications(page: Int, size: Int): Response<NotificationPageResponse> = error("unused")
            override suspend fun getUnreadCount(): Response<UnreadCountResponse> = error("unused")
            override suspend fun markAsRead(id: Long): Response<Unit> = Response.success(Unit)
            override suspend fun markAllAsRead(): Response<Unit> = error("unused")
        }

        val repository = NotificationRepository(api = fakeApi, enableMockFallback = false)
        val result = repository.markAsRead(1L)

        assertTrue(result.isSuccess)
    }

    @Test
    fun markAsRead_errorResponse_returnsFailureWhenFallbackDisabled() = runBlocking {
        val fakeApi = object : NotificationApi {
            override suspend fun registerDeviceToken(request: DeviceTokenRequest): Response<Unit> = error("unused")
            override suspend fun deleteDeviceToken(token: String): Response<Unit> = error("unused")
            override suspend fun getNotifications(page: Int, size: Int): Response<NotificationPageResponse> = error("unused")
            override suspend fun getUnreadCount(): Response<UnreadCountResponse> = error("unused")
            override suspend fun markAsRead(id: Long): Response<Unit> {
                return Response.error(404, "Not Found".toResponseBody("application/json".toMediaTypeOrNull()))
            }
            override suspend fun markAllAsRead(): Response<Unit> = error("unused")
        }

        val repository = NotificationRepository(api = fakeApi, enableMockFallback = false)
        val result = repository.markAsRead(99L)

        assertTrue(result.isFailure)
    }

    @Test
    fun markAllAsRead_success() = runBlocking {
        var called = false
        val fakeApi = object : NotificationApi {
            override suspend fun registerDeviceToken(request: DeviceTokenRequest): Response<Unit> = error("unused")
            override suspend fun deleteDeviceToken(token: String): Response<Unit> = error("unused")
            override suspend fun getNotifications(page: Int, size: Int): Response<NotificationPageResponse> = error("unused")
            override suspend fun getUnreadCount(): Response<UnreadCountResponse> = error("unused")
            override suspend fun markAsRead(id: Long): Response<Unit> = error("unused")
            override suspend fun markAllAsRead(): Response<Unit> {
                called = true
                return Response.success(Unit)
            }
        }

        val repository = NotificationRepository(api = fakeApi, enableMockFallback = false)
        val result = repository.markAllAsRead()

        assertTrue(result.isSuccess)
        assertTrue(called)
    }

    @Test
    fun markAllAsRead_error_returnsFailureWhenFallbackDisabled() = runBlocking {
        val fakeApi = object : NotificationApi {
            override suspend fun registerDeviceToken(request: DeviceTokenRequest): Response<Unit> = error("unused")
            override suspend fun deleteDeviceToken(token: String): Response<Unit> = error("unused")
            override suspend fun getNotifications(page: Int, size: Int): Response<NotificationPageResponse> = error("unused")
            override suspend fun getUnreadCount(): Response<UnreadCountResponse> = error("unused")
            override suspend fun markAsRead(id: Long): Response<Unit> = error("unused")
            override suspend fun markAllAsRead(): Response<Unit> {
                return Response.error(500, "Server Error".toResponseBody(null))
            }
        }

        val repository = NotificationRepository(api = fakeApi, enableMockFallback = false)
        val result = repository.markAllAsRead()

        assertTrue(result.isFailure)
    }

    @Test
    fun networkException_returnsFailureWithInternetMessage() = runBlocking {
        val fakeApi = object : NotificationApi {
            override suspend fun registerDeviceToken(request: DeviceTokenRequest): Response<Unit> {
                throw IOException("Connection timed out")
            }
            override suspend fun deleteDeviceToken(token: String): Response<Unit> = error("unused")
            override suspend fun getNotifications(page: Int, size: Int): Response<NotificationPageResponse> = error("unused")
            override suspend fun getUnreadCount(): Response<UnreadCountResponse> = error("unused")
            override suspend fun markAsRead(id: Long): Response<Unit> = error("unused")
            override suspend fun markAllAsRead(): Response<Unit> = error("unused")
        }

        val repository = NotificationRepository(api = fakeApi, enableMockFallback = false)
        val result = repository.registerDeviceToken("abc")

        assertTrue(result.isFailure)
        assertEquals("Sem conexão com o servidor. Verifique sua internet.", result.exceptionOrNull()?.message)
    }
}
