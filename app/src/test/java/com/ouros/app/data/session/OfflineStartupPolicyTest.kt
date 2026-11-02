package com.ouros.app.data.session

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes automatizados de session. Os casos verificam entradas e resultados esperados para ajudar a detectar regressões sem depender da interface real.
 */
class OfflineStartupPolicyTest {
    @Test
    fun allowsOfflineStartForPreviouslyConfirmedFarm() {
        assertTrue(
            OfflineStartupPolicy.canOpenMain(
                isOnline = false,
                hasSavedRefreshToken = true,
                ownerId = 10L,
                farmId = 20L
            )
        )
    }

    @Test
    fun deniesOfflineStartWithoutSavedAuthentication() {
        assertFalse(
            OfflineStartupPolicy.canOpenMain(
                isOnline = false,
                hasSavedRefreshToken = false,
                ownerId = 10L,
                farmId = 20L
            )
        )
    }

    @Test
    fun deniesOfflineStartBeforeFarmWasConfirmed() {
        assertFalse(
            OfflineStartupPolicy.canOpenMain(
                isOnline = false,
                hasSavedRefreshToken = true,
                ownerId = 10L,
                farmId = null
            )
        )
    }

    @Test
    fun doesNotMaskServerErrorsWhileOnline() {
        assertFalse(
            OfflineStartupPolicy.canOpenMain(
                isOnline = true,
                hasSavedRefreshToken = true,
                ownerId = 10L,
                farmId = 20L
            )
        )
    }
}
