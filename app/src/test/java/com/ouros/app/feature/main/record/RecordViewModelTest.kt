package com.ouros.app.feature.main.record

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import com.ouros.app.data.record.ChickenLeftRequest
import com.ouros.app.data.record.ChickenLeftResponse
import com.ouros.app.data.record.ChickenLeftUpdate
import com.ouros.app.data.record.EnergyRegistryRequest
import com.ouros.app.data.record.EnergyRegistryResponse
import com.ouros.app.data.record.EnergyRegistryUpdate
import com.ouros.app.data.record.FarmResponse
import com.ouros.app.data.record.FarmUpdateRequest
import com.ouros.app.data.record.LotResponse
import com.ouros.app.data.record.LotUpdateRequest
import com.ouros.app.data.record.RecordAPI
import com.ouros.app.data.record.RecordDraft
import com.ouros.app.data.record.RecordDraftStore
import com.ouros.app.data.record.RecordHistoryItem
import com.ouros.app.data.record.RecordMetric
import com.ouros.app.data.record.RecordRepository
import com.ouros.app.data.record.WaterRegistryRequest
import com.ouros.app.data.record.WaterRegistryResponse
import com.ouros.app.data.record.WaterRegistryUpdate
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecordViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var draftStore: RecordDraftStore
    private lateinit var fakeApi: FakeRecordApi
    private lateinit var repository: RecordRepository

    private class FakeRecordApi : RecordAPI {
        var waterResponse: Response<List<WaterRegistryResponse>> = Response.success(emptyList())
        var energyResponse: Response<List<EnergyRegistryResponse>> = Response.success(emptyList())
        var chickenResponse: Response<List<ChickenLeftResponse>> = Response.success(emptyList())
        var lotsResponse: Response<List<LotResponse>> = Response.success(emptyList())
        var farmResponse: Response<FarmResponse> = Response.success(FarmResponse(1L, 100))

        override suspend fun getWater(farmId: Long): Response<List<WaterRegistryResponse>> = waterResponse
        override suspend fun createWater(body: WaterRegistryRequest): Response<WaterRegistryResponse> = error("unused")
        override suspend fun updateWater(id: Long, body: WaterRegistryUpdate): Response<WaterRegistryResponse> = error("unused")

        override suspend fun getEnergy(farmId: Long): Response<List<EnergyRegistryResponse>> = energyResponse
        override suspend fun createEnergy(body: EnergyRegistryRequest): Response<EnergyRegistryResponse> = error("unused")
        override suspend fun updateEnergy(id: Long, body: EnergyRegistryUpdate): Response<EnergyRegistryResponse> = error("unused")

        override suspend fun getChickenLeft(farmId: Long): Response<List<ChickenLeftResponse>> = chickenResponse
        override suspend fun createChickenLeft(body: ChickenLeftRequest): Response<ChickenLeftResponse> = error("unused")
        override suspend fun updateChickenLeft(id: Long, body: ChickenLeftUpdate): Response<ChickenLeftResponse> = error("unused")
        override suspend fun deleteChickenLeft(id: Long): Response<Unit> = error("unused")

        override suspend fun getLots(farmId: Long): Response<List<LotResponse>> = lotsResponse
        override suspend fun updateLot(id: Long, body: LotUpdateRequest): Response<LotResponse> = error("unused")

        override suspend fun getFarm(id: Long): Response<FarmResponse> = farmResponse
        override suspend fun updateFarm(id: Long, body: FarmUpdateRequest): Response<FarmResponse> = error("unused")
    }

    @Before
    fun setUp() {
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread(): Boolean = true
        })
        Dispatchers.setMain(testDispatcher)

        val context = RuntimeEnvironment.getApplication()
        draftStore = RecordDraftStore(context)
        draftStore.clear()
        fakeApi = FakeRecordApi()
        repository = RecordRepository(fakeApi, draftStore)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        ArchTaskExecutor.getInstance().setDelegate(null)
    }

    @Test
    fun load_whenFarmIdIsNull_setsErrorMessage() {
        val viewModel = RecordViewModel(repository, draftStore, farmId = null)

        viewModel.load()

        assertEquals("Não foi possível identificar a granja.", viewModel.state.value?.error)
        assertFalse(viewModel.state.value?.loading == true)
    }

    @Test
    fun load_whenSuccess_updatesWaterAndEnergyLists() {
        fakeApi.waterResponse = Response.success(
            listOf(WaterRegistryResponse(1L, "2026-10-25", BigDecimal("10.0"), BigDecimal("15.0"), 1L))
        )
        fakeApi.energyResponse = Response.success(
            listOf(EnergyRegistryResponse(2L, "2026-10-25", BigDecimal("80.0"), 1L))
        )
        val viewModel = RecordViewModel(repository, draftStore, farmId = 1L)

        viewModel.load()

        val state = viewModel.state.value
        assertNotNull(state)
        assertEquals(1, state?.water?.size)
        assertEquals(1, state?.energy?.size)
        assertNull(state?.error)
        assertFalse(state?.loading == true)
    }

    @Test
    fun load_whenError_updatesErrorState() {
        fakeApi.waterResponse = Response.error(500, "".toResponseBody(null))
        val viewModel = RecordViewModel(repository, draftStore, farmId = 1L)

        viewModel.load()

        val state = viewModel.state.value
        assertEquals("O serviço está indisponível. Tente novamente.", state?.error)
        assertFalse(state?.loading == true)
    }

    @Test
    fun load_whenAuthenticationRequired_emitsAuthEvent() {
        fakeApi.waterResponse = Response.error(401, "".toResponseBody(null))
        val viewModel = RecordViewModel(repository, draftStore, farmId = 1L)

        viewModel.load()

        assertEquals(RecordEvent.AuthenticationRequired, viewModel.event.value)
    }

    @Test
    fun newOrResume_whenNoDraft_createsNewDraftAndEmitsEvent() {
        val viewModel = RecordViewModel(repository, draftStore, farmId = 1L)

        viewModel.newOrResume()

        assertEquals(RecordEvent.OpenWizard, viewModel.event.value)
        assertNotNull(draftStore.load())
    }

    @Test
    fun newOrResume_whenDraftHasContent_keepsExistingDraft() {
        draftStore.save(RecordDraft(lotId = "already_set"))
        val viewModel = RecordViewModel(repository, draftStore, farmId = 1L)

        viewModel.newOrResume()

        assertEquals(RecordEvent.OpenWizard, viewModel.event.value)
        assertEquals("already_set", draftStore.load()?.lotId)
    }

    @Test
    fun edit_whenNotEditable_doesNothing() {
        val viewModel = RecordViewModel(repository, draftStore, farmId = 1L)
        val nonEditableItem = RecordHistoryItem(
            id = 1L,
            metric = RecordMetric.WATER,
            date = LocalDate.now().minusDays(10), // > 7 days ago
            value = BigDecimal("10")
        )

        viewModel.edit(nonEditableItem)

        assertNull(viewModel.event.value)
    }

    @Test
    fun edit_whenHubIsNull_doesNothing() {
        val viewModel = RecordViewModel(repository, draftStore, farmId = 1L)
        val editableItem = RecordHistoryItem(
            id = 1L,
            metric = RecordMetric.WATER,
            date = LocalDate.now(),
            value = BigDecimal("10")
        )

        // hub is null because load() has not been called
        viewModel.edit(editableItem)

        assertNull(viewModel.event.value)
    }

    @Test
    fun edit_whenEditableAndHubLoaded_preparesEditAndEmitsEvent() {
        val todayStr = LocalDate.now().toString()
        fakeApi.waterResponse = Response.success(
            listOf(WaterRegistryResponse(1L, todayStr, BigDecimal("10.0"), BigDecimal("15.0"), 1L))
        )
        fakeApi.lotsResponse = Response.success(
            listOf(LotResponse(10L, 500, null, null, null, 1000.0, 1L))
        )
        val viewModel = RecordViewModel(repository, draftStore, farmId = 1L)
        viewModel.load()

        val editableItem = RecordHistoryItem(
            id = 1L,
            metric = RecordMetric.WATER,
            date = LocalDate.now(),
            value = BigDecimal("5000")
        )

        viewModel.edit(editableItem)

        assertEquals(RecordEvent.OpenWizard, viewModel.event.value)
        val loaded = draftStore.load()
        assertNotNull(loaded)
        assertEquals(1L, loaded?.waterRecordId)
        assertEquals("10", loaded?.lotId)
    }

    @Test
    fun consumeEvent_clearsCurrentEvent() {
        val viewModel = RecordViewModel(repository, draftStore, farmId = 1L)
        viewModel.newOrResume()
        assertNotNull(viewModel.event.value)

        viewModel.consumeEvent()
        assertNull(viewModel.event.value)
    }

    @Test
    fun factory_createsRecordViewModelInstance() {
        val factory = RecordViewModelFactory(repository, draftStore, 1L)
        val created = factory.create(RecordViewModel::class.java)
        assertNotNull(created)
    }
}
