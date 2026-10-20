package com.ouros.app.feature.main.goals

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import com.ouros.app.data.auth.AuthApi
import com.ouros.app.data.auth.AuthRepository
import com.ouros.app.data.auth.LoginStartRequest
import com.ouros.app.data.auth.LoginStartResponse
import com.ouros.app.data.auth.LoginVerifyRequest
import com.ouros.app.data.auth.PasswordResetConfirmRequest
import com.ouros.app.data.auth.PasswordResetConfirmResponse
import com.ouros.app.data.auth.PasswordResetStartRequest
import com.ouros.app.data.auth.PasswordResetStartResponse
import com.ouros.app.data.auth.PasswordResetVerifyRequest
import com.ouros.app.data.auth.PasswordResetVerifyResponse
import com.ouros.app.data.auth.RefreshRequest
import com.ouros.app.data.auth.RefreshTokenStorage
import com.ouros.app.data.auth.SessionManager
import com.ouros.app.data.auth.TokenResponse
import com.ouros.app.data.goals.CreateIndividualGoalRequest
import com.ouros.app.data.goals.GoalOrigin
import com.ouros.app.data.goals.GoalResponse
import com.ouros.app.data.goals.GoalsApi
import com.ouros.app.data.goals.GoalsRepository
import com.ouros.app.data.goals.UpdateGoalStatusRequest
import com.ouros.app.data.goals.UpdateIndividualGoalRequest
import com.ouros.app.data.session.FarmSessionStore
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
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class GoalsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private class FakeRefreshTokenStore : RefreshTokenStorage {
        var token: String? = "valid-refresh"
        override fun saveRefreshToken(token: String) { this.token = token }
        override fun getRefreshToken(): String? = token
        override fun clear() { token = null }
    }

    private class FakeAuthApi : AuthApi {
        override suspend fun refreshToken(request: RefreshRequest): Response<TokenResponse> =
            Response.success(
                TokenResponse(
                    accessToken = "access-token",
                    expiresIn = 3600L,
                    refreshExpiresIn = 7200L,
                    refreshToken = "refresh-token",
                    tokenType = "Bearer",
                    scope = null
                )
            )
        override suspend fun startLogin(request: LoginStartRequest): Response<LoginStartResponse> = error("Not used")
        override suspend fun verifyLogin(request: LoginVerifyRequest): Response<TokenResponse> = error("Not used")
        override suspend fun startPasswordReset(request: PasswordResetStartRequest): Response<PasswordResetStartResponse> = error("Not used")
        override suspend fun verifyPasswordReset(request: PasswordResetVerifyRequest): Response<PasswordResetVerifyResponse> = error("Not used")
        override suspend fun confirmPasswordReset(request: PasswordResetConfirmRequest): Response<PasswordResetConfirmResponse> = error("Not used")
    }

    private class FakeGoalsApi : GoalsApi {
        var personalGoals: MutableList<GoalResponse> = mutableListOf(
            GoalResponse(1L, "Meta 1", "D1", status = "PENDING")
        )
        var stateGoals: MutableList<GoalResponse> = mutableListOf(
            GoalResponse(2L, "Meta Estadual", "D2", status = "COMPLETED")
        )
        var shouldFail = false
        var failWith401 = false

        override suspend fun getIndividualGoals(authorization: String): Response<List<GoalResponse>> =
            if (failWith401) Response.error(401, "".toResponseBody(null))
            else if (shouldFail) Response.error(500, "".toResponseBody(null))
            else Response.success(personalGoals.toList())

        override suspend fun getStateGoals(authorization: String): Response<List<GoalResponse>> =
            if (failWith401) Response.error(401, "".toResponseBody(null))
            else if (shouldFail) Response.error(500, "".toResponseBody(null))
            else Response.success(stateGoals.toList())

        override suspend fun updateIndividualGoal(
            id: Long,
            authorization: String,
            request: UpdateIndividualGoalRequest
        ): Response<GoalResponse> {
            if (failWith401) return Response.error(401, "".toResponseBody(null))
            if (shouldFail) return Response.error(500, "".toResponseBody(null))
            val current = personalGoals.find { it.id == id } ?: GoalResponse(id, "Meta", null, status = "PENDING")
            val updated = current.copy(
                title = request.title ?: current.title,
                description = request.description ?: current.description,
                status = request.status ?: current.status
            )
            val index = personalGoals.indexOfFirst { it.id == id }
            if (index != -1) personalGoals[index] = updated
            return Response.success(updated)
        }

        override suspend fun updateStateGoal(
            id: Long,
            authorization: String,
            request: UpdateGoalStatusRequest
        ): Response<GoalResponse> {
            if (failWith401) return Response.error(401, "".toResponseBody(null))
            if (shouldFail) return Response.error(500, "".toResponseBody(null))
            val current = stateGoals.find { it.id == id } ?: GoalResponse(id, "Estadual", null, status = "PENDING")
            val updated = current.copy(status = request.status)
            val index = stateGoals.indexOfFirst { it.id == id }
            if (index != -1) stateGoals[index] = updated
            return Response.success(updated)
        }

        override suspend fun createIndividualGoal(
            authorization: String,
            request: CreateIndividualGoalRequest
        ): Response<GoalResponse> {
            if (failWith401) return Response.error(401, "".toResponseBody(null))
            if (shouldFail) return Response.error(500, "".toResponseBody(null))
            val created = GoalResponse(
                id = 99L,
                title = request.title,
                description = request.description,
                status = "PENDING"
            )
            return Response.success(created)
        }

        override suspend fun deleteIndividualGoal(
            id: Long,
            authorization: String
        ): Response<Unit> {
            if (failWith401) return Response.error(401, "".toResponseBody(null))
            if (shouldFail) return Response.error(500, "".toResponseBody(null))
            personalGoals.removeAll { it.id == id }
            return Response.success(Unit)
        }
    }

    private class FakeFarmSessionStore(var currentFarmId: Long? = 10L) : FarmSessionStore(null) {
        override fun farmId(): Long? = currentFarmId
    }

    private lateinit var api: FakeGoalsApi
    private lateinit var repository: GoalsRepository
    private lateinit var viewModel: GoalsViewModel

    @Before
    fun setUp() {
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread(): Boolean = true
        })
        Dispatchers.setMain(testDispatcher)

        api = FakeGoalsApi()
        val sessionManager = SessionManager().apply { setAccessToken("token", 3600L) }
        val authRepo = AuthRepository(FakeAuthApi(), FakeRefreshTokenStore(), sessionManager)
        val farmStore = FakeFarmSessionStore(10L)
        repository = GoalsRepository(api, authRepo, sessionManager, farmStore)
        viewModel = GoalsViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        ArchTaskExecutor.getInstance().setDelegate(null)
    }

    @Test
    fun loadInitialData_populatesState() {
        viewModel.loadInitialData()

        val state = viewModel.state.value
        assertNotNull(state)
        assertFalse(state?.isLoading == true)
        assertEquals(1, state?.personalGoals?.size)
        assertEquals(1, state?.stateGoals?.size)
        assertEquals(2, state?.totalGoals)
        assertEquals(50, state?.progress)
    }

    @Test
    fun loadInitialData_doesNotReloadTwice() {
        viewModel.loadInitialData()
        assertEquals(1, viewModel.state.value?.personalGoals?.size)

        api.personalGoals.add(GoalResponse(5L, "Outra", null, status = "PENDING"))
        viewModel.loadInitialData()

        assertEquals(1, viewModel.state.value?.personalGoals?.size)
    }

    @Test
    fun toggleGoal_personal_updatesLocallyAndPersists() {
        viewModel.loadInitialData()
        val goal = viewModel.state.value!!.personalGoals.first()

        viewModel.toggleGoal(GoalOrigin.PERSONAL, goal, completed = true)

        val updated = viewModel.state.value!!.personalGoals.first()
        assertTrue(updated.isCompleted)
    }

    @Test
    fun toggleGoal_state_updatesLocallyAndPersists() {
        viewModel.loadInitialData()
        val goal = viewModel.state.value!!.stateGoals.first()

        viewModel.toggleGoal(GoalOrigin.STATE, goal, completed = false)

        val updated = viewModel.state.value!!.stateGoals.first()
        assertFalse(updated.isCompleted)
    }

    @Test
    fun toggleGoal_onFailure_rollsBackAndEmitsError() {
        viewModel.loadInitialData()
        val goal = viewModel.state.value!!.personalGoals.first()
        api.shouldFail = true

        viewModel.toggleGoal(GoalOrigin.PERSONAL, goal, completed = true)

        val reverted = viewModel.state.value!!.personalGoals.first()
        assertFalse(reverted.isCompleted)
        assertTrue(viewModel.event.value is GoalsEvent.ShowMessage)
    }

    @Test
    fun saveGoal_withBlankTitle_emitsError() {
        viewModel.saveGoal(null, "   ", "Desc")

        assertTrue(viewModel.event.value is GoalsEvent.ShowMessage)
        val msg = (viewModel.event.value as GoalsEvent.ShowMessage).message
        assertEquals("Informe o título da meta.", msg)
    }

    @Test
    fun saveGoal_createsNewGoal_whenGoalIsNull() {
        viewModel.loadInitialData()
        assertEquals(1, viewModel.state.value?.personalGoals?.size)

        viewModel.saveGoal(null, "Nova Meta Pessoal", "Detalhes")

        assertEquals(2, viewModel.state.value?.personalGoals?.size)
        assertEquals(GoalsEvent.CloseGoalPopup, viewModel.event.value)

        viewModel.consumeEvent()
        assertNull(viewModel.event.value)
    }

    @Test
    fun saveGoal_updatesExistingGoal_whenGoalIsProvided() {
        viewModel.loadInitialData()
        val existing = viewModel.state.value!!.personalGoals.first()

        viewModel.saveGoal(existing, "Título Modificado", "Nova Descrição")

        val modified = viewModel.state.value!!.personalGoals.first()
        assertEquals("Título Modificado", modified.title)
        assertEquals("Nova Descrição", modified.description)
        assertEquals(GoalsEvent.CloseGoalPopup, viewModel.event.value)
    }

    @Test
    fun saveGoal_onFailure_emitsErrorMessage() {
        viewModel.loadInitialData()
        api.shouldFail = true

        viewModel.saveGoal(null, "Meta Falha", "Desc")

        assertTrue(viewModel.event.value is GoalsEvent.ShowMessage)
        assertFalse(viewModel.state.value?.isPopupSaving == true)
    }

    @Test
    fun deleteGoal_removesGoalAndEmitsClosePopup() {
        viewModel.loadInitialData()
        val existing = viewModel.state.value!!.personalGoals.first()

        viewModel.deleteGoal(existing)

        assertTrue(viewModel.state.value!!.personalGoals.isEmpty())
        assertEquals(GoalsEvent.CloseGoalPopup, viewModel.event.value)
    }

    @Test
    fun deleteGoal_onFailure_emitsError() {
        viewModel.loadInitialData()
        val existing = viewModel.state.value!!.personalGoals.first()
        api.shouldFail = true

        viewModel.deleteGoal(existing)

        assertEquals(1, viewModel.state.value!!.personalGoals.size)
        assertTrue(viewModel.event.value is GoalsEvent.ShowMessage)
    }

    @Test
    fun retry_triggersReload() {
        api.shouldFail = true
        viewModel.loadInitialData()
        assertTrue(viewModel.state.value?.error != null)

        api.shouldFail = false
        viewModel.retry()
        assertNull(viewModel.state.value?.error)
        assertEquals(1, viewModel.state.value?.personalGoals?.size)
    }
}
