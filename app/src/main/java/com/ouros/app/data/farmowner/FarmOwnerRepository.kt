package com.ouros.app.data.farmowner

import com.ouros.app.data.auth.AuthResult
import com.ouros.app.data.auth.SessionManager
import java.io.IOException
import kotlinx.coroutines.CancellationException
import retrofit2.Response

class FarmOwnerRepository(
    private val api: FarmOwnerApi,
    private val sessionManager: SessionManager,
    private val refreshSession: suspend () -> AuthResult<*>,
    private val clearSession: () -> Unit
) {
    suspend fun getMe(): AuthResult<FarmOwnerResponse> {
        return try {
            when (val result = authenticatedCall(api::getMe)) {
                ApiCallResult.AuthenticationRequired -> {
                    AuthResult.Error(
                        message = "Sessão expirada.",
                        statusCode = 401
                    )
                }

                ApiCallResult.TemporaryFailure -> {
                    AuthResult.Error(
                        message = "Não foi possível validar sua sessão. Verifique sua conexão e tente novamente."
                    )
                }

                is ApiCallResult.ResponseResult -> {
                    readProfileResponse(
                        response = result.response,
                        fallback = "Não foi possível carregar o perfil."
                    )
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: IOException) {
            AuthResult.Error(
                message = "Sem conexão. Verifique sua internet."
            )
        } catch (error: Exception) {
            AuthResult.Error(
                message = "Erro ao carregar o perfil: ${error.message}"
            )
        }
    }

    suspend fun update(
        id: Long,
        request: FarmOwnerUpdateRequest
    ): AuthResult<FarmOwnerResponse> {
        return try {
            when (
                val result = authenticatedCall { authorization ->
                    api.update(
                        id = id,
                        authorization = authorization,
                        request = request
                    )
                }
            ) {
                ApiCallResult.AuthenticationRequired -> {
                    AuthResult.Error(
                        message = "Sessão expirada.",
                        statusCode = 401
                    )
                }

                ApiCallResult.TemporaryFailure -> {
                    AuthResult.Error(
                        message = "Não foi possível validar sua sessão. Verifique sua conexão e tente novamente."
                    )
                }

                is ApiCallResult.ResponseResult -> {
                    readUpdateResponse(result.response)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: IOException) {
            AuthResult.Error(
                message = "Sem conexão. Verifique sua internet."
            )
        } catch (error: Exception) {
            AuthResult.Error(
                message = "Erro ao atualizar o perfil: ${error.message}"
            )
        }
    }

    private fun readProfileResponse(
        response: Response<FarmOwnerResponse>,
        fallback: String
    ): AuthResult<FarmOwnerResponse> {
        val farmOwner = response.body()

        return when {
            response.isSuccessful && farmOwner != null -> {
                AuthResult.Success(farmOwner)
            }

            response.isSuccessful -> {
                AuthResult.Error(
                    message = "A API retornou o perfil vazio. HTTP ${response.code()}",
                    statusCode = response.code()
                )
            }

            response.code() == 403 -> {
                AuthResult.Error(
                    message = "Este perfil não possui acesso de produtor rural.",
                    statusCode = 403
                )
            }

            response.code() == 404 -> {
                AuthResult.Error(
                    message = "Produtor rural não encontrado.",
                    statusCode = 404
                )
            }

            else -> {
                val errorBody = response.errorBody()?.string().orEmpty()

                AuthResult.Error(
                    message = buildString {
                        append("$fallback HTTP ${response.code()}")
                        if (errorBody.isNotBlank()) {
                            append(": $errorBody")
                        }
                    },
                    statusCode = response.code()
                )
            }
        }
    }

    private fun readUpdateResponse(
        response: Response<FarmOwnerResponse>
    ): AuthResult<FarmOwnerResponse> {
        val farmOwner = response.body()

        return when {
            response.isSuccessful && farmOwner != null -> {
                AuthResult.Success(farmOwner)
            }

            response.isSuccessful -> {
                AuthResult.Error(
                    message = "A API confirmou a atualização, mas retornou o perfil vazio. HTTP ${response.code()}",
                    statusCode = response.code()
                )
            }

            response.code() == 403 -> {
                AuthResult.Error(
                    message = "Você não possui permissão para alterar este perfil.",
                    statusCode = 403
                )
            }

            response.code() == 404 -> {
                AuthResult.Error(
                    message = "Produtor rural não encontrado.",
                    statusCode = 404
                )
            }

            response.code() == 422 -> {
                val errorBody = response.errorBody()?.string().orEmpty()

                AuthResult.Error(
                    message = if (errorBody.isBlank()) {
                        "Os dados informados são inválidos."
                    } else {
                        "Os dados informados são inválidos: $errorBody"
                    },
                    statusCode = 422
                )
            }

            else -> {
                val errorBody = response.errorBody()?.string().orEmpty()

                AuthResult.Error(
                    message = buildString {
                        append("Não foi possível atualizar o perfil. HTTP ${response.code()}")
                        if (errorBody.isNotBlank()) {
                            append(": $errorBody")
                        }
                    },
                    statusCode = response.code()
                )
            }
        }
    }

    private suspend fun <T> authenticatedCall(
        request: suspend (String) -> Response<T>
    ): ApiCallResult<T> {
        when (val authorization = ensureAuthorization()) {
            AuthorizationResult.AuthenticationRequired -> {
                return ApiCallResult.AuthenticationRequired
            }

            AuthorizationResult.TemporaryFailure -> {
                return ApiCallResult.TemporaryFailure
            }

            is AuthorizationResult.Ready -> {
                val response = request(authorization.header)

                if (response.code() != 401) {
                    return ApiCallResult.ResponseResult(response)
                }
            }
        }

        return when (val authorization = refreshAuthorization()) {
            AuthorizationResult.AuthenticationRequired -> {
                ApiCallResult.AuthenticationRequired
            }

            AuthorizationResult.TemporaryFailure -> {
                ApiCallResult.TemporaryFailure
            }

            is AuthorizationResult.Ready -> {
                val response = request(authorization.header)

                if (response.code() == 401) {
                    clearSession()
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
        return when (val result = refreshSession()) {
            is AuthResult.Success -> {
                val token = sessionManager.accessToken()

                if (token.isNullOrBlank()) {
                    clearSession()
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
                    clearSession()
                    AuthorizationResult.AuthenticationRequired
                } else {
                    AuthorizationResult.TemporaryFailure
                }
            }
        }
    }

    private sealed interface AuthorizationResult {
        data class Ready(
            val header: String
        ) : AuthorizationResult

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