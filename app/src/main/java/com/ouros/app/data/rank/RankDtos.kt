package com.ouros.app.data.rank

import com.google.gson.annotations.SerializedName

data class RankPodiumResponse(
    @SerializedName("current_farm_id") val currentFarmId: Long,
    @SerializedName("seconds_until_next_reset") val secondsUntilNextReset: Long?,
    @SerializedName("time_until_next_reset") val timeUntilNextReset: String?,
    @SerializedName("next_reset_at") val nextResetAt: String?,
    @SerializedName("ranking_podium") val rankingPodium: List<RankPodiumEntry>
) {
    val currentEntry: RankPodiumEntry?
        get() = rankingPodium.firstOrNull { it.relation.equals(RankPodiumEntry.CURRENT, ignoreCase = true) }
            ?: rankingPodium.firstOrNull { it.farmId == currentFarmId }
}

data class RankPodiumEntry(
    @SerializedName("farm_id") val farmId: Long,
    @SerializedName("farm_name") val farmName: String?,
    val region: String?,
    val score: Double,
    @SerializedName("rank_position") val rankPosition: Int,
    val relation: String?
) {
    val displayName: String
        get() = if (relation.equals(CURRENT, ignoreCase = true)) "Você"
        else farmName?.trim()?.takeIf(String::isNotBlank) ?: "Granja"

    companion object {
        const val ABOVE = "ABOVE"
        const val CURRENT = "CURRENT"
        const val BELOW = "BELOW"
    }
}