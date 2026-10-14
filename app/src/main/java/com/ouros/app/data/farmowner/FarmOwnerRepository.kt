package com.ouros.app.data.farmowner

import com.ouros.app.data.auth.AuthResult
import com.ouros.app.data.auth.SessionManager
import java.io.IOException
import kotlinx.coroutines.CancellationException

class FarmOwnerRepository(
    private val api: FarmOwnerApi,
    private val sessionManager: SessionManager
) {

    suspend fun getMe(): AuthResult<FarmOwnerResponse> {
        val authorization = authorizationHeader()
            ?: return AuthResult.Error(
                message = "Sessão expirada.",
                statusCode = 401
            )

        return try {
            val response = api.getMe(authorization)
            val farmOwner = response.body()

            when {
                response.isSuccessful && farmOwner != null -> {
                    AuthResult.Success(farmOwner)
                }

                response.code() == 401 -> {
                    AuthResult.Error(
                        message = "Sessão expirada.",
                        statusCode = 401
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
                    AuthResult.Error(
                        message = "Não foi possível carregar o perfil.",
                        statusCode = response.code()
                    )
                }
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
        val authorization = authorizationHeader()
            ?: return AuthResult.Error(
                message = "Sessão expirada.",
                statusCode = 401
            )

        return try {
            val response = api.update(
                id = id,
                authorization = authorization,
                request = request
            )

            val farmOwner = response.body()

            when {
                response.isSuccessful && farmOwner != null -> {
                    AuthResult.Success(farmOwner)
                }

                response.code() == 401 -> {
                    AuthResult.Error(
                        message = "Sessão expirada.",
                        statusCode = 401
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
                    AuthResult.Error(
                        message = "Os dados informados são inválidos.",
                        statusCode = 422
                    )
                }

                else -> {
                    AuthResult.Error(
                        message = "Não foi possível atualizar o perfil.",
                        statusCode = response.code()
                    )
                }
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

    private fun authorizationHeader(): String? {
        val accessToken = sessionManager.accessToken()
            ?: return null

        return "Bearer $accessToken"
    }
}