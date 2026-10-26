package com.ouros.app.data.simulator

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface SimulatorApi {
    @GET("farms/{id}/ranking")
    suspend fun getFarmRanking(@Path("id") farmId: Long): Response<SimulatorRankingResponse>

    @GET("farms/{id}")
    suspend fun getFarm(@Path("id") farmId: Long): Response<SimulatorFarmResponse>

    @GET("farms/{id}/ranking/simulate")
    suspend fun simulateRanking(
        @Path("id") farmId: Long,
        @Query("score") score: Double
    ): Response<SimulatorRankingSimulationResponse>
}