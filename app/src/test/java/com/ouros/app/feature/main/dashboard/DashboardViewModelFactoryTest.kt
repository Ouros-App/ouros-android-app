package com.ouros.app.feature.main.dashboard

import androidx.lifecycle.ViewModel
import com.ouros.app.data.auth.AuthApi
import com.ouros.app.data.auth.AuthRepository
import com.ouros.app.data.auth.RefreshTokenStorage
import com.ouros.app.data.auth.SessionManager
import com.ouros.app.data.dashboard.DashboardApi
import com.ouros.app.data.dashboard.DashboardCache
import com.ouros.app.data.dashboard.DashboardRepository
import com.ouros.app.data.dashboard.DashboardRequest
import com.ouros.app.data.dashboard.DashboardResponse
import com.ouros.app.data.dashboard.DashboardStatusResponse
import com.ouros.app.data.session.FarmSessionStore
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.Response

class DashboardViewModelFactoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private class FakeDashboardApi : DashboardApi {
        override suspend fun getStatus(authorization: String): Response<DashboardStatusResponse> =
            error("Not used")

        override suspend fun getCustomDashboard(
            authorization: String,
            request: DashboardRequest
        ): Response<DashboardResponse> = error("Not used")
    }

    private class FakeRefreshTokenStorage : RefreshTokenStorage {
        override fun getRefreshToken(): String? = null
        override fun saveRefreshToken(token: String) {}
        override fun clear() {}
    }

    private class FakeAuthApi : AuthApi {
        override suspend fun startLogin(request: com.ouros.app.data.auth.LoginStartRequest) = error("Not used")
        override suspend fun verifyLogin(request: com.ouros.app.data.auth.LoginVerifyRequest) = error("Not used")
        override suspend fun refreshToken(request: com.ouros.app.data.auth.RefreshRequest) = error("Not used")
        override suspend fun startPasswordReset(request: com.ouros.app.data.auth.PasswordResetStartRequest) = error("Not used")
        override suspend fun verifyPasswordReset(request: com.ouros.app.data.auth.PasswordResetVerifyRequest) = error("Not used")
        override suspend fun confirmPasswordReset(request: com.ouros.app.data.auth.PasswordResetConfirmRequest) = error("Not used")
    }

    @Test
    fun createsDashboardViewModelWhenRequested() {
        val sessionManager = SessionManager()
        val authRepository = AuthRepository(
            api = FakeAuthApi(),
            refreshTokenStore = FakeRefreshTokenStorage(),
            sessionManager = sessionManager
        )
        val repository = DashboardRepository(
            api = FakeDashboardApi(),
            authRepository = authRepository,
            sessionManager = sessionManager,
            cache = DashboardCache(temporaryFolder.root),
            farmSessionStore = FarmSessionStore(null)
        )
        val factory = DashboardViewModelFactory(repository)

        val vm = factory.create(DashboardViewModel::class.java)

        assertNotNull(vm)
        assertTrue(vm is DashboardViewModel)
    }

    @Test
    fun throwsIllegalArgumentExceptionWhenUnknownViewModelRequested() {
        val sessionManager = SessionManager()
        val authRepository = AuthRepository(
            api = FakeAuthApi(),
            refreshTokenStore = FakeRefreshTokenStorage(),
            sessionManager = sessionManager
        )
        val repository = DashboardRepository(
            api = FakeDashboardApi(),
            authRepository = authRepository,
            sessionManager = sessionManager,
            cache = DashboardCache(temporaryFolder.root),
            farmSessionStore = FarmSessionStore(null)
        )
        val factory = DashboardViewModelFactory(repository)

        class UnknownViewModel : ViewModel()

        assertThrows(IllegalArgumentException::class.java) {
            factory.create(UnknownViewModel::class.java)
        }
    }
}
