package com.ouros.app.feature.auth.login

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.ouros.app.R
import com.ouros.app.core.util.applyBottomSystemBarMargin
import com.ouros.app.core.util.applyImeAndNavigationPadding
import com.ouros.app.data.auth.AppSession
import com.ouros.app.data.auth.AuthNetwork
import com.ouros.app.data.auth.AuthRepository
import com.ouros.app.data.auth.SecureRefreshTokenStore
import com.ouros.app.data.farmowner.FarmOwnerNetwork
import com.ouros.app.data.farmowner.FarmOwnerRepository
import com.ouros.app.data.session.FarmSessionStore
import com.ouros.app.feature.auth.firstaccess.FirstAccessActivity
import com.ouros.app.feature.auth.forgotpassword.ForgotPasswordActivity
import com.ouros.app.feature.error.ErrorActivity
import com.ouros.app.feature.main.MainActivity

class LoginActivity :
    AppCompatActivity(),
    LoginViewModel.Listener {

    private enum class Page {
        CREDENTIALS,
        CODE
    }

    private lateinit var viewModel:
            LoginViewModel

    private lateinit var inputEmail:
            TextInputLayout

    private lateinit var inputPassword:
            TextInputLayout

    private lateinit var editEmail:
            TextInputEditText

    private lateinit var editPassword:
            TextInputEditText

    private lateinit var btnLogin: View

    private var codeFields:
            List<EditText> = emptyList()

    private var currentPage =
        Page.CREDENTIALS

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        configureViewModel()
        configureBackNavigation()

        val restoredPage =
            savedInstanceState
                ?.getString(STATE_PAGE)
                ?.let(Page::valueOf)

        if (restoredPage == Page.CODE) {
            showCodePage()
        } else {
            showCredentialsPage()
        }
    }

    private fun configureViewModel() {
        val refreshTokenStore =
            SecureRefreshTokenStore(
                applicationContext
            )

        val authRepository =
            AuthRepository(
                api = AuthNetwork.api,
                refreshTokenStore =
                    refreshTokenStore
            )

        val farmOwnerRepository = FarmOwnerRepository(
            api = FarmOwnerNetwork.api,
            sessionManager = AppSession.sessionManager,
            refreshSession = authRepository::refreshSession,
            clearSession = authRepository::clearSession
        )

        val factory =
            LoginViewModelFactory(
                authRepository =
                    authRepository,
                farmOwnerRepository =
                    farmOwnerRepository
            )

        viewModel = ViewModelProvider(
            this,
            factory
        )[LoginViewModel::class.java]

        viewModel.setListener(this)
    }

    private fun configureBackNavigation() {
        onBackPressedDispatcher.addCallback(
            this,
            object :
                OnBackPressedCallback(true) {

                override fun handleOnBackPressed() {
                    if (
                        currentPage == Page.CODE
                    ) {
                        returnToCredentials()
                    } else {
                        finish()
                    }
                }
            }
        )
    }

    private fun showCredentialsPage() {
        currentPage = Page.CREDENTIALS
        codeFields = emptyList()

        setContentView(
            R.layout.activity_login
        )

        inputEmail = findViewById(
            R.id.inputEmail
        )

        inputPassword = findViewById(
            R.id.inputPassword
        )

        editEmail = findViewById(
            R.id.editEmail
        )

        editPassword = findViewById(
            R.id.editPassword
        )

        btnLogin = findViewById(
            R.id.btnContinue
        )

        editEmail.setText(
            viewModel.currentEmail()
        )

        findViewById<View>(
            R.id.btnBack
        ).setOnClickListener {
            finish()
        }

        findViewById<View>(
            R.id.btnForgotPasswd
        ).setOnClickListener {
            startActivity(
                Intent(
                    this,
                    ForgotPasswordActivity::class.java
                )
            )
        }

        editEmail.doAfterTextChanged {
            viewModel.onEmailChanged(
                it.toString()
            )
        }

        editPassword.doAfterTextChanged {
            viewModel.onPasswordChanged(
                it.toString()
            )
        }

        btnLogin.setOnClickListener {
            viewModel.onLoginClicked()
        }

        findViewById<View>(
            R.id.loginScrollContent
        ).applyImeAndNavigationPadding()

        findViewById<View>(
            R.id.btnForgotPasswd
        ).applyBottomSystemBarMargin()

        renderLoginLoading(false)
    }

    private fun showCodePage() {
        currentPage = Page.CODE

        setContentView(
            R.layout.activity_login_code
        )

        codeFields = listOf(
            findViewById(R.id.editCodeDigitOne),
            findViewById(R.id.editCodeDigitTwo),
            findViewById(R.id.editCodeDigitThree),
            findViewById(R.id.editCodeDigitFour),
            findViewById(R.id.editCodeDigitFive),
            findViewById(R.id.editCodeDigitSix)
        )

        restoreVerificationCode()
        configureCodeFields()

        renderCodeDescription(
            maskedEmail =
                viewModel.currentMaskedEmail(),
            expiresInSeconds =
                viewModel
                    .currentCodeExpiresInSeconds()
        )

        renderCodeError(null)

        findViewById<View>(
            R.id.btnBack
        ).setOnClickListener {
            returnToCredentials()
        }

        findViewById<View>(
            R.id.btnContinue
        ).setOnClickListener {
            viewModel
                .onCodeContinueClicked()
        }

        findViewById<View>(
            R.id.btnResendCode
        ).setOnClickListener {
            viewModel
                .onResendCodeClicked()
        }

        findViewById<View>(
            R.id.loginCodeScrollContent
        ).applyImeAndNavigationPadding()

        findViewById<View>(
            R.id.btnResendCode
        ).applyBottomSystemBarMargin()

        codeFields
            .firstOrNull {
                it.text.isNullOrEmpty()
            }
            ?.post {
                val firstEmpty =
                    codeFields.firstOrNull {
                        it.text.isNullOrEmpty()
                    }

                firstEmpty?.requestFocus()

                firstEmpty?.let {
                    getSystemService<
                            InputMethodManager
                            >()?.showSoftInput(
                        it,
                        InputMethodManager
                            .SHOW_IMPLICIT
                    )
                }
            }
    }

    private fun restoreVerificationCode() {
        val savedCode =
            viewModel.currentCode()

        codeFields.forEachIndexed {
                index,
                field ->

            val digit =
                savedCode.getOrNull(index)

            field.setText(
                digit?.toString().orEmpty()
            )
        }
    }

    private fun configureCodeFields() {
        codeFields.forEachIndexed {
                index,
                field ->

            field.doAfterTextChanged {
                val originalValue =
                    field.text
                        ?.toString()
                        .orEmpty()

                val numericValue =
                    originalValue
                        .filter(Char::isDigit)
                        .take(1)

                if (
                    numericValue != originalValue
                ) {
                    field.setText(numericValue)

                    field.setSelection(
                        numericValue.length
                    )

                    return@doAfterTextChanged
                }

                viewModel.onCodeChanged(
                    currentCodeFromFields()
                )

                if (
                    numericValue.length == 1 &&
                    index <
                    codeFields.lastIndex
                ) {
                    codeFields[index + 1]
                        .requestFocus()
                }
            }

            field.setOnKeyListener {
                    _,
                    keyCode,
                    event ->

                val deleting =
                    keyCode ==
                            KeyEvent.KEYCODE_DEL &&
                            event.action ==
                            KeyEvent.ACTION_DOWN

                if (
                    deleting &&
                    field.text.isNullOrEmpty() &&
                    index > 0
                ) {
                    val previous =
                        codeFields[index - 1]

                    previous.requestFocus()
                    previous.text?.clear()

                    true
                } else {
                    false
                }
            }
        }
    }

    private fun currentCodeFromFields():
            String {
        return codeFields.joinToString(
            separator = ""
        ) {
            it.text
                ?.toString()
                .orEmpty()
        }
    }

    private fun returnToCredentials() {
        viewModel.clearPendingLogin()
        showCredentialsPage()
    }

    private fun renderLoginLoading(
        isLoading: Boolean
    ) {
        if (
            !this::btnLogin.isInitialized
        ) {
            return
        }

        val enabled = !isLoading

        btnLogin.isEnabled = enabled
        editEmail.isEnabled = enabled
        editPassword.isEnabled = enabled
    }

    private fun renderCodeDescription(
        maskedEmail: String,
        expiresInSeconds: Long
    ) {
        if (
            currentPage != Page.CODE ||
            maskedEmail.isBlank()
        ) {
            return
        }

        val description =
            if (expiresInSeconds > 0L) {
                val minutes =
                    (expiresInSeconds + 59L) /
                            60L

                "Enviamos um código temporário para $maskedEmail. Ele expira em $minutes minuto(s)."
            } else {
                "Enviamos um código temporário para $maskedEmail."
            }

        findViewById<TextView>(
            R.id.textCodeDescription
        ).text = description
    }

    private fun renderCodeError(
        message: String?
    ) {
        if (currentPage != Page.CODE) {
            return
        }

        val errorView =
            findViewById<TextView>(
                R.id.textCodeError
            )

        errorView.text = message

        errorView.isVisible =
            !message.isNullOrBlank()
    }

    override fun onLoginLoadingChanged(
        isLoading: Boolean
    ) {
        if (
            currentPage ==
            Page.CREDENTIALS
        ) {
            renderLoginLoading(isLoading)
        }
    }

    override fun onCodeLoadingChanged(
        isLoading: Boolean
    ) {
        if (currentPage != Page.CODE) {
            return
        }

        val enabled = !isLoading

        codeFields.forEach {
            it.isEnabled = enabled
        }

        findViewById<View>(
            R.id.btnContinue
        ).isEnabled = enabled

        findViewById<View>(
            R.id.btnResendCode
        ).isEnabled = enabled
    }

    override fun onEmailError(
        message: String?
    ) {
        if (
            currentPage ==
            Page.CREDENTIALS
        ) {
            inputEmail.error = message
        }
    }

    override fun onPasswordError(
        message: String?
    ) {
        if (
            currentPage ==
            Page.CREDENTIALS
        ) {
            inputPassword.error = message
        }
    }

    override fun onInvalidCredentials() {
        Toast.makeText(
            this,
            "Email ou senha inválidos.",
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onCodeError(
        message: String?
    ) {
        renderCodeError(message)
    }

    override fun onLoginError(
        message: String
    ) {
        startActivity(
            Intent(
                this,
                ErrorActivity::class.java
            ).apply {
                putExtra(
                    ErrorActivity.EXTRA_MESSAGE,
                    message
                )
            }
        )
    }

    override fun onCodeRequired(
        maskedEmail: String,
        expiresInSeconds: Long
    ) {
        showCodePage()

        renderCodeDescription(
            maskedEmail = maskedEmail,
            expiresInSeconds =
                expiresInSeconds
        )
    }

    override fun onCodeResent() {
        Toast.makeText(
            this,
            "Código reenviado com sucesso.",
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onFirstAccessRequired(
        farmOwnerId: Long
    ) {
        startActivity(
            Intent(
                this,
                FirstAccessActivity::class.java
            ).apply {
                putExtra(
                    FirstAccessActivity
                        .EXTRA_FARM_OWNER_ID,
                    farmOwnerId
                )

                putExtra(
                    FirstAccessActivity
                        .EXTRA_ALREADY_AUTHENTICATED,
                    true
                )
            }
        )
    }

    override fun onLoginSucceeded() {
        openMain()
    }

    private fun openMain() {
        FarmSessionStore(applicationContext).clear()
        startActivity(
            Intent(
                this,
                MainActivity::class.java
            ).apply {
                flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
    }

    override fun onSaveInstanceState(
        outState: Bundle
    ) {
        outState.putString(
            STATE_PAGE,
            currentPage.name
        )

        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        viewModel.clearListener(this)

        super.onDestroy()
    }

    companion object {

        private const val STATE_PAGE =
            "login_page"
    }
}