package com.ouros.app.data.dashboard

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface DashboardApi {

    @GET("v1/user/dashboards/status")
    suspend fun getStatus(
        @Header("Authorization")
        authorization: String
    ): Response<DashboardStatusResponse>

    @POST("v1/user/dashboards/custom")
    suspend fun getCustomDashboard(
        @Header("Authorization")
        authorization: String,

        @Body
        request: DashboardRequest
    ): Response<DashboardResponse>
}