package com.ouros.app.data.auth

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object AuthNetwork {

    private const val BASE_URL = "https://ms-auth-service.discloud.app/"

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val api: AuthApi by lazy {
        retrofit.create(AuthApi::class.java)
    }

    val refreshApi: AuthRefreshApi by lazy {
        retrofit.create(AuthRefreshApi::class.java)
    }
}