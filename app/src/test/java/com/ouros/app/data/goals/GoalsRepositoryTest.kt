package com.ouros.app.data.goals

import com.ouros.app.data.auth.AuthApi
import com.ouros.app.data.auth.AuthRepository
import com.ouros.app.data.auth.AuthResult
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
import com.ouros.app.data.session.FarmSessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException

class GoalsRepositoryTest {

    private class FakeRefreshTokenStore(var token: String? = "saved-refresh-token") : RefreshTokenStorage {
        var clearCalled = false
        override fun saveRefreshToken(token: String) { this.token = token }
        override fun getRefreshToken(): String? = token
        override fun clear() {
            clearCalled = true
            token = null
        }
    }

    private class FakeAuthApi : AuthApi {
        var refreshResult: Response<TokenResponse> = Response.success(
            TokenResponse(
                accessToken = "new-access-token",
                expiresIn = 3600L,
                refreshExpiresIn = 7200L,
                refreshToken = "new-refresh-token",
                tokenType = "Bearer",
                scope = null
            )
        )

        override suspend fun refreshToken(request: RefreshRequest): Response<TokenResponse> = refreshResult
        override suspend fun startLogin(request: LoginStartRequest): Response<LoginStartResponse> = error("Not used")
        override suspend fun verifyLogin(request: LoginVerifyRequest): Response<TokenResponse> = error("Not used")
        override suspend fun startPasswordReset(request: PasswordResetStartRequest): Response<PasswordResetStartResponse> = error("Not used")
        override suspend fun verifyPasswordReset(request: PasswordResetVerifyRequest): Response<PasswordResetVerifyResponse> = error("Not used")
        override suspend fun confirmPasswordReset(request: PasswordResetConfirmRequest): Response<PasswordResetConfirmResponse> = error("Not used")
    }

    private class FakeGoalsApi : GoalsApi {
        var individualGoalsResponse: Response<List<GoalResponse>> = Response.success(emptyList())
        var stateGoalsResponse: Response<List<GoalResponse>> = Response.success(emptyList())
        var updateIndividualResponse: Response<GoalResponse> = Response.success(
            GoalResponse(1L, "Atualizada", "Desc", status = "COMPLETED")
        )
        var updateStateResponse: Response<GoalResponse> = Response.success(
            GoalResponse(2L, "Estadual", "Desc", status = "COMPLETED")
        )
        var createResponse: Response<GoalResponse> = Response.success(
            GoalResponse(3L, "Criada", "Nova", status = "PENDING")
        )
        var deleteResponse: Response<Unit> = Response.success(Unit)

        var lastIndividualAuth: String? = null
        var lastStateAuth: String? = null
        var lastUpdateRequest: UpdateIndividualGoalRequest? = null
        var lastCreateRequest: CreateIndividualGoalRequest? = null
        var lastDeleteId: Long? = null

        override suspend fun getIndividualGoals(authorization: String): Response<List<GoalResponse>> {
            lastIndividualAuth = authorization
            return individualGoalsResponse
        }

        override suspend fun getStateGoals(authorization: String): Response<List<GoalResponse>> {
            lastStateAuth = authorization
            return stateGoalsResponse
        }

        override suspend fun updateIndividualGoal(
            id: Long,
            authorization: String,
            request: UpdateIndividualGoalRequest
        ): Response<GoalResponse> {
            lastUpdateRequest = request
            return updateIndividualResponse
        }

        override suspend fun updateStateGoal(
            id: Long,
            authorization: String,
            request: UpdateGoalStatusRequest
        ): Response<GoalResponse> = updateStateResponse

        override suspend fun createIndividualGoal(
            authorization: String,
            request: CreateIndividualGoalRequest
        ): Response<GoalResponse> {
            lastCreateRequest = request
            return createResponse
        }

        override suspend fun deleteIndividualGoal(
            id: Long,
            authorization: String
        ): Response<Unit> {
            lastDeleteId = id
            return deleteResponse
        }
    }

    private class FakeFarmSessionStore(var currentFarmId: Long? = 10L) : FarmSessionStore(null) {
        override fun farmId(): Long? = currentFarmId
    }

    private lateinit var api: FakeGoalsApi
    private lateinit var authApi: FakeAuthApi
    private lateinit var tokenStore: FakeRefreshTokenStore
    private lateinit var sessionManager: SessionManager
    private lateinit var authRepository: AuthRepository
    private lateinit var farmSessionStore: FakeFarmSessionStore
    private lateinit var repository: GoalsRepository

    @Before
    fun setUp() {
        api = FakeGoalsApi()
        authApi = FakeAuthApi()
        tokenStore = FakeRefreshTokenStore()
        sessionManager = SessionManager()
        sessionManager.setAccessToken("initial-token", 3600L)
        authRepository = AuthRepository(authApi, tokenStore, sessionManager)
        farmSessionStore = FakeFarmSessionStore(10L)
        repository = GoalsRepository(api, authRepository, sessionManager, farmSessionStore)
    }

