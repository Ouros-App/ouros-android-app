package com.ouros.app.data.dashboard

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object DashboardNetwork {

    private const val BASE_URL =
        "https://ouros-dashboard.discloud.app/"

    val api: DashboardApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(
                GsonConverterFactory.create()
            )
            .build()
            .create(DashboardApi::class.java)
    }
}