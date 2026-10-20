package com.ouros.app.data.auth

import com.ouros.app.BuildConfig
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object AuthNetwork {

    private val BASE_URL: String = BuildConfig.AUTH_API_BASE_URL

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
