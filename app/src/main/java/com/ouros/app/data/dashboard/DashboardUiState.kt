package com.ouros.app.data.dashboard

data class DashboardUiState(
    val isLoading: Boolean = false,

    val period: DashboardPeriod =
        DashboardPeriod.MONTH,

    val consumptionChartHtml: String? = null,
    val waterChartHtml: String? = null,
    val energyChartHtml: String? = null,

    val notice: String? = null,
    val error: String? = null,
    val canRetry: Boolean = false
)