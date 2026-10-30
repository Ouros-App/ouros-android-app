package com.ouros.app.feature.main.goals

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ouros.app.data.goals.GoalKey
import com.ouros.app.data.goals.GoalOrigin
import com.ouros.app.data.goals.GoalResponse
import com.ouros.app.data.goals.GoalsRepository
import com.ouros.app.data.goals.GoalsResult
import com.ouros.app.data.goals.GoalsUiState
import kotlinx.coroutines.launch

sealed interface GoalsEvent {
    data class ShowMessage(val message: String) : GoalsEvent
    data object CloseGoalPopup : GoalsEvent
    data object AuthenticationRequired : GoalsEvent
}

class GoalsViewModel(
    private val repository: GoalsRepository
) : ViewModel() {
    private val _state = MutableLiveData(GoalsUiState())
    val state: LiveData<GoalsUiState> = _state

    private val _event = MutableLiveData<GoalsEvent?>()
    val event: LiveData<GoalsEvent?> = _event

    private var initialLoadPerformed = false

    fun loadInitialData() {
        if (initialLoadPerformed || currentState().isLoading) return
        initialLoadPerformed = true
        loadGoals()
    }

    fun retry() {
        if (currentState().isLoading) return
        loadGoals()
    }

    fun toggleGoal(
        origin: GoalOrigin,
        goal: GoalResponse,
        completed: Boolean
    ) {
        val key = GoalKey(origin, goal.id)

        if (key in currentState().updatingGoals) return

        val previousGoal = goal
        val updatedGoal = goal.copy(
            status = if (completed) {
                GoalResponse.COMPLETED
            } else {
                GoalResponse.PENDING
            }
        )

        updateGoalLocally(origin, updatedGoal)
        updateState(
            currentState().copy(
                updatingGoals = currentState().updatingGoals + key
            )
        )

        viewModelScope.launch {
            when (
                val result = repository.updateStatus(
                    origin = origin,
                    goal = goal,
                    completed = completed
                )
            ) {
                is GoalsResult.Success ->
                    updateGoalLocally(origin, result.data)

                is GoalsResult.Error -> {
                    updateGoalLocally(origin, previousGoal)
                    _event.value = GoalsEvent.ShowMessage(result.message)
                }

                GoalsResult.AuthenticationRequired -> {
                    updateGoalLocally(origin, previousGoal)
                    _event.value = GoalsEvent.AuthenticationRequired
                }
            }

            updateState(
                currentState().copy(
                    updatingGoals = currentState().updatingGoals - key
                )
            )
        }
    }

    fun saveGoal(
        goal: GoalResponse?,
        title: String,
        description: String,
        type: String = "Outro"
    ) {
        val cleanTitle = title.trim()
        val cleanDescription = description.trim()
        val cleanType = type.trim().ifBlank { "Outro" }

        if (cleanTitle.isBlank()) {
            _event.value = GoalsEvent.ShowMessage("Informe o título da meta.")
            return
        }

        if (currentState().isPopupSaving) return

        updateState(currentState().copy(isPopupSaving = true))

        viewModelScope.launch {
            val result = if (goal == null) {
                repository.create(
                    title = cleanTitle,
                    description = cleanDescription,
                    type = cleanType
                )
            } else {
                repository.updateDetails(
                    goal = goal,
                    title = cleanTitle,
                    description = cleanDescription
                )
            }

            when (result) {
                is GoalsResult.Success -> {
                    val personalGoals = if (goal == null) {
                        currentState().personalGoals + result.data
                    } else {
                        currentState().personalGoals.map {
                            if (it.id == result.data.id) result.data else it
                        }
                    }

                    updateState(
                        currentState().copy(
                            isPopupSaving = false,
                            personalGoals = personalGoals
                        )
                    )

                    _event.value = GoalsEvent.CloseGoalPopup
                }

                is GoalsResult.Error -> {
                    updateState(currentState().copy(isPopupSaving = false))
                    _event.value = GoalsEvent.ShowMessage(result.message)
                }

                GoalsResult.AuthenticationRequired -> {
                    updateState(currentState().copy(isPopupSaving = false))
                    _event.value = GoalsEvent.AuthenticationRequired
                }
            }
        }
    }

    fun deleteGoal(goal: GoalResponse) {
        if (currentState().isPopupSaving) return

        updateState(currentState().copy(isPopupSaving = true))

        viewModelScope.launch {
            when (val result = repository.delete(goal.id)) {
                is GoalsResult.Success -> {
                    updateState(
                        currentState().copy(
                            isPopupSaving = false,
                            personalGoals = currentState().personalGoals.filterNot {
                                it.id == goal.id
                            }
                        )
                    )

                    _event.value = GoalsEvent.CloseGoalPopup
                }

                is GoalsResult.Error -> {
                    updateState(currentState().copy(isPopupSaving = false))
                    _event.value = GoalsEvent.ShowMessage(result.message)
                }

                GoalsResult.AuthenticationRequired -> {
                    updateState(currentState().copy(isPopupSaving = false))
                    _event.value = GoalsEvent.AuthenticationRequired
                }
            }
        }
    }

    fun consumeEvent() {
        _event.value = null
    }

    private fun loadGoals() {
        updateState(
            currentState().copy(
                isLoading = true,
                error = null,
                canRetry = false
            )
        )

        viewModelScope.launch {
            when (val result = repository.load()) {
                is GoalsResult.Success -> {
                    updateState(
                        currentState().copy(
                            isLoading = false,
                            personalGoals = result.data.personalGoals,
                            stateGoals = result.data.stateGoals,
                            error = null,
                            canRetry = false
                        )
                    )
                }

                is GoalsResult.Error -> {
                    updateState(
                        currentState().copy(
                            isLoading = false,
                            error = result.message,
                            canRetry = result.canRetry
                        )
                    )
                }

                GoalsResult.AuthenticationRequired -> {
                    updateState(currentState().copy(isLoading = false))
                    _event.value = GoalsEvent.AuthenticationRequired
                }
            }
        }
    }

    private fun updateGoalLocally(
        origin: GoalOrigin,
        updated: GoalResponse
    ) {
        val state = currentState()

        updateState(
            when (origin) {
                GoalOrigin.PERSONAL -> state.copy(
                    personalGoals = state.personalGoals.map {
                        if (it.id == updated.id) updated else it
                    }
                )

                GoalOrigin.STATE -> state.copy(
                    stateGoals = state.stateGoals.map {
                        if (it.id == updated.id) updated else it
                    }
                )
            }
        )
    }

    private fun currentState(): GoalsUiState =
        _state.value ?: GoalsUiState()

    private fun updateState(state: GoalsUiState) {
        _state.value = state
    }
}
