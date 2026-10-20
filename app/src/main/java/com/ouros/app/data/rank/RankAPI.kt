package com.ouros.app.data.rank

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path

interface RankAPI {
    @Headers("Accept: application/json")
    @GET("farms/{id}/ranking/podium")
    suspend fun getPodium(@Path("id") farmId: Long): Response<RankPodiumResponse>
}