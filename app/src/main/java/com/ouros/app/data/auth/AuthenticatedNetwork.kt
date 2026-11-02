package com.ouros.app.data.auth

import okhttp3.OkHttpClient

/**
 * Configuração de rede de auth. Cria clientes Retrofit compartilhados e centraliza URL, conversores e autenticação.
 */
object AuthenticatedNetwork {

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(
                BearerInterceptor(
                    sessionManager = AppSession.sessionManager
                )
            )
            .authenticator(
                TokenAuthenticator(
                    sessionManager =
                        AppSession.sessionManager,

                    refreshTokenStore =
                        AppSession.refreshTokenStore,

                    refreshToken = {
                            request ->
                        AuthNetwork
                            .refreshApi
                            .refreshToken(request)
                            .execute()
                    },

                    onSessionExpired =
                        AppSession::expireSession
                )
            )
            .build()
    }
}
