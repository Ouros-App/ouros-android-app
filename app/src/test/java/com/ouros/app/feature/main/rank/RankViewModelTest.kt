package com.ouros.app.feature.main.rank

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import com.ouros.app.data.auth.AuthResult
import com.ouros.app.data.auth.SessionManager
import com.ouros.app.data.farmowner.FarmOwnerApi
import com.ouros.app.data.farmowner.FarmOwnerRepository
import com.ouros.app.data.farmowner.FarmOwnerResponse
import com.ouros.app.data.farmowner.FarmOwnerUpdateRequest
import com.ouros.app.data.rank.RankAPI
import com.ouros.app.data.rank.RankPodiumEntry
import com.ouros.app.data.rank.RankPodiumResponse
import com.ouros.app.data.rank.RankRepository
import com.ouros.app.data.session.FarmSessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class RankViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private class FakeRankApi : RankAPI {
        var response: Response<RankPodiumResponse> = Response.error(500, "".toResponseBody(null))
        var callCount = 0

        override suspend fun getPodium(farmId: Long): Response<RankPodiumResponse> {
            callCount++
            return response
        }
    }

    private class FakeFarmSessionStore(var currentFarmId: Long? = 12L) : FarmSessionStore(null) {
        override fun farmId(): Long? = currentFarmId
    }

    private class FakeFarmOwnerRepository : FarmOwnerRepository(
        api = object : FarmOwnerApi {
            override suspend fun getMe(authorization: String): Response<FarmOwnerResponse> = error("Not used")
            override suspend fun update(
                id: Long,
                authorization: String,
                request: FarmOwnerUpdateRequest
            ): Response<FarmOwnerResponse> = error("Not used")
        },
        sessionManager = SessionManager(),
        refreshSession = { AuthResult.Error("Not used") },
        clearSession = {}
    )

    private fun createValidPodiumResponse(farmId: Long = 12L): RankPodiumResponse {
        val entry = RankPodiumEntry(
            farmId = farmId,
            farmName = "Minha Fazenda",
            region = "Sudeste",
            score = 100.0,
            rankPosition = 1,
            relation = "CURRENT"
        )
        return RankPodiumResponse(
            currentFarmId = farmId,
            totalRankedFarms = 10,
            rankingPodium = listOf(entry)
        )
    }

    @Before
    fun setUp() {
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread(): Boolean = true
        })
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        ArchTaskExecutor.getInstance().setDelegate(null)
    }

    @Test
    fun loadInitialData_successUpdatesState() {
        val fakeApi = FakeRankApi().apply {
            response = Response.success(createValidPodiumResponse())
        }
        val sessionStore = FakeFarmSessionStore(12L)
        val repository = RankRepository(fakeApi, FakeFarmOwnerRepository(), sessionStore)
        val viewModel = RankViewModel(repository)

        viewModel.loadInitialData()

        assertEquals(1, fakeApi.callCount)
        val state = viewModel.state.value
        assertFalse(state?.isLoading == true)
        assertEquals(12L, state?.ranking?.currentFarmId)
        assertNull(state?.error)
        assertFalse(state?.canRetry == true)
    }

    @Test
    fun loadInitialData_doesNotReloadIfAlreadyLoaded() {
        val fakeApi = FakeRankApi().apply {
            response = Response.success(createValidPodiumResponse())
        }
        val sessionStore = FakeFarmSessionStore(12L)
        val repository = RankRepository(fakeApi, FakeFarmOwnerRepository(), sessionStore)
        val viewModel = RankViewModel(repository)

        viewModel.loadInitialData()
        viewModel.loadInitialData()

        assertEquals(1, fakeApi.callCount)
    }

    @Test
    fun loadInitialData_errorUpdatesState() {
        val fakeApi = FakeRankApi().apply {
            response = Response.error(500, "".toResponseBody(null))
        }
        val sessionStore = FakeFarmSessionStore(12L)
        val repository = RankRepository(fakeApi, FakeFarmOwnerRepository(), sessionStore)
        val viewModel = RankViewModel(repository)

        viewModel.loadInitialData()

        val state = viewModel.state.value
        assertFalse(state?.isLoading == true)
        assertNull(state?.ranking)
        assertTrue(state?.error?.isNotBlank() == true)
        assertTrue(state?.canRetry == true)
    }

    @Test
    fun retry_triggersLoad() {
        val fakeApi = FakeRankApi().apply {
            response = Response.error(500, "".toResponseBody(null))
        }
        val sessionStore = FakeFarmSessionStore(12L)
        val repository = RankRepository(fakeApi, FakeFarmOwnerRepository(), sessionStore)
        val viewModel = RankViewModel(repository)

        viewModel.loadInitialData()
        assertEquals(1, fakeApi.callCount)

        fakeApi.response = Response.success(createValidPodiumResponse())
        viewModel.retry()

        assertEquals(2, fakeApi.callCount)
        assertEquals(12L, viewModel.state.value?.ranking?.currentFarmId)
    }

    @Test
    fun loadInitialData_authRequiredEmitsEvent() {
        val fakeApi = FakeRankApi().apply {
            response = Response.error(401, "".toResponseBody(null))
        }
        val sessionStore = FakeFarmSessionStore(12L)
        val repository = RankRepository(fakeApi, FakeFarmOwnerRepository(), sessionStore)
        val viewModel = RankViewModel(repository)

        viewModel.loadInitialData()

        assertEquals(RankEvent.AuthenticationRequired, viewModel.event.value)
        assertFalse(viewModel.state.value?.isLoading == true)

        viewModel.consumeEvent()
        assertNull(viewModel.event.value)
    }
}