    @Test
    fun load_successReturnsPayload() = runBlocking {
        val personal = listOf(GoalResponse(1L, "Pessoal", "D", status = "PENDING"))
        val state = listOf(GoalResponse(2L, "Estadual", "D", status = "COMPLETED"))
        api.individualGoalsResponse = Response.success(personal)
        api.stateGoalsResponse = Response.success(state)

        val result = repository.load()

        assertTrue(result is GoalsResult.Success)
        val payload = (result as GoalsResult.Success).data
        assertEquals(personal, payload.personalGoals)
        assertEquals(state, payload.stateGoals)
        assertEquals("Bearer initial-token", api.lastIndividualAuth)
    }

    @Test
    fun load_individualGoalsFails_returnsError() = runBlocking {
        api.individualGoalsResponse = Response.error(404, "".toResponseBody(null))

        val result = repository.load()

        assertTrue(result is GoalsResult.Error)
        assertEquals("A meta não foi encontrada.", (result as GoalsResult.Error).message)
        assertFalse(result.canRetry)
    }

    @Test
    fun load_stateGoalsFails_returnsError() = runBlocking {
        api.individualGoalsResponse = Response.success(emptyList())
        api.stateGoalsResponse = Response.error(500, "".toResponseBody(null))

        val result = repository.load()

        assertTrue(result is GoalsResult.Error)
        assertEquals("O serviço está indisponível. Tente novamente.", (result as GoalsResult.Error).message)
        assertTrue(result.canRetry)
    }

    @Test
    fun load_withExpiredToken_refreshesAndRetries() = runBlocking {
        var callCount = 0
        val dynamicApi = object : GoalsApi by api {
            override suspend fun getIndividualGoals(authorization: String): Response<List<GoalResponse>> {
                callCount++
                return if (callCount == 1) {
                    Response.error(401, "".toResponseBody(null))
                } else {
                    Response.success(emptyList())
                }
            }
        }
        val customRepo = GoalsRepository(dynamicApi, authRepository, sessionManager, farmSessionStore)

        val result = customRepo.load()

        assertTrue(result is GoalsResult.Success)
        assertEquals(2, callCount)
    }

    @Test
    fun load_whenInitialTokenBlank_refreshesAuthorization() = runBlocking {
        sessionManager.clear()
        api.individualGoalsResponse = Response.success(emptyList())

        val result = repository.load()

        assertTrue(result is GoalsResult.Success)
        assertEquals("Bearer new-access-token", api.lastIndividualAuth)
    }

    @Test
    fun load_whenRefreshFailsWith401_returnsAuthenticationRequired() = runBlocking {
        sessionManager.clear()
        authApi.refreshResult = Response.error(401, "".toResponseBody(null))

        val result = repository.load()

        assertEquals(GoalsResult.AuthenticationRequired, result)
    }

    @Test
    fun load_whenRefreshFailsWith500_returnsTemporaryError() = runBlocking {
        sessionManager.clear()
        authApi.refreshResult = Response.error(500, "".toResponseBody(null))

        val result = repository.load()

        assertTrue(result is GoalsResult.Error)
        val error = result as GoalsResult.Error
        assertEquals("Não foi possível validar sua sessão. Verifique sua conexão e tente novamente.", error.message)
        assertTrue(error.canRetry)
    }

    @Test
    fun load_whenSecondCallAfterRefreshReturns401_clearsSessionAndReturnsAuthRequired() = runBlocking {
        var callCount = 0
        val dynamicApi = object : GoalsApi by api {
            override suspend fun getIndividualGoals(authorization: String): Response<List<GoalResponse>> {
                callCount++
                return Response.error(401, "".toResponseBody(null))
            }
        }
        val customRepo = GoalsRepository(dynamicApi, authRepository, sessionManager, farmSessionStore)

        val result = customRepo.load()

        assertEquals(GoalsResult.AuthenticationRequired, result)
        assertTrue(tokenStore.clearCalled)
    }

    @Test
    fun updateStatus_personalGoal_success() = runBlocking {
        val goal = GoalResponse(1L, "Meta", "Desc", status = "PENDING")
        val result = repository.updateStatus(GoalOrigin.PERSONAL, goal, completed = true)

        assertTrue(result is GoalsResult.Success)
        assertEquals("COMPLETED", api.lastUpdateRequest?.status)
    }

    @Test
    fun updateStatus_stateGoal_success() = runBlocking {
        val goal = GoalResponse(2L, "Estadual", "Desc", status = "PENDING")
        val result = repository.updateStatus(GoalOrigin.STATE, goal, completed = false)

        assertTrue(result is GoalsResult.Success)
    }

    @Test
    fun create_withoutFarmId_returnsError() = runBlocking {
        farmSessionStore.currentFarmId = null

        val result = repository.create("Nova Meta", "Descrição")

        assertTrue(result is GoalsResult.Error)
        assertEquals("Não foi possível identificar a granja. Tente novamente.", (result as GoalsResult.Error).message)
    }

