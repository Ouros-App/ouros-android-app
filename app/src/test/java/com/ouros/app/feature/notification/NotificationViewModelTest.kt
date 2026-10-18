package com.ouros.app.feature.notification

import com.ouros.app.data.notification.DeviceTokenRequest
import com.ouros.app.data.notification.NotificationApi
import com.ouros.app.data.notification.NotificationItemResponse
import com.ouros.app.data.notification.NotificationPageResponse
import com.ouros.app.data.notification.NotificationRepository
import com.ouros.app.data.notification.UnreadCountResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadNotifications_success_updatesUiState() = runTest(testDispatcher) {
        val sampleList = listOf(
            NotificationItemResponse(id = 1L, title = "Aviso 1", message = "Corpo 1", type = "GENERAL", isRead = false),
            NotificationItemResponse(id = 2L, title = "Aviso 2", message = "Corpo 2", type = "ALERT_WATER", isRead = true)
        )

        val fakeApi = object : NotificationApi {
            override suspend fun registerDeviceToken(request: DeviceTokenRequest) = error("unused")
            override suspend fun deleteDeviceToken(token: String) = error("unused")
            override suspend fun getNotifications(page: Int, size: Int): Response<NotificationPageResponse> {
                return Response.success(
                    NotificationPageResponse(
                        content = sampleList,
                        totalElements = 2,
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

        val repository = NotificationRepository(api = fakeApi, enableMockFallback = false, ioDispatcher = testDispatcher)
        val viewModel = NotificationViewModel(repository)

        viewModel.loadNotifications()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertEquals(2, state.notifications.size)
        assertEquals(1, state.unreadCount)
    }

    @Test
    fun loadNotifications_failure_setsErrorMessage() = runTest(testDispatcher) {
        val fakeApi = object : NotificationApi {
            override suspend fun registerDeviceToken(request: DeviceTokenRequest) = error("unused")
            override suspend fun deleteDeviceToken(token: String) = error("unused")
            override suspend fun getNotifications(page: Int, size: Int): Response<NotificationPageResponse> {
                return Response.error(500, "Erro interno".toResponseBody("text/plain".toMediaTypeOrNull()))
            }
            override suspend fun getUnreadCount(): Response<UnreadCountResponse> = error("unused")
            override suspend fun markAsRead(id: Long): Response<Unit> = error("unused")
            override suspend fun markAllAsRead(): Response<Unit> = error("unused")
        }

        val repository = NotificationRepository(api = fakeApi, enableMockFallback = false, ioDispatcher = testDispatcher)
        val viewModel = NotificationViewModel(repository)

        viewModel.loadNotifications()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.errorMessage)
        assertTrue(state.notifications.isEmpty())
    }

    @Test
    fun refreshNotifications_success_updatesListAndClearsRefreshing() = runTest(testDispatcher) {
        val initialList = listOf(
            NotificationItemResponse(id = 1L, title = "Aviso 1", message = "Msg 1", type = "GENERAL", isRead = false)
        )
        val refreshedList = listOf(
            NotificationItemResponse(id = 1L, title = "Aviso 1", message = "Msg 1", type = "GENERAL", isRead = true),
            NotificationItemResponse(id = 2L, title = "Aviso 2", message = "Msg 2", type = "ALERT", isRead = false)
        )

        var callCount = 0
        val fakeApi = object : NotificationApi {
            override suspend fun registerDeviceToken(request: DeviceTokenRequest) = error("unused")
            override suspend fun deleteDeviceToken(token: String) = error("unused")
            override suspend fun getNotifications(page: Int, size: Int): Response<NotificationPageResponse> {
                callCount++
                val list = if (callCount == 1) initialList else refreshedList
                return Response.success(
                    NotificationPageResponse(
                        content = list,
                        totalElements = list.size.toLong(),
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

        val repository = NotificationRepository(api = fakeApi, enableMockFallback = false, ioDispatcher = testDispatcher)
        val viewModel = NotificationViewModel(repository)

        viewModel.loadNotifications()
        assertEquals(1, viewModel.uiState.value.notifications.size)

        // Calling loadNotifications when already populated delegates to refreshNotifications
        viewModel.loadNotifications()

        val state = viewModel.uiState.value
        assertFalse(state.isRefreshing)
        assertEquals(2, state.notifications.size)
        assertEquals(1, state.unreadCount)
    }

    @Test
    fun refreshNotifications_failure_setsErrorMessage() = runTest(testDispatcher) {
        var callCount = 0
        val fakeApi = object : NotificationApi {
            override suspend fun registerDeviceToken(request: DeviceTokenRequest) = error("unused")
            override suspend fun deleteDeviceToken(token: String) = error("unused")
            override suspend fun getNotifications(page: Int, size: Int): Response<NotificationPageResponse> {
                callCount++
                if (callCount == 1) {
                    return Response.success(
                        NotificationPageResponse(
                            content = listOf(NotificationItemResponse(id = 1L, title = "T", message = "M", type = "G", isRead = false)),
                            totalElements = 1,
                            totalPages = 1,
                            number = 0,
                            last = true
                        )
                    )
                }
                return Response.error(503, "Unavailable".toResponseBody(null))
            }
            override suspend fun getUnreadCount(): Response<UnreadCountResponse> = error("unused")
            override suspend fun markAsRead(id: Long): Response<Unit> = error("unused")
            override suspend fun markAllAsRead(): Response<Unit> = error("unused")
        }

        val repository = NotificationRepository(api = fakeApi, enableMockFallback = false, ioDispatcher = testDispatcher)
        val viewModel = NotificationViewModel(repository)

        viewModel.loadNotifications()

        viewModel.refreshNotifications()

        val state = viewModel.uiState.value
        assertFalse(state.isRefreshing)
        assertNotNull(state.errorMessage)
    }

    @Test
    fun markAsRead_optimisticallyUpdatesStateAndCallsRepository() = runTest(testDispatcher) {
        val item1 = NotificationItemResponse(id = 1L, title = "Item 1", message = "Corpo 1", type = "GENERAL", isRead = false)
        val item2 = NotificationItemResponse(id = 2L, title = "Item 2", message = "Corpo 2", type = "GENERAL", isRead = false)

        var markedId: Long? = null
        val fakeApi = object : NotificationApi {
            override suspend fun registerDeviceToken(request: DeviceTokenRequest) = error("unused")
            override suspend fun deleteDeviceToken(token: String) = error("unused")
            override suspend fun getNotifications(page: Int, size: Int): Response<NotificationPageResponse> {
                return Response.success(
                    NotificationPageResponse(
                        content = listOf(item1, item2),
                        totalElements = 2,
                        totalPages = 1,
                        number = 0,
                        last = true
                    )
                )
            }
            override suspend fun getUnreadCount(): Response<UnreadCountResponse> = error("unused")
            override suspend fun markAsRead(id: Long): Response<Unit> {
                markedId = id
                return Response.success(Unit)
            }
            override suspend fun markAllAsRead(): Response<Unit> = error("unused")
        }

        val repository = NotificationRepository(api = fakeApi, enableMockFallback = false, ioDispatcher = testDispatcher)
        val viewModel = NotificationViewModel(repository)

        viewModel.loadNotifications()
        assertEquals(2, viewModel.uiState.value.unreadCount)

        viewModel.markAsRead(item1)
        assertTrue(viewModel.uiState.value.notifications.first { it.id == 1L }.isRead)
        assertEquals(1, viewModel.uiState.value.unreadCount)
        assertEquals(1L, markedId)

        // Calling markAsRead on an already read item should do nothing
        viewModel.markAsRead(item1.copy(isRead = true))
        assertEquals(1, viewModel.uiState.value.unreadCount)
    }

    @Test
    fun markAllAsRead_optimisticallyMarksAllAsRead() = runTest(testDispatcher) {
        val item1 = NotificationItemResponse(id = 1L, title = "Item 1", message = "M1", type = "GENERAL", isRead = false)
        val item2 = NotificationItemResponse(id = 2L, title = "Item 2", message = "M2", type = "GENERAL", isRead = false)

        var markedAllCalled = false
        val fakeApi = object : NotificationApi {
            override suspend fun registerDeviceToken(request: DeviceTokenRequest) = error("unused")
            override suspend fun deleteDeviceToken(token: String) = error("unused")
            override suspend fun getNotifications(page: Int, size: Int): Response<NotificationPageResponse> {
                return Response.success(
                    NotificationPageResponse(
                        content = listOf(item1, item2),
                        totalElements = 2,
                        totalPages = 1,
                        number = 0,
                        last = true
                    )
                )
            }
            override suspend fun getUnreadCount(): Response<UnreadCountResponse> = error("unused")
            override suspend fun markAsRead(id: Long): Response<Unit> = error("unused")
            override suspend fun markAllAsRead(): Response<Unit> {
                markedAllCalled = true
                return Response.success(Unit)
            }
        }

        val repository = NotificationRepository(api = fakeApi, enableMockFallback = false, ioDispatcher = testDispatcher)
        val viewModel = NotificationViewModel(repository)

        viewModel.loadNotifications()

        viewModel.markAllAsRead()
        assertEquals(0, viewModel.uiState.value.unreadCount)
        assertTrue(viewModel.uiState.value.notifications.all { it.isRead })
        assertTrue(markedAllCalled)
    }

    @Test
    fun factory_createsNotificationViewModel() {
        val factory = NotificationViewModel.Factory()
        val created = factory.create(NotificationViewModel::class.java)
        assertNotNull(created)
    }
}
