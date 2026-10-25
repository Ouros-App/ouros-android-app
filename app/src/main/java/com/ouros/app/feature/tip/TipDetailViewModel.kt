package com.ouros.app.feature.tip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ouros.app.data.tip.TipDetailResponse
import com.ouros.app.data.tip.TipRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TipDetailUiState(
    val isLoading: Boolean = false,
    val tip: TipDetailResponse? = null,
    val errorMessage: String? = null,
    val isSubmittingReview: Boolean = false,
    val reviewSuccessMessage: String? = null,
    val userRating: Int? = null
)

class TipDetailViewModel(
    private val tipRepository: TipRepository = TipRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(TipDetailUiState())
    val uiState: StateFlow<TipDetailUiState> = _uiState.asStateFlow()

    fun loadTip(id: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = tipRepository.getTipById(id)
            result.fold(
                onSuccess = { tipResponse ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            tip = tipResponse,
                            errorMessage = null
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Erro ao carregar detalhes da dica"
                        )
                    }
                }
            )
        }
    }

    fun submitReview(tipId: Long, score: Int, comment: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingReview = true) }
            val result = tipRepository.createReview(tipId = tipId, score = score, comment = comment)
            result.fold(
                onSuccess = {
                    _uiState.update { state ->
                        val currentTip = state.tip
                        val newCount = (currentTip?.effectiveTotalReviews ?: 0) + 1
                        val updatedTip = currentTip?.copy(
                            totalReviews = newCount
                        )
                        state.copy(
                            isSubmittingReview = false,
                            tip = updatedTip,
                            userRating = score,
                            reviewSuccessMessage = "Avaliação registrada com sucesso! Obrigado pelo feedback."
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isSubmittingReview = false,
                            reviewSuccessMessage = null,
                            errorMessage = error.message ?: "Falha ao enviar avaliação"
                        )
                    }
                }
            )
        }
    }

    fun clearReviewMessage() {
        _uiState.update { it.copy(reviewSuccessMessage = null) }
    }

    class Factory(
        private val tipRepository: TipRepository = TipRepository()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TipDetailViewModel(tipRepository) as T
        }
    }
}
