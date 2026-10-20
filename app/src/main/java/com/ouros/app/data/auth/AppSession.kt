package com.ouros.app.data.auth

import android.content.Context
import android.content.Intent
import com.ouros.app.feature.auth.login.LoginActivity
import java.util.concurrent.atomic.AtomicBoolean

object AppSession {

    val sessionManager: SessionManager by lazy {
        SessionManager()
    }

    private var applicationContext: Context? = null
    private var secureTokenStore: SecureRefreshTokenStore? = null

    private val sessionExpirationHandled =
        AtomicBoolean(false)

    val refreshTokenStore: RefreshTokenStorage
        get() = checkNotNull(secureTokenStore) {
            "AppSession.initialize(context) deve ser chamado antes de acessar a rede."
        }

    fun initialize(context: Context) {
        synchronized(this) {
            if (applicationContext != null) {
                return
            }

            val appContext =
                context.applicationContext

            applicationContext =
                appContext

            secureTokenStore =
                SecureRefreshTokenStore(
                    context = appContext
                )
        }
    }

    fun markSessionActive() {
        sessionExpirationHandled.set(false)
    }

    fun expireSession() {
        sessionManager.clear()
        secureTokenStore?.clear()

        val context =
            applicationContext ?: return

        if (
            !sessionExpirationHandled.compareAndSet(
                false,
                true
            )
        ) {
            return
        }

        val intent =
            Intent(
                context,
                LoginActivity::class.java
            ).apply {
                flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TASK
            }

        context.startActivity(intent)
    }
}