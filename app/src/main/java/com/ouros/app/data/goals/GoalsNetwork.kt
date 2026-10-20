package com.ouros.app.data.goals

import com.ouros.app.BuildConfig
import com.ouros.app.data.auth.AuthenticatedNetwork
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object GoalsNetwork {

    private val BASE_URL: String = BuildConfig.OUROS_API_BASE_URL

    val api: GoalsApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(AuthenticatedNetwork.client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GoalsApi::class.java)
    }
}
