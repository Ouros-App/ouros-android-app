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
    data object GoalCreated : GoalsEvent
    data object AuthenticationRequired : GoalsEvent
}

class GoalsViewModel(private val repository: GoalsRepository) : ViewModel() {
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

    fun toggleGoal(origin: GoalOrigin, goal: GoalResponse, completed: Boolean) {
        val key = GoalKey(origin, goal.id)
        if (key in currentState().updatingGoals) return
        val previousGoal = goal
        updateGoalLocally(origin, goal.copy(status = if (completed) GoalResponse.COMPLETED else GoalResponse.PENDING))
        updateState(currentState().copy(updatingGoals = currentState().updatingGoals + key))

        viewModelScope.launch {
            when (val result = repository.update(origin, goal.id, completed)) {
                is GoalsResult.Success -> updateGoalLocally(origin, result.data)
                is GoalsResult.Error -> {
                    updateGoalLocally(origin, previousGoal)
                    _event.value = GoalsEvent.ShowMessage(result.message)
                }
                GoalsResult.AuthenticationRequired -> {
                    updateGoalLocally(origin, previousGoal)
                    _event.value = GoalsEvent.AuthenticationRequired
                }
            }
            updateState(currentState().copy(updatingGoals = currentState().updatingGoals - key))
        }
    }

    fun createGoal(title: String) {
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) {
            _event.value = GoalsEvent.ShowMessage("Informe o título da meta.")
            return
        }
        if (currentState().isCreating) return
        updateState(currentState().copy(isCreating = true))

        viewModelScope.launch {
            when (val result = repository.create(cleanTitle)) {
                is GoalsResult.Success -> {
                    updateState(currentState().copy(
                        isCreating = false,
                        personalGoals = currentState().personalGoals + result.data
                    ))
                    _event.value = GoalsEvent.GoalCreated
                }
                is GoalsResult.Error -> {
                    updateState(currentState().copy(isCreating = false))
                    _event.value = GoalsEvent.ShowMessage(result.message)
                }
                GoalsResult.AuthenticationRequired -> {
                    updateState(currentState().copy(isCreating = false))
                    _event.value = GoalsEvent.AuthenticationRequired
                }
            }
        }
    }

    fun consumeEvent() {
        _event.value = null
    }

    private fun loadGoals() {
        updateState(currentState().copy(isLoading = true, error = null, canRetry = false))
        viewModelScope.launch {
            when (val result = repository.load()) {
                is GoalsResult.Success -> updateState(currentState().copy(
                    isLoading = false,
                    personalGoals = result.data.personalGoals,
                    stateGoals = result.data.stateGoals,
                    error = null,
                    canRetry = false
                ))
                is GoalsResult.Error -> updateState(currentState().copy(
                    isLoading = false,
                    error = result.message,
                    canRetry = result.canRetry
                ))
                GoalsResult.AuthenticationRequired -> {
                    updateState(currentState().copy(isLoading = false))
                    _event.value = GoalsEvent.AuthenticationRequired
                }
            }
        }
    }

    private fun updateGoalLocally(origin: GoalOrigin, updated: GoalResponse) {
        val state = currentState()
        updateState(when (origin) {
            GoalOrigin.PERSONAL -> state.copy(
                personalGoals = state.personalGoals.map { if (it.id == updated.id) updated else it }
            )
            GoalOrigin.STATE -> state.copy(
                stateGoals = state.stateGoals.map { if (it.id == updated.id) updated else it }
            )
        })
    }

    private fun currentState(): GoalsUiState = _state.value ?: GoalsUiState()
    private fun updateState(state: GoalsUiState) { _state.value = state }
}