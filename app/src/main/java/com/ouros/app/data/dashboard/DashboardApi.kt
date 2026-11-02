package com.ouros.app.data.dashboard

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Contrato Retrofit de dashboard. Declara rotas, parâmetros e formatos HTTP esperados; a interpretação das respostas fica no repositório.
 */
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