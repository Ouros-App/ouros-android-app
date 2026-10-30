package com.ouros.app.data.goals

import com.google.gson.annotations.SerializedName

data class GoalResponse(
    val id: Long,
    val title: String,
    val description: String?,
    val type: String = "Outro",
    val status: String = PENDING,
    @SerializedName("target_value") val targetValue: Double = 0.0,
    @SerializedName("id_farm") val farmId: Long? = null,
    @SerializedName("date_creation") val dateCreation: String? = null,
    @SerializedName("date_end") val dateEnd: String? = null,
    val region: String? = null
) {
    val isCompleted: Boolean
        get() = status.equals(COMPLETED, ignoreCase = true) ||
                status.equals("Concluída", ignoreCase = true) ||
                status.equals("Concluida", ignoreCase = true) ||
                status.equals("ACHIEVED", ignoreCase = true)

    companion object {
        const val COMPLETED = "COMPLETED"
        const val PENDING = "PENDING"
    }
}

data class UpdateGoalStatusRequest(
    val status: String
)

data class UpdateIndividualGoalRequest(
    val title: String? = null,
    val description: String? = null,
    val status: String? = null,
    @SerializedName("target_value") val targetValue: Double? = null
)

data class CreateIndividualGoalRequest(
    val title: String,
    val description: String?,
    @SerializedName("id_farm") val farmId: Long,
    val type: String = "Outro",
    val status: String = "Em andamento",
    @SerializedName("target_value") val targetValue: Double = 1.0
)

enum class GoalOrigin { PERSONAL, STATE }

data class GoalKey(
    val origin: GoalOrigin,
    val id: Long
)

data class GoalsPayload(
    val personalGoals: List<GoalResponse>,
    val stateGoals: List<GoalResponse>
)
