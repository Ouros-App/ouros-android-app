package com.ouros.app.data.auth

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Componente de apoio a auth. Reúne comportamento relacionado a essa funcionalidade para manter o fluxo principal mais simples.
 */
class BearerInterceptor(private val sessionManager: SessionManager) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = sessionManager.accessToken()

        val request = if (token != null) {
            chain
                .request()
                .newBuilder()
                .header("Authorization","Bearer $token")
                .build()

        } else {
            chain.request()
        }

        return chain.proceed(request)
    }
}