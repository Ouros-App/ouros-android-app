package com.ouros.app.data.auth

import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response as RetrofitResponse

class TokenAuthenticatorTest {

    private class FakeRefreshTokenStore(var token: String? = null) : RefreshTokenStorage {
        var clearCalled = false

        override fun saveRefreshToken(token: String) {
            this.token = token
        }

        override fun getRefreshToken(): String? = token

        override fun clear() {
            clearCalled = true
            token = null
        }
    }

    private fun buildResponse(
        url: String = "https://example.com/api",
        headerToken: String? = "old_token",
        priorResponse: Response? = null,
        code: Int = 401
    ): Response {
        val requestBuilder = Request.Builder().url(url)
        if (headerToken != null) {
            requestBuilder.header("Authorization", "Bearer $headerToken")
        }
        val builder = Response.Builder()
            .request(requestBuilder.build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("Unauthorized")
        if (priorResponse != null) {
            builder.priorResponse(priorResponse)
        }
        return builder.build()
    }

    @Test
    fun authenticate_exceedsMaxAttempts_callsSessionExpiredAndReturnsNull() {
        var sessionExpiredCalled = false
        val sessionManager = SessionManager()
        val store = FakeRefreshTokenStore("refresh_token")

        val authenticator = TokenAuthenticator(
            sessionManager = sessionManager,
            refreshTokenStore = store,
            refreshToken = { error("Should not be called") },
            onSessionExpired = { sessionExpiredCalled = true }
        )

        val firstResponse = buildResponse()
        val secondResponse = buildResponse(priorResponse = firstResponse)

        val result = authenticator.authenticate(null, secondResponse)

        assertNull(result)
        assertTrue(sessionExpiredCalled)
    }

    @Test
    fun authenticate_tokenAlreadyUpdatedByOtherRequest_reusesNewToken() {
        var sessionExpiredCalled = false
        val sessionManager = SessionManager().apply {
            setAccessToken("new_token", 3600L)
        }
        val store = FakeRefreshTokenStore("refresh_token")

        val authenticator = TokenAuthenticator(
            sessionManager = sessionManager,
            refreshTokenStore = store,
            refreshToken = { error("Should not be called") },
            onSessionExpired = { sessionExpiredCalled = true }
        )

        val response = buildResponse(headerToken = "old_token")
        val result = authenticator.authenticate(null, response)

        assertNotNull(result)
        assertEquals("Bearer new_token", result?.header("Authorization"))
        assertEquals(false, sessionExpiredCalled)
    }

    @Test
    fun authenticate_noSavedRefreshToken_callsSessionExpired() {
        var sessionExpiredCalled = false
        val sessionManager = SessionManager()
        val store = FakeRefreshTokenStore(null)

        val authenticator = TokenAuthenticator(
            sessionManager = sessionManager,
            refreshTokenStore = store,
            refreshToken = { error("Should not be called") },
            onSessionExpired = { sessionExpiredCalled = true }
        )

        val response = buildResponse()
        val result = authenticator.authenticate(null, response)

        assertNull(result)
        assertTrue(sessionExpiredCalled)
    }

    @Test
    fun authenticate_networkException_returnsNullWithoutExpiringSession() {
        var sessionExpiredCalled = false
        val sessionManager = SessionManager()
        val store = FakeRefreshTokenStore("refresh_123")

        val authenticator = TokenAuthenticator(
            sessionManager = sessionManager,
            refreshTokenStore = store,
            refreshToken = { throw RuntimeException("Network down") },
            onSessionExpired = { sessionExpiredCalled = true }
        )

        val response = buildResponse()
        val result = authenticator.authenticate(null, response)

        assertNull(result)
        assertEquals(false, sessionExpiredCalled)
    }

    @Test
    fun authenticate_refreshReturns401_callsSessionExpired() {
        var sessionExpiredCalled = false
        val sessionManager = SessionManager()
        val store = FakeRefreshTokenStore("refresh_123")

        val authenticator = TokenAuthenticator(
            sessionManager = sessionManager,
            refreshTokenStore = store,
            refreshToken = {
                RetrofitResponse.error(401, "".toResponseBody(null))
            },
            onSessionExpired = { sessionExpiredCalled = true }
        )

        val response = buildResponse()
        val result = authenticator.authenticate(null, response)

        assertNull(result)
        assertTrue(sessionExpiredCalled)
    }

    @Test
    fun authenticate_refreshReturns500_returnsNullWithoutExpiring() {
        var sessionExpiredCalled = false
        val sessionManager = SessionManager()
        val store = FakeRefreshTokenStore("refresh_123")

        val authenticator = TokenAuthenticator(
            sessionManager = sessionManager,
            refreshTokenStore = store,
            refreshToken = {
                RetrofitResponse.error(500, "".toResponseBody(null))
            },
            onSessionExpired = { sessionExpiredCalled = true }
        )

        val response = buildResponse()
        val result = authenticator.authenticate(null, response)

        assertNull(result)
        assertEquals(false, sessionExpiredCalled)
    }

    @Test
    fun authenticate_successfulRefresh_updatesTokensAndRetries() {
        var sessionExpiredCalled = false
        val sessionManager = SessionManager()
        val store = FakeRefreshTokenStore("initial_refresh")

        val authenticator = TokenAuthenticator(
            sessionManager = sessionManager,
            refreshTokenStore = store,
            refreshToken = { req ->
                assertEquals("initial_refresh", req.refreshToken)
                val tokenResponse = TokenResponse(
                    accessToken = "fresh_access_token",
                    expiresIn = 1800L,
                    refreshExpiresIn = null,
                    refreshToken = "rotated_refresh_token",
                    tokenType = "Bearer",
                    scope = null
                )
                RetrofitResponse.success(tokenResponse)
            },
            onSessionExpired = { sessionExpiredCalled = true }
        )

        val response = buildResponse(headerToken = "old_token")
        val result = authenticator.authenticate(null, response)

        assertNotNull(result)
        assertEquals("Bearer fresh_access_token", result?.header("Authorization"))
        assertEquals("fresh_access_token", sessionManager.accessToken())
        assertEquals("rotated_refresh_token", store.getRefreshToken())
        assertEquals(false, sessionExpiredCalled)
    }
}
