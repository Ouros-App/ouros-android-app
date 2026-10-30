package com.ouros.app.feature.auth.forgotpassword

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.ouros.app.R
import com.ouros.app.data.auth.AuthNetwork
import com.ouros.app.data.auth.AuthRepository
import com.ouros.app.data.auth.SecureRefreshTokenStore
import com.ouros.app.feature.auth.login.LoginActivity

class ForgotPasswordActivity :
    AppCompatActivity(),
    ForgotPasswordViewModel.Listener {

    private lateinit var viewModel:
            ForgotPasswordViewModel

    private var currentStep =
        ForgotPasswordStep.EMAIL

    private var codeFields:
            List<EditText> = emptyList()

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        configureViewModel()
        configureBackNavigation()

        viewModel.setListener(this)
    }

    private fun configureViewModel() {
        val repository =
            AuthRepository(
                api = AuthNetwork.api,
                refreshTokenStore =
                    SecureRefreshTokenStore(
                        applicationContext
                    )
            )

        val factory =
            ForgotPasswordViewModelFactory(
                authRepository = repository
            )

        viewModel = ViewModelProvider(
            this,
            factory
        )[ForgotPasswordViewModel::class.java]
    }

    private fun configureBackNavigation() {
        onBackPressedDispatcher.addCallback(
            this,
            object :
                OnBackPressedCallback(true) {

                override fun handleOnBackPressed() {
                    handleBack()
                }
            }
        )
    }

    private fun handleBack() {
        if (
            currentStep ==
            ForgotPasswordStep.EMAIL
        ) {
            finish()
        } else {
            viewModel.onBackToEmail()
        }
    }

    override fun onStepChanged(
        step: ForgotPasswordStep
    ) {
        currentStep = step

        when (step) {
            ForgotPasswordStep.EMAIL ->
                showEmailPage()

            ForgotPasswordStep.CODE ->
                showCodePage()

            ForgotPasswordStep.NEW_PASSWORD ->
                showNewPasswordPage()
        }
    }

    private fun showEmailPage() {
        codeFields = emptyList()

        setContentView(
            R.layout
                .activity_forgot_password_email
        )

        val inputEmail =
            findViewById<TextInputLayout>(
                R.id.inputEmail
            )

        val editEmail =
            findViewById<TextInputEditText>(
                R.id.editEmail
            )

        editEmail.setText(
            viewModel.currentEmail()
        )

        findViewById<View>(
            R.id.btnBack
        ).setOnClickListener {
            finish()
        }

        editEmail.doAfterTextChanged {
            viewModel.onEmailChanged(
                it.toString()
            )

            inputEmail.error = null
        }

        findViewById<View>(
            R.id.btnContinue
        ).setOnClickListener {
            viewModel
                .onRequestCodeClicked()
        }
    }

    private fun showCodePage() {
        setContentView(
            R.layout
                .activity_forgot_password_code
        )

        codeFields = listOf(
            findViewById(
                R.id.editCodeDigitOne
            ),
            findViewById(
                R.id.editCodeDigitTwo
            ),
            findViewById(
                R.id.editCodeDigitThree
            ),
            findViewById(
                R.id.editCodeDigitFour
            ),
            findViewById(
                R.id.editCodeDigitFive
            ),
            findViewById(
                R.id.editCodeDigitSix
            )
        )

        restoreVerificationCode()
        configureCodeFields()
        renderCodeError(null)

        findViewById<View>(
            R.id.btnBack
        ).setOnClickListener {
            viewModel.onBackToEmail()
        }

        findViewById<View>(
            R.id.btnContinue
        ).setOnClickListener {
            viewModel
                .onVerifyCodeClicked()
        }

        findViewById<View>(
            R.id.btnResendCode
        ).setOnClickListener {
            viewModel
                .onResendCodeClicked()
        }

        codeFields
            .firstOrNull {
                it.text.isNullOrEmpty()
            }
            ?.requestFocus()
    }

    private fun showNewPasswordPage() {
        codeFields = emptyList()

        setContentView(
            R.layout
                .activity_forgot_password_new_password
        )

        val editNewPassword =
            findViewById<TextInputEditText>(
                R.id.editNewPassword
            )

        val editConfirmPassword =
            findViewById<TextInputEditText>(
                R.id.editConfirmPassword
            )

        editNewPassword.setText(
            viewModel.currentNewPassword()
        )

        editConfirmPassword.setText(
            viewModel.currentConfirmPassword()
        )

        findViewById<View>(
            R.id.btnBack
        ).setOnClickListener {
            viewModel.onBackToEmail()
        }

        editNewPassword.doAfterTextChanged {
            viewModel.onNewPasswordChanged(
                it.toString()
            )
        }

        editConfirmPassword.doAfterTextChanged {
            viewModel
                .onConfirmPasswordChanged(
                    it.toString()
                )
        }

        findViewById<View>(
            R.id.btnFinish
        ).setOnClickListener {
            viewModel
                .onConfirmPasswordClicked()
        }
    }

    private fun restoreVerificationCode() {
        val savedCode =
            viewModel.currentCode()

        codeFields.forEachIndexed {
                index,
                field ->

            field.setText(
                savedCode
                    .getOrNull(index)
                    ?.toString()
                    .orEmpty()
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
                    numericValue !=
                    originalValue
                ) {
                    field.setText(
                        numericValue
                    )

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

    override fun onLoadingChanged(
        isLoading: Boolean
    ) {
        val enabled = !isLoading

        when (currentStep) {
            ForgotPasswordStep.EMAIL -> {
                setEnabled(
                    R.id.editEmail,
                    enabled
                )

                setEnabled(
                    R.id.btnContinue,
                    enabled
                )
            }

            ForgotPasswordStep.CODE -> {
                codeFields.forEach {
                    it.isEnabled = enabled
                }

                setEnabled(
                    R.id.btnContinue,
                    enabled
                )
            }

            ForgotPasswordStep.NEW_PASSWORD -> {
                setEnabled(
                    R.id.editNewPassword,
                    enabled
                )

                setEnabled(
                    R.id.editConfirmPassword,
                    enabled
                )

                setEnabled(
                    R.id.btnFinish,
                    enabled
                )
            }
        }
    }

    override fun onEmailError(
        message: String?
    ) {
        if (
            currentStep !=
            ForgotPasswordStep.EMAIL
        ) {
            return
        }

        findViewById<TextInputLayout>(
            R.id.inputEmail
        ).error = message
    }

    override fun onCodeError(
        message: String?
    ) {
        renderCodeError(message)
    }

    private fun renderCodeError(
        message: String?
    ) {
        if (
            currentStep !=
            ForgotPasswordStep.CODE
        ) {
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

    override fun onPasswordErrors(
        newPasswordMessage: String?,
        confirmationMessage: String?
    ) {
        if (
            currentStep !=
            ForgotPasswordStep.NEW_PASSWORD
        ) {
            return
        }

        findViewById<TextInputLayout>(
            R.id.inputNewPassword
        ).error = newPasswordMessage

        findViewById<TextInputLayout>(
            R.id.inputConfirmPassword
        ).error = confirmationMessage
    }

    override fun onChallengeChanged(
        maskedEmail: String,
        resendEnabled: Boolean
    ) {
        if (
            currentStep !=
            ForgotPasswordStep.CODE
        ) {
            return
        }

        findViewById<TextView>(
            R.id.textCodeDescription
        ).text =
            "Enviamos um código temporário para $maskedEmail."

        val resendButton =
            findViewById<View>(
                R.id.btnResendCode
            )

        resendButton.isEnabled =
            resendEnabled

        resendButton.alpha =
            if (resendEnabled) {
                1f
            } else {
                0.5f
            }
    }

    override fun onMessage(
        message: String
    ) {
        Toast.makeText(
            this,
            message,
            Toast.LENGTH_LONG
        ).show()
    }

    override fun onResetSucceeded(
        message: String
    ) {
        Toast.makeText(
            this,
            message,
            Toast.LENGTH_LONG
        ).show()

        startActivity(
            Intent(
                this,
                LoginActivity::class.java
            ).apply {
                flags =
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        )

        finish()
    }

    private fun setEnabled(
        viewId: Int,
        enabled: Boolean
    ) {
        findViewById<View>(viewId)
            ?.isEnabled = enabled
    }

    override fun onDestroy() {
        viewModel.clearListener(this)

        super.onDestroy()
    }
}
