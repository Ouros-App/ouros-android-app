package com.ouros.app.feature.main.rank

import androidx.lifecycle.ViewModel
import com.ouros.app.data.auth.AuthResult
import com.ouros.app.data.auth.SessionManager
import com.ouros.app.data.farmowner.FarmOwnerApi
import com.ouros.app.data.farmowner.FarmOwnerRepository
import com.ouros.app.data.farmowner.FarmOwnerResponse
import com.ouros.app.data.farmowner.FarmOwnerUpdateRequest
import com.ouros.app.data.rank.RankAPI
import com.ouros.app.data.rank.RankPodiumResponse
import com.ouros.app.data.rank.RankRepository
import com.ouros.app.data.session.FarmSessionStore
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class RankViewModelFactoryTest {

    private class FakeRankApi : RankAPI {
        override suspend fun getPodium(farmId: Long): Response<RankPodiumResponse> =
            Response.success(null)
    }

    private class FakeFarmOwnerRepository : FarmOwnerRepository(
        api = object : FarmOwnerApi {
            override suspend fun getMe(authorization: String): Response<FarmOwnerResponse> = error("Not used")
            override suspend fun update(
                id: Long,
                authorization: String,
                request: FarmOwnerUpdateRequest
            ): Response<FarmOwnerResponse> = error("Not used")
        },
        sessionManager = SessionManager(),
        refreshSession = { AuthResult.Error("Not used") },
        clearSession = {}
    )

    private val repository = RankRepository(
        api = FakeRankApi(),
        farmOwnerRepository = FakeFarmOwnerRepository(),
        farmSessionStore = FarmSessionStore(null)
    )

    private val factory = RankViewModelFactory(repository)

    @Test
    fun `creates RankViewModel when requested`() {
        val vm = factory.create(RankViewModel::class.java)
        assertNotNull(vm)
        assertTrue(vm is RankViewModel)
    }

    @Test
    fun `throws IllegalArgumentException when unknown ViewModel requested`() {
        class UnknownViewModel : ViewModel()

        assertThrows(IllegalArgumentException::class.java) {
            factory.create(UnknownViewModel::class.java)
        }
    }
}
