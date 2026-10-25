package com.ouros.app.data.dashboard

import com.ouros.app.data.auth.AuthApi
import com.ouros.app.data.auth.AuthRepository
import com.ouros.app.data.auth.RefreshTokenStorage
import com.ouros.app.data.auth.SessionManager
import com.ouros.app.data.auth.TokenResponse
import com.ouros.app.data.session.FarmSessionStore
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.Response

class DashboardRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private class FakeFarmSessionStore(private val currentFarmId: Long? = 101L) : FarmSessionStore(null) {
        override fun farmId(): Long? = currentFarmId
    }

    private class FakeRefreshTokenStorage(var token: String? = null) : RefreshTokenStorage {
        var clearCalled = false
        override fun getRefreshToken(): String? = token
        override fun saveRefreshToken(token: String) { this.token = token }
        override fun clear() {
            token = null
            clearCalled = true
        }
    }

    private class FakeAuthApi : AuthApi {
        var refreshResponse: Response<TokenResponse>? = null

        override suspend fun startLogin(request: com.ouros.app.data.auth.LoginStartRequest) = error("Not used")
        override suspend fun verifyLogin(request: com.ouros.app.data.auth.LoginVerifyRequest) = error("Not used")
        override suspend fun refreshToken(request: com.ouros.app.data.auth.RefreshRequest): Response<TokenResponse> {
            return refreshResponse ?: error("refreshResponse not set")
        }
        override suspend fun startPasswordReset(request: com.ouros.app.data.auth.PasswordResetStartRequest) = error("Not used")
        override suspend fun verifyPasswordReset(request: com.ouros.app.data.auth.PasswordResetVerifyRequest) = error("Not used")
        override suspend fun confirmPasswordReset(request: com.ouros.app.data.auth.PasswordResetConfirmRequest) = error("Not used")
    }

    private class FakeDashboardApi : DashboardApi {
        var statusResponse: Response<DashboardStatusResponse>? = null
        var customResponse: Response<DashboardResponse>? = null
        var statusResponsesQueue: MutableList<Response<DashboardStatusResponse>> = mutableListOf()
        var customResponsesQueue: MutableList<Response<DashboardResponse>> = mutableListOf()
        var statusException: Exception? = null
        var customException: Exception? = null
        var lastStatusAuthHeader: String? = null
        var lastCustomAuthHeader: String? = null
        var lastCustomRequest: DashboardRequest? = null
        var statusCallCount = 0
        var customCallCount = 0

        override suspend fun getStatus(authorization: String): Response<DashboardStatusResponse> {
            statusCallCount++
            lastStatusAuthHeader = authorization
            statusException?.let { throw it }
            if (statusResponsesQueue.isNotEmpty()) {
                return statusResponsesQueue.removeAt(0)
            }
            return statusResponse ?: error("statusResponse not set")
        }

        override suspend fun getCustomDashboard(
            authorization: String,
            request: DashboardRequest
        ): Response<DashboardResponse> {
            customCallCount++
            lastCustomAuthHeader = authorization
            lastCustomRequest = request
            customException?.let { throw it }
            if (customResponsesQueue.isNotEmpty()) {
                return customResponsesQueue.removeAt(0)
            }
            return customResponse ?: error("customResponse not set")
        }
    }

    private fun createTokenResponse(
        accessToken: String = "refreshed-jwt",
        refreshToken: String? = "new-refresh-token"
    ): TokenResponse {
        return TokenResponse(
            accessToken = accessToken,
            expiresIn = 3600L,
            refreshExpiresIn = 7200L,
            refreshToken = refreshToken,
            tokenType = "Bearer",
            scope = "read write"
        )
    }

    private fun createValidDashboardResponse(): DashboardResponse {
        return DashboardResponse(
            title = "Telemetria de Consumo",
            charts = listOf(
                DashboardChartResponse(
                    id = DashboardRepository.CHART_CONSUMPTION_INDEX,
                    title = "Eficiência",
                    renderAs = "line",
                    html = "<div>chart1</div>"
                ),
                DashboardChartResponse(
                    id = DashboardRepository.CHART_MONTHLY_WATER_CONSUMPTION,
                    title = "Água",
                    renderAs = "bar",
                    html = "<div>chart2</div>"
                ),
                DashboardChartResponse(
                    id = DashboardRepository.CHART_MONTHLY_ENERGY_CONSUMPTION,
                    title = "Energia",
                    renderAs = "bar",
                    html = "<div>chart3</div>"
                )
            )
        )
    }

    private fun createRepository(
        api: DashboardApi,
        sessionManager: SessionManager = SessionManager().apply { setAccessToken("test-token", 3600L) },
        cache: DashboardCache = DashboardCache(temporaryFolder.root),
        farmSessionStore: FarmSessionStore = FakeFarmSessionStore(),
        authApi: AuthApi = FakeAuthApi(),
        refreshTokenStorage: RefreshTokenStorage = FakeRefreshTokenStorage()
    ): DashboardRepository {
        val authRepository = AuthRepository(
            api = authApi,
            refreshTokenStore = refreshTokenStorage,
            sessionManager = sessionManager
        )
        return DashboardRepository(
            api = api,
            authRepository = authRepository,
            sessionManager = sessionManager,
            cache = cache,
            farmSessionStore = farmSessionStore
        )
    }

    @Test
    fun loadWhenStatusIsFreshRequestsCustomDashboardAndReturnsSuccessWithoutNotice() = runBlocking {
        val api = FakeDashboardApi().apply {
            statusResponse = Response.success(
                DashboardStatusResponse(
                    status = "fresh",
                    lastUpdatedAt = "2026-10-24T02:00:00Z",
                    ageSeconds = 300L,
                    staleAfterSeconds = 2700L
                )
            )
            customResponse = Response.success(createValidDashboardResponse())
        }
        val repository = createRepository(api)

        val result = repository.load(DashboardPeriod.MONTH)

        assertTrue(result is DashboardResult.Success)
        val success = result as DashboardResult.Success
        assertNull(success.notice)
        assertEquals(3, success.dashboard.charts.size)
        assertNotNull(api.lastCustomRequest)
        assertEquals(DashboardPeriod.ANNUAL_COMPARISON_DAYS, api.lastCustomRequest?.periodDays)
    }

    @Test
    fun loadWhenStatusIsStaleRequestsCustomDashboardAndReturnsSuccessWithStaleNotice() = runBlocking {
        val api = FakeDashboardApi().apply {
            statusResponse = Response.success(
                DashboardStatusResponse(
                    status = "stale",
                    lastUpdatedAt = "2026-10-24T00:00:00Z",
                    ageSeconds = 7200L,
                    staleAfterSeconds = 2700L
                )
            )
            customResponse = Response.success(createValidDashboardResponse())
        }
        val repository = createRepository(api)

        val result = repository.load(DashboardPeriod.MONTH)

        assertTrue(result is DashboardResult.Success)
        val success = result as DashboardResult.Success
        assertEquals("Exibindo dados da última sincronização disponível.", success.notice)
        assertEquals(3, success.dashboard.charts.size)
        assertNotNull(api.lastCustomRequest)
    }

    @Test
    fun loadWhenStatusIsUnknownRequestsCustomDashboardAndReturnsSuccessWithUpdatingNotice() = runBlocking {
        val api = FakeDashboardApi().apply {
            statusResponse = Response.success(
                DashboardStatusResponse(
                    status = "unknown",
                    lastUpdatedAt = null,
                    ageSeconds = null,
                    staleAfterSeconds = 2700L
                )
            )
            customResponse = Response.success(createValidDashboardResponse())
        }
        val repository = createRepository(api)

        val result = repository.load(DashboardPeriod.WEEK)

        assertTrue(result is DashboardResult.Success)
        val success = result as DashboardResult.Success
        assertEquals("Dados de telemetria em atualização.", success.notice)
        assertEquals(3, success.dashboard.charts.size)
        assertEquals(DashboardPeriod.ANNUAL_COMPARISON_DAYS, api.lastCustomRequest?.periodDays)
    }

    @Test
    fun loadWhenStatusIs503ReturnsCachedResultOrError() = runBlocking {
        val api = FakeDashboardApi().apply {
            val errorBody = "{\"detail\":\"User analytics is temporarily unavailable\"}"
                .toResponseBody("application/json".toMediaTypeOrNull())
            statusResponse = Response.error(503, errorBody)
        }
        val repository = createRepository(api)

        val result = repository.load(DashboardPeriod.MONTH)

        assertTrue(result is DashboardResult.Error)
        val error = result as DashboardResult.Error
        assertEquals("Dados de telemetria em atualização.", error.message)
        assertTrue(error.canRetry)
        assertNull(api.lastCustomRequest)
    }

    @Test
    fun loadWhenStatusIs503WithExistingCacheReturnsCachedSuccess() = runBlocking {
        val cache = DashboardCache(temporaryFolder.root)
        val cached = createValidDashboardResponse()
        cache.save(101L, cached)

        val api = FakeDashboardApi().apply {
            val errorBody = "{\"detail\":\"Temporarily unavailable\"}"
                .toResponseBody("application/json".toMediaTypeOrNull())
            statusResponse = Response.error(503, errorBody)
        }
        val repository = createRepository(api = api, cache = cache)

        val result = repository.load(DashboardPeriod.MONTH)

        assertTrue(result is DashboardResult.Success)
        val success = result as DashboardResult.Success
        assertEquals("Dados de telemetria em atualização.", success.notice)
        assertEquals(cached.title, success.dashboard.title)
    }

    @Test
    fun loadWhenStatusIs403ReturnsErrorNonRetryable() = runBlocking {
        val api = FakeDashboardApi().apply {
            val errorBody = "{\"detail\":\"Forbidden\"}"
                .toResponseBody("application/json".toMediaTypeOrNull())
            statusResponse = Response.error(403, errorBody)
        }
        val repository = createRepository(api)

        val result = repository.load(DashboardPeriod.MONTH)

        assertTrue(result is DashboardResult.Error)
        val error = result as DashboardResult.Error
        assertEquals("Este conteúdo não está disponível para o seu perfil.", error.message)
        assertFalse(error.canRetry)
        assertNull(api.lastCustomRequest)
    }

    @Test
    fun loadWhenStatusReturns500ReturnsErrorRetryable() = runBlocking {
        val api = FakeDashboardApi().apply {
            val errorBody = "{\"detail\":\"Internal server error\"}"
                .toResponseBody("application/json".toMediaTypeOrNull())
            statusResponse = Response.error(500, errorBody)
        }
        val repository = createRepository(api)

        val result = repository.load(DashboardPeriod.MONTH)

        assertTrue(result is DashboardResult.Error)
        val error = result as DashboardResult.Error
        assertEquals("Não foi possível verificar os dados.", error.message)
        assertTrue(error.canRetry)
    }

    @Test
    fun loadWhenCustomDashboardReturns503FallsBackToCacheOrError() = runBlocking {
        val api = FakeDashboardApi().apply {
            statusResponse = Response.success(
                DashboardStatusResponse(
                    status = "fresh",
                    lastUpdatedAt = "2026-10-24T02:00:00Z",
                    ageSeconds = 100L,
                    staleAfterSeconds = 2700L
                )
            )
            val errorBody = "{\"detail\":\"User analytics is temporarily unavailable\"}"
                .toResponseBody("application/json".toMediaTypeOrNull())
            customResponse = Response.error(503, errorBody)
        }
        val repository = createRepository(api)

        val result = repository.load(DashboardPeriod.MONTH)

        assertTrue(result is DashboardResult.Error)
        val error = result as DashboardResult.Error
        assertEquals("Serviço temporariamente indisponível.", error.message)
        assertTrue(error.canRetry)
    }

    @Test
    fun loadWhenCustomDashboardReturns503WithCacheReturnsCachedSuccess() = runBlocking {
        val cache = DashboardCache(temporaryFolder.root)
        val cached = createValidDashboardResponse()
        cache.save(101L, cached)

        val api = FakeDashboardApi().apply {
            statusResponse = Response.success(
                DashboardStatusResponse(
                    status = "fresh",
                    lastUpdatedAt = "2026-10-24T02:00:00Z",
                    ageSeconds = 100L,
                    staleAfterSeconds = 2700L
                )
            )
            val errorBody = "{\"detail\":\"Unavailable\"}"
                .toResponseBody("application/json".toMediaTypeOrNull())
            customResponse = Response.error(503, errorBody)
        }
        val repository = createRepository(api = api, cache = cache)

        val result = repository.load(DashboardPeriod.MONTH)

        assertTrue(result is DashboardResult.Success)
        val success = result as DashboardResult.Success
        assertEquals("Serviço temporariamente indisponível.", success.notice)
        assertEquals(cached.title, success.dashboard.title)
    }

    @Test
    fun loadWhenCustomDashboardReturns403ReturnsErrorNonRetryable() = runBlocking {
        val api = FakeDashboardApi().apply {
            statusResponse = Response.success(
                DashboardStatusResponse(status = "fresh", lastUpdatedAt = null, ageSeconds = null, staleAfterSeconds = null)
            )
            val errorBody = "{\"detail\":\"Forbidden\"}"
                .toResponseBody("application/json".toMediaTypeOrNull())
            customResponse = Response.error(403, errorBody)
        }
        val repository = createRepository(api)

        val result = repository.load(DashboardPeriod.MONTH)

        assertTrue(result is DashboardResult.Error)
        val error = result as DashboardResult.Error
        assertEquals("Este conteúdo não está disponível para o seu perfil.", error.message)
        assertFalse(error.canRetry)
    }

    @Test
    fun loadWhenCustomDashboardReturns422ReturnsErrorNonRetryable() = runBlocking {
        val api = FakeDashboardApi().apply {
            statusResponse = Response.success(
                DashboardStatusResponse(status = "fresh", lastUpdatedAt = null, ageSeconds = null, staleAfterSeconds = null)
            )
            val errorBody = "{\"detail\":\"Unprocessable Entity\"}"
                .toResponseBody("application/json".toMediaTypeOrNull())
            customResponse = Response.error(422, errorBody)
        }
        val repository = createRepository(api)

        val result = repository.load(DashboardPeriod.MONTH)

        assertTrue(result is DashboardResult.Error)
        val error = result as DashboardResult.Error
        assertEquals("A configuração solicitada não é válida.", error.message)
        assertFalse(error.canRetry)
    }

    @Test
    fun loadWhenCustomDashboardMissingRequiredChartsReturnsErrorRetryable() = runBlocking {
        val incompleteDashboard = DashboardResponse(
            title = "Incomplete",
            charts = listOf(
                DashboardChartResponse(id = "only-one-chart", title = "T", renderAs = "bar", html = "<p>ok</p>")
            )
        )
        val api = FakeDashboardApi().apply {
            statusResponse = Response.success(
                DashboardStatusResponse(status = "fresh", lastUpdatedAt = null, ageSeconds = null, staleAfterSeconds = null)
            )
            customResponse = Response.success(incompleteDashboard)
        }
        val repository = createRepository(api)

        val result = repository.load(DashboardPeriod.MONTH)

        assertTrue(result is DashboardResult.Error)
        val error = result as DashboardResult.Error
        assertEquals("A API não retornou todos os gráficos.", error.message)
        assertTrue(error.canRetry)
    }

    @Test
    fun loadWhenCustomDashboardReturns500ReturnsErrorRetryable() = runBlocking {
        val api = FakeDashboardApi().apply {
            statusResponse = Response.success(
                DashboardStatusResponse(status = "fresh", lastUpdatedAt = null, ageSeconds = null, staleAfterSeconds = null)
            )
            val errorBody = "{\"detail\":\"Server error\"}".toResponseBody("application/json".toMediaTypeOrNull())
            customResponse = Response.error(500, errorBody)
        }
        val repository = createRepository(api)

        val result = repository.load(DashboardPeriod.MONTH)

        assertTrue(result is DashboardResult.Error)
        val error = result as DashboardResult.Error
        assertEquals("Não foi possível carregar os gráficos.", error.message)
        assertTrue(error.canRetry)
    }

    @Test
    fun loadWhenThrowsIOExceptionWithoutCacheReturnsErrorRetryable() = runBlocking {
        val api = FakeDashboardApi().apply {
            statusException = IOException("No internet connection")
        }
        val repository = createRepository(api)

        val result = repository.load(DashboardPeriod.MONTH)

        assertTrue(result is DashboardResult.Error)
        val error = result as DashboardResult.Error
        assertEquals("Sem conexão. Exibindo os últimos dados disponíveis.", error.message)
        assertTrue(error.canRetry)
    }

    @Test
    fun loadWhenThrowsIOExceptionWithCacheReturnsCachedSuccess() = runBlocking {
        val cache = DashboardCache(temporaryFolder.root)
        val cached = createValidDashboardResponse()
        cache.save(101L, cached)

        val api = FakeDashboardApi().apply {
            statusException = IOException("Network failure")
        }
        val repository = createRepository(api = api, cache = cache)

        val result = repository.load(DashboardPeriod.MONTH)

        assertTrue(result is DashboardResult.Success)
        val success = result as DashboardResult.Success
        assertEquals("Sem conexão. Exibindo os últimos dados disponíveis.", success.notice)
        assertEquals(cached.title, success.dashboard.title)
    }

    @Test
    fun loadWhenThrowsGenericExceptionWithoutCacheReturnsErrorRetryable() = runBlocking {
        val api = FakeDashboardApi().apply {
            statusException = RuntimeException("Unexpected parsing error")
        }
        val repository = createRepository(api)

        val result = repository.load(DashboardPeriod.MONTH)

        assertTrue(result is DashboardResult.Error)
        val error = result as DashboardResult.Error
        assertEquals("Não foi possível atualizar os dados.", error.message)
        assertTrue(error.canRetry)
    }

    @Test
    fun loadWhenThrowsGenericExceptionWithCacheReturnsCachedSuccess() = runBlocking {
        val cache = DashboardCache(temporaryFolder.root)
        val cached = createValidDashboardResponse()
        cache.save(101L, cached)

        val api = FakeDashboardApi().apply {
            statusException = RuntimeException("Unexpected parsing error")
        }
        val repository = createRepository(api = api, cache = cache)

        val result = repository.load(DashboardPeriod.MONTH)

        assertTrue(result is DashboardResult.Success)
        val success = result as DashboardResult.Success
        assertEquals("Não foi possível atualizar os dados.", success.notice)
        assertEquals(cached.title, success.dashboard.title)
    }

    @Test
    fun loadWhenThrowsCancellationExceptionRethrows(): Unit = runBlocking {
        val api = FakeDashboardApi().apply {
            statusException = CancellationException("Job was cancelled")
        }
        val repository = createRepository(api)

        assertThrows(CancellationException::class.java) {
            runBlocking {
                repository.load(DashboardPeriod.MONTH)
            }
        }
    }

    @Test
    fun loadWhenNoInitialAccessTokenAndRefreshSucceedsMakesCallWithRefreshedToken() = runBlocking {
        val sessionManager = SessionManager() // sem token
        val refreshTokenStorage = FakeRefreshTokenStorage(token = "valid-refresh-token")
        val authApi = FakeAuthApi().apply {
            refreshResponse = Response.success(createTokenResponse(accessToken = "refreshed-jwt"))
        }
        val api = FakeDashboardApi().apply {
            statusResponse = Response.success(
                DashboardStatusResponse(status = "fresh", lastUpdatedAt = null, ageSeconds = null, staleAfterSeconds = null)
            )
            customResponse = Response.success(createValidDashboardResponse())
        }
        val repository = createRepository(
            api = api,
            sessionManager = sessionManager,
            authApi = authApi,
            refreshTokenStorage = refreshTokenStorage
        )

        val result = repository.load(DashboardPeriod.MONTH)

        assertTrue(result is DashboardResult.Success)
        assertEquals("Bearer refreshed-jwt", api.lastStatusAuthHeader)
        assertEquals("Bearer refreshed-jwt", api.lastCustomAuthHeader)
    }

    @Test
    fun loadWhenNoInitialAccessTokenAndNoSavedSessionReturnsAuthenticationRequired() = runBlocking {
        val sessionManager = SessionManager() // sem token
        val refreshTokenStorage = FakeRefreshTokenStorage(token = null)
        val api = FakeDashboardApi()
        val repository = createRepository(
            api = api,
            sessionManager = sessionManager,
            refreshTokenStorage = refreshTokenStorage
        )

        val result = repository.load(DashboardPeriod.MONTH)

        assertEquals(DashboardResult.AuthenticationRequired, result)
        assertEquals(0, api.statusCallCount)
    }

    @Test
    fun loadWhenNoInitialAccessTokenAndRefreshReturns401ReturnsAuthenticationRequired() = runBlocking {
        val sessionManager = SessionManager()
        val refreshTokenStorage = FakeRefreshTokenStorage(token = "invalid-refresh")
        val authApi = FakeAuthApi().apply {
            val errorBody = "{\"detail\":\"Unauthorized\"}".toResponseBody("application/json".toMediaTypeOrNull())
            refreshResponse = Response.error(401, errorBody)
        }
        val api = FakeDashboardApi()
        val repository = createRepository(
            api = api,
            sessionManager = sessionManager,
            authApi = authApi,
            refreshTokenStorage = refreshTokenStorage
        )

        val result = repository.load(DashboardPeriod.MONTH)

        assertEquals(DashboardResult.AuthenticationRequired, result)
        assertEquals(0, api.statusCallCount)
    }

    @Test
    fun loadWhenNoInitialAccessTokenAndRefreshReturns500ReturnsTemporaryFailureOrCache() = runBlocking {
        val sessionManager = SessionManager()
        val refreshTokenStorage = FakeRefreshTokenStorage(token = "refresh-token")
        val authApi = FakeAuthApi().apply {
            val errorBody = "{\"detail\":\"Server error\"}".toResponseBody("application/json".toMediaTypeOrNull())
            refreshResponse = Response.error(500, errorBody)
        }
        val api = FakeDashboardApi()
        val repository = createRepository(
            api = api,
            sessionManager = sessionManager,
            authApi = authApi,
            refreshTokenStorage = refreshTokenStorage
        )

        val result = repository.load(DashboardPeriod.MONTH)

        assertTrue(result is DashboardResult.Error)
        val error = result as DashboardResult.Error
        assertEquals("Não foi possível atualizar os dados.", error.message)
        assertTrue(error.canRetry)
    }

    @Test
    fun loadWhenFirstCallReturns401AndRefreshSucceedsRetriesAndSucceeds() = runBlocking {
        val sessionManager = SessionManager().apply { setAccessToken("expired-token", 3600L) }
        val refreshTokenStorage = FakeRefreshTokenStorage(token = "valid-refresh")
        val authApi = FakeAuthApi().apply {
            refreshResponse = Response.success(createTokenResponse(accessToken = "brand-new-token"))
        }
        val api = FakeDashboardApi().apply {
            val error401 = "{\"detail\":\"Expired\"}".toResponseBody("application/json".toMediaTypeOrNull())
            statusResponsesQueue.add(Response.error(401, error401))
            statusResponsesQueue.add(Response.success(
                DashboardStatusResponse(status = "fresh", lastUpdatedAt = null, ageSeconds = null, staleAfterSeconds = null)
            ))
            customResponse = Response.success(createValidDashboardResponse())
        }
        val repository = createRepository(
            api = api,
            sessionManager = sessionManager,
            authApi = authApi,
            refreshTokenStorage = refreshTokenStorage
        )

        val result = repository.load(DashboardPeriod.MONTH)

        assertTrue(result is DashboardResult.Success)
        assertEquals(2, api.statusCallCount)
        assertEquals("Bearer brand-new-token", api.lastStatusAuthHeader)
    }

    @Test
    fun loadWhenFirstCallReturns401AndRetryAlsoReturns401ClearsSessionAndReturnsAuthenticationRequired() = runBlocking {
        val sessionManager = SessionManager().apply { setAccessToken("token1", 3600L) }
        val refreshTokenStorage = FakeRefreshTokenStorage(token = "refresh-token")
        val authApi = FakeAuthApi().apply {
            refreshResponse = Response.success(createTokenResponse(accessToken = "token2"))
        }
        val api = FakeDashboardApi().apply {
            val error401 = "{\"detail\":\"Unauthorized\"}".toResponseBody("application/json".toMediaTypeOrNull())
            statusResponsesQueue.add(Response.error(401, error401))
            statusResponsesQueue.add(Response.error(401, error401))
        }
        val repository = createRepository(
            api = api,
            sessionManager = sessionManager,
            authApi = authApi,
            refreshTokenStorage = refreshTokenStorage
        )

        val result = repository.load(DashboardPeriod.MONTH)

        assertEquals(DashboardResult.AuthenticationRequired, result)
        assertEquals(2, api.statusCallCount)
        assertTrue(refreshTokenStorage.clearCalled)
        assertNull(sessionManager.accessToken())
    }

    @Test
    fun loadWhenFirstCallReturns401AndRefreshReturns401ReturnsAuthenticationRequired() = runBlocking {
        val sessionManager = SessionManager().apply { setAccessToken("expired-token", 3600L) }
        val refreshTokenStorage = FakeRefreshTokenStorage(token = "revoked-refresh")
        val authApi = FakeAuthApi().apply {
            val errorBody = "{\"detail\":\"Refresh expired\"}".toResponseBody("application/json".toMediaTypeOrNull())
            refreshResponse = Response.error(401, errorBody)
        }
        val api = FakeDashboardApi().apply {
            val error401 = "{\"detail\":\"Unauthorized\"}".toResponseBody("application/json".toMediaTypeOrNull())
            statusResponse = Response.error(401, error401)
        }
        val repository = createRepository(
            api = api,
            sessionManager = sessionManager,
            authApi = authApi,
            refreshTokenStorage = refreshTokenStorage
        )

        val result = repository.load(DashboardPeriod.MONTH)

        assertEquals(DashboardResult.AuthenticationRequired, result)
        assertEquals(1, api.statusCallCount)
    }

    @Test
    fun loadWhenFirstCallReturns401AndRefreshFailsTemporaryReturnsTemporaryFailureOrCache() = runBlocking {
        val sessionManager = SessionManager().apply { setAccessToken("expired-token", 3600L) }
        val refreshTokenStorage = FakeRefreshTokenStorage(token = "valid-refresh")
        val authApi = FakeAuthApi().apply {
            val errorBody = "{\"detail\":\"Network timeout\"}".toResponseBody("application/json".toMediaTypeOrNull())
            refreshResponse = Response.error(500, errorBody)
        }
        val api = FakeDashboardApi().apply {
            val error401 = "{\"detail\":\"Unauthorized\"}".toResponseBody("application/json".toMediaTypeOrNull())
            statusResponse = Response.error(401, error401)
        }
        val repository = createRepository(
            api = api,
            sessionManager = sessionManager,
            authApi = authApi,
            refreshTokenStorage = refreshTokenStorage
        )

        val result = repository.load(DashboardPeriod.MONTH)

        assertTrue(result is DashboardResult.Error)
        val error = result as DashboardResult.Error
        assertEquals("Não foi possível atualizar os dados.", error.message)
        assertTrue(error.canRetry)
    }

    @Test
    fun loadWhenSuccessfulSavesDashboardToCacheForFarmId() = runBlocking {
        val cache = DashboardCache(temporaryFolder.root)
        val api = FakeDashboardApi().apply {
            statusResponse = Response.success(
                DashboardStatusResponse(status = "fresh", lastUpdatedAt = null, ageSeconds = null, staleAfterSeconds = null)
            )
            customResponse = Response.success(createValidDashboardResponse())
        }
        val repository = createRepository(api = api, cache = cache, farmSessionStore = FakeFarmSessionStore(currentFarmId = 999L))

        val result = repository.load(DashboardPeriod.MONTH)

        assertTrue(result is DashboardResult.Success)
        val saved = cache.read(999L)
        assertNotNull(saved)
        assertEquals("Telemetria de Consumo", saved?.title)
    }

    @Test
    fun loadWhenFarmIdIsNullDoesNotSaveToCache() = runBlocking {
        val cache = DashboardCache(temporaryFolder.root)
        val api = FakeDashboardApi().apply {
            statusResponse = Response.success(
                DashboardStatusResponse(status = "fresh", lastUpdatedAt = null, ageSeconds = null, staleAfterSeconds = null)
            )
            customResponse = Response.success(createValidDashboardResponse())
        }
        val repository = createRepository(api = api, cache = cache, farmSessionStore = FakeFarmSessionStore(currentFarmId = null))

        val result = repository.load(DashboardPeriod.MONTH)

        assertTrue(result is DashboardResult.Success)
        assertNull(cache.read(101L))
    }

    @Test
    fun loadWhenCustomDashboardAuthenticatedCallFailsAuthenticationRequired() = runBlocking {
        val sessionManager = SessionManager().apply { setAccessToken("token1", 3600L) }
        val refreshTokenStorage = FakeRefreshTokenStorage(token = "refresh-token")
        val authApi = FakeAuthApi().apply {
            val error401 = "{\"detail\":\"Refresh failed\"}".toResponseBody("application/json".toMediaTypeOrNull())
            refreshResponse = Response.error(401, error401)
        }
        val api = FakeDashboardApi().apply {
            statusResponse = Response.success(
                DashboardStatusResponse(status = "fresh", lastUpdatedAt = null, ageSeconds = null, staleAfterSeconds = null)
            )
            val error401 = "{\"detail\":\"Unauthorized custom\"}".toResponseBody("application/json".toMediaTypeOrNull())
            customResponse = Response.error(401, error401)
        }
        val repository = createRepository(
            api = api,
            sessionManager = sessionManager,
            authApi = authApi,
            refreshTokenStorage = refreshTokenStorage
        )

        val result = repository.load(DashboardPeriod.MONTH)

        assertEquals(DashboardResult.AuthenticationRequired, result)
    }

    @Test
    fun loadWhenCustomDashboardAuthenticatedCallFailsTemporary() = runBlocking {
        val sessionManager = SessionManager().apply { setAccessToken("token1", 3600L) }
        val refreshTokenStorage = FakeRefreshTokenStorage(token = "refresh-token")
        val authApi = FakeAuthApi().apply {
            val error500 = "{\"detail\":\"Refresh 500\"}".toResponseBody("application/json".toMediaTypeOrNull())
            refreshResponse = Response.error(500, error500)
        }
        val api = FakeDashboardApi().apply {
            statusResponse = Response.success(
                DashboardStatusResponse(status = "fresh", lastUpdatedAt = null, ageSeconds = null, staleAfterSeconds = null)
            )
            val error401 = "{\"detail\":\"Unauthorized custom\"}".toResponseBody("application/json".toMediaTypeOrNull())
            customResponse = Response.error(401, error401)
        }
        val repository = createRepository(
            api = api,
            sessionManager = sessionManager,
            authApi = authApi,
            refreshTokenStorage = refreshTokenStorage
        )

        val result = repository.load(DashboardPeriod.MONTH)

        assertTrue(result is DashboardResult.Error)
        val error = result as DashboardResult.Error
        assertEquals("Não foi possível atualizar os dados.", error.message)
    }

    @Test
    fun loadWhenCustomDashboardReturns404FallsBackToResourceEfficiencyChartAndSucceeds() = runBlocking {
        val error404 = "{\"detail\":\"Not found\"}".toResponseBody("application/json".toMediaTypeOrNull())
        val fallbackDashboard = DashboardResponse(
            title = "Telemetria de Consumo",
            charts = listOf(
                DashboardChartResponse(
                    id = DashboardRepository.CHART_RESOURCE_EFFICIENCY,
                    title = "Eficiência",
                    renderAs = "line",
                    html = "<div>chart-fallback</div>"
                ),
                DashboardChartResponse(
                    id = DashboardRepository.CHART_MONTHLY_WATER_CONSUMPTION,
                    title = "Água",
                    renderAs = "bar",
                    html = "<div>chart2</div>"
                ),
                DashboardChartResponse(
                    id = DashboardRepository.CHART_MONTHLY_ENERGY_CONSUMPTION,
                    title = "Energia",
                    renderAs = "bar",
                    html = "<div>chart3</div>"
                )
            )
        )
        val api = FakeDashboardApi().apply {
            statusResponse = Response.success(
                DashboardStatusResponse(status = "fresh", lastUpdatedAt = null, ageSeconds = null, staleAfterSeconds = null)
            )
            customResponsesQueue = mutableListOf(
                Response.error(404, error404),
                Response.success(fallbackDashboard)
            )
        }
        val repository = createRepository(api)

        val result = repository.load(DashboardPeriod.MONTH)

        assertTrue(result is DashboardResult.Success)
        val success = result as DashboardResult.Success
        assertEquals(DashboardRepository.CHART_RESOURCE_EFFICIENCY, success.dashboard.charts[0].id)
        assertEquals(2, api.customCallCount)
        assertEquals(DashboardRepository.CHART_RESOURCE_EFFICIENCY, api.lastCustomRequest?.charts?.get(0)?.chartId)
    }

    @Test
    fun loadWhenCustomDashboardReturns404AndFallbackAlsoReturns404ReturnsError() = runBlocking {
        val error404 = "{\"detail\":\"Not found\"}".toResponseBody("application/json".toMediaTypeOrNull())
        val api = FakeDashboardApi().apply {
            statusResponse = Response.success(
                DashboardStatusResponse(status = "fresh", lastUpdatedAt = null, ageSeconds = null, staleAfterSeconds = null)
            )
            customResponsesQueue = mutableListOf(
                Response.error(404, error404),
                Response.error(404, error404)
            )
        }
        val repository = createRepository(api)

        val result = repository.load(DashboardPeriod.MONTH)

        assertTrue(result is DashboardResult.Error)
        val error = result as DashboardResult.Error
        assertEquals("Não foi possível carregar os gráficos.", error.message)
        assertTrue(error.canRetry)
        assertEquals(2, api.customCallCount)
    }

    @Test
    fun loadWhenStatusResponseHasNullBodyRequestsDashboardWithNullNotice() = runBlocking {
        val api = FakeDashboardApi().apply {
            statusResponse = Response.success(null)
            customResponse = Response.success(createValidDashboardResponse())
        }
        val repository = createRepository(api)

        val result = repository.load(DashboardPeriod.MONTH)

        assertTrue(result is DashboardResult.Success)
        val success = result as DashboardResult.Success
        assertNull(success.notice)
    }

    @Test
    fun loadWhenMissingConsumptionChartReturnsPayloadError() = runBlocking {
        val invalidDashboard = DashboardResponse(
            title = "Telemetria de Consumo",
            charts = listOf(
                DashboardChartResponse(
                    id = DashboardRepository.CHART_MONTHLY_WATER_CONSUMPTION,
                    title = "Água",
                    renderAs = "bar",
                    html = "<div>chart2</div>"
                ),
                DashboardChartResponse(
                    id = DashboardRepository.CHART_MONTHLY_ENERGY_CONSUMPTION,
                    title = "Energia",
                    renderAs = "bar",
                    html = "<div>chart3</div>"
                )
            )
        )
        val api = FakeDashboardApi().apply {
            statusResponse = Response.success(
                DashboardStatusResponse(status = "fresh", lastUpdatedAt = null, ageSeconds = null, staleAfterSeconds = null)
            )
            customResponse = Response.success(invalidDashboard)
        }
        val repository = createRepository(api)

        val result = repository.load(DashboardPeriod.MONTH)

        assertTrue(result is DashboardResult.Error)
        val error = result as DashboardResult.Error
        assertEquals("A API não retornou todos os gráficos.", error.message)
        assertTrue(error.canRetry)
    }

    @Test
    fun loadWhenMissingWaterChartReturnsPayloadError() = runBlocking {
        val invalidDashboard = DashboardResponse(
            title = "Telemetria de Consumo",
            charts = listOf(
                DashboardChartResponse(
                    id = DashboardRepository.CHART_CONSUMPTION_INDEX,
                    title = "Eficiência",
                    renderAs = "line",
                    html = "<div>chart1</div>"
                ),
                DashboardChartResponse(
                    id = DashboardRepository.CHART_MONTHLY_ENERGY_CONSUMPTION,
                    title = "Energia",
                    renderAs = "bar",
                    html = "<div>chart3</div>"
                )
            )
        )
        val api = FakeDashboardApi().apply {
            statusResponse = Response.success(
                DashboardStatusResponse(status = "fresh", lastUpdatedAt = null, ageSeconds = null, staleAfterSeconds = null)
            )
            customResponse = Response.success(invalidDashboard)
        }
        val repository = createRepository(api)

        val result = repository.load(DashboardPeriod.MONTH)

        assertTrue(result is DashboardResult.Error)
        val error = result as DashboardResult.Error
        assertEquals("A API não retornou todos os gráficos.", error.message)
        assertTrue(error.canRetry)
    }

    @Test
    fun loadWhenMissingEnergyChartReturnsPayloadError() = runBlocking {
        val invalidDashboard = DashboardResponse(
            title = "Telemetria de Consumo",
            charts = listOf(
                DashboardChartResponse(
                    id = DashboardRepository.CHART_CONSUMPTION_INDEX,
                    title = "Eficiência",
                    renderAs = "line",
                    html = "<div>chart1</div>"
                ),
                DashboardChartResponse(
                    id = DashboardRepository.CHART_MONTHLY_WATER_CONSUMPTION,
                    title = "Água",
                    renderAs = "bar",
                    html = "<div>chart2</div>"
                )
            )
        )
        val api = FakeDashboardApi().apply {
            statusResponse = Response.success(
                DashboardStatusResponse(status = "fresh", lastUpdatedAt = null, ageSeconds = null, staleAfterSeconds = null)
            )
            customResponse = Response.success(invalidDashboard)
        }
        val repository = createRepository(api)

        val result = repository.load(DashboardPeriod.MONTH)

        assertTrue(result is DashboardResult.Error)
        val error = result as DashboardResult.Error
        assertEquals("A API não retornou todos os gráficos.", error.message)
        assertTrue(error.canRetry)
    }

    @Test
    fun dashboardResultDataClassCoverage() {
        val response = createValidDashboardResponse()
        val successDefault = DashboardResult.Success(dashboard = response)
        assertNull(successDefault.notice)
        assertEquals(response, successDefault.component1())
        assertNull(successDefault.component2())

        val successWithNotice = successDefault.copy(notice = "Aviso")
        assertEquals("Aviso", successWithNotice.notice)
        assertEquals(successDefault.dashboard, successWithNotice.dashboard)
        assertTrue(successDefault != successWithNotice)
        assertEquals(successDefault, successDefault.copy())
        assertEquals(successDefault.hashCode(), successDefault.copy().hashCode())
        assertTrue(successDefault.toString().contains("Success"))

        val error = DashboardResult.Error(message = "Falha", canRetry = true)
        assertEquals("Falha", error.component1())
        assertTrue(error.component2())
        val errorCopy = error.copy(canRetry = false)
        assertFalse(errorCopy.canRetry)
        assertEquals(error, error.copy())
        assertEquals(error.hashCode(), error.copy().hashCode())
        assertTrue(error.toString().contains("Error"))

        val authReq = DashboardResult.AuthenticationRequired
        assertEquals("AuthenticationRequired", authReq.toString())
    }
}
