package com.ouros.app.data.record

import java.io.IOException
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecordRepositoryTest {

    private lateinit var fakeApi: FakeRecordApi
    private lateinit var draftStore: RecordDraftStore
    private lateinit var repository: RecordRepository

    private class FakeRecordApi : RecordAPI {
        var waterListResponse: Response<List<WaterRegistryResponse>> = Response.success(emptyList())
        var createWaterResponse: Response<WaterRegistryResponse> = Response.success(
            WaterRegistryResponse(101L, "2026-10-25", BigDecimal("10.0"), BigDecimal("20.0"), 1L)
        )
        var updateWaterResponse: Response<WaterRegistryResponse> = Response.success(
            WaterRegistryResponse(101L, "2026-10-25", BigDecimal("10.0"), BigDecimal("20.0"), 1L)
        )

        var energyListResponse: Response<List<EnergyRegistryResponse>> = Response.success(emptyList())
        var createEnergyResponse: Response<EnergyRegistryResponse> = Response.success(
            EnergyRegistryResponse(201L, "2026-10-25", BigDecimal("50.0"), 1L)
        )
        var updateEnergyResponse: Response<EnergyRegistryResponse> = Response.success(
            EnergyRegistryResponse(201L, "2026-10-25", BigDecimal("50.0"), 1L)
        )

        var chickenLeftListResponse: Response<List<ChickenLeftResponse>> = Response.success(emptyList())
        var createChickenLeftResponse: Response<ChickenLeftResponse> = Response.success(
            ChickenLeftResponse(301L, 15, "2026-10-25", 1L)
        )
        var updateChickenLeftResponse: Response<ChickenLeftResponse> = Response.success(
            ChickenLeftResponse(301L, 15, "2026-10-25", 1L)
        )
        var deleteChickenLeftResponse: Response<Unit> = Response.success(Unit)

        var lotsResponse: Response<List<LotResponse>> = Response.success(emptyList())
        var updateLotResponse: Response<LotResponse> = Response.success(
            LotResponse(1L, 5000, null, null, null, 1500.0, 1L)
        )

        var farmResponse: Response<FarmResponse> = Response.success(FarmResponse(1L, 4985))
        var updateFarmResponse: Response<FarmResponse> = Response.success(FarmResponse(1L, 4985))

        var shouldThrowIoException = false
        var shouldThrowGenericException = false
        var shouldThrowCancellationException = false

        var lastCreatedWaterRequest: WaterRegistryRequest? = null
        var lastUpdatedWaterId: Long? = null
        var lastUpdatedWaterBody: WaterRegistryUpdate? = null

        var lastCreatedEnergyRequest: EnergyRegistryRequest? = null
        var lastUpdatedEnergyId: Long? = null
        var lastUpdatedEnergyBody: EnergyRegistryUpdate? = null

        var lastCreatedChickenLeftRequest: ChickenLeftRequest? = null
        var lastUpdatedChickenLeftId: Long? = null
        var lastDeletedChickenLeftId: Long? = null

        var lastUpdatedLotId: Long? = null
        var lastUpdatedLotBody: LotUpdateRequest? = null

        var lastUpdatedFarmId: Long? = null
        var lastUpdatedFarmBody: FarmUpdateRequest? = null

        private fun checkExceptions() {
            if (shouldThrowCancellationException) throw CancellationException("Cancelled")
            if (shouldThrowIoException) throw IOException("Network error")
            if (shouldThrowGenericException) throw RuntimeException("Unexpected error")
        }

        override suspend fun getWater(farmId: Long): Response<List<WaterRegistryResponse>> {
            checkExceptions()
            return waterListResponse
        }

        override suspend fun createWater(body: WaterRegistryRequest): Response<WaterRegistryResponse> {
            checkExceptions()
            lastCreatedWaterRequest = body
            return createWaterResponse
        }

        override suspend fun updateWater(id: Long, body: WaterRegistryUpdate): Response<WaterRegistryResponse> {
            checkExceptions()
            lastUpdatedWaterId = id
            lastUpdatedWaterBody = body
            return updateWaterResponse
        }

        override suspend fun getEnergy(farmId: Long): Response<List<EnergyRegistryResponse>> {
            checkExceptions()
            return energyListResponse
        }

        override suspend fun createEnergy(body: EnergyRegistryRequest): Response<EnergyRegistryResponse> {
            checkExceptions()
            lastCreatedEnergyRequest = body
            return createEnergyResponse
        }

        override suspend fun updateEnergy(id: Long, body: EnergyRegistryUpdate): Response<EnergyRegistryResponse> {
            checkExceptions()
            lastUpdatedEnergyId = id
            lastUpdatedEnergyBody = body
            return updateEnergyResponse
        }

        override suspend fun getChickenLeft(farmId: Long): Response<List<ChickenLeftResponse>> {
            checkExceptions()
            return chickenLeftListResponse
        }

        override suspend fun createChickenLeft(body: ChickenLeftRequest): Response<ChickenLeftResponse> {
            checkExceptions()
            lastCreatedChickenLeftRequest = body
            return createChickenLeftResponse
        }

        override suspend fun updateChickenLeft(id: Long, body: ChickenLeftUpdate): Response<ChickenLeftResponse> {
            checkExceptions()
            lastUpdatedChickenLeftId = id
            return updateChickenLeftResponse
        }

        override suspend fun deleteChickenLeft(id: Long): Response<Unit> {
            checkExceptions()
            lastDeletedChickenLeftId = id
            return deleteChickenLeftResponse
        }

        override suspend fun getLots(farmId: Long): Response<List<LotResponse>> {
            checkExceptions()
            return lotsResponse
        }

        override suspend fun updateLot(id: Long, body: LotUpdateRequest): Response<LotResponse> {
            checkExceptions()
            lastUpdatedLotId = id
            lastUpdatedLotBody = body
            return updateLotResponse
        }

        override suspend fun getFarm(id: Long): Response<FarmResponse> {
            checkExceptions()
            return farmResponse
        }

        override suspend fun updateFarm(id: Long, body: FarmUpdateRequest): Response<FarmResponse> {
            checkExceptions()
            lastUpdatedFarmId = id
            lastUpdatedFarmBody = body
            return updateFarmResponse
        }
    }

    @Before
    fun setUp() {
        fakeApi = FakeRecordApi()
        val context = RuntimeEnvironment.getApplication()
        draftStore = RecordDraftStore(context)
        draftStore.clear()
        repository = RecordRepository(fakeApi, draftStore)
    }

    @Test
    fun load_success_returnsRecordHubData() = runBlocking {
        fakeApi.waterListResponse = Response.success(
            listOf(WaterRegistryResponse(1L, "2026-10-25", BigDecimal("10.0"), BigDecimal("20.0"), 1L))
        )
        fakeApi.energyListResponse = Response.success(
            listOf(EnergyRegistryResponse(2L, "2026-10-25", BigDecimal("50.0"), 1L))
        )
        fakeApi.chickenLeftListResponse = Response.success(
            listOf(ChickenLeftResponse(3L, 10, "2026-10-25", 1L))
        )
        fakeApi.lotsResponse = Response.success(
            listOf(LotResponse(4L, 5000, null, null, null, 1200.0, 1L))
        )
        fakeApi.farmResponse = Response.success(FarmResponse(1L, 4990))

        val result = repository.load(1L)
        assertTrue(result is RecordResult.Success)
        val data = (result as RecordResult.Success).data
        assertEquals(1, data.water.size)
        assertEquals(1, data.energy.size)
        assertEquals(1, data.chickenLeft.size)
        assertEquals(1, data.lots.size)
        assertEquals(4990, data.farm.chickensNow)
    }

    @Test
    fun load_errorCodes_mappedCorrectly() = runBlocking {
        // 401 AuthenticationRequired
        fakeApi.waterListResponse = Response.error(401, "".toResponseBody(null))
        val res401 = repository.load(1L)
        assertEquals(RecordResult.AuthenticationRequired, res401)

        // 400
        fakeApi.waterListResponse = Response.error(400, "".toResponseBody(null))
        val res400 = repository.load(1L)
        assertEquals(RecordResult.Error("Confira os dados informados.", false), res400)

        // 422
        fakeApi.waterListResponse = Response.error(422, "".toResponseBody(null))
        val res422 = repository.load(1L)
        assertEquals(RecordResult.Error("Confira os dados informados.", false), res422)

        // 403
        fakeApi.waterListResponse = Response.error(403, "".toResponseBody(null))
        val res403 = repository.load(1L)
        assertEquals(RecordResult.Error("Seu usuário não possui permissão para registrar dados.", false), res403)

        // 404
        fakeApi.waterListResponse = Response.error(404, "".toResponseBody(null))
        val res404 = repository.load(1L)
        assertEquals(RecordResult.Error("Fazenda, lote ou registro não encontrado.", false), res404)

        // 409
        fakeApi.waterListResponse = Response.error(409, "".toResponseBody(null))
        val res409 = repository.load(1L)
        assertEquals(RecordResult.Error("Os dados entram em conflito com um registro existente.", false), res409)

        // 429
        fakeApi.waterListResponse = Response.error(429, "".toResponseBody(null))
        val res429 = repository.load(1L)
        assertEquals(RecordResult.Error("Muitas tentativas. Aguarde e tente novamente."), res429)

        // 500
        fakeApi.waterListResponse = Response.error(500, "".toResponseBody(null))
        val res500 = repository.load(1L)
        assertEquals(RecordResult.Error("O serviço está indisponível. Tente novamente."), res500)
    }

    @Test
    fun load_exceptions_handledGracefully() = runBlocking {
        // IOException
        fakeApi.shouldThrowIoException = true
        val ioResult = repository.load(1L)
        assertEquals(RecordResult.Error("Sem conexão. Seus dados continuam salvos neste aparelho."), ioResult)

        // Generic Exception
        fakeApi.shouldThrowIoException = false
        fakeApi.shouldThrowGenericException = true
        val genericResult = repository.load(1L)
        assertEquals(RecordResult.Error("Ocorreu um erro inesperado. Seus dados continuam salvos."), genericResult)

        // CancellationException rethrown
        fakeApi.shouldThrowGenericException = false
        fakeApi.shouldThrowCancellationException = true
        try {
            repository.load(1L)
            fail("Expected CancellationException")
        } catch (_: CancellationException) {
            // Success
        }
    }

    @Test
    fun prepareEdit_forWaterItem_preparesAndSavesDraft() {
        val date = LocalDate.of(2026, 10, 25)
        val water = WaterRegistryResponse(10L, "2026-10-25", BigDecimal("12.5"), BigDecimal("18.5"), 1L)
        val energy = EnergyRegistryResponse(20L, "2026-10-25", BigDecimal("45.0"), 1L)
        val chicken = ChickenLeftResponse(30L, 8, "2026-10-25", 1L)
        val lotActive = LotResponse(101L, 2000, null, null, null, 3500.0, 1L)
        val lotDelivered = LotResponse(102L, 2000, 1950, "2026-10-20", null, 3500.0, 1L)
        val farm = FarmResponse(1L, 1992)

        val hub = RecordHubData(
            water = listOf(water),
            energy = listOf(energy),
            chickenLeft = listOf(chicken),
            lots = listOf(lotActive, lotDelivered),
            farm = farm
        )

        val item = RecordHistoryItem(
            id = 10L,
            metric = RecordMetric.WATER,
            date = date,
            value = BigDecimal("6000.0")
        )

        val draft = repository.prepareEdit(item, hub)
        assertEquals(RecordDraftMode.EDIT, draft.mode)
        assertEquals(1, draft.step)
        assertEquals("101", draft.lotId)
        assertEquals("3500", draft.lotCost)
        assertEquals("2026-10-25", draft.waterDate)
        assertEquals("12.5", draft.hydrometerStart)
        assertEquals("18.5", draft.hydrometerEnd)
        assertEquals("2026-10-25", draft.energyDate)
        assertEquals("45.0", draft.energyKwh)
        assertEquals("2000", draft.chickensEntered)
        assertEquals("8", draft.chickensLeft)
        assertEquals("1992", draft.chickensCurrent)
        assertEquals(10L, draft.waterRecordId)
        assertEquals(20L, draft.energyRecordId)
        assertEquals(30L, draft.chickenLeftRecordId)

        // Verify saved to store
        val stored = draftStore.load()
        assertNotNull(stored)
        assertEquals("101", stored?.lotId)
    }

    @Test
    fun prepareEdit_forEnergyItem_withNoActiveLot_usesMaxLotId() {
        val date = LocalDate.of(2026, 10, 25)
        val water = WaterRegistryResponse(10L, "2026-10-25", BigDecimal("10.0"), BigDecimal("15.0"), 1L)
        val energy = EnergyRegistryResponse(20L, "2026-10-25", BigDecimal("60.0"), 1L)
        val lot1 = LotResponse(50L, 1000, 990, "2026-10-10", null, 1000.0, 1L)
        val lot2 = LotResponse(60L, 1000, 980, "2026-10-20", null, 1200.0, 1L)

        val hub = RecordHubData(
            water = listOf(water),
            energy = listOf(energy),
            chickenLeft = emptyList(),
            lots = listOf(lot1, lot2),
            farm = FarmResponse(1L, 980)
        )

        val item = RecordHistoryItem(
            id = 20L,
            metric = RecordMetric.ENERGY,
            date = date,
            value = BigDecimal("60.0")
        )

        val draft = repository.prepareEdit(item, hub)
        assertEquals("60", draft.lotId)
        assertEquals("1200", draft.lotCost)
        assertEquals("0", draft.chickensLeft)
        assertNull(draft.chickenLeftRecordId)
    }

    @Test
    fun submit_createsNewRecords_andClearsDraft() = runBlocking {
        val draft = RecordDraft(
            mode = RecordDraftMode.NEW,
            step = 1,
            lotId = "77",
            lotCost = "R$ 1.500,50",
            waterDate = "2026-10-25",
            hydrometerStart = "100,5",
            hydrometerEnd = "120,5",
            energyDate = "2026-10-25",
            energyKwh = "85,2",
            chickensEntered = "3000",
            chickensLeft = "12",
            chickensCurrent = "2988",
            waterRecordId = null,
            energyRecordId = null,
            chickenLeftRecordId = null
        )
        draftStore.save(draft)

        val result = repository.submit(1L, draft)
        assertTrue(result is RecordResult.Success)

        // Verify lot updated
        assertEquals(77L, fakeApi.lastUpdatedLotId)
        assertEquals(3000, fakeApi.lastUpdatedLotBody?.receivedChickens)
        assertEquals(1500.50, fakeApi.lastUpdatedLotBody?.cost ?: 0.0, 0.001)

        // Verify water created
        assertNotNull(fakeApi.lastCreatedWaterRequest)
        assertEquals(BigDecimal("100.5"), fakeApi.lastCreatedWaterRequest?.startHydrometer)
        assertEquals(BigDecimal("120.5"), fakeApi.lastCreatedWaterRequest?.endHydrometer)

        // Verify energy created
        assertNotNull(fakeApi.lastCreatedEnergyRequest)
        assertEquals(BigDecimal("85.2"), fakeApi.lastCreatedEnergyRequest?.energyConsumption)

        // Verify chicken created
        assertNotNull(fakeApi.lastCreatedChickenLeftRequest)
        assertEquals(12, fakeApi.lastCreatedChickenLeftRequest?.chickensCount)

        // Verify farm updated
        assertEquals(1L, fakeApi.lastUpdatedFarmId)
        assertEquals(2988, fakeApi.lastUpdatedFarmBody?.chickensNow)

        // Verify draft cleared
        assertNull(draftStore.load())
    }

    @Test
    fun submit_updatesExistingRecords_andDeletesChickenWhenLeftIsZero() = runBlocking {
        val draft = RecordDraft(
            mode = RecordDraftMode.EDIT,
            step = 1,
            lotId = "77",
            lotCost = "2000.00",
            waterDate = "2026-10-25",
            hydrometerStart = "150.0",
            hydrometerEnd = "200.0",
            energyDate = "2026-10-25",
            energyKwh = "120.0",
            chickensEntered = "3000",
            chickensLeft = "0",
            chickensCurrent = "3000",
            waterRecordId = 101L,
            energyRecordId = 201L,
            chickenLeftRecordId = 301L
        )

        val result = repository.submit(1L, draft)
        assertTrue(result is RecordResult.Success)

        // Water updated
        assertEquals(101L, fakeApi.lastUpdatedWaterId)
        assertEquals(BigDecimal("150.0"), fakeApi.lastUpdatedWaterBody?.startHydrometer)

        // Energy updated
        assertEquals(201L, fakeApi.lastUpdatedEnergyId)
        assertEquals(BigDecimal("120.0"), fakeApi.lastUpdatedEnergyBody?.energyConsumption)

        // Chicken deleted since left is 0 and chickenLeftRecordId was present
        assertEquals(301L, fakeApi.lastDeletedChickenLeftId)
        assertNull(draftStore.load())
    }

    @Test
    fun submit_updatesExistingChickenRecord_whenLeftGreaterThanZero() = runBlocking {
        val draft = RecordDraft(
            mode = RecordDraftMode.EDIT,
            step = 1,
            lotId = "77",
            lotCost = "2000.00",
            waterDate = "2026-10-25",
            hydrometerStart = "150.0",
            hydrometerEnd = "200.0",
            energyDate = "2026-10-25",
            energyKwh = "120.0",
            chickensEntered = "3000",
            chickensLeft = "5",
            chickensCurrent = "2995",
            waterRecordId = 101L,
            energyRecordId = 201L,
            chickenLeftRecordId = 301L
        )

        val result = repository.submit(1L, draft)
        assertTrue(result is RecordResult.Success)
        assertEquals(301L, fakeApi.lastUpdatedChickenLeftId)
    }

    @Test
    fun submit_doesNothingForChicken_whenLeftIsZeroAndNoExistingRecord() = runBlocking {
        val draft = RecordDraft(
            mode = RecordDraftMode.NEW,
            step = 1,
            lotId = "77",
            lotCost = "2000.00",
            waterDate = "2026-10-25",
            hydrometerStart = "150.0",
            hydrometerEnd = "200.0",
            energyDate = "2026-10-25",
            energyKwh = "120.0",
            chickensEntered = "3000",
            chickensLeft = "0",
            chickensCurrent = "3000",
            waterRecordId = 101L,
            energyRecordId = 201L,
            chickenLeftRecordId = null
        )

        val result = repository.submit(1L, draft)
        assertTrue(result is RecordResult.Success)
        assertNull(fakeApi.lastCreatedChickenLeftRequest)
        assertNull(fakeApi.lastUpdatedChickenLeftId)
        assertNull(fakeApi.lastDeletedChickenLeftId)
    }

    @Test
    fun submit_errorScenario_returnsFailure() = runBlocking {
        fakeApi.updateLotResponse = Response.error(400, "".toResponseBody(null))
        val draft = RecordDraft(
            lotId = "77",
            lotCost = "100",
            hydrometerStart = "10",
            hydrometerEnd = "20",
            energyKwh = "30",
            chickensEntered = "100",
            chickensLeft = "0",
            chickensCurrent = "100"
        )

        val result = repository.submit(1L, draft)
        assertEquals(RecordResult.Error("Confira os dados informados.", false), result)
    }

    @Test
    fun load_whenBodyIsNull_returnsIndisponivelError() = runBlocking {
        fakeApi.waterListResponse = Response.success(null)
        val result = repository.load(1L)
        assertEquals(RecordResult.Error("O serviço está indisponível. Tente novamente."), result)
    }

    @Test
    fun prepareEdit_withEmptyHub_handlesNullFallbacksGracefully() {
        val date = LocalDate.of(2026, 10, 25)
        val hub = RecordHubData(
            water = emptyList(),
            energy = emptyList(),
            chickenLeft = emptyList(),
            lots = emptyList(),
            farm = FarmResponse(1L, null)
        )
        val item = RecordHistoryItem(
            id = 999L,
            metric = RecordMetric.WATER,
            date = date,
            value = BigDecimal.TEN
        )

        val draft = repository.prepareEdit(item, hub)
        assertEquals("", draft.lotId)
        assertEquals("", draft.lotCost)
        assertEquals("2026-10-25", draft.waterDate)
        assertEquals("", draft.hydrometerStart)
        assertEquals("", draft.hydrometerEnd)
        assertEquals("2026-10-25", draft.energyDate)
        assertEquals("", draft.energyKwh)
        assertEquals("", draft.chickensEntered)
        assertEquals("0", draft.chickensLeft)
        assertEquals("", draft.chickensCurrent)
        assertNull(draft.waterRecordId)
        assertNull(draft.energyRecordId)
        assertNull(draft.chickenLeftRecordId)
    }
}
