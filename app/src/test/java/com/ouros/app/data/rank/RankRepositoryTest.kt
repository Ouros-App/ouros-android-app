package com.ouros.app.data.rank

import com.ouros.app.data.auth.AuthResult
import com.ouros.app.data.auth.SessionManager
import com.ouros.app.data.farmowner.FarmOwnerApi
import com.ouros.app.data.farmowner.FarmOwnerRepository
import com.ouros.app.data.farmowner.FarmOwnerResponse
import com.ouros.app.data.session.FarmSessionStore
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.Response

class RankRepositoryTest {

    private class FakeFarmSessionStore(var currentFarmId: Long? = null) : FarmSessionStore(null) {
        var savedOwnerId: Long? = null
        var savedFarmId: Long? = null

        override fun farmId(): Long? = currentFarmId

        override fun saveAccount(ownerId: Long?, farmId: Long?) {
            savedOwnerId = ownerId
            savedFarmId = farmId
            currentFarmId = farmId
        }
    }

    private class FakeFarmOwnerRepository(
        private val result: AuthResult<FarmOwnerResponse>
    ) : FarmOwnerRepository(
        api = object : FarmOwnerApi {
            override suspend fun getMe(authorization: String): Response<FarmOwnerResponse> =
                error("Not used")
            override suspend fun update(
                id: Long,
                authorization: String,
                request: com.ouros.app.data.farmowner.FarmOwnerUpdateRequest
            ): Response<FarmOwnerResponse> = error("Not used")
        },
        sessionManager = SessionManager(),
        refreshSession = { error("Not used") },
        clearSession = {}
    ) {
        var getMeCalls = 0

        override suspend fun getMe(): AuthResult<FarmOwnerResponse> {
            getMeCalls++
            return result
        }
    }

    private class FakeRankApi(
        private val responseProvider: (farmId: Long) -> Response<RankPodiumResponse>
    ) : RankApi {
        var requestedFarmId: Long? = null

        override suspend fun getPodium(farmId: Long): Response<RankPodiumResponse> {
            requestedFarmId = farmId
            return responseProvider(farmId)
        }
    }

    @Test
    fun load_usesCachedFarmIdWithoutCallingGetMe() = runBlocking {
        val sessionStore = FakeFarmSessionStore(currentFarmId = 10L)
        val ownerRepo = FakeFarmOwnerRepository(AuthResult.Success(fakeOwner(farmId = 10L)))
        val podiumResponse = fakePodiumResponse(currentFarmId = 10L)
        val api = FakeRankApi { Response.success(podiumResponse) }

        val repository = RankRepository(api, ownerRepo, sessionStore)
        val result = repository.load()

        assertEquals(0, ownerRepo.getMeCalls)
        assertEquals(10L, api.requestedFarmId)
        assertTrue(result is RankResult.Success)
        assertEquals(podiumResponse, (result as RankResult.Success).data)
    }

    @Test
    fun load_fetchesFarmIdFromGetMeWhenNotCached_andSavesToSession() = runBlocking {
        val sessionStore = FakeFarmSessionStore(currentFarmId = null)
        val ownerRepo = FakeFarmOwnerRepository(AuthResult.Success(fakeOwner(ownerId = 42L, farmId = 99L)))
        val podiumResponse = fakePodiumResponse(currentFarmId = 99L)
        val api = FakeRankApi { Response.success(podiumResponse) }

        val repository = RankRepository(api, ownerRepo, sessionStore)
        val result = repository.load()

        assertEquals(1, ownerRepo.getMeCalls)
        assertEquals(42L, sessionStore.savedOwnerId)
        assertEquals(99L, sessionStore.savedFarmId)
        assertEquals(99L, api.requestedFarmId)
        assertTrue(result is RankResult.Success)
    }

