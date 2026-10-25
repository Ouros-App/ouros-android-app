package com.ouros.app.data.simulator

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class SimulatorRepositoryTest {
    private class FakeApi : SimulatorApi {
        var rankingResponse: Response<SimulatorRankingResponse> = Response.success(ranking())
        var farmResponse: Response<SimulatorFarmResponse> = Response.success(farm())
        var simulationResponse: Response<SimulatorRankingSimulationResponse> = Response.success(simulation())
        var rankingFailure: Exception? = null
        var farmFailure: Exception? = null
        var simulationFailure: Exception? = null
        var rankingCalls = 0
        var farmCalls = 0
        var simulationCalls = 0
        var requestedFarmId: Long? = null
        var requestedScore: Double? = null

        override suspend fun getFarmRanking(farmId: Long): Response<SimulatorRankingResponse> {
            rankingCalls++
            requestedFarmId = farmId
            rankingFailure?.let { throw it }
            return rankingResponse
        }

        override suspend fun getFarm(farmId: Long): Response<SimulatorFarmResponse> {
            farmCalls++
            requestedFarmId = farmId
            farmFailure?.let { throw it }
            return farmResponse
        }

        override suspend fun simulateRanking(farmId: Long, score: Double): Response<SimulatorRankingSimulationResponse> {
            simulationCalls++
            requestedFarmId = farmId
            requestedScore = score
            simulationFailure?.let { throw it }
            return simulationResponse
        }
    }

    @Test
    fun loadInitialData_returnsRankingAndFarmPopulation() = runBlocking {
        val api = FakeApi()
        assertEquals(SimulatorResult.Success(SimulatorInitialData(7.85, 50_000)), SimulatorRepository(api).loadInitialData(42L))
        assertEquals(1, api.rankingCalls)
        assertEquals(1, api.farmCalls)
        assertEquals(42L, api.requestedFarmId)
    }

    @Test
    fun loadInitialData_usesZeroWhenFarmPopulationIsNull() = runBlocking {
        val api = FakeApi().apply { farmResponse = Response.success(farm(chickens = null)) }
        assertEquals(SimulatorResult.Success(SimulatorInitialData(7.85, 0)), SimulatorRepository(api).loadInitialData(42L))
    }

    @Test
    fun loadInitialData_mapsHttpStatusCodes() = runBlocking {
        val cases = listOf(
            401 to SimulatorResult.AuthenticationRequired,
            400 to SimulatorResult.Error("Confira os valores informados.", false),
            422 to SimulatorResult.Error("Confira os valores informados.", false),
            403 to SimulatorResult.Error("O simulador está disponível somente para produtores rurais.", false),
            404 to SimulatorResult.Error("Não foi possível encontrar os dados desta granja.", false),
            429 to SimulatorResult.Error("Muitas tentativas. Aguarde e tente novamente."),
            503 to SimulatorResult.Error("O serviço do simulador está indisponível. Tente novamente."),
            418 to SimulatorResult.Error("Não foi possível carregar o simulador.")
        )
        cases.forEach { (code, expected) ->
            val api = FakeApi().apply { rankingResponse = Response.error(code, "".toResponseBody(null)) }
            assertEquals("HTTP $code", expected, SimulatorRepository(api).loadInitialData(7L))
        }
    }

    @Test
    fun loadInitialData_mapsMissingBodyIoAndUnexpectedFailures() = runBlocking {
        val missing = FakeApi().apply { rankingResponse = Response.success(null) }
        assertEquals(SimulatorResult.Error("Não foi possível carregar o simulador."), SimulatorRepository(missing).loadInitialData(7L))
        val ioApi = FakeApi().apply { rankingFailure = IOException("offline") }
        assertEquals(SimulatorResult.Error("Sem conexão. Verifique sua internet."), SimulatorRepository(ioApi).loadInitialData(7L))
        val otherApi = FakeApi().apply { rankingFailure = IllegalStateException("bad response") }
        assertEquals(SimulatorResult.Error("Ocorreu um erro inesperado."), SimulatorRepository(otherApi).loadInitialData(7L))
    }

    @Test
    fun loadInitialData_rethrowsCancellation() = runBlocking {
        val api = FakeApi().apply { rankingFailure = CancellationException("cancelled") }
        try {
            SimulatorRepository(api).loadInitialData(7L)
            throw AssertionError("CancellationException deveria ser relançada")
        } catch (_: CancellationException) { }
    }

    @Test
    fun simulate_returnsResponseAndPassesFarmAndTargetScore() = runBlocking {
        val api = FakeApi()
        assertEquals(SimulatorResult.Success(simulation()), SimulatorRepository(api).simulate(9L, 6.07))
        assertEquals(9L, api.requestedFarmId)
        assertEquals(6.07, api.requestedScore!!, 0.0)
        assertEquals(1, api.simulationCalls)
    }

    @Test
    fun simulate_mapsHttpIoUnexpectedFailuresAndMissingBody() = runBlocking {
        val denied = FakeApi().apply { simulationResponse = Response.error(403, "".toResponseBody(null)) }
        assertEquals(SimulatorResult.Error("O simulador está disponível somente para produtores rurais.", false), SimulatorRepository(denied).simulate(1L, 6.0))
        val ioApi = FakeApi().apply { simulationFailure = IOException("offline") }
        assertEquals(SimulatorResult.Error("Sem conexão. Verifique sua internet."), SimulatorRepository(ioApi).simulate(1L, 6.0))
        val otherApi = FakeApi().apply { simulationFailure = IllegalStateException("bad response") }
        assertEquals(SimulatorResult.Error("Ocorreu um erro inesperado."), SimulatorRepository(otherApi).simulate(1L, 6.0))
        val missing = FakeApi().apply { simulationResponse = Response.success(null) }
        assertTrue(SimulatorRepository(missing).simulate(1L, 6.0) is SimulatorResult.Error)
    }

    @Test
    fun simulate_rethrowsCancellation() = runBlocking {
        val api = FakeApi().apply { simulationFailure = CancellationException("cancelled") }
        try {
            SimulatorRepository(api).simulate(1L, 6.0)
            throw AssertionError("CancellationException deveria ser relançada")
        } catch (_: CancellationException) { }
    }

    @Test
    fun modelsExposeApiFieldsAndUiDefaults() {
        val ranking = ranking()
        assertEquals(42L, ranking.farmId)
        assertEquals("Granja", ranking.farmName)
        assertEquals("Sudeste", ranking.region)
        assertEquals(7.85, ranking.score, 0.0)
        assertEquals(12, ranking.rankPosition)

        val farm = farm()
        assertEquals(42L, farm.id)
        assertEquals("Granja", farm.name)
        assertEquals(50_000, farm.chickensNow)

        val simulation = simulation()
        assertEquals(42L, simulation.farmId)
        assertEquals("Granja", simulation.farmName)
        assertEquals(7.85, simulation.currentScore, 0.0)
        assertEquals(12, simulation.currentPosition)
        assertEquals("BRONZE", simulation.currentLeague)
        assertEquals(6.07, simulation.simulatedScore, 0.0)
        assertEquals(4, simulation.simulatedPosition)
        assertEquals("OURO", simulation.simulatedLeague)
        assertEquals(8, simulation.positionsGained)
        assertEquals(25, simulation.totalFarms)
        assertEquals(16.0, simulation.simulatedPercentilePct!!, 0.0)

        val initial = SimulatorInitialData(7.85, 50_000)
        assertEquals(7.85, initial.currentCgi, 0.0)
        assertEquals(50_000, initial.chickens)
        val defaultState = SimulatorUiState()
        assertFalse(defaultState.initialLoading)
        assertFalse(defaultState.simulating)
        assertFalse(defaultState.hasInitialData)
        assertEquals(0.0, defaultState.currentCgi, 0.0)
        assertEquals(0, defaultState.currentChickens)
        assertEquals("--", defaultState.projectedCgiText)
        assertEquals("--", defaultState.targetEnergyText)
        assertEquals("R$ 0", defaultState.savingsText)
        assertEquals("0", defaultState.positionsGainedText)
        assertEquals("", defaultState.simulatedLeague)
        assertNull(defaultState.errorMessage)
        assertFalse(defaultState.canRetry)
    }

    private companion object {
        fun ranking() = SimulatorRankingResponse(42L, "Granja", "Sudeste", 7.85, 12)
        fun farm(chickens: Int? = 50_000) = SimulatorFarmResponse(42L, "Granja", chickens)
        fun simulation() = SimulatorRankingSimulationResponse(42L, "Granja", 7.85, 12, "BRONZE", 6.07, 4, "OURO", 8, 25, 16.0)
    }
}
