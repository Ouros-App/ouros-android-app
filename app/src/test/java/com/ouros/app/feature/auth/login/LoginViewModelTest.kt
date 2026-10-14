package com.ouros.app.feature.auth.login

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
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Response

class LoginViewModelTest {

    @Test
    fun temporaryProfileFailureKeepsAuthenticatedSession() = runBlocking {
        val sessionManager = SessionManager().apply {
            setAccessToken("access-valid", expiresInSeconds = 3600)
        }
        val refreshTokenStorage = MemoryRefreshTokenStorage("refresh-valid")
        val authRepository = AuthRepository(
            api = UnusedAuthApi,
            refreshTokenStore = refreshTokenStorage,
            sessionManager = sessionManager
        )
        val farmOwnerRepository = FarmOwnerRepository(
            api = OfflineFarmOwnerApi,
            sessionManager = sessionManager,
            refreshSession = authRepository::refreshSession,
            clearSession = authRepository::clearSession
        )
        val viewModel = LoginViewModel(authRepository, farmOwnerRepository)
        var reportedError: String? = null
        viewModel.setListener(object : LoginViewModel.Listener {
            override fun onLoginLoadingChanged(isLoading: Boolean) = Unit
            override fun onCodeLoadingChanged(isLoading: Boolean) = Unit
            override fun onEmailError(message: String?) = Unit
            override fun onPasswordError(message: String?) = Unit
            override fun onCodeError(message: String?) = Unit
            override fun onInvalidCredentials() = Unit
            override fun onLoginError(message: String) {
                reportedError = message
            }
            override fun onCodeRequired(maskedEmail: String, expiresInSeconds: Long) = Unit
            override fun onCodeResent() = Unit
            override fun onFirstAccessRequired(farmOwnerId: Long) = Unit
            override fun onLoginSucceeded() = Unit
        })

        viewModel.loadFarmOwnerProfile()

        assertEquals("Sem conexão. Verifique sua internet.", reportedError)
        assertEquals("access-valid", sessionManager.accessToken())
        assertEquals("refresh-valid", refreshTokenStorage.getRefreshToken())
        assertEquals(0, refreshTokenStorage.clearCalls)
    }

    @Test
    fun profileSuccessIsReplayedOnceWhenListenerAttaches() = runBlocking {
        val sessionManager = SessionManager().apply {
            setAccessToken("access-valid", expiresInSeconds = 3600)
        }
        val authRepository = AuthRepository(
            api = UnusedAuthApi,
            refreshTokenStore = MemoryRefreshTokenStorage("refresh-valid"),
            sessionManager = sessionManager
        )
        val farmOwnerRepository = FarmOwnerRepository(
            api = SuccessfulFarmOwnerApi,
            sessionManager = sessionManager,
            refreshSession = authRepository::refreshSession,
            clearSession = authRepository::clearSession
        )
        val viewModel = LoginViewModel(authRepository, farmOwnerRepository)
        val listener = RecordingListener()

        viewModel.loadFarmOwnerProfile()
        viewModel.setListener(listener)
        viewModel.setListener(listener)

        assertEquals(1, listener.loginSuccessCount)
    }


    private object SuccessfulFarmOwnerApi : FarmOwnerApi {
        override suspend fun getMe(authorization: String): Response<FarmOwnerResponse> =
            Response.success(
                FarmOwnerResponse(
                    id = 42,
                    name = "Owner",
                    documentNumber = null,
                    email = null,
                    telephone = null,
                    farmId = null,
                    firstAccess = false,
                    photoUrl = null
                )
            )

        override suspend fun update(
            id: Long,
            authorization: String,
            request: FarmOwnerUpdateRequest
        ): Response<FarmOwnerResponse> = error("Unused")
    }
    private object OfflineFarmOwnerApi : FarmOwnerApi {
        override suspend fun getMe(authorization: String): Response<FarmOwnerResponse> {
            throw IOException("offline")
        }

        override suspend fun update(
            id: Long,
            authorization: String,
            request: FarmOwnerUpdateRequest
        ): Response<FarmOwnerResponse> = error("Unused")
    }


    private class RecordingListener : LoginViewModel.Listener {
        var loginSuccessCount = 0

        override fun onLoginLoadingChanged(isLoading: Boolean) = Unit
        override fun onCodeLoadingChanged(isLoading: Boolean) = Unit
        override fun onEmailError(message: String?) = Unit
        override fun onPasswordError(message: String?) = Unit
        override fun onCodeError(message: String?) = Unit
        override fun onInvalidCredentials() = Unit
        override fun onLoginError(message: String) = Unit
        override fun onCodeRequired(maskedEmail: String, expiresInSeconds: Long) = Unit
        override fun onCodeResent() = Unit
        override fun onFirstAccessRequired(farmOwnerId: Long) = Unit
        override fun onLoginSucceeded() {
            loginSuccessCount++
        }
    }
    private object UnusedAuthApi : AuthApi {
        override suspend fun startLogin(request: LoginStartRequest): Response<LoginStartResponse> = error("Unused")
        override suspend fun verifyLogin(request: LoginVerifyRequest): Response<TokenResponse> = error("Unused")
        override suspend fun refreshToken(request: RefreshRequest): Response<TokenResponse> = error("Unused")
        override suspend fun startPasswordReset(request: PasswordResetStartRequest): Response<PasswordResetStartResponse> = error("Unused")
        override suspend fun verifyPasswordReset(request: PasswordResetVerifyRequest): Response<PasswordResetVerifyResponse> = error("Unused")
        override suspend fun confirmPasswordReset(request: PasswordResetConfirmRequest): Response<PasswordResetConfirmResponse> = error("Unused")
    }

    private class MemoryRefreshTokenStorage(
        private var token: String?
    ) : RefreshTokenStorage {
        var clearCalls = 0
            private set

        override fun saveRefreshToken(token: String) {
            this.token = token
        }

        override fun getRefreshToken(): String? = token

        override fun clear() {
            clearCalls++
            token = null
        }
    }
}
