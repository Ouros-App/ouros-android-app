package com.ouros.app.feature.onboarding.splash

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.ouros.app.R
import com.ouros.app.core.fcm.FcmTokenStore
import com.ouros.app.data.auth.AppSession
import com.ouros.app.data.auth.AuthNetwork
import com.ouros.app.data.auth.AuthRepository
import com.ouros.app.data.auth.AuthResult
import com.ouros.app.data.auth.SecureRefreshTokenStore
import com.ouros.app.data.farmowner.FarmOwnerNetwork
import com.ouros.app.data.farmowner.FarmOwnerRepository
import com.ouros.app.data.notification.NotificationRepository
import com.ouros.app.feature.auth.firstaccess.FirstAccessActivity
import com.ouros.app.feature.error.ErrorActivity
import com.ouros.app.feature.main.MainActivity
import com.ouros.app.feature.onboarding.welcome.WelcomeActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var authRepository: AuthRepository
    private lateinit var farmOwnerRepository: FarmOwnerRepository
    private lateinit var refreshTokenStore: SecureRefreshTokenStore

    private var navigationStarted = false
    private var isWaitingForPermission = false
    private var pendingNavigationIntent: Intent? = null

    private val fcmTokenStore: FcmTokenStore by lazy {
        FcmTokenStore(applicationContext)
    }

    private val notificationRepository: NotificationRepository by lazy {
        NotificationRepository()
    }

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            isWaitingForPermission = false
            if (isGranted) {
                registerFcmToken()
            }
            pendingNavigationIntent?.let { intent ->
                navigate(intent)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_splash)

        AppSession.initialize(
            applicationContext
        )

        configureRepositories()
        checkAndRequestNotificationPermission()
        restoreSession()
    }

    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                isWaitingForPermission = true
                requestNotificationPermissionLauncher.launch(permission)
            } else {
                registerFcmToken()
            }
        } else {
            registerFcmToken()
        }
    }

    private fun registerFcmToken() {
        runCatching {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val token = task.result
                    if (!token.isNullOrBlank()) {
                        fcmTokenStore.saveToken(token)
                        if (AppSession.sessionManager.hasValidAccessToken()) {
                            activityScope.launch {
                                notificationRepository.registerDeviceToken(token)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun configureRepositories() {
        refreshTokenStore = SecureRefreshTokenStore(applicationContext)

        authRepository = AuthRepository(
            api = AuthNetwork.api,
            refreshTokenStore = refreshTokenStore,
            sessionManager = AppSession.sessionManager
        )

        farmOwnerRepository = FarmOwnerRepository(
            api = FarmOwnerNetwork.api,
            sessionManager = AppSession.sessionManager,
            refreshSession = authRepository::refreshSession,
            clearSession = authRepository::clearSession
        )
    }

    private fun restoreSession() {
        activityScope.launch {
            val splashStartedAt = SystemClock.elapsedRealtime()
            val savedRefreshToken = refreshTokenStore.getRefreshToken()

            if (savedRefreshToken.isNullOrBlank()) {
                waitMinimumSplashTime(splashStartedAt)
                openWelcome()
                return@launch
            }

            when (val refreshResult = authRepository.refreshSession()) {
                is AuthResult.Error -> {
                    waitMinimumSplashTime(splashStartedAt)
                    handleRefreshError(refreshResult)
                }

                is AuthResult.Success -> {
                    loadFarmOwnerProfile(splashStartedAt)
                }
            }
        }
    }

    private suspend fun loadFarmOwnerProfile(splashStartedAt: Long) {
        when (val profileResult = farmOwnerRepository.getMe()) {
            is AuthResult.Error -> {
                waitMinimumSplashTime(splashStartedAt)

                if (profileResult.statusCode == 401) {
                    authRepository.clearSession()
                    openWelcome()
                } else {
                    openError(profileResult.message)
                }
            }

            is AuthResult.Success -> {
                waitMinimumSplashTime(splashStartedAt)

                val profile = profileResult.data
                val farmOwnerId = profile.id
                val firstAccess = profile.firstAccess

                if (farmOwnerId == null || firstAccess == null) {
                    openError("A API retornou um perfil inválido.")
                    return
                }

                if (firstAccess) {
                    openFirstAccess(farmOwnerId)

                } else {
                    openMain()
                }
            }
        }
    }

    private fun handleRefreshError(error: AuthResult.Error) {
        when (error.statusCode) {
            401 -> {
                // O refresh token expirou ou foi revogado.
                // O AuthRepository já apagou a sessão nesse caso.
                openWelcome()
            }

            else -> {
                // Em erros de conexão ou servidor, o refresh token permanece salvo.
                // Assim, o usuário pode tentar novamente depois.
                openError(error.message)
            }
        }
    }

    private suspend fun waitMinimumSplashTime(splashStartedAt: Long) {
        val elapsed = SystemClock.elapsedRealtime() - splashStartedAt
        val remaining = 1_000L - elapsed

        if (remaining > 0L) {
            delay(remaining)
        }
    }

    private fun openWelcome() {
        navigate(Intent(this, WelcomeActivity::class.java))
    }

    private fun openMain() {
        navigate(Intent(this, MainActivity::class.java))
    }

    private fun openFirstAccess(farmOwnerId: Long) {
        navigate(Intent(this,FirstAccessActivity::class.java).apply {
            putExtra(FirstAccessActivity.EXTRA_ALREADY_AUTHENTICATED,true)
            putExtra(FirstAccessActivity.EXTRA_FARM_OWNER_ID,farmOwnerId)
        })
    }

    private fun openError(message: String) {
        navigate(Intent(this, ErrorActivity::class.java).apply {
            putExtra(ErrorActivity.EXTRA_MESSAGE, message)
        })
    }

    private fun navigate(intent: Intent) {
        if (navigationStarted) {
            return
        }

        if (isWaitingForPermission) {
            pendingNavigationIntent = intent
            return
        }

        navigationStarted = true
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }

    override fun onDestroy() {
        activityScope.cancel()
        super.onDestroy()
    }
}