package com.ouros.app.data.auth

import okhttp3.OkHttpClient

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
