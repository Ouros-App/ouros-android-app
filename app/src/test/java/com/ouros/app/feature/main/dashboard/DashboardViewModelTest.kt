package com.ouros.app.feature.main.dashboard

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import com.ouros.app.data.auth.AuthApi
import com.ouros.app.data.auth.AuthRepository
import com.ouros.app.data.auth.RefreshTokenStorage
import com.ouros.app.data.auth.SessionManager
import com.ouros.app.data.dashboard.DashboardApi
import com.ouros.app.data.dashboard.DashboardCache
import com.ouros.app.data.dashboard.DashboardChartResponse
import com.ouros.app.data.dashboard.DashboardPeriod
import com.ouros.app.data.dashboard.DashboardRepository
import com.ouros.app.data.dashboard.DashboardRequest
import com.ouros.app.data.dashboard.DashboardResponse
import com.ouros.app.data.dashboard.DashboardStatusResponse
import com.ouros.app.data.session.FarmSessionStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private class FakeFarmSessionStore : FarmSessionStore(null) {
        override fun farmId(): Long? = null
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
        var callCount = 0

        override suspend fun getStatus(authorization: String): Response<DashboardStatusResponse> {
            callCount++
            return statusResponse ?: error("statusResponse not set")
        }

        override suspend fun getCustomDashboard(
            authorization: String,
            request: DashboardRequest
        ): Response<DashboardResponse> {
            return customResponse ?: error("customResponse not set")
        }
    }

    private fun createValidDashboardResponse(htmlResource: String? = null): DashboardResponse {
        val resourceEfficiencyHtml = htmlResource ?: """
            <script>
            var plotData = {"rows": [{"water_m3_per_chicken": 0.005, "energy_kwh_per_chicken": 8.0}]};
            </script>
        """.trimIndent()

        return DashboardResponse(
            title = "Telemetria de Consumo",
            charts = listOf(
                DashboardChartResponse(
                    id = DashboardRepository.CHART_CONSUMPTION_INDEX,
                    title = "Eficiência",
                    renderAs = "line",
                    html = resourceEfficiencyHtml
                ),
                DashboardChartResponse(
                    id = DashboardRepository.CHART_MONTHLY_WATER_CONSUMPTION,
                    title = "Água",
                    renderAs = "bar",
                    html = "<div>water-chart</div>"
                ),
                DashboardChartResponse(
                    id = DashboardRepository.CHART_MONTHLY_ENERGY_CONSUMPTION,
                    title = "Energia",
                    renderAs = "bar",
                    html = "<div>energy-chart</div>"
                )
            )
        )
    }

    private fun createRepository(api: DashboardApi): DashboardRepository {
        val sessionManager = SessionManager().apply { setAccessToken("test-token", 3600L) }
        val authRepository = AuthRepository(
            api = FakeAuthApi(),
            refreshTokenStore = FakeRefreshTokenStorage(),
            sessionManager = sessionManager
        )
        return DashboardRepository(
            api = api,
            authRepository = authRepository,
            sessionManager = sessionManager,
            cache = DashboardCache(temporaryFolder.root),
            farmSessionStore = FakeFarmSessionStore()
        )
    }

    @Before
    fun setUp() {
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread(): Boolean = true
        })
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        ArchTaskExecutor.getInstance().setDelegate(null)
    }

    @Test
    fun loadInitialData_successUpdatesStateWithChartsAndCalculatedKpis() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)

        val api = FakeDashboardApi().apply {
            statusResponse = Response.success(
                DashboardStatusResponse(
                    status = "fresh",
                    lastUpdatedAt = "2026-10-24T00:00:00Z",
                    ageSeconds = 60L,
                    staleAfterSeconds = 1800L
                )
            )
            customResponse = Response.success(createValidDashboardResponse())
        }
        val repository = createRepository(api)
        val viewModel = DashboardViewModel(repository)

        viewModel.loadInitialData()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertNotNull(state)
        assertFalse(state!!.isLoading)
        assertNull(state.error)
        assertFalse(state.canRetry)
        assertNotNull(state.consumptionChartHtml)
        assertEquals("<div>water-chart</div>", state.waterChartHtml)
        assertEquals("<div>energy-chart</div>", state.energyChartHtml)
        // 0.005 m3 * 1000 = 5.0 L; energy = 8.0 kWh; cgi = (5.0 * 0.7) + (8.0 * 0.3) = 3.5 + 2.4 = 5.90
        assertEquals("5.90", state.kpiConsumption)
        assertEquals("5.0", state.kpiWaterLitersPerBird)
        assertEquals("8.0", state.kpiEnergyKwhPerBird)
    }

    @Test
    fun loadInitialData_whenCalledSecondTime_isIgnored() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)

        val api = FakeDashboardApi().apply {
            statusResponse = Response.success(
                DashboardStatusResponse(
                    status = "fresh",
                    lastUpdatedAt = null,
                    ageSeconds = null,
                    staleAfterSeconds = null
                )
            )
            customResponse = Response.success(createValidDashboardResponse())
        }
        val repository = createRepository(api)
        val viewModel = DashboardViewModel(repository)

        viewModel.loadInitialData()
        advanceUntilIdle()
        assertEquals(1, api.callCount)

        viewModel.loadInitialData()
        advanceUntilIdle()
        assertEquals(1, api.callCount)
    }

    @Test
    fun loadInitialData_withStaleNotice_updatesNoticeInState() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)

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
        val viewModel = DashboardViewModel(repository)

        viewModel.loadInitialData()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertNotNull(state)
        assertEquals("Exibindo dados da última sincronização disponível.", state!!.notice)
    }

    @Test
    fun loadInitialData_kpiExtractionFallbacksOnEmptyOrMalformedHtml() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)

        val malformedHtml = "<html>sem rows válidas</html>"
        val api = FakeDashboardApi().apply {
            statusResponse = Response.success(
                DashboardStatusResponse(
                    status = "fresh",
                    lastUpdatedAt = null,
                    ageSeconds = null,
                    staleAfterSeconds = null
                )
            )
            customResponse = Response.success(createValidDashboardResponse(htmlResource = malformedHtml))
        }
        val repository = createRepository(api)
        val viewModel = DashboardViewModel(repository)

        viewModel.loadInitialData()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertNotNull(state)
        assertEquals("6.07", state!!.kpiConsumption)
        assertEquals("5.2", state.kpiWaterLitersPerBird)
        assertEquals("8.1", state.kpiEnergyKwhPerBird)
    }

    @Test
    fun loadInitialData_kpiExtractionFallbacksWhenHtmlIsNull() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)

        val emptyChartsResponse = DashboardResponse(
            title = "Empty",
            charts = listOf(
                DashboardChartResponse(id = DashboardRepository.CHART_CONSUMPTION_INDEX, title = null, renderAs = null, html = ""),
                DashboardChartResponse(id = DashboardRepository.CHART_MONTHLY_WATER_CONSUMPTION, title = null, renderAs = null, html = "water"),
                DashboardChartResponse(id = DashboardRepository.CHART_MONTHLY_ENERGY_CONSUMPTION, title = null, renderAs = null, html = "energy")
            )
        )
        val api = FakeDashboardApi().apply {
            statusResponse = Response.success(
                DashboardStatusResponse(status = "fresh", lastUpdatedAt = null, ageSeconds = null, staleAfterSeconds = null)
            )
            customResponse = Response.success(emptyChartsResponse)
        }
        val repository = createRepository(api)
        val viewModel = DashboardViewModel(repository)

        viewModel.loadInitialData()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertNotNull(state)
        assertEquals("6.07", state!!.kpiConsumption)
        assertEquals("5.2", state.kpiWaterLitersPerBird)
        assertEquals("8.1", state.kpiEnergyKwhPerBird)
    }

    @Test
    fun loadInitialData_kpiExtractionWithZeroOrMissingKeysFallbacksToDefault() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)

        val zeroHtml = """{"rows": [{"other_key": 100}]}"""
        val api = FakeDashboardApi().apply {
            statusResponse = Response.success(
                DashboardStatusResponse(status = "fresh", lastUpdatedAt = null, ageSeconds = null, staleAfterSeconds = null)
            )
            customResponse = Response.success(createValidDashboardResponse(htmlResource = zeroHtml))
        }
        val repository = createRepository(api)
        val viewModel = DashboardViewModel(repository)

        viewModel.loadInitialData()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertNotNull(state)
        assertEquals("6.07", state!!.kpiConsumption)
    }

    @Test
    fun loadInitialData_kpiExtractionWithEmptyJsonArrayFallbacksToDefault() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)

        val emptyArrayHtml = """{"rows": []}"""
        val api = FakeDashboardApi().apply {
            statusResponse = Response.success(
                DashboardStatusResponse(status = "fresh", lastUpdatedAt = null, ageSeconds = null, staleAfterSeconds = null)
            )
            customResponse = Response.success(createValidDashboardResponse(htmlResource = emptyArrayHtml))
        }
        val repository = createRepository(api)
        val viewModel = DashboardViewModel(repository)

        viewModel.loadInitialData()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertNotNull(state)
        assertEquals("6.07", state!!.kpiConsumption)
    }

    @Test
    fun retry_whenNotLoading_triggersReload() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)

        val api = FakeDashboardApi().apply {
            val errorBody = "{\"detail\":\"Forbidden\"}".toResponseBody("application/json".toMediaTypeOrNull())
            statusResponse = Response.error(403, errorBody)
        }
        val repository = createRepository(api)
        val viewModel = DashboardViewModel(repository)

        viewModel.loadInitialData()
        advanceUntilIdle()
        assertEquals(1, api.callCount)
        assertEquals("Este conteúdo não está disponível para o seu perfil.", viewModel.state.value?.error)

        // Retry
        api.statusResponse = Response.success(
            DashboardStatusResponse(status = "fresh", lastUpdatedAt = null, ageSeconds = null, staleAfterSeconds = null)
        )
        api.customResponse = Response.success(createValidDashboardResponse())

        viewModel.retry()
        advanceUntilIdle()
        assertEquals(2, api.callCount)
        assertNull(viewModel.state.value?.error)
        assertNotNull(viewModel.state.value?.consumptionChartHtml)
    }

    @Test
    fun selectPeriod_samePeriodDoesNotTriggerReload() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)

        val api = FakeDashboardApi().apply {
            statusResponse = Response.success(
                DashboardStatusResponse(status = "fresh", lastUpdatedAt = null, ageSeconds = null, staleAfterSeconds = null)
            )
            customResponse = Response.success(createValidDashboardResponse())
        }
        val repository = createRepository(api)
        val viewModel = DashboardViewModel(repository)

        viewModel.loadInitialData()
        advanceUntilIdle()
        assertEquals(1, api.callCount)

        viewModel.selectPeriod(DashboardPeriod.YEAR)
        advanceUntilIdle()
        assertEquals(1, api.callCount)
    }

    @Test
    fun selectPeriod_differentPeriodUpdatesStateAndTriggersReload() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)

        val api = FakeDashboardApi().apply {
            statusResponse = Response.success(
                DashboardStatusResponse(status = "fresh", lastUpdatedAt = null, ageSeconds = null, staleAfterSeconds = null)
            )
            customResponse = Response.success(createValidDashboardResponse())
        }
        val repository = createRepository(api)
        val viewModel = DashboardViewModel(repository)

        viewModel.loadInitialData()
        advanceUntilIdle()
        assertEquals(1, api.callCount)

        viewModel.selectPeriod(DashboardPeriod.MONTH)
        advanceUntilIdle()
        assertEquals(2, api.callCount)
        assertEquals(DashboardPeriod.MONTH, viewModel.state.value?.period)
    }

    @Test
    fun consumeEvent_clearsActiveEvent() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)

        val api = FakeDashboardApi().apply {
            val errorBody = "{\"detail\":\"Unauthorized\"}".toResponseBody("application/json".toMediaTypeOrNull())
            statusResponse = Response.error(401, errorBody)
        }
        val sessionManager = SessionManager() // sem token
        val authRepository = AuthRepository(
            api = FakeAuthApi(),
            refreshTokenStore = FakeRefreshTokenStorage(),
            sessionManager = sessionManager
        )
        val repository = DashboardRepository(
            api = api,
            authRepository = authRepository,
            sessionManager = sessionManager,
            cache = DashboardCache(temporaryFolder.root),
            farmSessionStore = FakeFarmSessionStore()
        )
        val viewModel = DashboardViewModel(repository)

        viewModel.loadInitialData()
        advanceUntilIdle()

        assertEquals(DashboardEvent.AuthenticationRequired, viewModel.event.value)

        viewModel.consumeEvent()
        assertNull(viewModel.event.value)
    }

    @Test
    fun loadDashboard_whenErrorResult_updatesErrorAndCanRetry() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)

        val api = FakeDashboardApi().apply {
            val errorBody = "{\"detail\":\"Internal server error\"}".toResponseBody("application/json".toMediaTypeOrNull())
            statusResponse = Response.error(500, errorBody)
        }
        val repository = createRepository(api)
        val viewModel = DashboardViewModel(repository)

        viewModel.loadInitialData()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertNotNull(state)
        assertFalse(state!!.isLoading)
        assertEquals("Não foi possível verificar os dados.", state.error)
        assertTrue(state.canRetry)
    }

    @Test
    fun raceCondition_olderRequestDoesNotOverwriteNewerRequest() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)

        val deferredFirst = CompletableDeferred<Response<DashboardStatusResponse>>()
        val deferredSecond = CompletableDeferred<Response<DashboardStatusResponse>>()

        var getStatusCalls = 0
        val api = object : DashboardApi {
            override suspend fun getStatus(authorization: String): Response<DashboardStatusResponse> {
                getStatusCalls++
                return if (getStatusCalls == 1) {
                    deferredFirst.await()
                } else {
                    deferredSecond.await()
                }
            }

            override suspend fun getCustomDashboard(
                authorization: String,
                request: DashboardRequest
            ): Response<DashboardResponse> {
                return Response.success(createValidDashboardResponse())
            }
        }

        val repository = createRepository(api)
        val viewModel = DashboardViewModel(repository)

        // 1ª chamada
        viewModel.loadInitialData()
        // 2ª chamada (com período diferente)
        viewModel.selectPeriod(DashboardPeriod.WEEK)

        // Completa a 2ª chamada primeiro
        deferredSecond.complete(
            Response.success(
                DashboardStatusResponse(
                    status = "fresh",
                    lastUpdatedAt = "2026-10-24T02:00:00Z",
                    ageSeconds = 10L,
                    staleAfterSeconds = 1800L
                )
            )
        )
        advanceUntilIdle()

        assertEquals(DashboardPeriod.WEEK, viewModel.state.value?.period)
        assertFalse(viewModel.state.value?.isLoading == true)
        val weekChart = viewModel.state.value?.consumptionChartHtml
        assertNotNull(weekChart)

        // Agora completa a 1ª chamada mais antiga com erro (deve ser ignorada pois version != requestVersion)
        deferredFirst.complete(
            Response.error(500, "".toResponseBody(null))
        )
        advanceUntilIdle()

        // Estado continua preservando o resultado da 2ª requisição
        assertNull(viewModel.state.value?.error)
        assertEquals(DashboardPeriod.WEEK, viewModel.state.value?.period)
        assertEquals(weekChart, viewModel.state.value?.consumptionChartHtml)
    }

    @Test
    fun retry_whenIsLoading_returnsImmediatelyWithoutStartingNewRequest() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)

        val deferred = CompletableDeferred<Response<DashboardStatusResponse>>()
        val api = object : DashboardApi {
            var callCount = 0
            override suspend fun getStatus(authorization: String): Response<DashboardStatusResponse> {
                callCount++
                return deferred.await()
            }
            override suspend fun getCustomDashboard(authorization: String, request: DashboardRequest) =
                Response.success(createValidDashboardResponse())
        }
        val repository = createRepository(api)
        val viewModel = DashboardViewModel(repository)

        viewModel.loadInitialData()
        testScheduler.runCurrent()
        assertTrue(viewModel.state.value?.isLoading == true)
        assertEquals(1, api.callCount)

        // Calling retry while loading should be ignored
        viewModel.retry()
        assertEquals(1, api.callCount)

        deferred.complete(Response.success(DashboardStatusResponse(status = "fresh", lastUpdatedAt = null, ageSeconds = null, staleAfterSeconds = null)))
        advanceUntilIdle()
        assertFalse(viewModel.state.value?.isLoading == true)
    }

    @Test
    fun loadDashboard_withChartResourceEfficiency_correctlyExtractsKpisAndUpdatesState() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)

        val resourceEfficiencyDashboard = DashboardResponse(
            title = "Telemetria de Consumo",
            charts = listOf(
                DashboardChartResponse(
                    id = DashboardRepository.CHART_RESOURCE_EFFICIENCY,
                    title = "Eficiência",
                    renderAs = "line",
                    html = """
                        <script>
                        var plotData = {"rows": [{"water_m3_per_chicken": 0.0052, "energy_kwh_per_chicken": 8.1}]};
                        </script>
                    """.trimIndent()
                ),
                DashboardChartResponse(
                    id = DashboardRepository.CHART_MONTHLY_WATER_CONSUMPTION,
                    title = "Água",
                    renderAs = "bar",
                    html = "<div>water-chart</div>"
                ),
                DashboardChartResponse(
                    id = DashboardRepository.CHART_MONTHLY_ENERGY_CONSUMPTION,
                    title = "Energia",
                    renderAs = "bar",
                    html = "<div>energy-chart</div>"
                )
            )
        )

        val api = FakeDashboardApi().apply {
            statusResponse = Response.success(DashboardStatusResponse(status = "fresh", lastUpdatedAt = null, ageSeconds = null, staleAfterSeconds = null))
            customResponse = Response.success(resourceEfficiencyDashboard)
        }
        val repository = createRepository(api)
        val viewModel = DashboardViewModel(repository)

        viewModel.loadInitialData()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertNotNull(state)
        assertEquals("6.07", state?.kpiConsumption)
        assertEquals("5.2", state?.kpiWaterLitersPerBird)
        assertEquals("8.1", state?.kpiEnergyKwhPerBird)
        assertNotNull(state?.consumptionChartHtml)
    }

    @Test
    fun loadDashboard_withWaterPerBirdAndEnergyPerBird_calculatesKpisCorrectly() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)

        val html = """
            <script>
            var plotData = {"rows": [{"water_per_bird": 5200.0, "energy_per_bird": 8.1}]};
            </script>
        """.trimIndent()

        val api = FakeDashboardApi().apply {
            statusResponse = Response.success(DashboardStatusResponse(status = "fresh", lastUpdatedAt = null, ageSeconds = null, staleAfterSeconds = null))
            customResponse = Response.success(createValidDashboardResponse(htmlResource = html))
        }
        val repository = createRepository(api)
        val viewModel = DashboardViewModel(repository)

        viewModel.loadInitialData()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertNotNull(state)
        // water_per_bird = 5200.0 / 1000.0 = 5.2 m3 -> waterLiters = 5200.0
        // energy_per_bird = 8.1
        // cgi = 5200 * 0.7 + 8.1 * 0.3 = 3640.0 + 2.43 = 3642.43
        assertEquals("3642.43", state?.kpiConsumption)
        assertEquals("5200.0", state?.kpiWaterLitersPerBird)
        assertEquals("8.1", state?.kpiEnergyKwhPerBird)
    }

    @Test
    fun loadDashboard_withNullOrBlankOrMalformedHtml_fallsBackToBaseline() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)

        // 1. Blank HTML
        val apiBlank = FakeDashboardApi().apply {
            statusResponse = Response.success(DashboardStatusResponse(status = "fresh", lastUpdatedAt = null, ageSeconds = null, staleAfterSeconds = null))
            customResponse = Response.success(createValidDashboardResponse(htmlResource = "   "))
        }
        val vmBlank = DashboardViewModel(createRepository(apiBlank))
        vmBlank.loadInitialData()
        advanceUntilIdle()
        assertEquals("6.07", vmBlank.state.value?.kpiConsumption)
        assertEquals("5.2", vmBlank.state.value?.kpiWaterLitersPerBird)
        assertEquals("8.1", vmBlank.state.value?.kpiEnergyKwhPerBird)

        // 2. Malformed JSON inside "rows"
        val apiMalformed = FakeDashboardApi().apply {
            statusResponse = Response.success(DashboardStatusResponse(status = "fresh", lastUpdatedAt = null, ageSeconds = null, staleAfterSeconds = null))
            customResponse = Response.success(createValidDashboardResponse(htmlResource = "var plotData = {\"rows\": [not valid json]};"))
        }
        val vmMalformed = DashboardViewModel(createRepository(apiMalformed))
        vmMalformed.loadInitialData()
        advanceUntilIdle()
        assertEquals("6.07", vmMalformed.state.value?.kpiConsumption)
        assertEquals("5.2", vmMalformed.state.value?.kpiWaterLitersPerBird)
        assertEquals("8.1", vmMalformed.state.value?.kpiEnergyKwhPerBird)

        // 3. Empty rows []
        val apiEmpty = FakeDashboardApi().apply {
            statusResponse = Response.success(DashboardStatusResponse(status = "fresh", lastUpdatedAt = null, ageSeconds = null, staleAfterSeconds = null))
            customResponse = Response.success(createValidDashboardResponse(htmlResource = "var plotData = {\"rows\": []};"))
        }
        val vmEmpty = DashboardViewModel(createRepository(apiEmpty))
        vmEmpty.loadInitialData()
        advanceUntilIdle()
        assertEquals("6.07", vmEmpty.state.value?.kpiConsumption)
        assertEquals("5.2", vmEmpty.state.value?.kpiWaterLitersPerBird)
        assertEquals("8.1", vmEmpty.state.value?.kpiEnergyKwhPerBird)

        // 4. Zero values (waterM3 = 0.0 and energyKwh = 0.0)
        val apiZero = FakeDashboardApi().apply {
            statusResponse = Response.success(DashboardStatusResponse(status = "fresh", lastUpdatedAt = null, ageSeconds = null, staleAfterSeconds = null))
            customResponse = Response.success(createValidDashboardResponse(htmlResource = "var plotData = {\"rows\": [{\"water_m3_per_chicken\": 0.0, \"energy_kwh_per_chicken\": 0.0}]};"))
        }
        val vmZero = DashboardViewModel(createRepository(apiZero))
        vmZero.loadInitialData()
        advanceUntilIdle()
        assertEquals("6.07", vmZero.state.value?.kpiConsumption)
        assertEquals("5.2", vmZero.state.value?.kpiWaterLitersPerBird)
        assertEquals("8.1", vmZero.state.value?.kpiEnergyKwhPerBird)

        // 5. JsonNull values
        val apiNulls = FakeDashboardApi().apply {
            statusResponse = Response.success(DashboardStatusResponse(status = "fresh", lastUpdatedAt = null, ageSeconds = null, staleAfterSeconds = null))
            customResponse = Response.success(createValidDashboardResponse(htmlResource = "var plotData = {\"rows\": [{\"water_m3_per_chicken\": null, \"water_per_bird\": null, \"energy_kwh_per_chicken\": null, \"energy_per_bird\": null}]};"))
        }
        val vmNulls = DashboardViewModel(createRepository(apiNulls))
        vmNulls.loadInitialData()
        advanceUntilIdle()
        assertEquals("6.07", vmNulls.state.value?.kpiConsumption)
        assertEquals("5.2", vmNulls.state.value?.kpiWaterLitersPerBird)
        assertEquals("8.1", vmNulls.state.value?.kpiEnergyKwhPerBird)
    }

    @Test
    fun loadDashboard_withoutConsumptionChartInList_handlesNullHtmlGracefully() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)

        val dashboardWithoutConsumption = DashboardResponse(
            title = "Telemetria de Consumo",
            charts = listOf(
                DashboardChartResponse(
                    id = DashboardRepository.CHART_MONTHLY_WATER_CONSUMPTION,
                    title = "Água",
                    renderAs = "bar",
                    html = "<div>water-chart</div>"
                ),
                DashboardChartResponse(
                    id = DashboardRepository.CHART_MONTHLY_ENERGY_CONSUMPTION,
                    title = "Energia",
                    renderAs = "bar",
                    html = "<div>energy-chart</div>"
                )
            )
        )

        val api = FakeDashboardApi().apply {
            statusResponse = Response.success(DashboardStatusResponse(status = "fresh", lastUpdatedAt = null, ageSeconds = null, staleAfterSeconds = null))
            customResponse = Response.success(dashboardWithoutConsumption)
        }
        val repository = createRepository(api)
        val viewModel = DashboardViewModel(repository)

        viewModel.loadInitialData()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertNotNull(state)
        assertNull(state?.consumptionChartHtml)
        assertEquals("6.07", state?.kpiConsumption)
    }

    @Test
    fun raceCondition_olderSuccessDoesNotOverwriteNewerRequest() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)

        val deferredFirst = CompletableDeferred<Response<DashboardStatusResponse>>()
        val deferredSecond = CompletableDeferred<Response<DashboardStatusResponse>>()

        var getStatusCalls = 0
        val api = object : DashboardApi {
            override suspend fun getStatus(authorization: String): Response<DashboardStatusResponse> {
                getStatusCalls++
                return if (getStatusCalls == 1) {
                    deferredFirst.await()
                } else {
                    deferredSecond.await()
                }
            }

            override suspend fun getCustomDashboard(
                authorization: String,
                request: DashboardRequest
            ): Response<DashboardResponse> {
                return Response.success(createValidDashboardResponse())
            }
        }

        val repository = createRepository(api)
        val viewModel = DashboardViewModel(repository)

        viewModel.loadInitialData()
        viewModel.selectPeriod(DashboardPeriod.WEEK)

        // Conclui a 2ª chamada com sucesso
        deferredSecond.complete(
            Response.success(
                DashboardStatusResponse(
                    status = "fresh",
                    lastUpdatedAt = "2026-10-24T02:00:00Z",
                    ageSeconds = 10L,
                    staleAfterSeconds = 1800L
                )
            )
        )
        advanceUntilIdle()
        assertEquals(DashboardPeriod.WEEK, viewModel.state.value?.period)

        // Conclui a 1ª chamada antiga com Sucesso (deve ser ignorada pois version != requestVersion)
        deferredFirst.complete(
            Response.success(
                DashboardStatusResponse(
                    status = "stale",
                    lastUpdatedAt = "2026-10-24T01:00:00Z",
                    ageSeconds = 20L,
                    staleAfterSeconds = 1800L
                )
            )
        )
        advanceUntilIdle()

        assertEquals(DashboardPeriod.WEEK, viewModel.state.value?.period)
        assertNull(viewModel.state.value?.notice)
    }

    @Test
    fun raceCondition_olderAuthRequiredDoesNotOverwriteNewerRequest() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)

        val deferredFirst = CompletableDeferred<Response<DashboardStatusResponse>>()
        val deferredSecond = CompletableDeferred<Response<DashboardStatusResponse>>()

        var getStatusCalls = 0
        val sessionManager = SessionManager().apply { setAccessToken("token", 3600L) }
        val fakeAuthApi = FakeAuthApi()
        val api = object : DashboardApi {
            override suspend fun getStatus(authorization: String): Response<DashboardStatusResponse> {
                getStatusCalls++
                return if (getStatusCalls == 1) {
                    deferredFirst.await()
                } else {
                    deferredSecond.await()
                }
            }

            override suspend fun getCustomDashboard(
                authorization: String,
                request: DashboardRequest
            ): Response<DashboardResponse> {
                return Response.success(createValidDashboardResponse())
            }
        }

        val authRepository = AuthRepository(
            api = fakeAuthApi,
            refreshTokenStore = FakeRefreshTokenStorage(),
            sessionManager = sessionManager
        )
        val repository = DashboardRepository(
            api = api,
            authRepository = authRepository,
            sessionManager = sessionManager,
            cache = DashboardCache(temporaryFolder.root),
            farmSessionStore = FakeFarmSessionStore()
        )
        val viewModel = DashboardViewModel(repository)

        viewModel.loadInitialData()
        viewModel.selectPeriod(DashboardPeriod.WEEK)

        // Conclui a 2ª chamada com sucesso
        deferredSecond.complete(
            Response.success(
                DashboardStatusResponse(
                    status = "fresh",
                    lastUpdatedAt = "2026-10-24T02:00:00Z",
                    ageSeconds = 10L,
                    staleAfterSeconds = 1800L
                )
            )
        )
        advanceUntilIdle()
        assertEquals(DashboardPeriod.WEEK, viewModel.state.value?.period)
        assertNull(viewModel.event.value)

        // Conclui a 1ª chamada antiga com 401 que aciona AuthenticationRequired (deve ser descartada)
        val error401 = "{\"detail\":\"Token expired\"}".toResponseBody("application/json".toMediaTypeOrNull())
        deferredFirst.complete(Response.error(401, error401))
        advanceUntilIdle()

        // Evento AuthenticationRequired não deve ter sido disparado pela 1ª requisição antiga
        assertNull(viewModel.event.value)
        assertEquals(DashboardPeriod.WEEK, viewModel.state.value?.period)
    }
}
