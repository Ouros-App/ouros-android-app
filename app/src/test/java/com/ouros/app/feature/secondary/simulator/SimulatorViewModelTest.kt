package com.ouros.app.feature.secondary.simulator

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import com.ouros.app.data.simulator.SimulatorApi
import com.ouros.app.data.simulator.SimulatorFarmResponse
import com.ouros.app.data.simulator.SimulatorRankingResponse
import com.ouros.app.data.simulator.SimulatorRankingSimulationResponse
import com.ouros.app.data.simulator.SimulatorRepository
import com.ouros.app.data.simulator.SimulatorUiState
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
class SimulatorViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    private class FakeApi : SimulatorApi {
        var rankingResponse: Response<SimulatorRankingResponse> = Response.success(ranking())
        var farmResponse: Response<SimulatorFarmResponse> = Response.success(farm())
        var simulationResponse: Response<SimulatorRankingSimulationResponse> = Response.success(simulation())
        var rankingCalls = 0
        var simulationCalls = 0
        override suspend fun getFarmRanking(farmId: Long): Response<SimulatorRankingResponse> { rankingCalls++; return rankingResponse }
        override suspend fun getFarm(farmId: Long): Response<SimulatorFarmResponse> = farmResponse
        override suspend fun simulateRanking(farmId: Long, score: Double): Response<SimulatorRankingSimulationResponse> { simulationCalls++; return simulationResponse }
    }

    @Before
    fun setUp() {
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread() = true
        })
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        ArchTaskExecutor.getInstance().setDelegate(null)
    }

    @Test
    fun loadInitialData_successPopulatesStateAndAvoidsDuplicateLoad() {
        val api = FakeApi()
        val viewModel = SimulatorViewModel(SimulatorRepository(api), 8L)
        viewModel.loadInitialData()
        viewModel.loadInitialData()
        val state = viewModel.state.value!!
        assertEquals(1, api.rankingCalls)
        assertTrue(state.hasInitialData)
        assertFalse(state.initialLoading)
        assertEquals(7.85, state.currentCgi, 0.0)
        assertEquals(50_000, state.currentChickens)
        assertEquals("7.85", state.projectedCgiText)
        assertEquals("--", state.targetEnergyText)
        assertEquals("R$ 0", state.savingsText)
        assertEquals("0", state.positionsGainedText)
        assertNull(state.errorMessage)
    }

    @Test
    fun loadInitialData_withoutFarmIdShowsNonRetryableError() {
        val viewModel = SimulatorViewModel(SimulatorRepository(FakeApi()), null)
        viewModel.loadInitialData()
        assertEquals("Não foi possível identificar a granja vinculada ao seu perfil.", viewModel.state.value?.errorMessage)
        assertFalse(viewModel.state.value!!.canRetry)
        assertFalse(viewModel.state.value!!.initialLoading)
    }

    @Test
    fun loadInitialData_errorAndAuthenticationAreHandled() {
        val failingApi = FakeApi().apply { rankingResponse = Response.error(500, "".toResponseBody(null)) }
        val failing = SimulatorViewModel(SimulatorRepository(failingApi), 8L)
        failing.loadInitialData()
        assertEquals("O serviço do simulador está indisponível. Tente novamente.", failing.state.value?.errorMessage)
        assertTrue(failing.state.value!!.canRetry)

        val authApi = FakeApi().apply { rankingResponse = Response.error(401, "".toResponseBody(null)) }
        val auth = SimulatorViewModel(SimulatorRepository(authApi), 8L)
        auth.loadInitialData()
        assertEquals(SimulatorEvent.AuthenticationRequired, auth.event.value)
        assertFalse(auth.state.value!!.initialLoading)
        auth.consumeEvent()
        assertNull(auth.event.value)
    }

    @Test
    fun retry_reloadsAfterFailure() {
        val api = FakeApi().apply { rankingResponse = Response.error(500, "".toResponseBody(null)) }
        val viewModel = SimulatorViewModel(SimulatorRepository(api), 8L)
        viewModel.loadInitialData()
        api.rankingResponse = Response.success(ranking())
        viewModel.retry()
        assertEquals(2, api.rankingCalls)
        assertTrue(viewModel.state.value!!.hasInitialData)
    }

    @Test
    fun simulate_successCalculatesKpisAndUpdatesRanking() {
        val api = FakeApi()
        val viewModel = SimulatorViewModel(SimulatorRepository(api), 8L)
        viewModel.loadInitialData()
        viewModel.simulate(targetCgi = 6.07, waterPricePerM3 = 5.5, energyPricePerKwh = 0.85, chickens = 50_000, targetKwhPerBird = 5.2)
        val state = viewModel.state.value!!
        assertEquals(1, api.simulationCalls)
        assertFalse(state.simulating)
        assertEquals("6.07", state.projectedCgiText)
        assertEquals("5.2", state.targetEnergyText)
        assertEquals("8", state.positionsGainedText)
        assertEquals("OURO", state.simulatedLeague)
        assertTrue(state.savingsText.startsWith("R$"))
    }

    @Test
    fun simulate_withLowerEnergyTargetCalculatesPositiveSavings() {
        val viewModel = SimulatorViewModel(SimulatorRepository(FakeApi()), 8L)
        viewModel.loadInitialData()
        viewModel.simulate(targetCgi = 6.07, waterPricePerM3 = 5.5, energyPricePerKwh = 0.85, chickens = 50_000, targetKwhPerBird = 0.2)
        val state = viewModel.state.value!!
        assertEquals("0.2", state.targetEnergyText)
        assertEquals("6.07", state.projectedCgiText)
        assertTrue(state.savingsText.contains("9.176"))
    }

    @Test
    fun simulate_failureEmitsMessageAndAuthenticationEvent() {
        val api = FakeApi()
        val viewModel = SimulatorViewModel(SimulatorRepository(api), 8L)
        api.simulationResponse = Response.error(403, "".toResponseBody(null))
        viewModel.simulate(6.0, 5.0, 1.0, 100, 0.3)
        assertEquals(SimulatorEvent.Message("O simulador está disponível somente para produtores rurais."), viewModel.event.value)
        assertFalse(viewModel.state.value!!.simulating)
        viewModel.consumeEvent()
        api.simulationResponse = Response.error(401, "".toResponseBody(null))
        viewModel.simulate(6.0, 5.0, 1.0, 100, 0.3)
        assertEquals(SimulatorEvent.AuthenticationRequired, viewModel.event.value)
        assertFalse(viewModel.state.value!!.simulating)
    }

    @Test
    fun simulate_withoutFarmIdDoesNothing() {
        val api = FakeApi()
        val viewModel = SimulatorViewModel(SimulatorRepository(api), null)
        viewModel.simulate(6.0, 5.0, 1.0, 100, 0.3)
        assertEquals(0, api.simulationCalls)
        assertFalse(viewModel.state.value!!.simulating)
    }

    @Test
    fun factoryCreatesViewModelAndRejectsUnknownType() {
        val factory = SimulatorViewModelFactory(SimulatorRepository(FakeApi()), 8L)
        assertTrue(factory.create(SimulatorViewModel::class.java) is SimulatorViewModel)
        try {
            factory.create(AnyViewModel::class.java)
            throw AssertionError("Tipo não suportado deveria lançar exceção")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message!!.contains(AnyViewModel::class.java.name))
        }
    }

    private class AnyViewModel : androidx.lifecycle.ViewModel()

    private companion object {
        fun ranking() = SimulatorRankingResponse(8L, "Granja", "Sul", 7.85, 12)
        fun farm() = SimulatorFarmResponse(8L, "Granja", 50_000)
        fun simulation() = SimulatorRankingSimulationResponse(8L, "Granja", 7.85, 12, "BRONZE", 6.07, 4, "OURO", 8, 25, 16.0)
    }
}
