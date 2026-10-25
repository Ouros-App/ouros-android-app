package com.ouros.app.data.record

import com.ouros.app.BuildConfig
import com.ouros.app.data.auth.AuthenticatedNetwork
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RecordNetwork {
    val api: RecordAPI by lazy {
        Retrofit
            .Builder()
            .baseUrl(BuildConfig.OUROS_API_BASE_URL)
            .client(AuthenticatedNetwork.client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RecordAPI::class.java)
    }
}