    @Test
    fun load_returnsErrorWhenFarmIdIsNull() = runBlocking {
        val sessionStore = FakeFarmSessionStore(currentFarmId = null)
        val ownerRepo = FakeFarmOwnerRepository(AuthResult.Success(fakeOwner(farmId = null)))
        val api = FakeRankApi { error("Should not be called") }

        val repository = RankRepository(api, ownerRepo, sessionStore)
        val result = repository.load()

        assertTrue(result is RankResult.Error)
        assertEquals("Não foi possível identificar a granja vinculada ao seu perfil.", (result as RankResult.Error).message)
        assertEquals(false, result.canRetry)
    }

    @Test
    fun load_returnsAuthenticationRequiredOn401() = runBlocking {
        val sessionStore = FakeFarmSessionStore(currentFarmId = 10L)
        val ownerRepo = FakeFarmOwnerRepository(AuthResult.Success(fakeOwner(farmId = 10L)))
        val errorBody = "{\"message\":\"Unauthorized\"}".toResponseBody("application/json".toMediaTypeOrNull())
        val api = FakeRankApi { Response.error(401, errorBody) }

        val repository = RankRepository(api, ownerRepo, sessionStore)
        val result = repository.load()

        assertEquals(RankResult.AuthenticationRequired, result)
    }

    @Test
    fun load_returnsForbiddenErrorOn403() = runBlocking {
        val sessionStore = FakeFarmSessionStore(currentFarmId = 10L)
        val ownerRepo = FakeFarmOwnerRepository(AuthResult.Success(fakeOwner(farmId = 10L)))
        val errorBody = "{\"message\":\"Forbidden\"}".toResponseBody("application/json".toMediaTypeOrNull())
        val api = FakeRankApi { Response.error(403, errorBody) }

        val repository = RankRepository(api, ownerRepo, sessionStore)
        val result = repository.load()

        assertTrue(result is RankResult.Error)
        val error = result as RankResult.Error
        assertEquals("O ranking está disponível somente para produtores rurais.", error.message)
        assertEquals(false, error.canRetry)
    }

    @Test
    fun rankPodiumResponse_computesCurrentEntryAndLeagueCorrectly() {
        val podium = RankPodiumResponse(
            currentFarmId = 10L,
            secondsUntilNextReset = 432000L,
            timeUntilNextReset = "5 dias",
            nextResetAt = "2026-10-23T00:00:00-03:00",
            rankingPodium = listOf(
                RankPodiumEntry(farmId = 7L, farmName = "Marcelina", region = "Sudeste", score = 6.05, rankPosition = 2, relation = "ABOVE"),
                RankPodiumEntry(farmId = 10L, farmName = "Você", region = "Sudeste", score = 6.07, rankPosition = 3, relation = "CURRENT"),
                RankPodiumEntry(farmId = 15L, farmName = "Vladimir", region = "Sudeste", score = 7.01, rankPosition = 4, relation = "BELOW")
            )
        )

        val current = podium.currentEntry
        assertNotNull(current)
        assertEquals(10L, current?.farmId)
        assertEquals("Você", current?.displayName)
        assertEquals(3, current?.rankPosition)
        assertEquals(RankLeague.BRONZE, podium.currentLeague)
    }

    @Test
    fun rankLeague_fromPosition_assignsLeaguesCorrectly() {
        assertEquals(RankLeague.OURO, RankLeague.fromPosition(1))
        assertEquals(RankLeague.PRATA, RankLeague.fromPosition(2))
        assertEquals(RankLeague.BRONZE, RankLeague.fromPosition(3))
        assertEquals(RankLeague.COBRE, RankLeague.fromPosition(4))
        assertEquals(RankLeague.COBRE, RankLeague.fromPosition(5))
        assertEquals(RankLeague.FERRO, RankLeague.fromPosition(6))
        assertEquals(RankLeague.FERRO, RankLeague.fromPosition(99))
    }

