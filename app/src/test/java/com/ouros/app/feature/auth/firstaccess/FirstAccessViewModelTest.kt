package com.ouros.app.feature.auth.firstaccess

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
import com.ouros.app.data.farmowner.FarmOwnerApi
import com.ouros.app.data.farmowner.FarmOwnerRepository
import com.ouros.app.data.farmowner.FarmOwnerResponse
import com.ouros.app.data.farmowner.FarmOwnerUpdateRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class FirstAccessViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private class MemoryRefreshTokenStorage(var token: String? = null) : RefreshTokenStorage {
        override fun saveRefreshToken(token: String) { this.token = token }
        override fun getRefreshToken(): String? = token
        override fun clear() { token = null }
    }

    private object DummyAuthApi : AuthApi {
        override suspend fun startLogin(request: LoginStartRequest): Response<LoginStartResponse> = error("Unused")
        override suspend fun verifyLogin(request: LoginVerifyRequest): Response<TokenResponse> = error("Unused")
        override suspend fun refreshToken(request: RefreshRequest): Response<TokenResponse> = error("Unused")
        override suspend fun startPasswordReset(request: PasswordResetStartRequest): Response<PasswordResetStartResponse> = error("Unused")
        override suspend fun verifyPasswordReset(request: PasswordResetVerifyRequest): Response<PasswordResetVerifyResponse> = error("Unused")
        override suspend fun confirmPasswordReset(request: PasswordResetConfirmRequest): Response<PasswordResetConfirmResponse> = error("Unused")
    }

    private class FakeFarmOwnerApi : FarmOwnerApi {
        var lastUpdateRequest: FarmOwnerUpdateRequest? = null
        var lastUpdateId: Long? = null

        override suspend fun getMe(authorization: String): Response<FarmOwnerResponse> =
            Response.success(
                FarmOwnerResponse(
                    id = 42L,
                    name = "Produtor Teste",
                    documentNumber = "12345678909",
                    email = "teste@ouros.com",
                    telephone = "11987654321",
                    farmId = 1L,
                    firstAccess = true,
                    photoUrl = null
                )
            )

        override suspend fun update(
            id: Long,
            authorization: String,
            request: FarmOwnerUpdateRequest
        ): Response<FarmOwnerResponse> {
            lastUpdateId = id
            lastUpdateRequest = request
            return Response.success(
                FarmOwnerResponse(
                    id = id,
                    name = request.name ?: "Produtor",
                    documentNumber = request.documentNumber,
                    email = "teste@ouros.com",
                    telephone = request.telephone,
                    farmId = 1L,
                    firstAccess = false,
                    photoUrl = null
                )
            )
        }
    }

    private class TestListener : FirstAccessViewModel.Listener {
        var locationPermissionRequiredCalled = false
        var lastErrorMessage: String? = null
        var completedCalled = false

        override fun onFirstAccessStepChanged(step: FirstAccessStep) = Unit
        override fun onFirstAccessLoadingChanged(isLoading: Boolean) = Unit
        override fun onCredentialsErrors(emailMessage: String?, passwordMessage: String?) = Unit
        override fun onInvalidCredentials() = Unit
        override fun onCodeError(message: String?) = Unit
        override fun onCodeChallengeChanged(maskedEmail: String, expiresInSeconds: Long) = Unit
        override fun onPersonalDataErrors(nameMessage: String?, cpfMessage: String?, telephoneMessage: String?) = Unit
        override fun onNewPasswordError(message: String?) = Unit
        override fun onLocationPermissionRequired() {
            locationPermissionRequiredCalled = true
        }
        override fun onFirstAccessMessage(message: String) = Unit
        override fun onFirstAccessError(message: String) {
            lastErrorMessage = message
        }
        override fun onNormalAccessDetected() = Unit
        override fun onFirstAccessCompleted() {
            completedCalled = true
        }
        override fun onFirstAccessCloseRequested() = Unit
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(api: FakeFarmOwnerApi): Pair<FirstAccessViewModel, TestListener> {
        val sessionManager = SessionManager().apply {
            setAccessToken("valid-token", 3600L)
        }
        val authRepo = AuthRepository(DummyAuthApi, MemoryRefreshTokenStorage("refresh"), sessionManager)
        val farmOwnerRepo = FarmOwnerRepository(
            api = api,
            sessionManager = sessionManager,
            refreshSession = authRepo::refreshSession,
            clearSession = authRepo::clearSession
        )
        val viewModel = FirstAccessViewModel(authRepo, farmOwnerRepo)
        val listener = TestListener()
        viewModel.setListener(listener)
        viewModel.initialize(alreadyAuthenticated = true, farmOwnerId = 42L)
        // Advance personal data
        viewModel.onNameChanged("Produtor Teste")
        viewModel.onCpfChanged("12345678909")
        viewModel.onTelephoneChanged("11987654321")
        viewModel.onPersonalDataContinueClicked()
        viewModel.onNewPasswordChanged("Senha@123")
        return Pair(viewModel, listener)
    }

    @Test
    fun onFinishClicked_withoutLocation_requestsLocationPermission() {
        val api = FakeFarmOwnerApi()
        val (viewModel, listener) = createViewModel(api)

        viewModel.onFinishClicked()

        assertTrue(listener.locationPermissionRequiredCalled)
        assertFalse(listener.completedCalled)
        assertEquals(null, api.lastUpdateRequest)
    }

    @Test
    fun onLocationAvailable_withInvalidCoordinates_emitsError() {
        val api = FakeFarmOwnerApi()
        val (viewModel, listener) = createViewModel(api)

        viewModel.onLocationAvailable(latitude = 95.0, longitude = -46.0)
        assertEquals("A localização obtida é inválida. Tente novamente.", listener.lastErrorMessage)

        viewModel.onLocationAvailable(latitude = -23.0, longitude = 200.0)
        assertEquals("A localização obtida é inválida. Tente novamente.", listener.lastErrorMessage)
    }

    @Test
    fun onLocationAvailable_withValidCoordinates_submitsWithLocation() {
        val api = FakeFarmOwnerApi()
        val (viewModel, listener) = createViewModel(api)

        viewModel.onLocationAvailable(latitude = -23.5505, longitude = -46.6333)

        assertTrue(listener.completedCalled)
        assertNotNull(api.lastUpdateRequest)
        assertEquals(42L, api.lastUpdateId)
        assertEquals(-23.5505, api.lastUpdateRequest?.latitude)
        assertEquals(-46.6333, api.lastUpdateRequest?.longitude)
        assertEquals(false, api.lastUpdateRequest?.firstAccess)
    }

    @Test
    fun onLocationUnavailable_emitsError() {
        val api = FakeFarmOwnerApi()
        val (viewModel, listener) = createViewModel(api)

        viewModel.onLocationUnavailable("Localização desativada no aparelho.")

        assertEquals("Localização desativada no aparelho.", listener.lastErrorMessage)
        assertFalse(listener.completedCalled)
    }

    @Test
    fun cancelLocationRequest_allowsSubsequentFlow() {
        val api = FakeFarmOwnerApi()
        val (viewModel, listener) = createViewModel(api)

        viewModel.onFinishClicked()
        assertTrue(listener.locationPermissionRequiredCalled)

        viewModel.cancelLocationRequest()

        // Calling cancel does not emit errors
        assertEquals(null, listener.lastErrorMessage)
    }
}
