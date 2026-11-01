package com.ouros.app.feature.main

import android.app.Application
import android.os.Looper
import com.ouros.app.data.auth.AuthResult
import com.ouros.app.data.auth.SessionManager
import com.ouros.app.data.auth.TokenResponse
import com.ouros.app.data.farmowner.FarmOwnerApi
import com.ouros.app.data.farmowner.FarmOwnerRepository
import com.ouros.app.data.farmowner.FarmOwnerResponse
import com.ouros.app.data.farmowner.FarmOwnerUpdateRequest
import com.ouros.app.data.local.OfflineSyncRegistry
import com.ouros.app.data.session.FarmSessionStore
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainViewModelOfflineTest {
    private val context: Application
        get() = RuntimeEnvironment.getApplication()

    @Before
    fun initializeOfflineState() {
        OfflineSyncRegistry.initialize(context)
    }

    @Test
    fun loadProfile_doesNotShowRemoteErrorWhileOffline() {
        val listener = RecordingListener()
        val viewModel = MainViewModel(
            farmOwnerRepository = FakeFarmOwnerRepository(
                AuthResult.Error("Não foi possível carregar o perfil.", 503)
            ),
            farmSessionStore = FarmSessionStore(context)
        )
        viewModel.setListener(listener)

        viewModel.loadProfile()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(emptyList<String>(), listener.errors)
        assertEquals(listOf(false, true, false), listener.loadingStates)
    }

    private class RecordingListener : MainViewModel.Listener {
        val errors = mutableListOf<String>()
        val loadingStates = mutableListOf<Boolean>()
        override fun onMainLoadingChanged(isLoading: Boolean) { loadingStates += isLoading }
        override fun onMainProfileLoaded(profile: MainProfileUiModel) = Unit
        override fun onMainProfileError(message: String) { errors += message }
        override fun onMainSessionExpired() = Unit
    }

    private class FakeFarmOwnerRepository(
        private val result: AuthResult<FarmOwnerResponse>
    ) : FarmOwnerRepository(
        api = object : FarmOwnerApi {
            override suspend fun getMe(authorization: String): Response<FarmOwnerResponse> = error("unused")
            override suspend fun update(
                id: Long,
                authorization: String,
                request: FarmOwnerUpdateRequest
            ): Response<FarmOwnerResponse> = error("unused")
        },
        sessionManager = SessionManager(),
        refreshSession = { AuthResult.Error("unused") },
        clearSession = {}
    ) {
        override suspend fun getMe(): AuthResult<FarmOwnerResponse> = result
    }
}
