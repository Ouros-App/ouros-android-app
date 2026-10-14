package com.ouros.app.data.dashboard

import com.ouros.app.BuildConfig
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object DashboardNetwork {

    private val BASE_URL: String =
        BuildConfig.DASHBOARD_BASE_URL

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