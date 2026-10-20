package com.ouros.app.feature.main.rank

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ouros.app.data.rank.RankRepository
import com.ouros.app.data.rank.RankResult
import com.ouros.app.data.rank.RankUiState
import kotlinx.coroutines.launch

sealed interface RankEvent {
    data object AuthenticationRequired : RankEvent
}

class RankViewModel(private val repository: RankRepository) : ViewModel() {
    private val _state = MutableLiveData(RankUiState())
    val state: LiveData<RankUiState> = _state

    private val _event = MutableLiveData<RankEvent?>()
    val event: LiveData<RankEvent?> = _event

    private var initialLoadPerformed = false

    fun loadInitialData() {
        if (initialLoadPerformed || currentState().isLoading) return
        initialLoadPerformed = true
        loadRanking()
    }

    fun retry() {
        if (currentState().isLoading) return
        loadRanking()
    }

    fun consumeEvent() {
        _event.value = null
    }

    private fun loadRanking() {
        updateState(
            currentState().copy(
                isLoading = true,
                error = null,
                canRetry = false
            )
        )

        viewModelScope.launch {
            when (val result = repository.load()) {
                is RankResult.Success -> updateState(
                    currentState().copy(
                        isLoading = false,
                        ranking = result.data,
                        error = null,
                        canRetry = false
                    )
                )
                is RankResult.Error -> updateState(
                    currentState().copy(
                        isLoading = false,
                        ranking = null,
                        error = result.message,
                        canRetry = result.canRetry
                    )
                )
                RankResult.AuthenticationRequired -> {
                    updateState(
                        currentState().copy(
                            isLoading = false,
                            ranking = null
                        )
                    )
                    _event.value = RankEvent.AuthenticationRequired
                }
            }
        }
    }

    private fun currentState(): RankUiState {
        return _state.value ?: RankUiState()
    }

    private fun updateState(state: RankUiState) {
        _state.value = state
    }
}