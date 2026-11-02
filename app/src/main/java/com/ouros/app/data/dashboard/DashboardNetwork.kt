package com.ouros.app.data.dashboard

import com.ouros.app.BuildConfig
import com.ouros.app.data.auth.AuthenticatedNetwork
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Configuração de rede de dashboard. Cria clientes Retrofit compartilhados e centraliza URL, conversores e autenticação.
 */
object DashboardNetwork {

    private val BASE_URL: String =
        BuildConfig.DASHBOARD_BASE_URL

    val api: DashboardApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(
                AuthenticatedNetwork.client
            )
            .addConverterFactory(
                GsonConverterFactory.create()
            )
            .build()
            .create(
                DashboardApi::class.java
            )
    }
}
