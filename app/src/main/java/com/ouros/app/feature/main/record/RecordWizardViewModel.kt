package com.ouros.app.feature.main.record

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ouros.app.data.record.RecordDraft
import com.ouros.app.data.record.RecordRepository
import com.ouros.app.data.record.RecordResult
import kotlinx.coroutines.launch

data class RecordWizardUiState(val submitting: Boolean = false)
sealed interface RecordWizardEvent { data object Completed : RecordWizardEvent; data object AuthenticationRequired : RecordWizardEvent; data class Error(val message: String) : RecordWizardEvent }

class RecordWizardViewModel(private val repository: RecordRepository, private val draftStore: com.ouros.app.data.record.RecordDraftStore, private val farmId: Long?) : ViewModel() {
    private val _state = MutableLiveData(RecordWizardUiState())
    val state: LiveData<RecordWizardUiState> = _state
    private val _draft = MutableLiveData(draftStore.load() ?: RecordDraft().also(draftStore::save))
    val draft: LiveData<RecordDraft> = _draft
    private val _event = MutableLiveData<RecordWizardEvent?>()
    val event: LiveData<RecordWizardEvent?> = _event

    fun currentDraft(): RecordDraft = _draft.value ?: RecordDraft()

    fun update(transform: (RecordDraft) -> RecordDraft) {
        val updated = transform(currentDraft()).copy(updatedAt = System.currentTimeMillis())
        draftStore.save(updated)
        _draft.value = updated
    }

    fun setStep(step: Int) = update { it.copy(step = step) }

    fun submit() {
        if (_state.value?.submitting == true) return
        val id = farmId ?: run { _event.value = RecordWizardEvent.Error("Não foi possível identificar a granja."); return }
        _state.value = RecordWizardUiState(submitting = true)
        viewModelScope.launch {
            when (val result = repository.submit(id, currentDraft())) {
                is RecordResult.Success -> _event.value = RecordWizardEvent.Completed
                is RecordResult.Error -> _event.value = RecordWizardEvent.Error(result.message)
                RecordResult.AuthenticationRequired -> _event.value = RecordWizardEvent.AuthenticationRequired
            }
            _state.value = RecordWizardUiState()
        }
    }

    fun consumeEvent() { _event.value = null }
}

class RecordWizardViewModelFactory(private val repository: RecordRepository, private val draftStore: com.ouros.app.data.record.RecordDraftStore, private val farmId: Long?) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = RecordWizardViewModel(repository, draftStore, farmId) as T
}