package com.ouros.app.data.goals

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object GoalsNetwork {
    private const val BASE_URL = "https://ms-spring-api.discloud.app/"

    val api: GoalsApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GoalsApi::class.java)
    }
}