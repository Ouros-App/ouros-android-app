package com.ouros.app.data.rank

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RankDtosTest {

    @Test
    fun formatRemainingTime_formatsVariousDurationsCorrectly() {
        assertEquals("Em instantes", formatRemainingTime(null))
        assertEquals("Em instantes", formatRemainingTime(0L))
        assertEquals("Em instantes", formatRemainingTime(-10L))
        assertEquals("1 segundo", formatRemainingTime(1L))
        assertEquals("45 segundos", formatRemainingTime(45L))
        assertEquals("1 minuto", formatRemainingTime(60L))
        assertEquals("5 minutos", formatRemainingTime(300L))
        assertEquals("1 hora", formatRemainingTime(3600L))
        assertEquals("2 horas", formatRemainingTime(7200L))
        assertEquals("1 dia", formatRemainingTime(86400L))
        assertEquals("6 dias", formatRemainingTime(86400L * 6))
    }

    @Test
    fun rankTime_toDisplayString_returnsHighestUnitOrFallback() {
        assertEquals("6 dias", RankTime(days = "6 dias").toDisplayString())
        assertEquals("4 horas", RankTime(days = "0 dias", hours = "4 horas").toDisplayString())
        assertEquals("35 minutos", RankTime(days = null, hours = "0 hora", minutes = "35 minutos").toDisplayString())
        assertEquals("20 segundos", RankTime(days = "", hours = null, minutes = "0 minuto", seconds = "20 segundos").toDisplayString())
        assertEquals("Em instantes", RankTime(days = null, hours = null, minutes = null, seconds = "0 segundos").toDisplayString())
    }

    @Test
    fun rankPodiumEntry_displayNameAndIsCurrent() {
        val currentEntry = RankPodiumEntry(
            farmId = 1L,
            farmName = "Granja Esperança",
            region = "Sul",
            score = 95.0,
            rankPosition = 1,
            relation = "CURRENT"
        )
        assertTrue(currentEntry.isCurrent)
        assertEquals("Você", currentEntry.displayName)

        val otherEntry = RankPodiumEntry(
            farmId = 2L,
            farmName = "Fazenda Sol",
            region = "Norte",
            score = 90.0,
            rankPosition = 2,
            relation = "ABOVE"
        )
        assertFalse(otherEntry.isCurrent)
        assertEquals("Fazenda Sol", otherEntry.displayName)

        val fallbackNameEntry = RankPodiumEntry(
            farmId = 3L,
            farmName = "",
            region = null,
            score = 80.0,
            rankPosition = 3,
            relation = "BELOW"
        )
        assertEquals("Granja #3", fallbackNameEntry.displayName)
    }

    @Test
    fun rankPodiumResponse_currentEntryAndCurrentLeague() {
        val entry1 = RankPodiumEntry(10L, "Fazenda 1", null, 100.0, 1, "ABOVE")
        val current = RankPodiumEntry(20L, "Minha Fazenda", null, 90.0, 2, "CURRENT")
        val response = RankPodiumResponse(
            currentFarmId = 20L,
            totalRankedFarms = 10,
            rankingPodium = listOf(entry1, current)
        )

        val resolved = response.currentEntry
        assertNotNull(resolved)
        assertEquals(20L, resolved?.farmId)
        assertEquals(RankLeague.PRATA, response.currentLeague)

        val responseFallbackId = RankPodiumResponse(
            currentFarmId = 10L,
            totalRankedFarms = null,
            rankingPodium = listOf(entry1)
        )
        assertEquals(10L, responseFallbackId.currentEntry?.farmId)
        assertEquals(RankLeague.OURO, responseFallbackId.currentLeague)
    }
}