    @Test
    fun rankTime_toDisplayString_formatsPriorityUnitCorrectly() {
        val comDias = RankTime(days = "6 dias", hours = "4 horas", minutes = "8 minutos")
        assertEquals("6 dias", comDias.toDisplayString())

        val umDia = RankTime(days = "1 dia", hours = "4 horas", minutes = "8 minutos")
        assertEquals("1 dia", umDia.toDisplayString())

        val semDias = RankTime(days = null, hours = "4 horas", minutes = "8 minutos")
        assertEquals("4 horas", semDias.toDisplayString())

        val diasZero = RankTime(days = "0 dias", hours = "4 horas", minutes = "8 minutos")
        assertEquals("4 horas", diasZero.toDisplayString())

        val umaHora = RankTime(days = null, hours = "1 hora", minutes = "8 minutos")
        assertEquals("1 hora", umaHora.toDisplayString())

        val semHoras = RankTime(days = null, hours = "0 horas", minutes = "35 minutos")
        assertEquals("35 minutos", semHoras.toDisplayString())

        val umMinuto = RankTime(days = null, hours = null, minutes = "1 minuto")
        assertEquals("1 minuto", umMinuto.toDisplayString())

        val segundos = RankTime(days = null, hours = "0 horas", minutes = "0 minutos", seconds = "45 segundos")
        assertEquals("45 segundos", segundos.toDisplayString())

        val vazio = RankTime()
        assertEquals("Em instantes", vazio.toDisplayString())
    }

    @Test
    fun formatRemainingTime_formatsCorrectlyAccordingToUserRules() {
        assertEquals("6 dias", formatRemainingTime(86_400L * 6 + 3600L))
        assertEquals("1 dia", formatRemainingTime(86_400L * 1 + 7200L))

        assertEquals("4 horas", formatRemainingTime(3_600L * 4 + 600L))
        assertEquals("1 hora", formatRemainingTime(3_600L * 1 + 120L))

        assertEquals("35 minutos", formatRemainingTime(60L * 35 + 20L))
        assertEquals("1 minuto", formatRemainingTime(60L * 1))

        assertEquals("45 segundos", formatRemainingTime(45L))
        assertEquals("1 segundo", formatRemainingTime(1L))
        assertEquals("Em instantes", formatRemainingTime(0L))
        assertEquals("Em instantes", formatRemainingTime(-10L))
        assertEquals("Em instantes", formatRemainingTime(null))
    }

    @Test
    fun rankPodiumResponse_deserializesTimeObjectCorrectly() {
        val json = """
            {
                "current_farm_id": 10,
                "seconds_until_next_reset": 518400,
                "time": {
                    "days": "6 dias",
                    "hours": "4 horas",
                    "minutes": "8 minutos"
                },
                "next_reset_at": "2026-10-23T00:00:00-03:00",
                "ranking_podium": []
            }
        """.trimIndent()

        val parsed = com.google.gson.Gson().fromJson(json, RankPodiumResponse::class.java)
        assertNotNull(parsed.time)
        assertEquals("6 dias", parsed.time?.days)
        assertEquals("4 horas", parsed.time?.hours)
        assertEquals("8 minutos", parsed.time?.minutes)
        assertEquals("6 dias", parsed.time?.toDisplayString())
    }

    @Test
    fun load_returnsNotFoundErrorOn404() = runBlocking {
        val sessionStore = FakeFarmSessionStore(currentFarmId = 10L)
        val ownerRepo = FakeFarmOwnerRepository(AuthResult.Success(fakeOwner(farmId = 10L)))
        val api = FakeRankApi { Response.error(404, "".toResponseBody(null)) }

        val repository = RankRepository(api, ownerRepo, sessionStore)
        val result = repository.load()

        assertTrue(result is RankResult.Error)
        val error = result as RankResult.Error
        assertEquals("Ainda não existe um ranking para esta granja.", error.message)
        assertEquals(false, error.canRetry)
    }

