package com.ouros.app.data.tip

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class TipRepositoryTest {

    @Test
    fun getTipById_success() = runBlocking {
        val fakeApi = object : TipApi {
            override suspend fun getTipById(id: Long): Response<TipDetailResponse> {
                val tip = TipDetailResponse(
                    id = id,
                    title = "Como diminuir a conta de água",
                    tip = "Dica completa de manejo hídrico",
                    totalReviews = 10,
                    averageRating = 4.9
                )
                return Response.success(tip)
            }

            override suspend fun createReview(
                tipId: Long,
                request: ReviewRequest
            ): Response<ReviewResponse> = error("unused")
        }

        val repository = TipRepository(api = fakeApi, enableMockFallback = false)
        val result = repository.getTipById(12L)

        assertTrue(result.isSuccess)
        val tip = result.getOrNull()
        assertEquals(12L, tip?.id)
        assertEquals("Como diminuir a conta de água", tip?.title)
        assertEquals(4.9, tip?.averageRating ?: 0.0, 0.01)
    }

    @Test
    fun createReview_success() = runBlocking {
        var submittedTipId: Long? = null
        var submittedScore: Int? = null

        val fakeApi = object : TipApi {
            override suspend fun getTipById(id: Long): Response<TipDetailResponse> = error("unused")

            override suspend fun createReview(
                tipId: Long,
                request: ReviewRequest
            ): Response<ReviewResponse> {
                submittedTipId = tipId
                submittedScore = request.score
                return Response.success(
                    ReviewResponse(
                        id = 1L,
                        idTip = tipId,
                        idFarmOwner = 5L,
                        score = request.score,
                        comment = request.comment,
                        createdAt = "2026-10-16"
                    )
                )
            }
        }

        val repository = TipRepository(api = fakeApi, enableMockFallback = false)
        val result = repository.createReview(tipId = 7L, score = 5, comment = "Muito boa!")

        assertTrue(result.isSuccess)
        assertEquals(7L, submittedTipId)
        assertEquals(5, submittedScore)
    }

    @Test
    fun getTipById_failure_returnsFailureWhenFallbackDisabled() = runBlocking {
        val fakeApi = object : TipApi {
            override suspend fun getTipById(id: Long): Response<TipDetailResponse> {
                return Response.error(404, "Not Found".toResponseBody("application/json".toMediaTypeOrNull()))
            }

            override suspend fun createReview(
                tipId: Long,
                request: ReviewRequest
            ): Response<ReviewResponse> = error("unused")
        }

        val repository = TipRepository(api = fakeApi, enableMockFallback = false)
        val result = repository.getTipById(999L)

        assertTrue(result.isFailure)
    }

    @Test
    fun getTipById_withMockFallback_returnsFallbackTip() = runBlocking {
        val fakeApi = object : TipApi {
            override suspend fun getTipById(id: Long): Response<TipDetailResponse> {
                return Response.error(404, "Not Found".toResponseBody("application/json".toMediaTypeOrNull()))
            }

            override suspend fun createReview(
                tipId: Long,
                request: ReviewRequest
            ): Response<ReviewResponse> = error("unused")
        }

        val repository = TipRepository(api = fakeApi, enableMockFallback = true)
        val result = repository.getTipById(1L)

        assertTrue(result.isSuccess)
        val tip = result.getOrNull()
        assertEquals("Como diminuir a conta de água", tip?.title)
        assertEquals(3.5, tip?.effectiveAverageRating ?: 0.0, 0.01)
    }

    @Test
    fun createReview_failure_returnsFailureWhenFallbackDisabled() = runBlocking {
        val fakeApi = object : TipApi {
            override suspend fun getTipById(id: Long): Response<TipDetailResponse> = error("unused")
            override suspend fun createReview(tipId: Long, request: ReviewRequest): Response<ReviewResponse> {
                return Response.error(500, "Internal Server Error".toResponseBody(null))
            }
        }

        val repository = TipRepository(api = fakeApi, enableMockFallback = false)
        val result = repository.createReview(tipId = 5L, score = 4)

        assertTrue(result.isFailure)
    }

    @Test
    fun createReview_withMockFallback_returnsMockReview() = runBlocking {
        val fakeApi = object : TipApi {
            override suspend fun getTipById(id: Long): Response<TipDetailResponse> = error("unused")
            override suspend fun createReview(tipId: Long, request: ReviewRequest): Response<ReviewResponse> {
                return Response.error(503, "Unavailable".toResponseBody(null))
            }
        }

        val repository = TipRepository(api = fakeApi, enableMockFallback = true)
        val result = repository.createReview(tipId = 5L, score = 5, comment = "Muito boa")

        assertTrue(result.isSuccess)
        val review = result.getOrNull()
        assertEquals(5, review?.score)
        assertEquals("Muito boa", review?.comment)
        assertEquals(5L, review?.idTip)
    }

    @Test
    fun createReview_withBlankComment_usesDefaultComment() = runBlocking {
        var capturedComment: String? = null
        val fakeApi = object : TipApi {
            override suspend fun getTipById(id: Long): Response<TipDetailResponse> = error("unused")
            override suspend fun createReview(tipId: Long, request: ReviewRequest): Response<ReviewResponse> {
                capturedComment = request.comment
                return Response.success(
                    ReviewResponse(id = 1L, idTip = tipId, idFarmOwner = 1L, score = request.score, comment = request.comment, createdAt = "Hoje")
                )
            }
        }

        val repository = TipRepository(api = fakeApi, enableMockFallback = false)
        val result = repository.createReview(tipId = 5L, score = 4, comment = "   ")

        assertTrue(result.isSuccess)
        assertEquals("Avaliação enviada pelo aplicativo", capturedComment)
    }

    @Test
    fun networkException_returnsFailureWithInternetMessage() = runBlocking {
        val fakeApi = object : TipApi {
            override suspend fun getTipById(id: Long): Response<TipDetailResponse> {
                throw java.io.IOException("Timeout")
            }
            override suspend fun createReview(tipId: Long, request: ReviewRequest): Response<ReviewResponse> = error("unused")
        }

        val repository = TipRepository(api = fakeApi, enableMockFallback = false)
        val result = repository.getTipById(1L)

        assertTrue(result.isFailure)
        assertEquals("Sem conexão com o servidor. Verifique sua internet.", result.exceptionOrNull()?.message)
    }

    @Test
    fun reviewRequest_serializesRatingFieldForSpringApiCompatibility() {
        val request = ReviewRequest(score = 5, comment = "Excelente dica")
        val json = com.google.gson.Gson().toJson(request)
        assertTrue(json.contains("\"rating\":5"))
        assertTrue(json.contains("\"comment\":\"Excelente dica\""))
    }

    @Test
    fun reviewResponse_deserializesRatingFieldCorrectly() {
        val json = """{"id":10,"id_tip":1,"rating":4,"comment":"Muito bom"}"""
        val response = com.google.gson.Gson().fromJson(json, ReviewResponse::class.java)
        assertEquals(4, response.score)
        assertEquals(10L, response.id)
        assertEquals(1L, response.idTip)
        assertEquals("Muito bom", response.comment)
    }
}
