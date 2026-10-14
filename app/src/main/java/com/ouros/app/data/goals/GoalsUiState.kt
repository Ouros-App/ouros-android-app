package com.ouros.app.data.goals

import kotlin.math.roundToInt

data class GoalsUiState(
    val isLoading: Boolean = false,
    val isCreating: Boolean = false,
    val personalGoals: List<GoalResponse> = emptyList(),
    val stateGoals: List<GoalResponse> = emptyList(),
    val updatingGoals: Set<GoalKey> = emptySet(),
    val error: String? = null,
    val canRetry: Boolean = false
) {
    val allGoals: List<GoalResponse> get() = personalGoals + stateGoals
    val totalGoals: Int get() = allGoals.size
    val completedGoals: Int get() = allGoals.count(GoalResponse::isCompleted)
    val progress: Int get() = if (totalGoals == 0) 0 else ((completedGoals.toDouble() / totalGoals) * 100).roundToInt()
}