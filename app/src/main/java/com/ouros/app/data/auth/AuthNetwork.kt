package com.ouros.app.data.auth

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object AuthNetwork {
    private const val BASE_URL = "https://ms-auth-service.discloud.app/"

    val api: AuthApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AuthApi::class.java)
    }
}