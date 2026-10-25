package com.ouros.app.feature.secondary.simulator

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ouros.app.data.simulator.SimulatorRepository
import com.ouros.app.data.simulator.SimulatorResult
import com.ouros.app.data.simulator.SimulatorUiState
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.launch

sealed interface SimulatorEvent {
    data object AuthenticationRequired : SimulatorEvent
    data class Message(val text: String) : SimulatorEvent
}

class SimulatorViewModel(
    private val repository: SimulatorRepository,
    private val farmId: Long?
) : ViewModel() {
    private val _state = MutableLiveData(SimulatorUiState())
    val state: LiveData<SimulatorUiState> = _state
    private val _event = MutableLiveData<SimulatorEvent?>()
    val event: LiveData<SimulatorEvent?> = _event
    private var initialLoadPerformed = false

    fun loadInitialData() {
        if (initialLoadPerformed || currentState().initialLoading) return
        initialLoadPerformed = true
        val id = farmId
        if (id == null) {
            updateState(currentState().copy(errorMessage = "Não foi possível identificar a granja vinculada ao seu perfil.", canRetry = false))
            return
        }

        updateState(currentState().copy(initialLoading = true, errorMessage = null, canRetry = false))
        viewModelScope.launch {
            when (val result = repository.loadInitialData(id)) {
                is SimulatorResult.Success -> {
                    val data = result.data
                    updateState(
                        currentState().copy(
                            initialLoading = false,
                            hasInitialData = true,
                            currentCgi = data.currentCgi,
                            currentChickens = data.chickens,
                            projectedCgiText = formatDecimal(data.currentCgi, 2),
                            targetEnergyText = "--",
                            savingsText = "R$ 0",
                            positionsGainedText = "0",
                            errorMessage = null,
                            canRetry = false
                        )
                    )
                }
                is SimulatorResult.Error -> updateState(
                    currentState().copy(
                        initialLoading = false,
                        hasInitialData = false,
                        errorMessage = result.message,
                        canRetry = result.canRetry
                    )
                )
                SimulatorResult.AuthenticationRequired -> {
                    updateState(currentState().copy(initialLoading = false))
                    _event.value = SimulatorEvent.AuthenticationRequired
                }
            }
        }
    }

    fun retry() {
        if (currentState().initialLoading) return
        initialLoadPerformed = false
        loadInitialData()
    }

    fun simulate(
        targetCgi: Double,
        waterPricePerM3: Double,
        energyPricePerKwh: Double,
        chickens: Int,
        targetKwhPerBird: Double
    ) {
        val id = farmId ?: return
        if (currentState().simulating) return
        updateState(currentState().copy(simulating = true))

        viewModelScope.launch {
            when (val result = repository.simulate(id, targetCgi)) {
                is SimulatorResult.Success -> {
                    val currentCgi = currentState().currentCgi
                    val currentKwhPerBird = CURRENT_KWH_PER_BIRD_REFERENCE
                    val targetWaterLitersPerBird = ((targetCgi - 0.3 * targetKwhPerBird) / 0.7).coerceAtLeast(0.0)
                    val currentWaterLitersPerBird = ((currentCgi - 0.3 * currentKwhPerBird) / 0.7).coerceAtLeast(0.0)
                    val energySavings = (currentKwhPerBird - targetKwhPerBird).coerceAtLeast(0.0) * chickens * energyPricePerKwh
                    val savedWaterLiters = (currentWaterLitersPerBird - targetWaterLitersPerBird).coerceAtLeast(0.0) * chickens
                    val waterSavings = savedWaterLiters / 1000.0 * waterPricePerM3
                    val totalSavings = energySavings + waterSavings
                    val simulation = result.data

                    updateState(
                        currentState().copy(
                            simulating = false,
                            projectedCgiText = formatDecimal(simulation.simulatedScore, 2),
                            targetEnergyText = formatDecimal(targetKwhPerBird, 1),
                            savingsText = formatCurrency(totalSavings),
                            positionsGainedText = simulation.positionsGained.toString(),
                            simulatedLeague = simulation.simulatedLeague.orEmpty()
                        )
                    )
                }
                is SimulatorResult.Error -> {
                    updateState(currentState().copy(simulating = false))
                    _event.value = SimulatorEvent.Message(result.message)
                }
                SimulatorResult.AuthenticationRequired -> {
                    updateState(currentState().copy(simulating = false))
                    _event.value = SimulatorEvent.AuthenticationRequired
                }
            }
        }
    }

    fun consumeEvent() {
        _event.value = null
    }

    private fun currentState() = _state.value ?: SimulatorUiState()

    private fun updateState(state: SimulatorUiState) {
        _state.value = state
    }

    private fun formatDecimal(value: Double, decimals: Int) =
        String.format(Locale.US, "%.${decimals}f", value)

    private fun formatCurrency(value: Double): String {
        return NumberFormat.getCurrencyInstance(Locale("pt", "BR")).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 0
        }.format(value)
    }

    private companion object {
        const val CURRENT_KWH_PER_BIRD_REFERENCE = 0.40
    }
}

class SimulatorViewModelFactory(
    private val repository: SimulatorRepository,
    private val farmId: Long?
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SimulatorViewModel::class.java)) {
            return SimulatorViewModel(repository, farmId) as T
        }
        throw IllegalArgumentException("ViewModel não suportado: ${modelClass.name}")
    }
}