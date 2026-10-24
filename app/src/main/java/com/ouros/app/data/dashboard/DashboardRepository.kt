package com.ouros.app.data.dashboard

import com.ouros.app.data.auth.AuthRepository
import com.ouros.app.data.auth.AuthResult
import com.ouros.app.data.auth.SessionManager
import com.ouros.app.data.session.FarmSessionStore
import java.io.IOException
import kotlinx.coroutines.CancellationException
import retrofit2.Response

sealed interface DashboardResult {

    data class Success(
        val dashboard: DashboardResponse,
        val notice: String? = null
    ) : DashboardResult

    data class Error(
        val message: String,
        val canRetry: Boolean
    ) : DashboardResult

    /*
     * Somente retornado quando o refresh token não existe
     * ou foi recusado pela API.
     */
    data object AuthenticationRequired :
        DashboardResult
}

class DashboardRepository(
    private val api: DashboardApi,
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
    private val cache: DashboardCache,
    private val farmSessionStore: FarmSessionStore
) {

    suspend fun load(
        period: DashboardPeriod
    ): DashboardResult {
        val farmId = farmSessionStore.farmId()
        return try {
            when (
                val statusResult =
                    authenticatedCall {
                            authorization ->

                        api.getStatus(
                            authorization
                        )
                    }
            ) {
                AuthenticatedCallResult.AuthenticationRequired -> {
                    DashboardResult.AuthenticationRequired
                }

                AuthenticatedCallResult.TemporaryFailure -> {
                    cachedResult(
                        "Não foi possível atualizar os dados.",
                        farmId
                    )
                }

                is AuthenticatedCallResult.ResponseResult -> {
                    handleStatusResponse(
                        response = statusResult.response,
                        period = period,
                        farmId = farmId
                    )
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: IOException) {
            cachedResult(
                "Sem conexão. Exibindo os últimos dados disponíveis.",
                farmId
            )
        } catch (_: Exception) {
            cachedResult(
                "Não foi possível atualizar os dados.",
                farmId
            )
        }
    }

    private suspend fun handleStatusResponse(
        response: Response<DashboardStatusResponse>,
        period: DashboardPeriod,
        farmId: Long?
    ): DashboardResult {
        return when {
            response.code() == 403 -> {
                DashboardResult.Error(
                    message =
                        "Este conteúdo não está disponível para o seu perfil.",
                    canRetry = false
                )
            }

            response.code() == 503 -> {
                cachedResult(
                    "Dados de telemetria em atualização.",
                    farmId
                )
            }

            !response.isSuccessful -> {
                DashboardResult.Error(
                    message =
                        "Não foi possível verificar os dados.",
                    canRetry = true
                )
            }

            else -> {
                val status = response.body()?.status
                val notice = when {
                    status.equals("stale", ignoreCase = true) ->
                        "Exibindo dados da última sincronização disponível."
                    status.equals("unknown", ignoreCase = true) ->
                        "Dados de telemetria em atualização."
                    else -> null
                }

                requestDashboard(
                    period = period,
                    farmId = farmId,
                    notice = notice
                )
            }
        }
    }

    private suspend fun requestDashboard(
        period: DashboardPeriod,
        farmId: Long?,
        notice: String? = null
    ): DashboardResult {
        val request =
            DashboardRequest(
                title =
                    "Telemetria de Consumo",
                periodDays =
                    period.days,
                charts = listOf(
                    DashboardChartRequest(
                        chartId =
                            CHART_RESOURCE_EFFICIENCY,
                        renderAs = "line"
                    ),
                    DashboardChartRequest(
                        chartId =
                            CHART_MONTHLY_WATER_CONSUMPTION,
                        renderAs = "bar"
                    ),
                    DashboardChartRequest(
                        chartId =
                            CHART_MONTHLY_ENERGY_CONSUMPTION,
                        renderAs = "bar"
                    )
                )
            )

        return when (
            val dashboardResult =
                authenticatedCall {
                        authorization ->

                    api.getCustomDashboard(
                        authorization =
                            authorization,
                        request = request
                    )
                }
        ) {
            AuthenticatedCallResult.AuthenticationRequired -> {
                DashboardResult.AuthenticationRequired
            }

            AuthenticatedCallResult.TemporaryFailure -> {
                cachedResult(
                    "Não foi possível atualizar os dados.",
                    farmId
                )
            }

            is AuthenticatedCallResult.ResponseResult -> {
                handleDashboardResponse(
                    response = dashboardResult.response,
                    farmId = farmId,
                    notice = notice
                )
            }
        }
    }

    private suspend fun handleDashboardResponse(
        response: Response<DashboardResponse>,
        farmId: Long?,
        notice: String? = null
    ): DashboardResult {
        return when {
            response.code() == 403 -> {
                DashboardResult.Error(
                    message =
                        "Este conteúdo não está disponível para o seu perfil.",
                    canRetry = false
                )
            }

            response.code() == 422 -> {
                DashboardResult.Error(
                    message =
                        "A configuração solicitada não é válida.",
                    canRetry = false
                )
            }

            response.code() == 503 -> {
                cachedResult(
                    "Serviço temporariamente indisponível.",
                    farmId
                )
            }

            response.isSuccessful &&
                    response.body() != null -> {
                val dashboard =
                    response.body()!!

                if (
                    !hasRequiredCharts(
                        dashboard
                    )
                ) {
                    DashboardResult.Error(
                        message =
                            "A API não retornou todos os gráficos.",
                        canRetry = true
                    )
                } else {
                    farmId?.let { cache.save(it, dashboard) }

                    DashboardResult.Success(
                        dashboard = dashboard,
                        notice = notice
                    )
                }
            }

            else -> {
                DashboardResult.Error(
                    message =
                        "Não foi possível carregar os gráficos.",
                    canRetry = true
                )
            }
        }
    }

    /*
     * Usa o access token que o login já colocou em
     * AppSession.sessionManager.
     *
     * Se o token estiver vencido, usa AuthRepository.refreshSession(),
     * que já trabalha com SecureRefreshTokenStore.
     */
    private suspend fun <T> authenticatedCall(
        request: suspend (
            authorization: String
        ) -> Response<T>
    ): AuthenticatedCallResult<T> {
        val firstAuthorization =
            ensureAuthorization()

        when (firstAuthorization) {
            AuthorizationResult.AuthenticationRequired -> {
                return AuthenticatedCallResult
                    .AuthenticationRequired
            }

            AuthorizationResult.TemporaryFailure -> {
                return AuthenticatedCallResult
                    .TemporaryFailure
            }

            is AuthorizationResult.Ready -> {
                val firstResponse =
                    request(
                        firstAuthorization.header
                    )

                if (firstResponse.code() != 401) {
                    return AuthenticatedCallResult
                        .ResponseResult(
                            firstResponse
                        )
                }
            }
        }

        /*
         * A API recusou o access token.
         * Renova silenciosamente e tenta somente mais uma vez.
         */
        val refreshedAuthorization =
            refreshAuthorization()

        return when (refreshedAuthorization) {
            AuthorizationResult.AuthenticationRequired -> {
                AuthenticatedCallResult
                    .AuthenticationRequired
            }

            AuthorizationResult.TemporaryFailure -> {
                AuthenticatedCallResult
                    .TemporaryFailure
            }

            is AuthorizationResult.Ready -> {
                val retryResponse =
                    request(
                        refreshedAuthorization.header
                    )

                if (retryResponse.code() == 401) {
                    authRepository.clearSession()

                    AuthenticatedCallResult
                        .AuthenticationRequired
                } else {
                    AuthenticatedCallResult
                        .ResponseResult(
                            retryResponse
                        )
                }
            }
        }
    }

    private suspend fun ensureAuthorization():
            AuthorizationResult {
        val currentToken =
            sessionManager.accessToken()

        if (!currentToken.isNullOrBlank()) {
            return AuthorizationResult.Ready(
                header = "Bearer $currentToken"
            )
        }

        return refreshAuthorization()
    }

    private suspend fun refreshAuthorization():
            AuthorizationResult {
        return when (
            val result =
                authRepository.refreshSession()
        ) {
            is AuthResult.Success -> {
                val refreshedToken =
                    sessionManager.accessToken()

                if (refreshedToken.isNullOrBlank()) {
                    AuthorizationResult
                        .AuthenticationRequired
                } else {
                    AuthorizationResult.Ready(
                        header =
                            "Bearer $refreshedToken"
                    )
                }
            }

            is AuthResult.Error -> {
                when {
                    result.statusCode == 401 -> {
                        AuthorizationResult
                            .AuthenticationRequired
                    }

                    result.message ==
                            NO_SAVED_SESSION_MESSAGE -> {
                        AuthorizationResult
                            .AuthenticationRequired
                    }

                    else -> {
                        AuthorizationResult
                            .TemporaryFailure
                    }
                }
            }
        }
    }

    private suspend fun cachedResult(
        message: String,
        farmId: Long?
    ): DashboardResult {
        val cachedDashboard =
            farmId?.let { cache.read(it) }
        return if (cachedDashboard != null) {
            DashboardResult.Success(
                dashboard =
                    cachedDashboard,
                notice = message
            )
        } else {
            DashboardResult.Error(
                message = message,
                canRetry = true
            )
        }
    }

    private fun hasRequiredCharts(
        dashboard: DashboardResponse
    ): Boolean {
        val chartIds =
            dashboard.charts
                .map(
                    DashboardChartResponse::id
                )
                .toSet()

        return chartIds.containsAll(
            setOf(
                CHART_RESOURCE_EFFICIENCY,
                CHART_MONTHLY_WATER_CONSUMPTION,
                CHART_MONTHLY_ENERGY_CONSUMPTION
            )
        )
    }

    private sealed interface AuthorizationResult {

        data class Ready(
            val header: String
        ) : AuthorizationResult

        data object AuthenticationRequired :
            AuthorizationResult

        data object TemporaryFailure :
            AuthorizationResult
    }

    private sealed interface AuthenticatedCallResult<
            out T
            > {

        data class ResponseResult<T>(
            val response: Response<T>
        ) : AuthenticatedCallResult<T>

        data object AuthenticationRequired :
            AuthenticatedCallResult<Nothing>

        data object TemporaryFailure :
            AuthenticatedCallResult<Nothing>
    }

    companion object {
        const val CHART_RESOURCE_EFFICIENCY =
            "resource-efficiency"

        const val CHART_MONTHLY_WATER_CONSUMPTION =
            "monthly-water-consumption"

        const val CHART_MONTHLY_ENERGY_CONSUMPTION =
            "monthly-energy-consumption"

        private const val NO_SAVED_SESSION_MESSAGE =
            "Nenhuma sessão salva."
    }
}