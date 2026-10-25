package com.ouros.app.data.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardUiStateTest {

    @Test
    fun defaultValuesAreCorrect() {
        val state = DashboardUiState()

        assertFalse(state.isLoading)
        assertEquals(DashboardPeriod.YEAR, state.period)
        assertEquals("6.07", state.kpiConsumption)
        assertEquals("5.2", state.kpiWaterLitersPerBird)
        assertEquals("8.1", state.kpiEnergyKwhPerBird)
        assertNull(state.consumptionChartHtml)
        assertNull(state.waterChartHtml)
        assertNull(state.energyChartHtml)
        assertNull(state.notice)
        assertNull(state.error)
        assertFalse(state.canRetry)
    }

    @Test
    fun copyUpdatesPropertiesCorrectly() {
        val initial = DashboardUiState()
        val updated = initial.copy(
            isLoading = true,
            period = DashboardPeriod.QUARTER,
            kpiConsumption = "10.50",
            kpiWaterLitersPerBird = "4.0",
            kpiEnergyKwhPerBird = "7.5",
            consumptionChartHtml = "<html>1</html>",
            waterChartHtml = "<html>2</html>",
            energyChartHtml = "<html>3</html>",
            notice = "Aviso teste",
            error = "Erro teste",
            canRetry = true
        )

        assertTrue(updated.isLoading)
        assertEquals(DashboardPeriod.QUARTER, updated.period)
        assertEquals("10.50", updated.kpiConsumption)
        assertEquals("4.0", updated.kpiWaterLitersPerBird)
        assertEquals("7.5", updated.kpiEnergyKwhPerBird)
        assertEquals("<html>1</html>", updated.consumptionChartHtml)
        assertEquals("<html>2</html>", updated.waterChartHtml)
        assertEquals("<html>3</html>", updated.energyChartHtml)
        assertEquals("Aviso teste", updated.notice)
        assertEquals("Erro teste", updated.error)
        assertTrue(updated.canRetry)
    }

    @Test
    fun equalsAndHashCodeAndToStringWork() {
        val state1 = DashboardUiState(kpiConsumption = "5.00")
        val state2 = DashboardUiState(kpiConsumption = "5.00")
        val state3 = DashboardUiState(kpiConsumption = "6.00")

        assertEquals(state1, state2)
        assertEquals(state1.hashCode(), state2.hashCode())
        assertTrue(state1 != state3)
        assertTrue(state1.toString().contains("kpiConsumption=5.00"))
    }
}
