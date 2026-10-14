package com.ouros.app.feature.main.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ouros.app.data.dashboard.DashboardPeriod
import com.ouros.app.data.dashboard.DashboardRepository
import com.ouros.app.data.dashboard.DashboardResult
import com.ouros.app.data.dashboard.DashboardUiState
import kotlinx.coroutines.launch

sealed interface DashboardEvent {

    data object AuthenticationRequired : DashboardEvent
}

class DashboardViewModel(private val dashboardRepository: DashboardRepository) : ViewModel() {

    private val _state = MutableLiveData(DashboardUiState())
    val state: LiveData<DashboardUiState> = _state
    private val _event = MutableLiveData<DashboardEvent?>()
    val event: LiveData<DashboardEvent?> = _event

    private var initialLoadPerformed = false
    private var requestVersion = 0

    fun loadInitialData() {
        if (initialLoadPerformed || currentState().isLoading) {
            return
        }

        initialLoadPerformed = true
        loadDashboard()
    }

    fun retry() {
        if (currentState().isLoading) {
            return
        }

        loadDashboard()
    }

    fun selectPeriod(period: DashboardPeriod) {
        if (period == currentState().period) {
            return
        }

        updateState(currentState().copy(period = period))
        loadDashboard()
    }

    fun consumeEvent() {
        _event.value = null
    }

    private fun loadDashboard() {
        val version = ++requestVersion

        val selectedPeriod = currentState().period

        updateState(currentState().copy(
            isLoading = true,
            notice = null,
            error = null,
            canRetry = false
            )
        )

        viewModelScope.launch {
            when (val result = dashboardRepository.load(selectedPeriod)) {
                is DashboardResult.Success -> {
                    if (version != requestVersion) {
                        return@launch
                    }

                    val charts = result.dashboard.charts

                    updateState(currentState().copy(
                            isLoading = false,

                            consumptionChartHtml = charts.firstOrNull {it.id == DashboardRepository.CHART_RESOURCE_EFFICIENCY}?.html,
                            waterChartHtml = charts.firstOrNull {it.id == DashboardRepository.CHART_MONTHLY_WATER_CONSUMPTION}?.html,
                            energyChartHtml = charts.firstOrNull {it.id == DashboardRepository.CHART_MONTHLY_ENERGY_CONSUMPTION}?.html,
                            notice = result.notice,
                            error = null,
                            canRetry = false
                        )
                    )
                }

                is DashboardResult.Error -> {
                    if (version != requestVersion) {
                        return@launch
                    }

                    updateState(currentState().copy(
                        isLoading = false,
                        error = result.message,
                        canRetry = result.canRetry
                    ))
                }

                DashboardResult.AuthenticationRequired -> {
                    if (version != requestVersion) {
                        return@launch
                    }

                    updateState(currentState().copy(
                        isLoading = false,
                        error = null,
                        canRetry = false
                    ))

                    _event.value = DashboardEvent.AuthenticationRequired
                }
            }
        }
    }

    private fun currentState(): DashboardUiState {
        return _state.value
            ?: DashboardUiState()
    }

    private fun updateState(state: DashboardUiState) {
        _state.value = state
    }
}