package com.ouros.app.data.rank

import com.ouros.app.data.auth.AuthenticatedNetwork
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RankNetwork {
    private const val BASE_URL = "https://ms-spring-api.discloud.app/"

    val api: RankApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(AuthenticatedNetwork.client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RankApi::class.java)
    }
}