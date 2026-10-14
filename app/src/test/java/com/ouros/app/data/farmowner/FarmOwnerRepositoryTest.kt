package com.ouros.app.data.farmowner

import com.ouros.app.data.auth.AuthResult
import com.ouros.app.data.auth.SessionManager
import com.ouros.app.data.auth.TokenResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Response

class FarmOwnerRepositoryTest {

    @Test
    fun getMe_refreshesExpiredAccessTokenBeforeRequest() = runBlocking {
        val sessionManager = SessionManager().apply {
            setAccessToken("expired-token", expiresInSeconds = 0)
        }
        var refreshCalls = 0
        var requestAuthorization: String? = null
        val api = object : FarmOwnerApi {
            override suspend fun getMe(authorization: String): Response<FarmOwnerResponse> {
                requestAuthorization = authorization
                return Response.success(farmOwner())
            }

            override suspend fun update(
                id: Long,
                authorization: String,
                request: FarmOwnerUpdateRequest
            ): Response<FarmOwnerResponse> = error("Not used")
        }
        val repository = FarmOwnerRepository(
            api = api,
            sessionManager = sessionManager,
            refreshSession = {
                refreshCalls++
                sessionManager.setAccessToken("fresh-token", expiresInSeconds = 3600)
                AuthResult.Success(tokenResponse("fresh-token"))
            },
            clearSession = {}
        )

        val result = repository.getMe()

        assertEquals(1, refreshCalls)
        assertEquals("Bearer fresh-token", requestAuthorization)
        assertEquals(AuthResult.Success(farmOwner()), result)
    }

    private fun farmOwner() = FarmOwnerResponse(
        id = 12L,
        name = "Ana",
        documentNumber = null,
        email = "ana@example.com",
        telephone = null,
        farmId = 34L,
        firstAccess = false,
        photoUrl = null
    )

    private fun tokenResponse(accessToken: String) = TokenResponse(
        accessToken = accessToken,
        expiresIn = 3600,
        refreshExpiresIn = null,
        refreshToken = "refresh-token",
        tokenType = "Bearer",
        scope = null
    )
}
