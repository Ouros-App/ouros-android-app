package com.ouros.app.data.auth

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.util.concurrent.atomic.AtomicInteger

class AuthRepositoryTest {

    @Test
    fun concurrentRepositoriesShareOneRefreshExchange() = runBlocking {
        val sessionManager = SessionManager()
        val tokenStorage = MemoryRefreshTokenStorage("refresh-original")
        val api = RotatingAuthApi()
        val firstRepository = AuthRepository(api, tokenStorage, sessionManager)
        val secondRepository = AuthRepository(api, tokenStorage, sessionManager)

        val first = async { firstRepository.refreshSession() }
        api.firstRequestStarted.await()
        val secondStarted = CompletableDeferred<Unit>()
        val second = async(start = CoroutineStart.UNDISPATCHED) {
            secondStarted.complete(Unit)
            secondRepository.refreshSession()
        }
        secondStarted.await()
        api.releaseRequests.complete(Unit)

        val firstResult = first.await()
        val secondResult = second.await()

        assertEquals(1, api.refreshCallCount.get())
        assertTrue(firstResult is AuthResult.Success)
        assertEquals(firstResult, secondResult)
        assertEquals("refresh-rotated", tokenStorage.getRefreshToken())
        assertEquals("access-refreshed", sessionManager.accessToken())
    }

    private class RotatingAuthApi : AuthApi {
        val refreshCallCount = AtomicInteger()
        val firstRequestStarted = CompletableDeferred<Unit>()
        val releaseRequests = CompletableDeferred<Unit>()
        private val lock = Any()
        private var validRefreshToken = "refresh-original"

        override suspend fun startLogin(request: LoginStartRequest): Response<LoginStartResponse> = error("Unused")

        override suspend fun verifyLogin(request: LoginVerifyRequest): Response<TokenResponse> = error("Unused")

        override suspend fun refreshToken(request: RefreshRequest): Response<TokenResponse> {
            if (refreshCallCount.incrementAndGet() == 1) {
                firstRequestStarted.complete(Unit)
            }
            releaseRequests.await()

            return synchronized(lock) {
                if (request.refreshToken != validRefreshToken) {
                    Response.error(401, "Refresh token already rotated".toResponseBody())
                } else {
                    validRefreshToken = "refresh-rotated"
                    Response.success(tokenResponse())
                }
            }
        }

        override suspend fun startPasswordReset(request: PasswordResetStartRequest): Response<PasswordResetStartResponse> = error("Unused")

        override suspend fun verifyPasswordReset(request: PasswordResetVerifyRequest): Response<PasswordResetVerifyResponse> = error("Unused")

        override suspend fun confirmPasswordReset(request: PasswordResetConfirmRequest): Response<PasswordResetConfirmResponse> = error("Unused")

        private fun tokenResponse() = TokenResponse(
            accessToken = "access-refreshed",
            expiresIn = 3600,
            refreshExpiresIn = null,
            refreshToken = validRefreshToken,
            tokenType = "Bearer",
            scope = null
        )
    }

    private class MemoryRefreshTokenStorage(
        private var token: String?
    ) : RefreshTokenStorage {
        override fun saveRefreshToken(token: String) {
            this.token = token
        }

        override fun getRefreshToken(): String? = token

        override fun clear() {
            token = null
        }
    }
}
