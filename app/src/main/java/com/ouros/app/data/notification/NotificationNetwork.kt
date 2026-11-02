package com.ouros.app.data.notification

import com.ouros.app.BuildConfig
import com.ouros.app.data.auth.AuthenticatedNetwork
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Configuração de rede de notification. Cria clientes Retrofit compartilhados e centraliza URL, conversores e autenticação.
 */
object NotificationNetwork {

    private val BASE_URL: String = BuildConfig.OUROS_API_BASE_URL

    val api: NotificationApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(AuthenticatedNetwork.client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NotificationApi::class.java)
    }
}
