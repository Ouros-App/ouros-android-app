package com.ouros.app.data.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardDtosTest {

    @Test
    fun dashboardPeriodHasExpectedLabelsAndDays() {
        assertEquals("Mês", DashboardPeriod.MONTH.label)
        assertEquals(30, DashboardPeriod.MONTH.days)

        assertEquals("Semana", DashboardPeriod.WEEK.label)
        assertEquals(7, DashboardPeriod.WEEK.days)

        assertEquals("Ano", DashboardPeriod.YEAR.label)
        assertEquals(365, DashboardPeriod.YEAR.days)
        assertEquals(365, DashboardPeriod.ANNUAL_COMPARISON_DAYS)

        assertEquals(3, DashboardPeriod.values().size)
        assertEquals(DashboardPeriod.MONTH, DashboardPeriod.valueOf("MONTH"))
    }

    @Test
    fun dashboardStatusResponseProperties() {
        val status = DashboardStatusResponse(
            status = "fresh",
            lastUpdatedAt = "2026-10-24T00:00:00Z",
            ageSeconds = 120L,
            staleAfterSeconds = 1800L
        )

        assertEquals("fresh", status.status)
        assertEquals("2026-10-24T00:00:00Z", status.lastUpdatedAt)
        assertEquals(120L, status.ageSeconds)
        assertEquals(1800L, status.staleAfterSeconds)
        assertTrue(status.toString().contains("fresh"))
    }

    @Test
    fun dashboardRequestAndChartRequestProperties() {
        val chart = DashboardChartRequest(chartId = "eff", renderAs = "line")
        val request = DashboardRequest(
            title = "Telemetry",
            periodDays = 30,
            charts = listOf(chart)
        )

        assertEquals("eff", chart.chartId)
        assertEquals("line", chart.renderAs)
        assertEquals("Telemetry", request.title)
        assertEquals(30, request.periodDays)
        assertEquals(1, request.charts.size)
        assertEquals(chart, request.charts.first())
    }

    @Test
    fun dashboardResponseAndChartResponseProperties() {
        val chart = DashboardChartResponse(
            id = "water",
            title = "Water Chart",
            renderAs = "bar",
            html = "<div>water</div>"
        )
        val response = DashboardResponse(
            title = "Farm Dashboard",
            charts = listOf(chart)
        )

        assertEquals("Farm Dashboard", response.title)
        assertEquals(1, response.charts.size)
        assertEquals("water", chart.id)
        assertEquals("Water Chart", chart.title)
        assertEquals("bar", chart.renderAs)
        assertEquals("<div>water</div>", chart.html)
        assertNotNull(response.toString())
    }
}
