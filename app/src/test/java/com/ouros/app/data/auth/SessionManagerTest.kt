package com.ouros.app.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes automatizados de auth. Os casos verificam entradas e resultados esperados para ajudar a detectar regressões sem depender da interface real.
 */
class SessionManagerTest {

    @Test
    fun initialSession_hasNoToken() {
        val manager = SessionManager()
        assertNull(manager.accessToken())
        assertFalse(manager.hasValidAccessToken())
    }

    @Test
    fun setAccessToken_withValidExpiry_returnsToken() {
        val manager = SessionManager()
        manager.setAccessToken("valid-token", 3600L)

        assertEquals("valid-token", manager.accessToken())
        assertTrue(manager.hasValidAccessToken())
    }

    @Test
    fun setAccessToken_withExpiredTime_returnsNull() {
        val manager = SessionManager()
        manager.setAccessToken("expired-token", 0L)

        assertNull(manager.accessToken())
        assertFalse(manager.hasValidAccessToken())
    }

    @Test
    fun clear_removesToken() {
        val manager = SessionManager()
        manager.setAccessToken("valid-token", 3600L)
        manager.clear()

        assertNull(manager.accessToken())
        assertFalse(manager.hasValidAccessToken())
    }
}
