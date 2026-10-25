package com.ouros.app.data.record

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface RecordAPI {
    @GET("water-registries")
    suspend fun getWater(@Query("farm_id") farmId: Long): Response<List<WaterRegistryResponse>>

    @POST("water-registries")
    suspend fun createWater(@Body body: WaterRegistryRequest): Response<WaterRegistryResponse>

    @PATCH("water-registries/{id}")
    suspend fun updateWater(@Path("id") id: Long, @Body body: WaterRegistryUpdate): Response<WaterRegistryResponse>

    @GET("energy-registries")
    suspend fun getEnergy(@Query("farm_id") farmId: Long): Response<List<EnergyRegistryResponse>>

    @POST("energy-registries")
    suspend fun createEnergy(@Body body: EnergyRegistryRequest): Response<EnergyRegistryResponse>

    @PATCH("energy-registries/{id}")
    suspend fun updateEnergy(@Path("id") id: Long, @Body body: EnergyRegistryUpdate): Response<EnergyRegistryResponse>

    @GET("chicken-left")
    suspend fun getChickenLeft(@Query("farm_id") farmId: Long): Response<List<ChickenLeftResponse>>

    @POST("chicken-left")
    suspend fun createChickenLeft(@Body body: ChickenLeftRequest): Response<ChickenLeftResponse>

    @PATCH("chicken-left/{id}")
    suspend fun updateChickenLeft(@Path("id") id: Long, @Body body: ChickenLeftUpdate): Response<ChickenLeftResponse>

    @DELETE("chicken-left/{id}")
    suspend fun deleteChickenLeft(@Path("id") id: Long): Response<Unit>

    @GET("lots")
    suspend fun getLots(@Query("id_farm") farmId: Long): Response<List<LotResponse>>

    @PATCH("lots/{id}")
    suspend fun updateLot(@Path("id") id: Long, @Body body: LotUpdateRequest): Response<LotResponse>

    @GET("farms/{id}")
    suspend fun getFarm(@Path("id") id: Long): Response<FarmResponse>

    @PATCH("farms/{id}")
    suspend fun updateFarm(@Path("id") id: Long, @Body body: FarmUpdateRequest): Response<FarmResponse>
}