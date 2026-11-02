package com.ouros.app.data.rank

/**
 * Modelo de apresentação de rank. Agrupa os valores de estado que a interface precisa para renderizar a tela.
 */
data class RankUiState(
    val isLoading: Boolean = false,
    val ranking: RankPodiumResponse? = null,
    val error: String? = null,
    val canRetry: Boolean = false
)
