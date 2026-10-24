package com.ouros.app.data.dashboard

import com.ouros.app.data.auth.AuthApi
import com.ouros.app.data.auth.AuthRepository
import com.ouros.app.data.auth.RefreshTokenStorage
import com.ouros.app.data.auth.SessionManager
import com.ouros.app.data.session.FarmSessionStore
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    private class FakeRefreshTokenStorage : RefreshTokenStorage {
        var token: String? = null
        override fun getRefreshToken(): String? = token
        override fun saveRefreshToken(token: String) { this.token = token }
        override fun clear() { token = null }
    }

    private class FakeAuthApi : AuthApi {
        override suspend fun startLogin(request: com.ouros.app.data.auth.LoginStartRequest) = error("Not used")
        override suspend fun verifyLogin(request: com.ouros.app.data.auth.LoginVerifyRequest) = error("Not used")
        override suspend fun refreshToken(request: com.ouros.app.data.auth.RefreshRequest) = error("Not used")
        override suspend fun startPasswordReset(request: com.ouros.app.data.auth.PasswordResetStartRequest) = error("Not used")
        override suspend fun verifyPasswordReset(request: com.ouros.app.data.auth.PasswordResetVerifyRequest) = error("Not used")
        override suspend fun confirmPasswordReset(request: com.ouros.app.data.auth.PasswordResetConfirmRequest) = error("Not used")
    }

    private class FakeDashboardApi : DashboardApi {
        var statusResponse: Response<DashboardStatusResponse>? = null
        var customResponse: Response<DashboardResponse>? = null
        var lastStatusAuthHeader: String? = null
        var lastCustomAuthHeader: String? = null
        var lastCustomRequest: DashboardRequest? = null

        override suspend fun getStatus(authorization: String): Response<DashboardStatusResponse> {
            lastStatusAuthHeader = authorization
            return statusResponse ?: error("statusResponse not set")
        }

        override suspend fun getCustomDashboard(
            authorization: String,
            request: DashboardRequest
        ): Response<DashboardResponse> {
            lastCustomAuthHeader = authorization
            lastCustomRequest = request
            return customResponse ?: error("customResponse not set")
        }
    }

    private fun createValidDashboardResponse(): DashboardResponse {
        return DashboardResponse(
            title = "Telemetria de Consumo",
            charts = listOf(
                DashboardChartResponse(
                    id = DashboardRepository.CHART_RESOURCE_EFFICIENCY,
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
        farmSessionStore: FarmSessionStore = FakeFarmSessionStore()
    ): DashboardRepository {
        val authRepository = AuthRepository(
            api = FakeAuthApi(),
            refreshTokenStore = FakeRefreshTokenStorage(),
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
        assertEquals(30, api.lastCustomRequest?.periodDays)
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
        assertEquals(7, api.lastCustomRequest?.periodDays)
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
}
