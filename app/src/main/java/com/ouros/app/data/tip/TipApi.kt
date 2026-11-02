package com.ouros.app.data.tip

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Contrato Retrofit de tip. Declara rotas, parâmetros e formatos HTTP esperados; a interpretação das respostas fica no repositório.
 */
interface TipApi {

    @GET("tips/{id}")
    suspend fun getTipById(
        @Path("id") id: Long
    ): Response<TipDetailResponse>

    @POST("tips/{tipId}/reviews")
    suspend fun createReview(
        @Path("tipId") tipId: Long,
        @Body request: ReviewRequest
    ): Response<ReviewResponse>
}
