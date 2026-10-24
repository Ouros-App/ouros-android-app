package com.ouros.app.data.dashboard

data class DashboardUiState(
    val isLoading: Boolean = false,

    val period: DashboardPeriod =
        DashboardPeriod.MONTH,

    val kpiConsumption: String = "6.07",
    val kpiWaterLitersPerBird: String = "5.2",
    val kpiEnergyKwhPerBird: String = "8.1",

    val consumptionChartHtml: String? = null,
    val waterChartHtml: String? = null,
    val energyChartHtml: String? = null,

    val notice: String? = null,
    val error: String? = null,
    val canRetry: Boolean = false
)
