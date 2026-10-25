package com.ouros.app.data.tip

import com.google.gson.annotations.SerializedName

data class TipDetailResponse(
    val id: Long,
    val tip: String,
    @SerializedName("title", alternate = ["titulo"])
    val title: String? = null,
    @SerializedName("image_url", alternate = ["foto_url", "imageUrl", "fotoUrl"])
    val imageUrl: String? = null,
    @SerializedName("id_farm")
    val idFarm: Long? = null,
    val categories: List<String>? = emptyList(),
    @SerializedName("total_reviews", alternate = ["totalReviews"])
    val totalReviews: Int = 0,
    @SerializedName("average_rating", alternate = ["averageRating"])
    val averageRating: Double = 0.0
) {
    val effectiveTitle: String
        get() = title?.takeIf { it.isNotBlank() } ?: "Dica Operacional"

    val effectiveImageUrl: String?
        get() = imageUrl

    val effectiveTotalReviews: Int
        get() = totalReviews

    val effectiveAverageRating: Double
        get() = averageRating
}

data class ReviewRequest(
    @SerializedName("rating")
    val score: Int,
    val comment: String = "Avaliação enviada pelo aplicativo"
)

data class ReviewResponse(
    val id: Long,
    @SerializedName("id_tip")
    val idTip: Long,
    @SerializedName("id_farm_owner")
    val idFarmOwner: Long? = null,
    @SerializedName("rating", alternate = ["score"])
    val score: Int = 0,
    val comment: String? = null,
    @SerializedName("created_at")
    val createdAt: String? = null
)
