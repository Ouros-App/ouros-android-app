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
import com.ouros.app.data.record.RecordRepository
import com.ouros.app.data.record.WaterRegistryRequest
import com.ouros.app.data.record.WaterRegistryResponse
import com.ouros.app.data.record.WaterRegistryUpdate
import java.math.BigDecimal
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
class RecordWizardViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var draftStore: RecordDraftStore
    private lateinit var fakeApi: FakeRecordApi
    private lateinit var repository: RecordRepository

    private class FakeRecordApi : RecordAPI {
        var updateLotResponse: Response<LotResponse> = Response.success(
            LotResponse(1L, 100, null, null, null, 100.0, 1L)
        )
        var createWaterResponse: Response<WaterRegistryResponse> = Response.success(
            WaterRegistryResponse(1L, "2026-10-25", BigDecimal.ONE, BigDecimal.TEN, 1L)
        )
        var updateWaterResponse: Response<WaterRegistryResponse> = Response.success(
            WaterRegistryResponse(1L, "2026-10-25", BigDecimal.ONE, BigDecimal.TEN, 1L)
        )
        var createEnergyResponse: Response<EnergyRegistryResponse> = Response.success(
            EnergyRegistryResponse(1L, "2026-10-25", BigDecimal.TEN, 1L)
        )
        var updateEnergyResponse: Response<EnergyRegistryResponse> = Response.success(
            EnergyRegistryResponse(1L, "2026-10-25", BigDecimal.TEN, 1L)
        )
        var createChickenResponse: Response<ChickenLeftResponse> = Response.success(
            ChickenLeftResponse(1L, 0, "2026-10-25", 1L)
        )
        var updateChickenResponse: Response<ChickenLeftResponse> = Response.success(
            ChickenLeftResponse(1L, 0, "2026-10-25", 1L)
        )
        var deleteChickenResponse: Response<Unit> = Response.success(Unit)
        var updateFarmResponse: Response<FarmResponse> = Response.success(FarmResponse(1L, 100))
        var onUpdateLot: (() -> Unit)? = null

        override suspend fun getWater(farmId: Long): Response<List<WaterRegistryResponse>> = error("unused")
        override suspend fun createWater(body: WaterRegistryRequest): Response<WaterRegistryResponse> = createWaterResponse
        override suspend fun updateWater(id: Long, body: WaterRegistryUpdate): Response<WaterRegistryResponse> = updateWaterResponse

        override suspend fun getEnergy(farmId: Long): Response<List<EnergyRegistryResponse>> = error("unused")
        override suspend fun createEnergy(body: EnergyRegistryRequest): Response<EnergyRegistryResponse> = createEnergyResponse
        override suspend fun updateEnergy(id: Long, body: EnergyRegistryUpdate): Response<EnergyRegistryResponse> = updateEnergyResponse

        override suspend fun getChickenLeft(farmId: Long): Response<List<ChickenLeftResponse>> = error("unused")
        override suspend fun createChickenLeft(body: ChickenLeftRequest): Response<ChickenLeftResponse> = createChickenResponse
        override suspend fun updateChickenLeft(id: Long, body: ChickenLeftUpdate): Response<ChickenLeftResponse> = updateChickenResponse
        override suspend fun deleteChickenLeft(id: Long): Response<Unit> = deleteChickenResponse

        override suspend fun getLots(farmId: Long): Response<List<LotResponse>> = error("unused")
        override suspend fun updateLot(id: Long, body: LotUpdateRequest): Response<LotResponse> {
            onUpdateLot?.invoke()
            return updateLotResponse
        }

        override suspend fun getFarm(id: Long): Response<FarmResponse> = error("unused")
        override suspend fun updateFarm(id: Long, body: FarmUpdateRequest): Response<FarmResponse> = updateFarmResponse
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
    fun init_withExistingDraft_loadsFromStore() {
        val existing = RecordDraft(lotId = "initial_lot", step = 2)
        draftStore.save(existing)

        val viewModel = RecordWizardViewModel(repository, draftStore, farmId = 1L)

        assertEquals("initial_lot", viewModel.currentDraft().lotId)
        assertEquals(2, viewModel.currentDraft().step)
    }

    @Test
    fun init_withoutExistingDraft_createsAndStoresDefault() {
        val viewModel = RecordWizardViewModel(repository, draftStore, farmId = 1L)

        assertNotNull(viewModel.currentDraft())
        assertNotNull(draftStore.load())
    }

    @Test
    fun update_andSetStep_modifiesDraftAndPersists() {
        val viewModel = RecordWizardViewModel(repository, draftStore, farmId = 1L)

        viewModel.update { it.copy(lotCost = "5000", energyKwh = "120") }
        assertEquals("5000", viewModel.currentDraft().lotCost)
        assertEquals("120", viewModel.currentDraft().energyKwh)
        assertEquals("5000", draftStore.load()?.lotCost)

        viewModel.setStep(3)
        assertEquals(3, viewModel.currentDraft().step)
        assertEquals(3, draftStore.load()?.step)
    }

    @Test
    fun submit_whenAlreadySubmitting_returnsEarly() {
        val validDraft = RecordDraft(
            lotId = "10",
            lotCost = "1000",
            hydrometerStart = "10",
            hydrometerEnd = "20",
            energyKwh = "50",
            chickensEntered = "500",
            chickensLeft = "0",
            chickensCurrent = "500"
        )
        draftStore.save(validDraft)
        val viewModel = RecordWizardViewModel(repository, draftStore, farmId = 1L)
        var nestedSubmitCalled = false
        fakeApi.onUpdateLot = {
            nestedSubmitCalled = true
            viewModel.submit()
        }

        viewModel.submit()
        assertTrue(nestedSubmitCalled)
    }

    @Test
    fun submit_whenFarmIdNull_emitsError() {
        val viewModel = RecordWizardViewModel(repository, draftStore, farmId = null)

        viewModel.submit()

        assertEquals(RecordWizardEvent.Error("Não foi possível identificar a granja."), viewModel.event.value)
        assertFalse(viewModel.state.value?.submitting == true)
    }

    @Test
    fun submit_whenSuccess_emitsCompleted() {
        val validDraft = RecordDraft(
            lotId = "10",
            lotCost = "1000",
            hydrometerStart = "10",
            hydrometerEnd = "20",
            energyKwh = "50",
            chickensEntered = "500",
            chickensLeft = "0",
            chickensCurrent = "500"
        )
        draftStore.save(validDraft)
        val viewModel = RecordWizardViewModel(repository, draftStore, farmId = 1L)

        viewModel.submit()

        assertEquals(RecordWizardEvent.Completed, viewModel.event.value)
        assertFalse(viewModel.state.value?.submitting == true)
    }

    @Test
    fun submit_whenError_emitsErrorEvent() {
        val validDraft = RecordDraft(
            lotId = "10",
            lotCost = "1000",
            hydrometerStart = "10",
            hydrometerEnd = "20",
            energyKwh = "50",
            chickensEntered = "500",
            chickensLeft = "0",
            chickensCurrent = "500"
        )
        draftStore.save(validDraft)
        fakeApi.updateLotResponse = Response.error(400, "".toResponseBody(null))
        val viewModel = RecordWizardViewModel(repository, draftStore, farmId = 1L)

        viewModel.submit()

        assertEquals(RecordWizardEvent.Error("Confira os dados informados."), viewModel.event.value)
        assertFalse(viewModel.state.value?.submitting == true)
    }

    @Test
    fun submit_whenAuthRequired_emitsAuthEvent() {
        val validDraft = RecordDraft(
            lotId = "10",
            lotCost = "1000",
            hydrometerStart = "10",
            hydrometerEnd = "20",
            energyKwh = "50",
            chickensEntered = "500",
            chickensLeft = "0",
            chickensCurrent = "500"
        )
        draftStore.save(validDraft)
        fakeApi.updateLotResponse = Response.error(401, "".toResponseBody(null))
        val viewModel = RecordWizardViewModel(repository, draftStore, farmId = 1L)

        viewModel.submit()

        assertEquals(RecordWizardEvent.AuthenticationRequired, viewModel.event.value)
        assertFalse(viewModel.state.value?.submitting == true)
    }

    @Test
    fun consumeEvent_clearsCurrentEvent() {
        val validDraft = RecordDraft(
            lotId = "10",
            lotCost = "1000",
            hydrometerStart = "10",
            hydrometerEnd = "20",
            energyKwh = "50",
            chickensEntered = "500",
            chickensLeft = "0",
            chickensCurrent = "500"
        )
        draftStore.save(validDraft)
        val viewModel = RecordWizardViewModel(repository, draftStore, farmId = 1L)
        viewModel.submit()
        assertNotNull(viewModel.event.value)

        viewModel.consumeEvent()
        assertNull(viewModel.event.value)
    }

    @Test
    fun factory_createsRecordWizardViewModelInstance() {
        val factory = RecordWizardViewModelFactory(repository, draftStore, 1L)
        val created = factory.create(RecordWizardViewModel::class.java)
        assertNotNull(created)
    }
}
