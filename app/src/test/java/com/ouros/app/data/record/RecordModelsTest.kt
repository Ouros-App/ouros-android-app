package com.ouros.app.data.record

import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordModelsTest {

    @Test
    fun dataClasses_propertiesHoldCorrectValues() {
        val waterResponse = WaterRegistryResponse(
            id = 1L,
            registrationDate = "2026-10-25",
            startHydrometer = BigDecimal("10.5"),
            endHydrometer = BigDecimal("20.5"),
            farmId = 100L
        )
        assertEquals(1L, waterResponse.id)
        assertEquals("2026-10-25", waterResponse.registrationDate)
        assertEquals(BigDecimal("10.5"), waterResponse.startHydrometer)
        assertEquals(BigDecimal("20.5"), waterResponse.endHydrometer)
        assertEquals(100L, waterResponse.farmId)

        val waterRequest = WaterRegistryRequest(
            registrationDate = "2026-10-25",
            startHydrometer = BigDecimal("10.5"),
            endHydrometer = BigDecimal("20.5"),
            farmId = 100L
        )
        assertEquals(100L, waterRequest.farmId)

        val waterUpdate = WaterRegistryUpdate(
            registrationDate = "2026-10-25",
            startHydrometer = BigDecimal("10.5"),
            endHydrometer = BigDecimal("20.5")
        )
        assertEquals("2026-10-25", waterUpdate.registrationDate)

        val energyResponse = EnergyRegistryResponse(
            id = 2L,
            registrationDate = "2026-10-25",
            energyConsumption = BigDecimal("150.0"),
            farmId = 100L
        )
        assertEquals(2L, energyResponse.id)
        assertEquals(BigDecimal("150.0"), energyResponse.energyConsumption)

        val energyRequest = EnergyRegistryRequest(
            registrationDate = "2026-10-25",
            energyConsumption = BigDecimal("150.0"),
            farmId = 100L
        )
        assertEquals(100L, energyRequest.farmId)

        val energyUpdate = EnergyRegistryUpdate(
            registrationDate = "2026-10-25",
            energyConsumption = BigDecimal("150.0")
        )
        assertEquals(BigDecimal("150.0"), energyUpdate.energyConsumption)

        val chickenResponse = ChickenLeftResponse(
            id = 3L,
            chickensCount = 5,
            exitDate = "2026-10-25",
            farmId = 100L
        )
        assertEquals(3L, chickenResponse.id)
        assertEquals(5, chickenResponse.chickensCount)

        val chickenRequest = ChickenLeftRequest(
            chickensCount = 5,
            exitDate = "2026-10-25",
            farmId = 100L
        )
        assertEquals(5, chickenRequest.chickensCount)

        val chickenUpdate = ChickenLeftUpdate(
            chickensCount = 5,
            exitDate = "2026-10-25"
        )
        assertEquals(5, chickenUpdate.chickensCount)

        val lotUpdate = LotUpdateRequest(
            receivedChickens = 1000,
            cost = 2500.0
        )
        assertEquals(1000, lotUpdate.receivedChickens)
        assertEquals(2500.0, lotUpdate.cost, 0.001)

        val lotResponse = LotResponse(
            id = 4L,
            receivedChickens = 1000,
            deliveredChickens = 980,
            deliveryDate = "2026-10-20",
            losts = 20,
            cost = 2500.0,
            farmId = 100L
        )
        assertEquals(4L, lotResponse.id)
        assertEquals(1000, lotResponse.receivedChickens)
        assertEquals(980, lotResponse.deliveredChickens)
        assertEquals("2026-10-20", lotResponse.deliveryDate)
        assertEquals(20, lotResponse.losts)
        assertEquals(2500.0, lotResponse.cost ?: 0.0, 0.001)
        assertEquals(100L, lotResponse.farmId)

        val farmUpdate = FarmUpdateRequest(chickensNow = 5000)
        assertEquals(5000, farmUpdate.chickensNow)

        val farmResponse = FarmResponse(id = 100L, chickensNow = 5000)
        assertEquals(100L, farmResponse.id)
        assertEquals(5000, farmResponse.chickensNow)
    }

    @Test
    fun recordMetric_andRecordDraftMode_enumsCovered() {
        assertEquals(RecordMetric.WATER, RecordMetric.valueOf("WATER"))
        assertEquals(RecordMetric.ENERGY, RecordMetric.valueOf("ENERGY"))
        assertEquals(2, RecordMetric.values().size)

        assertEquals(RecordDraftMode.NEW, RecordDraftMode.valueOf("NEW"))
        assertEquals(RecordDraftMode.EDIT, RecordDraftMode.valueOf("EDIT"))
        assertEquals(2, RecordDraftMode.values().size)
    }

    @Test
    fun recordHistoryItem_editable_validatesCorrectDateWindow() {
        val today = LocalDate.now()
        val itemToday = RecordHistoryItem(1L, RecordMetric.WATER, today, BigDecimal("100"))
        assertTrue(itemToday.editable)

        val item7DaysAgo = RecordHistoryItem(2L, RecordMetric.WATER, today.minusDays(7), BigDecimal("100"))
        assertTrue(item7DaysAgo.editable)

        val item8DaysAgo = RecordHistoryItem(3L, RecordMetric.WATER, today.minusDays(8), BigDecimal("100"))
        assertFalse(item8DaysAgo.editable)

        val itemTomorrow = RecordHistoryItem(4L, RecordMetric.WATER, today.plusDays(1), BigDecimal("100"))
        assertFalse(itemTomorrow.editable)
    }

    @Test
    fun recordHistoryItem_formattedValue_formatsWaterAndEnergy() {
        val waterItem = RecordHistoryItem(1L, RecordMetric.WATER, LocalDate.now(), BigDecimal("1234.5"))
        assertEquals(String.format(Locale("pt", "BR"), "%,.0f L", BigDecimal("1234.5")), waterItem.formattedValue())

        val energyItem = RecordHistoryItem(2L, RecordMetric.ENERGY, LocalDate.now(), BigDecimal("5678.9"))
        assertEquals(String.format(Locale("pt", "BR"), "%,.0f kWh", BigDecimal("5678.9")), energyItem.formattedValue())
    }

    @Test
    fun recordHistoryItem_monthYear_formatsCorrectly() {
        val date = LocalDate.of(2026, 10, 25)
        val item = RecordHistoryItem(1L, RecordMetric.WATER, date, BigDecimal("100"))
        val expectedMonth = date.month.getDisplayName(TextStyle.FULL, Locale("pt", "BR")).replaceFirstChar {
            it.titlecase(Locale("pt", "BR"))
        }
        assertEquals("$expectedMonth 2026", item.monthYear())
    }

    @Test
    fun recordHubData_waterAndEnergyItems_sortedAndCalculatedProperly() {
        val water1 = WaterRegistryResponse(1L, "2026-10-20", BigDecimal("10.0"), BigDecimal("12.5"), 10L)
        val water2 = WaterRegistryResponse(2L, "2026-10-25", BigDecimal("12.5"), BigDecimal("15.0"), 10L)
        val waterNegative = WaterRegistryResponse(3L, "2026-10-22", BigDecimal("20.0"), BigDecimal("15.0"), 10L)

        val energy1 = EnergyRegistryResponse(1L, "2026-10-21", BigDecimal("50.0"), 10L)
        val energy2 = EnergyRegistryResponse(2L, "2026-10-24", BigDecimal("75.0"), 10L)

        val hub = RecordHubData(
            water = listOf(water1, water2, waterNegative),
            energy = listOf(energy1, energy2),
            chickenLeft = emptyList(),
            lots = emptyList(),
            farm = FarmResponse(10L, 1000)
        )

        val waterItems = hub.waterItems()
        assertEquals(3, waterItems.size)
        // Descending date order: 2026-10-25, 2026-10-22, 2026-10-20
        assertEquals(2L, waterItems[0].id)
        assertEquals(BigDecimal("2500.0"), waterItems[0].value)
        assertEquals(3L, waterItems[1].id)
        assertEquals(BigDecimal.ZERO, waterItems[1].value)
        assertEquals(1L, waterItems[2].id)
        assertEquals(BigDecimal("2500.0"), waterItems[2].value)

        val energyItems = hub.energyItems()
        assertEquals(2, energyItems.size)
        // Descending date order: 2026-10-24, 2026-10-21
        assertEquals(2L, energyItems[0].id)
        assertEquals(BigDecimal("75.0"), energyItems[0].value)
        assertEquals(1L, energyItems[1].id)
        assertEquals(BigDecimal("50.0"), energyItems[1].value)
    }

    @Test
    fun recordDraft_hasContent_checksAllFields() {
        val emptyDraft = RecordDraft()
        assertFalse(emptyDraft.hasContent())

        assertTrue(RecordDraft(lotId = "1").hasContent())
        assertTrue(RecordDraft(lotCost = "100").hasContent())
        assertTrue(RecordDraft(waterDate = "2026-10-25").hasContent())
        assertTrue(RecordDraft(hydrometerStart = "10").hasContent())
        assertTrue(RecordDraft(hydrometerEnd = "20").hasContent())
        assertTrue(RecordDraft(energyDate = "2026-10-25").hasContent())
        assertTrue(RecordDraft(energyKwh = "100").hasContent())
        assertTrue(RecordDraft(chickensEntered = "500").hasContent())
        assertTrue(RecordDraft(chickensLeft = "10").hasContent())
        assertTrue(RecordDraft(chickensCurrent = "490").hasContent())
    }

    @Test
    fun recordDraft_displayDate_formatsDatesOrFallback() {
        val withWaterDate = RecordDraft(waterDate = "2026-10-25")
        assertEquals("25/10", withWaterDate.displayDate())

        val withEnergyDateOnly = RecordDraft(waterDate = "", energyDate = "2026-05-04")
        assertEquals("04/05", withEnergyDateOnly.displayDate())

        val invalidDate = RecordDraft(waterDate = "invalid-date")
        assertEquals("em andamento", invalidDate.displayDate())

        val blankDates = RecordDraft(waterDate = "", energyDate = "")
        assertEquals("em andamento", blankDates.displayDate())
    }
}
