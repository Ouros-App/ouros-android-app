package com.ouros.app.feature.auth.firstaccess

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.snackbar.Snackbar
import com.ouros.app.R
import com.ouros.app.data.auth.AppSession
import com.ouros.app.data.auth.AuthNetwork
import com.ouros.app.data.auth.AuthRepository
import com.ouros.app.data.auth.SecureRefreshTokenStore
import com.ouros.app.data.farmowner.FarmOwnerNetwork
import com.ouros.app.data.farmowner.FarmOwnerRepository
import com.ouros.app.data.session.FarmSessionStore
import com.ouros.app.feature.main.MainActivity

class FirstAccessActivity : AppCompatActivity(), FirstAccessViewModel.Listener {

    private lateinit var viewModel: FirstAccessViewModel

    private val locationManager: LocationManager by lazy {
        getSystemService(LocationManager::class.java)
    }

    private val locationHandler = Handler(Looper.getMainLooper())
    private var locationCancellationSignal: CancellationSignal? = null
    private var locationRequestFinished = false

    private val locationTimeoutRunnable = Runnable {
        completeLocationRequest(lastKnownApproximateLocation())
    }

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted || hasApproximateLocationPermission()) {
                captureApproximateLocation()
            } else {
                viewModel.onLocationUnavailable(
                    "Autorize a localização aproximada para concluir o primeiro acesso."
                )
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_firstaccess)

        configureViewModel()
        configureBackNavigation()
        viewModel.setListener(this)
        initializeFlow()
    }

    private fun configureViewModel() {
        val refreshTokenStore = SecureRefreshTokenStore(applicationContext)

        val authRepository = AuthRepository(
            api = AuthNetwork.api,
            refreshTokenStore = refreshTokenStore,
            sessionManager = AppSession.sessionManager
        )

        val farmOwnerRepository = FarmOwnerRepository(
            api = FarmOwnerNetwork.api,
            sessionManager = AppSession.sessionManager,
            refreshSession = authRepository::refreshSession,
            clearSession = authRepository::clearSession
        )

        val factory = FirstAccessViewModelFactory(
            authRepository = authRepository,
            farmOwnerRepository = farmOwnerRepository
        )

        viewModel = ViewModelProvider(this, factory)[FirstAccessViewModel::class.java]
    }

    private fun configureBackNavigation() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() { viewModel.onBackClicked() }
            }
        )
    }

    private fun initializeFlow() {
        val alreadyAuthenticated = intent.getBooleanExtra(
            EXTRA_ALREADY_AUTHENTICATED,
            false
        )

        val farmOwnerId = intent
            .takeIf { it.hasExtra(EXTRA_FARM_OWNER_ID) }
            ?.getLongExtra(EXTRA_FARM_OWNER_ID, INVALID_ID)
            ?.takeIf { it > 0L }

        viewModel.initialize(
            alreadyAuthenticated = alreadyAuthenticated,
            farmOwnerId = farmOwnerId
        )
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshUi()
    }

    override fun onFirstAccessStepChanged(step: FirstAccessStep) {
        if (supportFragmentManager.isStateSaved) {
            return
        }

        val current = supportFragmentManager.findFragmentById(R.id.firstAccessContainer)
        if (current is FirstAccessPageFragment && current.tag == step.name) {
            return
        }

        supportFragmentManager.beginTransaction()
            .replace(
                R.id.firstAccessContainer,
                FirstAccessPageFragment.newInstance(step),
                step.name
            )
            .commit()
    }

    override fun onFirstAccessLoadingChanged(isLoading: Boolean) {
        currentPage()?.renderLoading(isLoading)
    }

    override fun onCredentialsErrors(
        emailMessage: String?,
        passwordMessage: String?
    ) {
        currentPage()?.renderCredentialsErrors(
            emailMessage = emailMessage,
            passwordMessage = passwordMessage
        )
    }

    override fun onInvalidCredentials() {
        Toast.makeText(this, "Email ou senha inválidos.", Toast.LENGTH_SHORT).show()
    }

    override fun onCodeError(message: String?) {
        currentPage()?.renderCodeError(message)
    }

    override fun onCodeChallengeChanged(
        maskedEmail: String,
        expiresInSeconds: Long
    ) {
        currentPage()?.renderCodeChallenge(
            maskedEmail = maskedEmail,
            expiresInSeconds = expiresInSeconds
        )
    }

    override fun onPersonalDataErrors(
        nameMessage: String?,
        cpfMessage: String?,
        telephoneMessage: String?
    ) {
        currentPage()?.renderPersonalDataErrors(
            nameMessage = nameMessage,
            cpfMessage = cpfMessage,
            telephoneMessage = telephoneMessage
        )
    }

    override fun onNewPasswordError(message: String?) {
        currentPage()?.renderNewPasswordError(message)
    }

    override fun onLocationPermissionRequired() {
        if (hasApproximateLocationPermission()) {
            captureApproximateLocation()
            return
        }

        locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    override fun onFirstAccessMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onFirstAccessError(message: String) {
        showMessage(message)
    }

    override fun onNormalAccessDetected() { openMain() }
    override fun onFirstAccessCompleted() { openMain() }
    override fun onFirstAccessCloseRequested() { finish() }

    private fun hasApproximateLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun captureApproximateLocation() {
        if (!hasApproximateLocationPermission()) {
            viewModel.onLocationUnavailable(
                "Autorize a localização aproximada para concluir o primeiro acesso."
            )
            return
        }

        val networkProviderEnabled = try {
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (_: Exception) {
            false
        }

        if (!networkProviderEnabled) {
            viewModel.onLocationUnavailable(
                "Ative a localização do aparelho e tente novamente."
            )
            return
        }

        cancelLocationRequest()
        locationRequestFinished = false

        val cancellationSignal = CancellationSignal()
        locationCancellationSignal = cancellationSignal

        locationHandler.postDelayed(locationTimeoutRunnable, LOCATION_TIMEOUT_MILLIS)

        try {
            locationManager.getCurrentLocation(
                LocationManager.NETWORK_PROVIDER,
                cancellationSignal,
                mainExecutor
            ) { location ->
                completeLocationRequest(location ?: lastKnownApproximateLocation())
            }
        } catch (_: SecurityException) {
            completeLocationRequest(null)
        } catch (_: IllegalArgumentException) {
            completeLocationRequest(null)
        }
    }

    @SuppressLint("MissingPermission")
    private fun lastKnownApproximateLocation(): Location? {
        if (!hasApproximateLocationPermission()) {
            return null
        }

        return try {
            locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (_: SecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun completeLocationRequest(location: Location?) {
        if (locationRequestFinished) {
            return
        }

        locationRequestFinished = true
        locationHandler.removeCallbacks(locationTimeoutRunnable)
        locationCancellationSignal?.cancel()
        locationCancellationSignal = null

        if (location == null) {
            viewModel.onLocationUnavailable(
                "Não foi possível obter sua localização. Tente novamente."
            )
            return
        }

        viewModel.onLocationAvailable(
            latitude = location.latitude,
            longitude = location.longitude
        )
    }

    private fun cancelLocationRequest() {
        locationRequestFinished = true
        locationHandler.removeCallbacks(locationTimeoutRunnable)
        locationCancellationSignal?.cancel()
        locationCancellationSignal = null
    }

    private fun currentPage(): FirstAccessPageFragment? {
        return supportFragmentManager
            .findFragmentById(R.id.firstAccessContainer) as? FirstAccessPageFragment
    }

    private fun showMessage(message: String) {
        Snackbar.make(
            findViewById(R.id.main),
            message,
            Snackbar.LENGTH_LONG
        ).show()
    }

    private fun openMain() {
        FarmSessionStore(applicationContext).clear()
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
    }

    override fun onDestroy() {
        cancelLocationRequest()
        viewModel.cancelLocationRequest()
        viewModel.clearListener(this)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_FARM_OWNER_ID = "farm_owner_id"
        const val EXTRA_ALREADY_AUTHENTICATED = "already_authenticated"
        private const val INVALID_ID = -1L
        private const val LOCATION_TIMEOUT_MILLIS = 10_000L
    }
}
