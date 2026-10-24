package com.ouros.app.data.dashboard

import com.google.gson.annotations.SerializedName

data class DashboardStatusResponse(
    val status: String,

    @SerializedName("last_updated_at")
    val lastUpdatedAt: String?,

    @SerializedName("age_seconds")
    val ageSeconds: Long?,

    @SerializedName("stale_after_seconds")
    val staleAfterSeconds: Long?
)

data class DashboardRequest(
    val title: String,

    @SerializedName("period_days")
    val periodDays: Int,

    val charts: List<DashboardChartRequest>
)

data class DashboardChartRequest(
    @SerializedName("chart_id")
    val chartId: String,

    @SerializedName("render_as")
    val renderAs: String
)

data class DashboardResponse(
    val title: String?,
    val charts: List<DashboardChartResponse>
)

data class DashboardChartResponse(
    val id: String,
    val title: String?,

    @SerializedName("render_as")
    val renderAs: String?,

    val html: String
)

enum class DashboardPeriod(
    val label: String,
    val days: Int
) {
    YEAR(
        label = "Ano",
        days = 365
    ),

    MONTH(
        label = "Mês",
        days = 30
    ),

    WEEK(
        label = "Semana",
        days = 7
    );

    companion object {
        const val ANNUAL_COMPARISON_DAYS = 365
    }
}
