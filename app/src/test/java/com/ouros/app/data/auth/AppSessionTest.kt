package com.ouros.app.data.auth

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppSessionTest {

    @Test
    fun initializeAndSessionOperations_workCorrectly() {
        val context = RuntimeEnvironment.getApplication()
        AppSession.initialize(context)

        assertNotNull(AppSession.refreshTokenStore)
        assertNotNull(AppSession.sessionManager)

        AppSession.sessionManager.setAccessToken("token123", 3600L)
        AppSession.markSessionActive()

        AppSession.expireSession()

        assertNull(AppSession.sessionManager.accessToken())
    }
}
