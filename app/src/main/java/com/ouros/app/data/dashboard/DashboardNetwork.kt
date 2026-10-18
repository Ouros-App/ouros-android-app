package com.ouros.app.data.dashboard

import com.ouros.app.data.auth.AuthenticatedNetwork
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object DashboardNetwork {

    private const val BASE_URL = "https://ouros-dashboard.discloud.app/"

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