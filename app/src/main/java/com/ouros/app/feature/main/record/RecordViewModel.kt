package com.ouros.app.feature.main.record

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ouros.app.data.record.RecordDraftStore
import com.ouros.app.data.record.RecordHistoryItem
import com.ouros.app.data.record.RecordHubData
import com.ouros.app.data.record.RecordRepository
import com.ouros.app.data.record.RecordResult
import kotlinx.coroutines.launch

data class RecordUiState(val loading: Boolean = false, val water: List<RecordHistoryItem> = emptyList(), val energy: List<RecordHistoryItem> = emptyList(), val error: String? = null)
sealed interface RecordEvent { data object OpenWizard : RecordEvent; data object AuthenticationRequired : RecordEvent }

class RecordViewModel(private val repository: RecordRepository, private val draftStore: RecordDraftStore, private val farmId: Long?) : ViewModel() {
    private val _state = MutableLiveData(RecordUiState())
    val state: LiveData<RecordUiState> = _state
    private val _event = MutableLiveData<RecordEvent?>()
    val event: LiveData<RecordEvent?> = _event
    private var hub: RecordHubData? = null

    fun load() {
        val id = farmId ?: run { _state.value = RecordUiState(error = "Não foi possível identificar a granja."); return }
        _state.value = (_state.value ?: RecordUiState()).copy(loading = true, error = null)
        viewModelScope.launch {
            when (val result = repository.load(id)) {
                is RecordResult.Success -> {
                    hub = result.data
                    _state.value = RecordUiState(water = result.data.waterItems(), energy = result.data.energyItems())
                }
                is RecordResult.Error -> _state.value = RecordUiState(error = result.message)
                RecordResult.AuthenticationRequired -> { _state.value = RecordUiState(); _event.value = RecordEvent.AuthenticationRequired }
            }
        }
    }

    fun newOrResume() {
        if (draftStore.load()?.hasContent() != true) draftStore.save(com.ouros.app.data.record.RecordDraft())
        _event.value = RecordEvent.OpenWizard
    }

    fun edit(item: RecordHistoryItem) {
        if (!item.editable) return
        val data = hub ?: return
        repository.prepareEdit(item, data)
        _event.value = RecordEvent.OpenWizard
    }

    fun consumeEvent() { _event.value = null }
}

class RecordViewModelFactory(private val repository: RecordRepository, private val draftStore: RecordDraftStore, private val farmId: Long?) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = RecordViewModel(repository, draftStore, farmId) as T
}