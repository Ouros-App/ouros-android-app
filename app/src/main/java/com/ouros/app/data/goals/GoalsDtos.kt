package com.ouros.app.data.goals

import com.google.gson.annotations.SerializedName

data class GoalResponse(
    val id: Long,
    val title: String,
    val description: String?,
    val type: String,
    val status: String,
    @SerializedName("target_value") val targetValue: Double,
    @SerializedName("id_farm") val farmId: Long?,
    @SerializedName("date_creation") val dateCreation: String?,
    @SerializedName("date_end") val dateEnd: String?,
    val region: String?
) {
    val isCompleted: Boolean
        get() = status.equals(COMPLETED, ignoreCase = true)

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
    val status: String? = null
)

data class CreateIndividualGoalRequest(
    val title: String,
    val description: String? = null,
    val type: String = "MAINTENANCE",
    val status: String = GoalResponse.PENDING,
    @SerializedName("target_value") val targetValue: Double = 1.0,
    @SerializedName("id_farm") val farmId: Long
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