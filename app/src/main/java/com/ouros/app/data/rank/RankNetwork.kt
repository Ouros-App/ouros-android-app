package com.ouros.app.data.rank

import com.ouros.app.BuildConfig
import com.ouros.app.data.auth.AuthenticatedNetwork
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

typealias RankApi = RankAPI

object RankNetwork {

    private val BASE_URL: String = BuildConfig.OUROS_API_BASE_URL

    val api: RankAPI by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(AuthenticatedNetwork.client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RankAPI::class.java)
    }
}
