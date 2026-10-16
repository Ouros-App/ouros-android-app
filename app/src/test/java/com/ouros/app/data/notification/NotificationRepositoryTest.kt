package com.ouros.app.data.notification

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
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
}
