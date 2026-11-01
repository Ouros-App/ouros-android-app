package com.ouros.app.data.notification

import com.ouros.app.data.local.OfflineStore
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationOfflineCacheTest {
    private val context get() = RuntimeEnvironment.getApplication<android.app.Application>()

    @Before
    fun clearDatabase() {
        context.deleteDatabase("ouros_offline.db")
    }

    @Test
    fun offlineLoad_returnsOnlyPreviouslyCachedBackendNotifications() = runBlocking {
        var online = true
        val backendItem = NotificationItemResponse(
            id = 91L,
            title = "Aviso real",
            message = "Mensagem enviada pelo back",
            type = "GENERAL"
        )
        val fakeApi = object : NotificationApi {
            override suspend fun registerDeviceToken(request: DeviceTokenRequest): Response<DeviceTokenResponse> = error("unused")
            override suspend fun deleteDeviceToken(token: String): Response<Unit> = error("unused")
            override suspend fun getNotifications(page: Int, size: Int, unreadOnly: Boolean?): Response<NotificationPageResponse> {
                if (!online) throw IOException("offline")
                return Response.success(NotificationPageResponse(content = listOf(backendItem)))
            }
            override suspend fun getUnreadCount(): Response<UnreadCountResponse> = error("unused")
            override suspend fun markAsRead(id: Long): Response<NotificationItemResponse> = error("unused")
            override suspend fun markAllAsRead(): Response<Unit> = error("unused")
        }
        val repository = NotificationRepository(
            api = fakeApi,
            offlineStore = OfflineStore(context),
            ioDispatcher = Dispatchers.Unconfined
        )

        assertTrue(repository.getNotifications().isSuccess)
        online = false
        val offlineResult = repository.getNotifications()

        assertTrue(offlineResult.isSuccess)
        assertEquals(listOf(backendItem), offlineResult.getOrNull()?.content)
    }
}
