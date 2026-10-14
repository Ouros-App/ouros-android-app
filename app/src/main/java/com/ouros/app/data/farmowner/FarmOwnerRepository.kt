package com.ouros.app.data.farmowner

import com.ouros.app.data.auth.AuthResult
import com.ouros.app.data.auth.SessionManager
import com.ouros.app.data.auth.TokenResponse
import retrofit2.Response
import java.io.IOException
import kotlinx.coroutines.CancellationException

class FarmOwnerRepository(
    private val api: FarmOwnerApi,
    private val sessionManager: SessionManager,
    private val refreshSession: suspend () -> AuthResult<TokenResponse>,
    private val clearSession: () -> Unit
) {

    suspend fun getMe(): AuthResult<FarmOwnerResponse> {
        return try {
            when (val result = authenticatedCall(api::getMe)) {
                is AuthenticatedCallResult.ResponseResult -> {
                    val response = result.response
                    val farmOwner = response.body()

                    when {
                        response.isSuccessful && farmOwner != null ->
                            AuthResult.Success(farmOwner)
                        response.code() == 403 ->
                            AuthResult.Error(
                                message = "Este perfil não possui acesso de produtor rural.",
                                statusCode = 403
                            )
                        response.code() == 404 ->
                            AuthResult.Error(
                                message = "Produtor rural não encontrado.",
                                statusCode = 404
                            )
                        else ->
                            AuthResult.Error(
                                message = "Não foi possível carregar o perfil.",
                                statusCode = response.code()
                            )
                    }
                }

                AuthenticatedCallResult.AuthenticationRequired ->
                    sessionExpired()

                is AuthenticatedCallResult.TemporaryFailure ->
                    AuthResult.Error(
                        message = result.error.message,
                        statusCode = result.error.statusCode
                    )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: IOException) {
            AuthResult.Error(
                message = "Sem conexão. Verifique sua internet."
            )
        } catch (_: Exception) {
            AuthResult.Error(
                message = "Ocorreu um erro inesperado."
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
                is AuthenticatedCallResult.ResponseResult -> {
                    val response = result.response
                    val farmOwner = response.body()

                    when {
                        response.isSuccessful && farmOwner != null ->
                            AuthResult.Success(farmOwner)
                        response.code() == 403 ->
                            AuthResult.Error(
                                message = "Você não possui permissão para alterar este perfil.",
                                statusCode = 403
                            )
                        response.code() == 404 ->
                            AuthResult.Error(
                                message = "Produtor rural não encontrado.",
                                statusCode = 404
                            )
                        response.code() == 422 ->
                            AuthResult.Error(
                                message = "Os dados informados são inválidos.",
                                statusCode = 422
                            )
                        else ->
                            AuthResult.Error(
                                message = "Não foi possível atualizar o perfil.",
                                statusCode = response.code()
                            )
                    }
                }

                AuthenticatedCallResult.AuthenticationRequired ->
                    sessionExpired()

                is AuthenticatedCallResult.TemporaryFailure ->
                    AuthResult.Error(
                        message = result.error.message,
                        statusCode = result.error.statusCode
                    )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: IOException) {
            AuthResult.Error(
                message = "Sem conexão. Verifique sua internet."
            )
        } catch (_: Exception) {
            AuthResult.Error(
                message = "Ocorreu um erro inesperado."
            )
        }
    }

    private suspend fun <T> authenticatedCall(
        request: suspend (authorization: String) -> Response<T>
    ): AuthenticatedCallResult<T> {
        return when (val firstAuthorization = ensureAuthorization()) {
            AuthorizationResult.AuthenticationRequired ->
                AuthenticatedCallResult.AuthenticationRequired
            is AuthorizationResult.TemporaryFailure ->
                AuthenticatedCallResult.TemporaryFailure(firstAuthorization.error)
            is AuthorizationResult.Ready -> {
                val firstResponse = request(firstAuthorization.header)
                if (firstResponse.code() != 401) {
                    return AuthenticatedCallResult.ResponseResult(firstResponse)
                }

                when (val refreshedAuthorization = refreshAuthorization()) {
                    AuthorizationResult.AuthenticationRequired ->
                        AuthenticatedCallResult.AuthenticationRequired
                    is AuthorizationResult.TemporaryFailure ->
                        AuthenticatedCallResult.TemporaryFailure(refreshedAuthorization.error)
                    is AuthorizationResult.Ready -> {
                        val retryResponse = request(refreshedAuthorization.header)
                        if (retryResponse.code() == 401) {
                            clearSession()
                            AuthenticatedCallResult.AuthenticationRequired
                        } else {
                            AuthenticatedCallResult.ResponseResult(retryResponse)
                        }
                    }
                }
            }
        }

    }
    private suspend fun ensureAuthorization(): AuthorizationResult {
        val accessToken = sessionManager.accessToken()
            ?.takeIf(String::isNotBlank)

        return if (accessToken != null) {
            AuthorizationResult.Ready("Bearer $accessToken")
        } else {
            refreshAuthorization()
        }
    }

    private suspend fun refreshAuthorization(): AuthorizationResult {
        return when (val result = refreshSession()) {
            is AuthResult.Success -> {
                val accessToken = sessionManager.accessToken()
                    ?.takeIf(String::isNotBlank)

                if (accessToken == null) {
                    AuthorizationResult.AuthenticationRequired
                } else {
                    AuthorizationResult.Ready("Bearer $accessToken")
                }
            }

            is AuthResult.Error -> {
                if (
                    result.statusCode == 401 ||
                    result.message == NO_SAVED_SESSION_MESSAGE
                ) {
                    AuthorizationResult.AuthenticationRequired
                } else {
                    AuthorizationResult.TemporaryFailure(result)
                }
            }
        }
    }


    private fun sessionExpired() =
        AuthResult.Error(
            message = "Sessão expirada.",
            statusCode = 401
        )

    private sealed interface AuthorizationResult {
        data class Ready(val header: String) : AuthorizationResult
        data object AuthenticationRequired : AuthorizationResult
        data class TemporaryFailure(val error: AuthResult.Error) : AuthorizationResult
    }

    private sealed interface AuthenticatedCallResult<out T> {
        data class ResponseResult<T>(val response: Response<T>) : AuthenticatedCallResult<T>
        data object AuthenticationRequired : AuthenticatedCallResult<Nothing>
        data class TemporaryFailure(val error: AuthResult.Error) : AuthenticatedCallResult<Nothing>
    }

    private companion object {
        const val NO_SAVED_SESSION_MESSAGE = "Nenhuma sessão salva."
    }
}