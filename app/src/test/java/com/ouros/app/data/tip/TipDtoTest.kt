package com.ouros.app.data.tip

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TipDtoTest {

    private val gson = Gson()

    @Test
    fun deserializesTipDetailResponseCorrectly() {
        val json = """
            {
                "id": 42,
                "title": "Manejo Hídrico",
                "tip": "Mantenha a pressão adequada dos nipples...",
                "id_farm": 10,
                "categories": ["Manejo", "Água"],
                "total_reviews": 8,
                "average_rating": 4.75,
                "image_url": "https://example.com/nipple.jpg"
            }
        """.trimIndent()

        val tip = gson.fromJson(json, TipDetailResponse::class.java)

        assertEquals(42L, tip.id)
        assertEquals("Manejo Hídrico", tip.effectiveTitle)
        assertEquals("Mantenha a pressão adequada dos nipples...", tip.tip)
        assertEquals(8, tip.effectiveTotalReviews)
        assertEquals(4.75, tip.effectiveAverageRating, 0.001)
        assertEquals("https://example.com/nipple.jpg", tip.effectiveImageUrl)
        assertEquals(listOf("Manejo", "Água"), tip.categories)
    }

    @Test
    fun serializesReviewRequest() {
        val request = ReviewRequest(score = 5, comment = "Excelente recomendação!")
        val json = gson.toJson(request)
        assertTrue(json.contains("\"rating\":5"))
        assertTrue(json.contains("Excelente recomendação!"))
    }

    @Test
    fun deserializesReviewResponse() {
        val json = """
            {
                "id": 1,
                "id_tip": 42,
                "id_farm_owner": 99,
                "rating": 5,
                "comment": "Muito útil",
                "created_at": "2026-10-16T11:00:00Z"
            }
        """.trimIndent()

        val review = gson.fromJson(json, ReviewResponse::class.java)

        assertEquals(1L, review.id)
        assertEquals(42L, review.idTip)
        assertEquals(99L, review.idFarmOwner)
        assertEquals(5, review.score)
        assertEquals("Muito útil", review.comment)
    }

    @Test
    fun deserializesReviewResponse_withScoreAlternate() {
        val json = """
            {
                "id": 1,
                "id_tip": 42,
                "id_farm_owner": 99,
                "score": 5,
                "comment": "Muito útil",
                "created_at": "2026-10-16T11:00:00Z"
            }
        """.trimIndent()

        val review = gson.fromJson(json, ReviewResponse::class.java)
        assertEquals(5, review.score)
    }
}
