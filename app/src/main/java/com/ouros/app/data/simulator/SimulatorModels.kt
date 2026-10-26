package com.ouros.app.data.simulator

import com.google.gson.annotations.SerializedName

data class SimulatorRankingResponse(
    @SerializedName("farm_id") val farmId: Long,
    @SerializedName("farm_name") val farmName: String?,
    @SerializedName("region") val region: String?,
    @SerializedName("score") val score: Double,
    @SerializedName("rank_position") val rankPosition: Int
)

data class SimulatorFarmResponse(
    val id: Long,
    val name: String?,
    @SerializedName("chickens_now") val chickensNow: Int?
)

data class SimulatorRankingSimulationResponse(
    @SerializedName("farm_id") val farmId: Long,
    @SerializedName("farm_name") val farmName: String?,
    @SerializedName("current_score") val currentScore: Double,
    @SerializedName("current_position") val currentPosition: Int,
    @SerializedName("current_league") val currentLeague: String?,
    @SerializedName("simulated_score") val simulatedScore: Double,
    @SerializedName("simulated_position") val simulatedPosition: Int,
    @SerializedName("simulated_league") val simulatedLeague: String?,
    @SerializedName("positions_gained") val positionsGained: Int,
    @SerializedName("total_farms") val totalFarms: Int,
    @SerializedName("simulated_percentile_pct") val simulatedPercentilePct: Double?
)

data class SimulatorInitialData(
    val currentCgi: Double,
    val chickens: Int
)

data class SimulatorUiState(
    val initialLoading: Boolean = false,
    val simulating: Boolean = false,
    val hasInitialData: Boolean = false,
    val currentCgi: Double = 0.0,
    val currentChickens: Int = 0,
    val projectedCgiText: String = "--",
    val targetEnergyText: String = "--",
    val savingsText: String = "R$ 0",
    val positionsGainedText: String = "0",
    val simulatedLeague: String = "",
    val errorMessage: String? = null,
    val canRetry: Boolean = false
)