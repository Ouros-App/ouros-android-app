package com.ouros.app.data.tip

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun deserializesTipDetailResponse_withFotoUrl() {
        val json = """
            {
                "id": 1,
                "titulo": "Regulagem de Pressão e Vazão dos Bebedouros Nipple",
                "tip": "* **Diagnóstico Zootécnico:** Vazamentos geram umidade na cama.",
                "foto_url": "https://res.cloudinary.com/ouros/image/upload/v1/tips/tip_nipple_hero.webp",
                "total_reviews": 12,
                "average_rating": 4.8
            }
        """.trimIndent()

        val tip = gson.fromJson(json, TipDetailResponse::class.java)

        assertEquals(1L, tip.id)
        assertEquals("Regulagem de Pressão e Vazão dos Bebedouros Nipple", tip.effectiveTitle)
        assertEquals("https://res.cloudinary.com/ouros/image/upload/v1/tips/tip_nipple_hero.webp", tip.effectiveImageUrl)
        assertEquals(12, tip.effectiveTotalReviews)
        assertEquals(4.8, tip.effectiveAverageRating, 0.001)
    }

    @Test
    fun deserializesTipDetailResponse_withComplexMarkdownAndLineBreaks() {
        val json = """
            {
                "id": 2,
                "tip": "* **Diagnóstico Zootécnico:** Consumo hídrico reduzido.\n\n1. **Ajuste de Altura:** Posicionar bicos a 45°.\n2. **Higienização:** Cloração a 3-5 ppm.",
                "image_url": "https://res.cloudinary.com/ouros/image/upload/v1/tips/dica2.webp"
            }
        """.trimIndent()

        val tip = gson.fromJson(json, TipDetailResponse::class.java)

        assertEquals(2L, tip.id)
        assertEquals("Dica Operacional", tip.effectiveTitle)
        assertTrue(tip.tip.contains("* **Diagnóstico Zootécnico:**"))
        assertTrue(tip.tip.contains("1. **Ajuste de Altura:**"))
        assertEquals("https://res.cloudinary.com/ouros/image/upload/v1/tips/dica2.webp", tip.effectiveImageUrl)
        assertEquals(0, tip.effectiveTotalReviews)
        assertEquals(0.0, tip.effectiveAverageRating, 0.001)
    }

    @Test
    fun deserializesTipDetailResponse_withFallbackTitleAndNullImage() {
        val json = """
            {
                "id": 3,
                "tip": "Dica sem título e sem imagem"
            }
        """.trimIndent()

        val tip = gson.fromJson(json, TipDetailResponse::class.java)

        assertEquals(3L, tip.id)
        assertEquals("Dica Operacional", tip.effectiveTitle)
        assertNull(tip.effectiveImageUrl)
        assertEquals(0, tip.effectiveTotalReviews)
        assertEquals(0.0, tip.effectiveAverageRating, 0.001)
    }

    @Test
    fun deserializesTipDetailResponse_withCamelCaseKeys() {
        val json = """
            {
                "id": 4,
                "imageUrl": "https://example.com/tip4.webp",
                "tip": "Texto com chave imageUrl",
                "totalReviews": 15,
                "averageRating": 4.2
            }
        """.trimIndent()

        val tip = gson.fromJson(json, TipDetailResponse::class.java)

        assertEquals("https://example.com/tip4.webp", tip.effectiveImageUrl)
        assertEquals(15, tip.effectiveTotalReviews)
        assertEquals(4.2, tip.effectiveAverageRating, 0.001)
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
