package com.ouros.app.data.auth

import com.google.gson.Gson
import java.io.IOException
import kotlinx.coroutines.CancellationException
import retrofit2.Response

class AuthRepository(
    private val api: AuthApi,
    private val refreshTokenStore: RefreshTokenStorage,
    private val sessionManager: SessionManager = AppSession.sessionManager
) {

    private var pendingLogin: PendingLogin? = null

    suspend fun startLogin(email: String, password: String): AuthResult<LoginStartResponse> {
        return try {
            val normalizedEmail = email.trim().lowercase()

            val response = api.startLogin(
                LoginStartRequest(
                    email = normalizedEmail,
                    password = password
                )
            )

            val body = response.body()

            if (response.isSuccessful && body != null) {
                pendingLogin = PendingLogin(
                    email = normalizedEmail,
                    password = password,
                    challengeId = body.challengeId,
                    maskedEmail = body.maskedEmail,
                    expiresAtMillis = System.currentTimeMillis() + body.expiresIn * 1_000L
                )
                AuthResult.Success(body)

            } else {
                errorFor(response.code())
            }

        } catch (error: CancellationException) {
            throw error

        } catch (_: IOException) {
            AuthResult.Error("Sem conexão. Verifique sua internet.")

        } catch (_: Exception) {
            AuthResult.Error("Ocorreu um erro inesperado.")
        }
    }

    suspend fun verifyLogin(code: String): AuthResult<TokenResponse> {
        val pending = pendingLogin
            ?: return AuthResult.Error("O login expirou. Informe seu email novamente.")

        if (System.currentTimeMillis() > pending.expiresAtMillis) {
            pendingLogin = null
            return AuthResult.Error("O código expirou. Informe seu email novamente.")
        }

        return try {
            val response = api.verifyLogin(
                LoginVerifyRequest(
                    challengeId = pending.challengeId,
                    email = pending.email,
                    password = pending.password,
                    code = code
                )
            )

            val token = response.body()

            if (response.isSuccessful && token != null && token.accessToken.isNotBlank()) {
                token.refreshToken?.let {
                    refreshTokenStore.saveRefreshToken(it)
                }

                sessionManager.setAccessToken(
                    token = token.accessToken,
                    expiresInSeconds = token.expiresIn
                )

                pendingLogin = null
                AuthResult.Success(token)

            } else {
                errorFor(response.code())
            }

        } catch (error: CancellationException) {
            throw error

        } catch (_: IOException) {
            AuthResult.Error("Sem conexão. Tente novamente.")

        } catch (_: Exception) {
            AuthResult.Error("Ocorreu um erro inesperado.")
        }
    }

    suspend fun refreshSession(): AuthResult<TokenResponse> {
        val oldRefreshToken = refreshTokenStore.getRefreshToken()
            ?: return AuthResult.Error("Nenhuma sessão salva.")

        return try {
            val response = api.refreshToken(
                RefreshRequest(oldRefreshToken)
            )

            val token = response.body()

            if (response.isSuccessful && token != null && token.accessToken.isNotBlank()) {
                val newRefreshToken = token.refreshToken
                    ?: oldRefreshToken

                refreshTokenStore.saveRefreshToken(newRefreshToken)

                sessionManager.setAccessToken(
                    token = token.accessToken,
                    expiresInSeconds = token.expiresIn
                )

                AuthResult.Success(token)
            } else if (response.code() == 401) {
                clearSession()
                AuthResult.Error("Sua sessão expirou.", 401)

            } else {
                AuthResult.Error("Não foi possível restaurar a sessão.", response.code())
            }

        } catch (error: CancellationException) {
            throw error

        } catch (_: IOException) {
            AuthResult.Error("Sem conexão para restaurar a sessão.")

        } catch (_: Exception) {
            AuthResult.Error("Não foi possível restaurar a sessão.")
        }
    }

    suspend fun startPasswordReset(email: String): AuthResult<PasswordResetStartResponse> {
        return try {
            val response = api.startPasswordReset(
                PasswordResetStartRequest(email = email.trim().lowercase())
            )

            val body = response.body()

            if (response.isSuccessful && body != null && body.challengeId.isNotBlank()) {
                AuthResult.Success(body)

            } else {
                passwordResetErrorFor(
                    response = response,
                    fallback = "Não foi possível enviar o código."
                )
            }

        } catch (error: CancellationException) {
            throw error

        } catch (_: IOException) {
            AuthResult.Error("Sem conexão. Verifique sua internet.")

        } catch (_: Exception) {
            AuthResult.Error("Ocorreu um erro inesperado.")
        }
    }

    suspend fun verifyPasswordReset(challengeId: String, email: String, code: String): AuthResult<PasswordResetVerifyResponse> {
        return try {
            val response = api.verifyPasswordReset(
                PasswordResetVerifyRequest(
                    challengeId = challengeId,
                    email = email.trim().lowercase(),
                    code = code
                )
            )

            val body = response.body()

            if (response.isSuccessful && body != null && body.resetToken.isNotBlank()) {
                AuthResult.Success(body)

            } else {
                passwordResetErrorFor(
                    response = response,
                    fallback = "Não foi possível validar o código."
                )
            }
        } catch (error: CancellationException) {
            throw error

        } catch (_: IOException) {
            AuthResult.Error("Sem conexão. Verifique sua internet.")

        } catch (_: Exception) {
            AuthResult.Error("Ocorreu um erro inesperado.")
        }
    }

    suspend fun confirmPasswordReset(resetToken: String, newPassword: String): AuthResult<PasswordResetConfirmResponse> {
        return try {
            val response = api.confirmPasswordReset(
                PasswordResetConfirmRequest(
                    resetToken = resetToken,
                    newPassword = newPassword
                )
            )

            val body = response.body()

            if (response.isSuccessful && body != null) {
                AuthResult.Success(body)

            } else {
                passwordResetErrorFor(
                    response = response,
                    fallback ="Não foi possível redefinir a senha."
                )
            }

        } catch (error: CancellationException) {
            throw error

        } catch (_: IOException) {
            AuthResult.Error("Sem conexão. Verifique sua internet.")

        } catch (_: Exception) {
            AuthResult.Error("Ocorreu um erro inesperado.")
        }
    }

    fun clearPendingLogin() {
        pendingLogin = null
    }

    fun clearSession() {
        pendingLogin = null
        sessionManager.clear()
        refreshTokenStore.clear()
    }

    private fun passwordResetErrorFor(response: Response<*>, fallback: String): AuthResult.Error {
        val detail = readErrorDetail(response)

        val message = detail
            ?: when (response.code()) {
                400 -> fallback
                401 -> "Não foi possível validar a solicitação."
                422 -> "Confira os dados informados."
                429 -> "Muitas tentativas. Tente novamente mais tarde."
                503 -> "Serviço de recuperação de senha temporariamente indisponível."
                in 500..599 -> "Serviço temporariamente indisponível."
                else -> fallback
            }

        return AuthResult.Error(
            message = message,
            statusCode = response.code()
        )
    }

    private fun readErrorDetail(response: Response<*>): String? {
        val errorJson =
            try {
                response.errorBody()?.string()

            } catch (_: Exception) {
                null
            }

        if (errorJson.isNullOrBlank()) {
            return null
        }

        return try {
            Gson().fromJson(errorJson, ApiErrorResponse::class.java).detail
                ?.takeIf(String::isNotBlank)
        } catch (_: Exception) {
            null
        }
    }

    private fun errorFor(statusCode: Int): AuthResult.Error {
        val message = when (statusCode) {
            401 -> "Email, senha ou código inválido."
            429 -> "Muitas tentativas. Aguarde e tente novamente."
            503 -> "Serviço temporariamente indisponível."
            422 -> "Confira os dados informados."
            in 500..599 -> "Serviço indisponível."
            else -> "Não foi possível autenticar."
        }

        return AuthResult.Error(message = message, statusCode = statusCode)
    }
}