    @Test
    fun create_withFarmId_submitsAndReturnsSuccess() = runBlocking {
        val result = repository.create("Nova Meta", "Descrição")

        assertTrue(result is GoalsResult.Success)
        assertEquals("Nova Meta", api.lastCreateRequest?.title)
        assertEquals("Descrição", api.lastCreateRequest?.description)
        assertEquals(10L, api.lastCreateRequest?.farmId)
    }

    @Test
    fun create_withBlankDescription_sendsNullDescription() = runBlocking {
        val result = repository.create("Nova Meta", "   ")

        assertTrue(result is GoalsResult.Success)
        assertEquals("Nova Meta", api.lastCreateRequest?.title)
        assertNull(api.lastCreateRequest?.description)
    }

    @Test
    fun updateDetails_submitsCleanDataAndReturnsSuccess() = runBlocking {
        val goal = GoalResponse(1L, "Título Antigo", "Desc Antiga", status = "PENDING")
        val result = repository.updateDetails(goal, "  Novo Título  ", "  Nova Descrição  ")

        assertTrue(result is GoalsResult.Success)
        assertEquals("Novo Título", api.lastUpdateRequest?.title)
        assertEquals("Nova Descrição", api.lastUpdateRequest?.description)
    }

    @Test
    fun updateDetails_fails_returnsError() = runBlocking {
        val goal = GoalResponse(1L, "Título", null, status = "PENDING")
        api.updateIndividualResponse = Response.error(400, "".toResponseBody(null))

        val result = repository.updateDetails(goal, "Título", "Desc")

        assertTrue(result is GoalsResult.Error)
        assertEquals("Os dados enviados são inválidos.", (result as GoalsResult.Error).message)
    }

    @Test
    fun delete_success() = runBlocking {
        val result = repository.delete(42L)

        assertTrue(result is GoalsResult.Success)
        assertEquals(42L, api.lastDeleteId)
    }

    @Test
    fun delete_failsWithForbidden() = runBlocking {
        api.deleteResponse = Response.error(403, "".toResponseBody(null))

        val result = repository.delete(42L)

        assertTrue(result is GoalsResult.Error)
        assertEquals("Você não possui permissão para realizar esta ação.", (result as GoalsResult.Error).message)
        assertFalse(result.canRetry)
    }

    @Test
    fun delete_failsWith429_returnsTooManyRequests() = runBlocking {
        api.deleteResponse = Response.error(429, "".toResponseBody(null))

        val result = repository.delete(42L)

        assertTrue(result is GoalsResult.Error)
        assertEquals("Muitas tentativas. Aguarde e tente novamente.", (result as GoalsResult.Error).message)
        assertTrue(result.canRetry)
    }

    @Test
    fun delete_failsWithUnexpectedStatus_returnsFallback() = runBlocking {
        api.deleteResponse = Response.error(418, "".toResponseBody(null))

        val result = repository.delete(42L)

        assertTrue(result is GoalsResult.Error)
        assertEquals("Não foi possível excluir a meta.", (result as GoalsResult.Error).message)
        assertTrue(result.canRetry)
    }

    @Test
    fun safeRequest_catchesIOException() = runBlocking {
        val failingApi = object : GoalsApi by api {
            override suspend fun getIndividualGoals(authorization: String): Response<List<GoalResponse>> {
                throw IOException("Sem rede")
            }
        }
        val customRepo = GoalsRepository(failingApi, authRepository, sessionManager, farmSessionStore)

        val result = customRepo.load()

        assertTrue(result is GoalsResult.Error)
        assertEquals("Sem conexão. Verifique sua internet.", (result as GoalsResult.Error).message)
        assertTrue(result.canRetry)
    }

    @Test
    fun safeRequest_catchesGenericException() = runBlocking {
        val failingApi = object : GoalsApi by api {
            override suspend fun getIndividualGoals(authorization: String): Response<List<GoalResponse>> {
                throw RuntimeException("Falha inesperada")
            }
        }
        val customRepo = GoalsRepository(failingApi, authRepository, sessionManager, farmSessionStore)

        val result = customRepo.load()

        assertTrue(result is GoalsResult.Error)
        assertEquals("Ocorreu um erro inesperado.", (result as GoalsResult.Error).message)
        assertTrue(result.canRetry)
    }

    @Test
    fun safeRequest_rethrowsCancellationException() = runBlocking {
        val failingApi = object : GoalsApi by api {
            override suspend fun getIndividualGoals(authorization: String): Response<List<GoalResponse>> {
                throw CancellationException("Cancelado")
            }
        }
        val customRepo = GoalsRepository(failingApi, authRepository, sessionManager, farmSessionStore)

        try {
            customRepo.load()
            fail("Deveria ter relançado CancellationException")
        } catch (_: CancellationException) {
            // Sucesso
        }
    }
}
