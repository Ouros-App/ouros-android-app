package com.ouros.app.data.auth

import com.ouros.app.BuildConfig
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object AuthNetwork {
    private val BASE_URL: String = BuildConfig.AUTH_API_BASE_URL

    val api: AuthApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AuthApi::class.java)
    }
}