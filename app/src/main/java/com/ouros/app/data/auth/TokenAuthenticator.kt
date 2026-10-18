package com.ouros.app.data.auth

import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.Response as RetrofitResponse

class TokenAuthenticator(
    private val sessionManager: SessionManager,
    private val refreshTokenStore: RefreshTokenStorage,
    private val refreshToken:
        (RefreshRequest) ->
    RetrofitResponse<TokenResponse>,
    private val onSessionExpired: () -> Unit
) : Authenticator {

    override fun authenticate(
        route: Route?,
        response: Response
    ): Request? {
        /*
         * A requisição já foi repetida com um token
         * renovado e mesmo assim recebeu outro 401.
         *
         * Interrompe o ciclo e encerra a sessão.
         */
        if (
            responseCount(response) >=
            MAX_AUTH_ATTEMPTS
        ) {
            onSessionExpired()
            return null
        }

        /*
         * Somente uma requisição pode renovar o token
         * por vez. As demais ficam aguardando aqui.
         */
        synchronized(this) {
            val requestAccessToken =
                response.request
                    .header(
                        AUTHORIZATION_HEADER
                    )
                    ?.removePrefix(
                        BEARER_PREFIX
                    )

            val currentAccessToken =
                sessionManager.accessToken()

            /*
             * Se o token atual for diferente daquele
             * usado pela requisição que recebeu 401,
             * outra requisição já concluiu o refresh.
             *
             * Nesse caso, apenas reutiliza o token novo.
             */
            if (
                !currentAccessToken.isNullOrBlank() &&
                currentAccessToken !=
                requestAccessToken
            ) {
                return response.request
                    .withBearerToken(
                        currentAccessToken
                    )
            }

            val savedRefreshToken =
                refreshTokenStore
                    .getRefreshToken()

            if (
                savedRefreshToken.isNullOrBlank()
            ) {
                onSessionExpired()
                return null
            }

            val refreshResponse =
                try {
                    refreshToken(
                        RefreshRequest(
                            refreshToken =
                                savedRefreshToken
                        )
                    )
                } catch (_: Exception) {
                    /*
                     * Falha de conexão ou erro temporário.
                     * Não apaga a sessão.
                     */
                    return null
                }

            /*
             * Somente o 401 do próprio endpoint de
             * refresh representa sessão expirada.
             */
            if (
                refreshResponse.code() == 401
            ) {
                onSessionExpired()
                return null
            }

            val tokens =
                refreshResponse.body()

            if (
                !refreshResponse.isSuccessful ||
                tokens == null ||
                tokens.accessToken.isBlank()
            ) {
                /*
                 * 5xx, resposta inválida ou outro erro
                 * temporário não apaga os tokens.
                 */
                return null
            }

            /*
             * O Keycloak pode rotacionar o refresh token.
             * Se a API não enviar outro, mantém o atual.
             */
            val rotatedRefreshToken =
                tokens.refreshToken
                    ?.takeIf(
                        String::isNotBlank
                    )
                    ?: savedRefreshToken

            refreshTokenStore
                .saveRefreshToken(
                    rotatedRefreshToken
                )

            sessionManager.setAccessToken(
                token = tokens.accessToken,
                expiresInSeconds =
                    tokens.expiresIn
            )

            /*
             * Repete a requisição original com o novo
             * access token.
             */
            return response.request
                .withBearerToken(
                    tokens.accessToken
                )
        }
    }

    private fun Request.withBearerToken(
        token: String
    ): Request {
        return newBuilder()
            .header(
                AUTHORIZATION_HEADER,
                "$BEARER_PREFIX$token"
            )
            .build()
    }

    private fun responseCount(
        response: Response
    ): Int {
        var count = 1
        var priorResponse =
            response.priorResponse

        while (priorResponse != null) {
            count++
            priorResponse =
                priorResponse.priorResponse
        }

        return count
    }

    private companion object {
        const val AUTHORIZATION_HEADER =
            "Authorization"

        const val BEARER_PREFIX =
            "Bearer "

        const val MAX_AUTH_ATTEMPTS =
            2
    }
}