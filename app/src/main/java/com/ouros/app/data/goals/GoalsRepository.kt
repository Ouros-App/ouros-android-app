package com.ouros.app.data.goals

import com.ouros.app.data.auth.AuthRepository
import com.ouros.app.data.auth.AuthResult
import com.ouros.app.data.auth.SessionManager
import com.ouros.app.data.session.FarmSessionStore
import java.io.IOException
import kotlinx.coroutines.CancellationException
import retrofit2.Response

sealed interface GoalsResult<out T> {
    data class Success<T>(
        val data: T
    ) : GoalsResult<T>

    data class Error(
        val message: String,
        val canRetry: Boolean
    ) : GoalsResult<Nothing>

    data object AuthenticationRequired :
        GoalsResult<Nothing>
}

class GoalsRepository(
    private val api: GoalsApi,
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
    private val farmSessionStore: FarmSessionStore
) {

    suspend fun load(): GoalsResult<GoalsPayload> =
        safeRequest<GoalsPayload> {
            when (
                val personalCall =
                    authenticatedCall {
                            authorization ->

                        api.getIndividualGoals(
                            authorization
                        )
                    }
            ) {
                ApiCallResult.AuthenticationRequired -> {
                    GoalsResult.AuthenticationRequired
                }

                ApiCallResult.TemporaryFailure -> {
                    temporaryError()
                }

                is ApiCallResult.ResponseResult -> {
                    val personalResponse =
                        personalCall.response

                    val personalGoals =
                        personalResponse.body()

                    if (
                        !personalResponse.isSuccessful ||
                        personalGoals == null
                    ) {
                        return@safeRequest responseError(
                            code =
                                personalResponse.code(),
                            fallback =
                                "Não foi possível carregar as metas pessoais."
                        )
                    }

                    when (
                        val stateCall =
                            authenticatedCall {
                                    authorization ->

                                api.getStateGoals(
                                    authorization
                                )
                            }
                    ) {
                        ApiCallResult.AuthenticationRequired -> {
                            GoalsResult.AuthenticationRequired
                        }

                        ApiCallResult.TemporaryFailure -> {
                            temporaryError()
                        }

                        is ApiCallResult.ResponseResult -> {
                            val stateResponse =
                                stateCall.response

                            val stateGoals =
                                stateResponse.body()

                            if (
                                !stateResponse.isSuccessful ||
                                stateGoals == null
                            ) {
                                return@safeRequest responseError(
                                    code =
                                        stateResponse.code(),
                                    fallback =
                                        "Não foi possível carregar as metas estaduais."
                                )
                            }

                            GoalsResult.Success(
                                GoalsPayload(
                                    personalGoals =
                                        personalGoals,
                                    stateGoals =
                                        stateGoals
                                )
                            )
                        }
                    }
                }
            }
        }

    suspend fun update(
        origin: GoalOrigin,
        id: Long,
        completed: Boolean
    ): GoalsResult<GoalResponse> =
        safeRequest {
            val request =
                UpdateGoalStatusRequest(
                    status =
                        if (completed) {
                            GoalResponse.COMPLETED
                        } else {
                            GoalResponse.PENDING
                        }
                )

            val call:
                    suspend (
                        String
                    ) -> Response<GoalResponse> =
                when (origin) {
                    GoalOrigin.PERSONAL -> {
                            authorization ->

                        api.updateIndividualGoal(
                            id = id,
                            authorization =
                                authorization,
                            request = request
                        )
                    }

                    GoalOrigin.STATE -> {
                            authorization ->

                        api.updateStateGoal(
                            id = id,
                            authorization =
                                authorization,
                            request = request
                        )
                    }
                }

            when (
                val result =
                    authenticatedCall(call)
            ) {
                ApiCallResult.AuthenticationRequired -> {
                    GoalsResult.AuthenticationRequired
                }

                ApiCallResult.TemporaryFailure -> {
                    temporaryError()
                }

                is ApiCallResult.ResponseResult -> {
                    readGoal(
                        response =
                            result.response,
                        fallback =
                            "Não foi possível atualizar a meta."
                    )
                }
            }
        }

    suspend fun create(
        title: String
    ): GoalsResult<GoalResponse> =
        safeRequest {
            val farmId =
                farmSessionStore.farmId()
                    ?: return@safeRequest GoalsResult.Error(
                        message =
                            "Não foi possível identificar a granja. Tente novamente.",
                        canRetry = true
                    )

            val request =
                CreateIndividualGoalRequest(
                    title = title.trim(),
                    farmId = farmId
                )

            when (
                val result =
                    authenticatedCall {
                            authorization ->

                        api.createIndividualGoal(
                            authorization =
                                authorization,
                            request = request
                        )
                    }
            ) {
                ApiCallResult.AuthenticationRequired -> {
                    GoalsResult.AuthenticationRequired
                }

                ApiCallResult.TemporaryFailure -> {
                    temporaryError()
                }

                is ApiCallResult.ResponseResult -> {
                    readGoal(
                        response =
                            result.response,
                        fallback =
                            "Não foi possível adicionar a meta."
                    )
                }
            }
        }

    private fun readGoal(
        response: Response<GoalResponse>,
        fallback: String
    ): GoalsResult<GoalResponse> {
        val goal = response.body()

        return if (
            response.isSuccessful &&
            goal != null
        ) {
            GoalsResult.Success(goal)
        } else {
            responseError(
                code = response.code(),
                fallback = fallback
            )
        }
    }

    private fun responseError(
        code: Int,
        fallback: String
    ): GoalsResult.Error {
        return when (code) {
            400,
            422 -> {
                GoalsResult.Error(
                    message =
                        "Os dados enviados são inválidos.",
                    canRetry = false
                )
            }

            403 -> {
                GoalsResult.Error(
                    message =
                        "Você não possui permissão para realizar esta ação.",
                    canRetry = false
                )
            }

            404 -> {
                GoalsResult.Error(
                    message =
                        "A meta não foi encontrada.",
                    canRetry = false
                )
            }

            429 -> {
                GoalsResult.Error(
                    message =
                        "Muitas tentativas. Aguarde e tente novamente.",
                    canRetry = true
                )
            }

            in 500..599 -> {
                GoalsResult.Error(
                    message =
                        "O serviço está indisponível. Tente novamente.",
                    canRetry = true
                )
            }

            else -> {
                GoalsResult.Error(
                    message = fallback,
                    canRetry = true
                )
            }
        }
    }

    private fun temporaryError():
            GoalsResult.Error {
        return GoalsResult.Error(
            message =
                "Não foi possível validar sua sessão. Verifique sua conexão e tente novamente.",
            canRetry = true
        )
    }

    private suspend fun <T> authenticatedCall(
        request:
        suspend (
            String
        ) -> Response<T>
    ): ApiCallResult<T> {
        val firstAuthorization =
            ensureAuthorization()

        when (firstAuthorization) {
            AuthorizationResult.AuthenticationRequired -> {
                return ApiCallResult
                    .AuthenticationRequired
            }

            AuthorizationResult.TemporaryFailure -> {
                return ApiCallResult
                    .TemporaryFailure
            }

            is AuthorizationResult.Ready -> {
                val response =
                    request(
                        firstAuthorization.header
                    )

                if (response.code() != 401) {
                    return ApiCallResult
                        .ResponseResult(
                            response
                        )
                }
            }
        }

        return when (
            val refreshedAuthorization =
                refreshAuthorization()
        ) {
            AuthorizationResult.AuthenticationRequired -> {
                ApiCallResult.AuthenticationRequired
            }

            AuthorizationResult.TemporaryFailure -> {
                ApiCallResult.TemporaryFailure
            }

            is AuthorizationResult.Ready -> {
                val response =
                    request(
                        refreshedAuthorization.header
                    )

                if (response.code() == 401) {
                    authRepository.clearSession()

                    ApiCallResult
                        .AuthenticationRequired
                } else {
                    ApiCallResult.ResponseResult(
                        response
                    )
                }
            }
        }
    }

    private suspend fun ensureAuthorization():
            AuthorizationResult {
        val token =
            sessionManager.accessToken()

        return if (!token.isNullOrBlank()) {
            AuthorizationResult.Ready(
                header = "Bearer $token"
            )
        } else {
            refreshAuthorization()
        }
    }

    private suspend fun refreshAuthorization():
            AuthorizationResult {
        return when (
            val result =
                authRepository.refreshSession()
        ) {
            is AuthResult.Success -> {
                val token =
                    sessionManager.accessToken()

                if (token.isNullOrBlank()) {
                    AuthorizationResult
                        .AuthenticationRequired
                } else {
                    AuthorizationResult.Ready(
                        header = "Bearer $token"
                    )
                }
            }

            is AuthResult.Error -> {
                if (
                    result.statusCode == 401 ||
                    result.message ==
                    NO_SAVED_SESSION_MESSAGE
                ) {
                    AuthorizationResult
                        .AuthenticationRequired
                } else {
                    AuthorizationResult
                        .TemporaryFailure
                }
            }
        }
    }

    private suspend fun <T> safeRequest(
        block:
        suspend () -> GoalsResult<T>
    ): GoalsResult<T> {
        return try {
            block()
        } catch (
            error: CancellationException
        ) {
            throw error
        } catch (_: IOException) {
            GoalsResult.Error(
                message =
                    "Sem conexão. Verifique sua internet.",
                canRetry = true
            )
        } catch (_: Exception) {
            GoalsResult.Error(
                message =
                    "Ocorreu um erro inesperado.",
                canRetry = true
            )
        }
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

    private sealed interface ApiCallResult<out T> {
        data class ResponseResult<T>(
            val response: Response<T>
        ) : ApiCallResult<T>

        data object AuthenticationRequired :
            ApiCallResult<Nothing>

        data object TemporaryFailure :
            ApiCallResult<Nothing>
    }

    private companion object {
        const val NO_SAVED_SESSION_MESSAGE =
            "Nenhuma sessão salva."
    }
}