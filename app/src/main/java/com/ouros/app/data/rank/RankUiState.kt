package com.ouros.app.data.rank

data class RankUiState(
    val isLoading: Boolean = false,
    val ranking: RankPodiumResponse? = null,
    val error: String? = null,
    val canRetry: Boolean = false
)
