package com.ouros.app.data.farmowner

import com.ouros.app.BuildConfig
import com.ouros.app.data.auth.AppSession
import com.ouros.app.data.auth.BearerInterceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object FarmOwnerNetwork {

    private val BASE_URL: String = BuildConfig.OUROS_API_BASE_URL

    private val client: OkHttpClient by lazy {
        OkHttpClient
            .Builder()
            .addInterceptor(BearerInterceptor(AppSession.sessionManager))
            .build()
    }

    val api: FarmOwnerApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FarmOwnerApi::class.java)
    }
}