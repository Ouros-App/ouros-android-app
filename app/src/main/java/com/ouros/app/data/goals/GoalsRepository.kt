package com.ouros.app.data.goals

import com.ouros.app.data.auth.AuthRepository
import com.ouros.app.data.auth.AuthResult
import com.ouros.app.data.auth.SessionManager
import com.ouros.app.data.session.FarmSessionStore
import java.io.IOException
import kotlinx.coroutines.CancellationException
import retrofit2.Response

sealed interface GoalsResult<out T> {
    data class Success<T>(val data: T) : GoalsResult<T>
    data class Error(val message: String, val canRetry: Boolean) : GoalsResult<Nothing>
    data object AuthenticationRequired : GoalsResult<Nothing>
}

class GoalsRepository(
    private val api: GoalsApi,
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
    private val farmSessionStore: FarmSessionStore
) {
    suspend fun load(): GoalsResult<GoalsPayload> = safeRequest<GoalsPayload> {
        when (val personalCall = authenticatedCall { api.getIndividualGoals(it) }) {
            ApiCallResult.AuthenticationRequired -> GoalsResult.AuthenticationRequired
            ApiCallResult.TemporaryFailure -> temporaryError()
            is ApiCallResult.ResponseResult -> {
                val personalResponse = personalCall.response
                val personalGoals = personalResponse.body()

                if (!personalResponse.isSuccessful || personalGoals == null) {
                    return@safeRequest responseError(
                        personalResponse.code(),
                        "Não foi possível carregar as metas pessoais."
                    )
                }

                when (val stateCall = authenticatedCall { api.getStateGoals(it) }) {
                    ApiCallResult.AuthenticationRequired -> GoalsResult.AuthenticationRequired
                    ApiCallResult.TemporaryFailure -> temporaryError()
                    is ApiCallResult.ResponseResult -> {
                        val stateResponse = stateCall.response
                        val stateGoals = stateResponse.body()

                        if (!stateResponse.isSuccessful || stateGoals == null) {
                            return@safeRequest responseError(
                                stateResponse.code(),
                                "Não foi possível carregar as metas estaduais."
                            )
                        }

                        GoalsResult.Success(
                            GoalsPayload(
                                personalGoals = personalGoals,
                                stateGoals = stateGoals
                            )
                        )
                    }
                }
            }
        }
    }

    suspend fun updateStatus(
        origin: GoalOrigin,
        goal: GoalResponse,
        completed: Boolean
    ): GoalsResult<GoalResponse> = safeRequest<GoalResponse> {
        val newStatus = if (completed) GoalResponse.COMPLETED else GoalResponse.PENDING

        val result = when (origin) {
            GoalOrigin.PERSONAL -> authenticatedCall {
                api.updateIndividualGoal(
                    id = goal.id,
                    authorization = it,
                    request = UpdateIndividualGoalRequest(status = newStatus)
                )
            }

            GoalOrigin.STATE -> authenticatedCall {
                api.updateStateGoal(
                    id = goal.id,
                    authorization = it,
                    request = UpdateGoalStatusRequest(status = newStatus)
                )
            }
        }

        when (result) {
            ApiCallResult.AuthenticationRequired -> GoalsResult.AuthenticationRequired
            ApiCallResult.TemporaryFailure -> temporaryError()
            is ApiCallResult.ResponseResult -> {
                val response = result.response

                if (response.isSuccessful) {
                    GoalsResult.Success(
                        response.body() ?: goal.copy(status = newStatus)
                    )
                } else {
                    responseError(
                        response.code(),
                        "Não foi possível atualizar a meta."
                    )
                }
            }
        }
    }

    suspend fun create(
        title: String,
        description: String?
    ): GoalsResult<GoalResponse> = safeRequest<GoalResponse> {
        val farmId = farmSessionStore.farmId()
            ?: return@safeRequest GoalsResult.Error(
                message = "Não foi possível identificar a granja. Tente novamente.",
                canRetry = true
            )

        val request = CreateIndividualGoalRequest(
            title = title.trim(),
            description = description?.trim()?.takeIf(String::isNotEmpty),
            farmId = farmId
        )

        when (val result = authenticatedCall {
            api.createIndividualGoal(it, request)
        }) {
            ApiCallResult.AuthenticationRequired -> GoalsResult.AuthenticationRequired
            ApiCallResult.TemporaryFailure -> temporaryError()
            is ApiCallResult.ResponseResult -> readGoal(
                result.response,
                "Não foi possível adicionar a meta."
            )
        }
    }

    suspend fun updateDetails(
        goal: GoalResponse,
        title: String,
        description: String
    ): GoalsResult<GoalResponse> = safeRequest<GoalResponse> {
        val cleanTitle = title.trim()
        val cleanDescription = description.trim()
        val request = UpdateIndividualGoalRequest(
            title = cleanTitle,
            description = cleanDescription
        )

        when (val result = authenticatedCall {
            api.updateIndividualGoal(
                id = goal.id,
                authorization = it,
                request = request
            )
        }) {
            ApiCallResult.AuthenticationRequired -> GoalsResult.AuthenticationRequired
            ApiCallResult.TemporaryFailure -> temporaryError()
            is ApiCallResult.ResponseResult -> {
                val response = result.response

                if (response.isSuccessful) {
                    GoalsResult.Success(
                        response.body() ?: goal.copy(
                            title = cleanTitle,
                            description = cleanDescription
                        )
                    )
                } else {
                    responseError(
                        response.code(),
                        "Não foi possível salvar a meta."
                    )
                }
            }
        }
    }

    suspend fun delete(id: Long): GoalsResult<Unit> = safeRequest<Unit> {
        when (val result = authenticatedCall {
            api.deleteIndividualGoal(id, it)
        }) {
            ApiCallResult.AuthenticationRequired -> GoalsResult.AuthenticationRequired
            ApiCallResult.TemporaryFailure -> temporaryError()
            is ApiCallResult.ResponseResult -> {
                if (result.response.isSuccessful) {
                    GoalsResult.Success(Unit)
                } else {
                    responseError(
                        result.response.code(),
                        "Não foi possível excluir a meta."
                    )
                }
            }
        }
    }

    private fun readGoal(
        response: Response<GoalResponse>,
        fallback: String
    ): GoalsResult<GoalResponse> {
        val goal = response.body()

        return if (response.isSuccessful && goal != null) {
            GoalsResult.Success(goal)
        } else {
            responseError(response.code(), fallback)
        }
    }

    private fun responseError(code: Int, fallback: String): GoalsResult.Error {
        return when (code) {
            400, 422 -> GoalsResult.Error(
                "Os dados enviados são inválidos.",
                false
            )

            403 -> GoalsResult.Error(
                "Você não possui permissão para realizar esta ação.",
                false
            )

            404 -> GoalsResult.Error(
                "A meta não foi encontrada.",
                false
            )

            429 -> GoalsResult.Error(
                "Muitas tentativas. Aguarde e tente novamente.",
                true
            )

            in 500..599 -> GoalsResult.Error(
                "O serviço está indisponível. Tente novamente.",
                true
            )

            else -> GoalsResult.Error(fallback, true)
        }
    }

    private fun temporaryError() = GoalsResult.Error(
        message = "Não foi possível validar sua sessão. Verifique sua conexão e tente novamente.",
        canRetry = true
    )

    private suspend fun <T> authenticatedCall(
        request: suspend (String) -> Response<T>
    ): ApiCallResult<T> {
        when (val authorization = ensureAuthorization()) {
            AuthorizationResult.AuthenticationRequired ->
                return ApiCallResult.AuthenticationRequired

            AuthorizationResult.TemporaryFailure ->
                return ApiCallResult.TemporaryFailure

            is AuthorizationResult.Ready -> {
                val response = request(authorization.header)

                if (response.code() != 401) {
                    return ApiCallResult.ResponseResult(response)
                }
            }
        }

        return when (val authorization = refreshAuthorization()) {
            AuthorizationResult.AuthenticationRequired ->
                ApiCallResult.AuthenticationRequired

            AuthorizationResult.TemporaryFailure ->
                ApiCallResult.TemporaryFailure

            is AuthorizationResult.Ready -> {
                val response = request(authorization.header)

                if (response.code() == 401) {
                    authRepository.clearSession()
                    ApiCallResult.AuthenticationRequired
                } else {
                    ApiCallResult.ResponseResult(response)
                }
            }
        }
    }

    private suspend fun ensureAuthorization(): AuthorizationResult {
        val token = sessionManager.accessToken()

        return if (token.isNullOrBlank()) {
            refreshAuthorization()
        } else {
            AuthorizationResult.Ready("Bearer $token")
        }
    }

    private suspend fun refreshAuthorization(): AuthorizationResult {
        return when (val result = authRepository.refreshSession()) {
            is AuthResult.Success -> {
                val token = sessionManager.accessToken()

                if (token.isNullOrBlank()) {
                    AuthorizationResult.AuthenticationRequired
                } else {
                    AuthorizationResult.Ready("Bearer $token")
                }
            }

            is AuthResult.Error -> {
                if (
                    result.statusCode == 401 ||
                    result.message == NO_SAVED_SESSION_MESSAGE
                ) {
                    AuthorizationResult.AuthenticationRequired
                } else {
                    AuthorizationResult.TemporaryFailure
                }
            }
        }
    }

    private suspend fun <T> safeRequest(
        block: suspend () -> GoalsResult<T>
    ): GoalsResult<T> {
        return try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (_: IOException) {
            GoalsResult.Error(
                "Sem conexão. Verifique sua internet.",
                true
            )
        } catch (_: Exception) {
            GoalsResult.Error(
                "Ocorreu um erro inesperado.",
                true
            )
        }
    }

    private sealed interface AuthorizationResult {
        data class Ready(val header: String) : AuthorizationResult
        data object AuthenticationRequired : AuthorizationResult
        data object TemporaryFailure : AuthorizationResult
    }

    private sealed interface ApiCallResult<out T> {
        data class ResponseResult<T>(
            val response: Response<T>
        ) : ApiCallResult<T>

        data object AuthenticationRequired : ApiCallResult<Nothing>
        data object TemporaryFailure : ApiCallResult<Nothing>
    }

    private companion object {
        const val NO_SAVED_SESSION_MESSAGE = "Nenhuma sessão salva."
    }
}