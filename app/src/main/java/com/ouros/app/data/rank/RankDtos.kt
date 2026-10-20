package com.ouros.app.data.rank

import com.google.gson.annotations.SerializedName

data class RankPodiumResponse(
    @SerializedName("current_farm_id") val currentFarmId: Long,
    @SerializedName("total_ranked_farms") val totalRankedFarms: Int? = null,
    @SerializedName("seconds_until_next_reset") val secondsUntilNextReset: Long? = null,
    @SerializedName("time") val time: RankTime? = null,
    @SerializedName("time_until_next_reset") val timeUntilNextReset: String? = null,
    @SerializedName("next_reset_at") val nextResetAt: String? = null,
    @SerializedName("ranking_podium") val rankingPodium: List<RankPodiumEntry>? = null
) {
    /**
     * Localiza a fazenda do produtor logado na lista do pódio:
     * 1. Prioriza o item explicitamente marcado com relation == "CURRENT"
     * 2. Fallback buscando pelo current_farm_id
     */
    val currentEntry: RankPodiumEntry?
        get() {
            val list = rankingPodium.orEmpty()
            return list.firstOrNull { it.relation.equals(RankPodiumEntry.CURRENT, ignoreCase = true) }
                ?: list.firstOrNull { it.farmId == currentFarmId }
        }

    /**
     * Calcula a liga da fazenda com base na colocação encontrada.
     * Suporta cálculo percentual quando totalRankedFarms estiver disponível,
     * ou cálculo ordinal direto por colocação como fallback.
     */
    val currentLeague: RankLeague?
        get() {
            val position = currentEntry?.rankPosition ?: return null
            val total = totalRankedFarms?.takeIf { it > 0 }
            return if (total != null) {
                RankLeague.fromPosition(position, total)
            } else {
                RankLeague.fromPosition(position)
            }
        }
}

/**
 * Representa o tempo restante até o próximo reset do ranking.
 */
data class RankTime(
    @SerializedName("days") val days: String? = null,
    @SerializedName("hours") val hours: String? = null,
    @SerializedName("minutes") val minutes: String? = null,
    @SerializedName("seconds") val seconds: String? = null
) {
    /**
     * Retorna a unidade mais relevante:
     * - Se houver dias (> 0), exibe apenas os dias (ex: "6 dias")
     * - Se dias for 0 ou nulo, vira horas (ex: "4 horas")
     * - Se horas for 0 ou nulo, fica apenas os minutos (ex: "35 minutos")
     * - Se minutos for 0 ou nulo, exibe os segundos ou "Em instantes"
     */
    fun toDisplayString(): String {
        val d = days?.trim()?.takeIf { it.isNotBlank() && it != "0 dias" && it != "0 dia" }
        if (d != null) return d

        val h = hours?.trim()?.takeIf { it.isNotBlank() && it != "0 horas" && it != "0 hora" }
        if (h != null) return h

        val m = minutes?.trim()?.takeIf { it.isNotBlank() && it != "0 minutos" && it != "0 minuto" }
        if (m != null) return m

        val s = seconds?.trim()?.takeIf { it.isNotBlank() && it != "0 segundos" && it != "0 segundo" }
        if (s != null) return s

        return "Em instantes"
    }
}

/**
 * Formata os segundos restantes na unidade prioritária:
 * - Se dias > 0: "X dias" (ex: "6 dias")
 * - Se dias == 0 e horas > 0: "X horas" (ex: "4 horas")
 * - Se horas == 0 e minutos > 0: "X minutos" (ex: "35 minutos")
 * - Se minutos == 0 e segundos > 0: "X segundos" (ex: "45 segundos")
 * - <= 0: "Em instantes"
 */
fun formatRemainingTime(seconds: Long?): String {
    val remaining = kotlin.math.max(0L, seconds ?: 0L)
    if (remaining <= 0L) return "Em instantes"

    val days = remaining / 86_400
    if (days > 0L) {
        return if (days == 1L) "1 dia" else "$days dias"
    }

    val hours = (remaining % 86_400) / 3_600
    if (hours > 0L) {
        return if (hours == 1L) "1 hora" else "$hours horas"
    }

    val minutes = (remaining % 3_600) / 60
    if (minutes > 0L) {
        return if (minutes == 1L) "1 minuto" else "$minutes minutos"
    }

    val sec = remaining % 60
    return if (sec == 1L) "1 segundo" else "$sec segundos"
}

data class RankPodiumEntry(
    @SerializedName("farm_id") val farmId: Long,
    @SerializedName("farm_name") val farmName: String?,
    @SerializedName("region") val region: String?,
    @SerializedName("score") val score: Double?,
    @SerializedName("rank_position") val rankPosition: Int,
    @SerializedName("relation") val relation: String?
) {
    val displayName: String
        get() = if (relation.equals(CURRENT, ignoreCase = true)) "Você"
        else farmName?.trim()?.takeIf(String::isNotBlank) ?: "Granja #$farmId"

    val isCurrent: Boolean
        get() = relation.equals(CURRENT, ignoreCase = true)

    companion object {
        const val ABOVE = "ABOVE"
        const val CURRENT = "CURRENT"
        const val BELOW = "BELOW"
    }
}