    @Test
    fun load_returnsErrorWhenCurrentEntryMissingFromPodium() = runBlocking {
        val sessionStore = FakeFarmSessionStore(currentFarmId = 10L)
        val ownerRepo = FakeFarmOwnerRepository(AuthResult.Success(fakeOwner(farmId = 10L)))
        val otherEntry = RankPodiumEntry(farmId = 99L, farmName = "Outra", region = null, score = 5.0, rankPosition = 1, relation = "ABOVE")
        val api = FakeRankApi {
            Response.success(RankPodiumResponse(currentFarmId = 10L, rankingPodium = listOf(otherEntry)))
        }

        val repository = RankRepository(api, ownerRepo, sessionStore)
        val result = repository.load()

        assertTrue(result is RankResult.Error)
        assertEquals("Não foi possível identificar sua posição no ranking.", (result as RankResult.Error).message)
    }

    @Test
    fun load_returnsErrorOnNetworkException() = runBlocking {
        val sessionStore = FakeFarmSessionStore(currentFarmId = 10L)
        val ownerRepo = FakeFarmOwnerRepository(AuthResult.Success(fakeOwner(farmId = 10L)))
        val api = FakeRankApi { throw java.io.IOException("No network") }

        val repository = RankRepository(api, ownerRepo, sessionStore)
        val result = repository.load()

        assertTrue(result is RankResult.Error)
        assertEquals("Sem conexão. Verifique sua internet.", (result as RankResult.Error).message)
        assertTrue(result.canRetry)
    }

    @Test
    fun load_returnsErrorOnGenericException() = runBlocking {
        val sessionStore = FakeFarmSessionStore(currentFarmId = 10L)
        val ownerRepo = FakeFarmOwnerRepository(AuthResult.Success(fakeOwner(farmId = 10L)))
        val api = FakeRankApi { throw RuntimeException("Boom") }

        val repository = RankRepository(api, ownerRepo, sessionStore)
        val result = repository.load()

        assertTrue(result is RankResult.Error)
        assertEquals("Ocorreu um erro inesperado ao carregar o ranking.", (result as RankResult.Error).message)
        assertTrue(result.canRetry)
    }

    @Test
    fun load_returnsAuthenticationRequiredWhenGetMeReturns401() = runBlocking {
        val sessionStore = FakeFarmSessionStore(currentFarmId = null)
        val ownerRepo = FakeFarmOwnerRepository(AuthResult.Error("Unauthorized", statusCode = 401))
        val api = FakeRankApi { error("Not called") }

        val repository = RankRepository(api, ownerRepo, sessionStore)
        val result = repository.load()

        assertEquals(RankResult.AuthenticationRequired, result)
    }

    @Test
    fun load_returnsNonRetryableErrorWhenGetMeReturns403() = runBlocking {
        val sessionStore = FakeFarmSessionStore(currentFarmId = null)
        val ownerRepo = FakeFarmOwnerRepository(AuthResult.Error("Sem permissão", statusCode = 403))
        val api = FakeRankApi { error("Not called") }

        val repository = RankRepository(api, ownerRepo, sessionStore)
        val result = repository.load()

        assertTrue(result is RankResult.Error)
        val error = result as RankResult.Error
        assertEquals("Sem permissão", error.message)
        assertEquals(false, error.canRetry)
    }

    @Test
    fun load_returnsTooManyRequestsErrorOn429() = runBlocking {
        val sessionStore = FakeFarmSessionStore(currentFarmId = 10L)
        val ownerRepo = FakeFarmOwnerRepository(AuthResult.Success(fakeOwner(farmId = 10L)))
        val api = FakeRankApi { Response.error(429, "".toResponseBody(null)) }

        val repository = RankRepository(api, ownerRepo, sessionStore)
        val result = repository.load()

        assertTrue(result is RankResult.Error)
        val error = result as RankResult.Error
        assertEquals("Muitas tentativas. Aguarde e tente novamente.", error.message)
        assertTrue(error.canRetry)
    }

