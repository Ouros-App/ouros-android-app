package com.ouros.app.data.rank

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path

/**
 * Contrato Retrofit de rank. Declara rotas, parâmetros e formatos HTTP esperados; a interpretação das respostas fica no repositório.
 */
interface RankAPI {
    @Headers("Accept: application/json")
    @GET("farms/{id}/ranking/podium")
    suspend fun getPodium(@Path("id") farmId: Long): Response<RankPodiumResponse>
}