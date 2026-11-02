package com.ouros.app.core.fcm

import com.ouros.app.data.fcm.FcmTokenStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Testes automatizados de fcm. Os casos verificam entradas e resultados esperados para ajudar a detectar regressões sem depender da interface real.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FcmTokenStoreTest {

    @Test
    fun saveToken_persistsTokenAndClearRemovesIt() {
        val context = RuntimeEnvironment.getApplication()
        val store = FcmTokenStore(context)

        assertNull(store.getToken())

        store.saveToken("test_token_abc_123")
        assertEquals("test_token_abc_123", store.getToken())

        store.clear()
        assertNull(store.getToken())
    }
}