    @Test
    fun load_returnsServerErrorOn500() = runBlocking {
        val sessionStore = FakeFarmSessionStore(currentFarmId = 10L)
        val ownerRepo = FakeFarmOwnerRepository(AuthResult.Success(fakeOwner(farmId = 10L)))
        val api = FakeRankApi { Response.error(503, "".toResponseBody(null)) }

        val repository = RankRepository(api, ownerRepo, sessionStore)
        val result = repository.load()

        assertTrue(result is RankResult.Error)
        val error = result as RankResult.Error
        assertEquals("O serviço de ranking está indisponível. Tente novamente.", error.message)
        assertTrue(error.canRetry)
    }

    @Test
    fun load_returnsGenericErrorOnUnexpectedStatus() = runBlocking {
        val sessionStore = FakeFarmSessionStore(currentFarmId = 10L)
        val ownerRepo = FakeFarmOwnerRepository(AuthResult.Success(fakeOwner(farmId = 10L)))
        val api = FakeRankApi { Response.error(418, "".toResponseBody(null)) }

        val repository = RankRepository(api, ownerRepo, sessionStore)
        val result = repository.load()

        assertTrue(result is RankResult.Error)
        val error = result as RankResult.Error
        assertEquals("Não foi possível carregar o ranking.", error.message)
        assertTrue(error.canRetry)
    }

    @Test
    fun load_sortsPodiumEntriesByRankPosition() = runBlocking {
        val sessionStore = FakeFarmSessionStore(currentFarmId = 10L)
        val ownerRepo = FakeFarmOwnerRepository(AuthResult.Success(fakeOwner(farmId = 10L)))
        val entry1 = RankPodiumEntry(farmId = 10L, farmName = "Current", region = null, score = 10.0, rankPosition = 2, relation = "CURRENT")
        val entry2 = RankPodiumEntry(farmId = 20L, farmName = "Top", region = null, score = 20.0, rankPosition = 1, relation = "ABOVE")
        val api = FakeRankApi {
            Response.success(RankPodiumResponse(currentFarmId = 10L, rankingPodium = listOf(entry1, entry2)))
        }

        val repository = RankRepository(api, ownerRepo, sessionStore)
        val result = repository.load()

        assertTrue(result is RankResult.Success)
        val podium = (result as RankResult.Success).data.rankingPodium
        assertEquals(1, podium?.first()?.rankPosition)
        assertEquals(2, podium?.last()?.rankPosition)
    }

    @Test
    fun load_rethrowsCancellationException() = runBlocking {
        val sessionStore = FakeFarmSessionStore(currentFarmId = 10L)
        val ownerRepo = FakeFarmOwnerRepository(AuthResult.Success(fakeOwner(farmId = 10L)))
        val api = FakeRankApi { throw kotlinx.coroutines.CancellationException("Cancelled") }

        val repository = RankRepository(api, ownerRepo, sessionStore)
        try {
            repository.load()
            fail("Should have rethrown CancellationException")
        } catch (_: kotlinx.coroutines.CancellationException) {
            // Expected
        }
    }

    private fun fakeOwner(ownerId: Long = 1L, farmId: Long? = 10L) = FarmOwnerResponse(
        id = ownerId,
        name = "Lucas Porto",
        documentNumber = null,
        email = "lucas@example.com",
        telephone = null,
        farmId = farmId,
        firstAccess = false,
        photoUrl = null
    )

    private fun fakePodiumResponse(currentFarmId: Long) = RankPodiumResponse(
        currentFarmId = currentFarmId,
        secondsUntilNextReset = 3600L,
        timeUntilNextReset = "1 hora",
        nextResetAt = null,
        rankingPodium = listOf(
            RankPodiumEntry(
                farmId = currentFarmId,
                farmName = "Você",
                region = "Sudeste",
                score = 10.0,
                rankPosition = 1,
                relation = "CURRENT"
            )
        )
    )
}
