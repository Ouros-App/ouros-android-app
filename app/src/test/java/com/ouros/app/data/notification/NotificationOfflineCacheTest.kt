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
    private val context: android.app.Application
        get() = RuntimeEnvironment.getApplication()

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

    @Test
    fun offlineReads_updateCacheQueueAndSynchronizeWhenOnline() = runBlocking {
        var online = true
        var markedId: Long? = null
        var markAllCalls = 0
        val unread = NotificationItemResponse(10L, "Nova", "Mensagem", "GENERAL", isRead = false)
        val api = object : NotificationApi {
            override suspend fun registerDeviceToken(request: DeviceTokenRequest): Response<DeviceTokenResponse> = error("unused")
            override suspend fun deleteDeviceToken(token: String): Response<Unit> = error("unused")
            override suspend fun getNotifications(page: Int, size: Int, unreadOnly: Boolean?): Response<NotificationPageResponse> {
                if (!online) throw IOException("offline")
                return Response.success(NotificationPageResponse(content = listOf(unread)))
            }
            override suspend fun getUnreadCount(): Response<UnreadCountResponse> {
                if (!online) throw IOException("offline")
                return Response.success(UnreadCountResponse(1))
            }
            override suspend fun markAsRead(id: Long): Response<NotificationItemResponse> {
                if (!online) throw IOException("offline")
                markedId = id
                return Response.success(unread.copy(isRead = true))
            }
            override suspend fun markAllAsRead(): Response<Unit> {
                if (!online) throw IOException("offline")
                markAllCalls++
                return Response.success(Unit)
            }
        }
        val store = OfflineStore(context)
        val repository = NotificationRepository(
            api = api,
            offlineStore = store,
            ownerIdProvider = { 77L },
            ioDispatcher = Dispatchers.Unconfined
        )
        repository.getNotifications()
        online = false

        assertEquals(1, repository.getUnreadCount().getOrNull())
        assertTrue(repository.markAsRead(10L).isSuccess)
        assertEquals(0, repository.getUnreadCount().getOrNull())
        assertTrue(repository.markAllAsRead().isSuccess)
        assertEquals(2, store.pending("notifications:77").size)

        online = true
        repository.synchronize()
        assertTrue(store.pending("notifications:77").isEmpty())
        assertEquals(10L, markedId)
        assertEquals(1, markAllCalls)
    }

    @Test
    fun synchronize_keepsFailedOperationAndCompletesUnknownOperation() = runBlocking {
        val store = OfflineStore(context)
        var successful = false
        val api = object : NotificationApi {
            override suspend fun registerDeviceToken(request: DeviceTokenRequest): Response<DeviceTokenResponse> = error("unused")
            override suspend fun deleteDeviceToken(token: String): Response<Unit> = error("unused")
            override suspend fun getNotifications(page: Int, size: Int, unreadOnly: Boolean?): Response<NotificationPageResponse> = error("unused")
            override suspend fun getUnreadCount(): Response<UnreadCountResponse> = error("unused")
            override suspend fun markAsRead(id: Long): Response<NotificationItemResponse> =
                if (successful) Response.success(null) else throw IOException("offline")
            override suspend fun markAllAsRead(): Response<Unit> = error("unused")
        }
        store.enqueue("notifications:anonymous", "unknown", null, emptyMap<String, String>())
        val repository = NotificationRepository(api, offlineStore = store, ioDispatcher = Dispatchers.Unconfined)
        repository.synchronize()
        assertTrue(store.pending("notifications:anonymous").isEmpty())

        repository.markAsRead(5L)
        assertEquals(1, store.pending("notifications:anonymous").size)
        repository.synchronize()
        assertEquals(1, store.pending("notifications:anonymous").size)
        successful = true
        repository.synchronize()
        assertTrue(store.pending("notifications:anonymous").isEmpty())
    }
}
