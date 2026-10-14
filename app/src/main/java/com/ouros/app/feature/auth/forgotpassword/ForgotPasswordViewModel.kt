package com.ouros.app.feature.auth.forgotpassword

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ouros.app.data.auth.AuthRepository
import com.ouros.app.data.auth.AuthResult
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ForgotPasswordViewModel(private val authRepository:AuthRepository) : ViewModel() {

    interface Listener {

        fun onStepChanged(step: ForgotPasswordStep)
        fun onLoadingChanged(isLoading: Boolean)
        fun onEmailError(message: String?)

        fun onCodeError(
            message: String?
        )

        fun onPasswordErrors(
            newPasswordMessage: String?,
            confirmationMessage: String?
        )

        fun onChallengeChanged(
            maskedEmail: String,
            resendEnabled: Boolean
        )

        fun onMessage(
            message: String
        )

        fun onResetSucceeded(
            message: String
        )
    }

    private var listener: Listener? = null

    private var step =
        ForgotPasswordStep.EMAIL

    private var email = ""
    private var code = ""
    private var newPassword = ""
    private var confirmPassword = ""

    private var challengeId = ""
    private var maskedEmail = ""
    private var challengeExpiresAtMillis = 0L

    private var resetToken = ""
    private var resetTokenExpiresAtMillis = 0L

    private var isLoading = false

    private var countdownJob:
            Job? = null

    fun setListener(
        listener: Listener
    ) {
        this.listener = listener

        listener.onStepChanged(step)

        listener.onLoadingChanged(
            isLoading
        )

        if (
            step ==
            ForgotPasswordStep.CODE
        ) {
            notifyChallengeChanged()
        }
    }

    fun clearListener(
        listener: Listener
    ) {
        if (this.listener === listener) {
            this.listener = null
        }
    }

    fun currentEmail(): String {
        return email
    }

    fun currentCode(): String {
        return code
    }

    fun currentNewPassword(): String {
        return newPassword
    }

    fun currentConfirmPassword(): String {
        return confirmPassword
    }

    fun onEmailChanged(
        value: String
    ) {
        email = value

        listener?.onEmailError(null)
    }

    fun onCodeChanged(
        value: String
    ) {
        code = value
            .filter(Char::isDigit)
            .take(CODE_LENGTH)

        listener?.onCodeError(null)
    }

    fun onNewPasswordChanged(
        value: String
    ) {
        newPassword = value

        listener?.onPasswordErrors(
            newPasswordMessage = null,
            confirmationMessage = null
        )
    }

    fun onConfirmPasswordChanged(
        value: String
    ) {
        confirmPassword = value

        listener?.onPasswordErrors(
            newPasswordMessage = null,
            confirmationMessage = null
        )
    }

    fun onRequestCodeClicked() {
        if (
            isLoading ||
            !validateEmail()
        ) {
            return
        }

        email = email
            .trim()
            .lowercase()

        requestPasswordReset(
            isResend = false
        )
    }

    fun onResendCodeClicked() {
        if (isLoading) {
            return
        }

        if (
            remainingChallengeSeconds() >
            0L
        ) {
            return
        }

        requestPasswordReset(
            isResend = true
        )
    }

    fun onVerifyCodeClicked() {
        if (
            isLoading ||
            !validateCode()
        ) {
            return
        }

        if (
            challengeId.isBlank() ||
            remainingChallengeSeconds() <= 0L
        ) {
            returnToEmail(
                EXPIRED_CODE_MESSAGE
            )

            return
        }

        viewModelScope.launch {
            setLoading(true)

            try {
                when (
                    val result =
                        authRepository
                            .verifyPasswordReset(
                                challengeId =
                                    challengeId,
                                email = email,
                                code = code
                            )
                ) {
                    is AuthResult.Success -> {
                        resetToken =
                            result.data.resetToken

                        resetTokenExpiresAtMillis =
                            System.currentTimeMillis() +
                                    result.data
                                        .expiresIn *
                                    1_000L

                        code = ""
                        challengeId = ""
                        maskedEmail = ""
                        challengeExpiresAtMillis = 0L

                        countdownJob?.cancel()
                        countdownJob = null

                        step =
                            ForgotPasswordStep
                                .NEW_PASSWORD

                        listener?.onStepChanged(
                            step
                        )
                    }

                    is AuthResult.Error -> {
                        handleCodeError(
                            result
                        )
                    }
                }
            } finally {
                setLoading(false)
            }
        }
    }

    fun onConfirmPasswordClicked() {
        if (
            isLoading ||
            !validatePasswords()
        ) {
            return
        }

        if (
            resetToken.isBlank() ||
            System.currentTimeMillis() >
            resetTokenExpiresAtMillis
        ) {
            returnToEmail(
                "Sessão de redefinição expirada. Solicite um novo código."
            )

            return
        }

        viewModelScope.launch {
            setLoading(true)

            try {
                when (
                    val result =
                        authRepository
                            .confirmPasswordReset(
                                resetToken =
                                    resetToken,
                                newPassword =
                                    newPassword
                            )
                ) {
                    is AuthResult.Success -> {
                        clearAllState()

                        listener
                            ?.onResetSucceeded(
                                SUCCESS_MESSAGE
                            )
                    }

                    is AuthResult.Error -> {
                        handleConfirmationError(
                            result
                        )
                    }
                }
            } finally {
                setLoading(false)
            }
        }
    }

    fun onBackToEmail() {
        clearTemporaryState()

        step = ForgotPasswordStep.EMAIL

        listener?.onStepChanged(step)
    }

    private fun requestPasswordReset(
        isResend: Boolean
    ) {
        viewModelScope.launch {
            setLoading(true)

            try {
                when (
                    val result =
                        authRepository
                            .startPasswordReset(
                                email = email
                            )
                ) {
                    is AuthResult.Success -> {
                        challengeId =
                            result.data.challengeId

                        maskedEmail =
                            result.data.maskedEmail

                        challengeExpiresAtMillis =
                            System.currentTimeMillis() +
                                    result.data
                                        .expiresIn *
                                    1_000L

                        code = ""
                        resetToken = ""
                        resetTokenExpiresAtMillis = 0L

                        step =
                            ForgotPasswordStep.CODE

                        listener?.onStepChanged(
                            step
                        )

                        startCountdown()

                        if (isResend) {
                            listener?.onMessage(
                                "Código reenviado com sucesso."
                            )
                        }
                    }

                    is AuthResult.Error -> {
                        if (isResend) {
                            listener?.onCodeError(
                                result.message
                            )
                        } else {
                            listener?.onEmailError(
                                result.message
                            )
                        }
                    }
                }
            } finally {
                setLoading(false)
            }
        }
    }

    private fun handleCodeError(
        error: AuthResult.Error
    ) {
        val expiredOrExceeded =
            error.statusCode == 429 ||
                    messageIndicatesExpiredOrExceeded(
                        error.message
                    )

        if (expiredOrExceeded) {
            returnToEmail(
                EXPIRED_CODE_MESSAGE
            )
        } else if (
            error.statusCode == 400
        ) {
            listener?.onCodeError(
                INVALID_CODE_MESSAGE
            )
        } else {
            listener?.onMessage(
                error.message
            )
        }
    }

    private fun handleConfirmationError(
        error: AuthResult.Error
    ) {
        val normalized =
            error.message.lowercase(
                Locale.ROOT
            )

        val invalidToken =
            error.statusCode == 400 &&
                    (
                            normalized.contains(
                                "token"
                            ) ||
                                    normalized.contains(
                                        "expir"
                                    ) ||
                                    normalized.contains(
                                        "utilizado"
                                    )
                            )

        if (invalidToken) {
            returnToEmail(
                "Sessão de redefinição expirada. Solicite um novo código."
            )
        } else {
            listener?.onMessage(
                error.message
            )
        }
    }

    private fun validateEmail(): Boolean {
        val normalizedEmail =
            email.trim()

        val error = when {
            normalizedEmail.isBlank() ->
                "Informe o email."

            !ForgotPasswordValidator
                .isValidEmail(
                    normalizedEmail
                ) ->
                "Informe um email válido."

            else -> null
        }

        listener?.onEmailError(error)

        return error == null
    }

    private fun validateCode(): Boolean {
        val error =
            if (
                ForgotPasswordValidator
                    .isValidCode(code)
            ) {
                null
            } else {
                "Informe o código com 6 dígitos."
            }

        listener?.onCodeError(error)

        return error == null
    }

    private fun validatePasswords():
            Boolean {
        val passwordError = when {
            newPassword.isBlank() ->
                "Informe a nova senha."

            !ForgotPasswordValidator
                .isValidPassword(
                    newPassword
                ) ->
                "A senha deve ter entre 8 e 20 caracteres, com letra maiúscula, letra minúscula, número e caractere especial."

            else -> null
        }

        val confirmationError = when {
            confirmPassword.isBlank() ->
                "Confirme a nova senha."

            newPassword != confirmPassword ->
                "As senhas não coincidem."

            else -> null
        }

        listener?.onPasswordErrors(
            newPasswordMessage =
                passwordError,
            confirmationMessage =
                confirmationError
        )

        return passwordError == null &&
                confirmationError == null
    }

    private fun startCountdown() {
        countdownJob?.cancel()

        countdownJob =
            viewModelScope.launch {
                while (true) {
                    notifyChallengeChanged()

                    if (
                        remainingChallengeSeconds() <=
                        0L
                    ) {
                        break
                    }

                    delay(1_000L)
                }
            }
    }

    private fun notifyChallengeChanged() {
        listener?.onChallengeChanged(
            maskedEmail = maskedEmail,
            resendEnabled =
                remainingChallengeSeconds() <=
                        0L
        )
    }

    private fun remainingChallengeSeconds():
            Long {
        val difference =
            challengeExpiresAtMillis -
                    System.currentTimeMillis()

        if (difference <= 0L) {
            return 0L
        }

        return (
                difference + 999L
                ) / 1_000L
    }

    private fun returnToEmail(
        message: String
    ) {
        clearTemporaryState()

        step = ForgotPasswordStep.EMAIL

        listener?.onStepChanged(step)

        listener?.onMessage(message)
    }

    private fun clearTemporaryState() {
        countdownJob?.cancel()
        countdownJob = null

        code = ""
        newPassword = ""
        confirmPassword = ""

        challengeId = ""
        maskedEmail = ""
        challengeExpiresAtMillis = 0L

        resetToken = ""
        resetTokenExpiresAtMillis = 0L
    }

    private fun clearAllState() {
        clearTemporaryState()

        email = ""
    }

    private fun setLoading(
        value: Boolean
    ) {
        isLoading = value

        listener?.onLoadingChanged(
            value
        )
    }

    private fun messageIndicatesExpiredOrExceeded(
        message: String
    ): Boolean {
        val normalized =
            message.lowercase(
                Locale.ROOT
            )

        if (
            normalized.contains("tentativ") ||
            normalized.contains("limite")
        ) {
            return true
        }

        val saysExpired =
            normalized.contains("expir")

        val genericInvalidMessage =
            normalized.contains("inválid") ||
                    normalized.contains("invalid") ||
                    normalized.contains("incorret")

        return saysExpired &&
                !genericInvalidMessage
    }

    override fun onCleared() {
        clearAllState()
        listener = null

        super.onCleared()
    }

    private companion object {

        const val CODE_LENGTH = 6

        const val INVALID_CODE_MESSAGE =
            "Código inválido. Verifique o código recebido no e-mail."

        const val EXPIRED_CODE_MESSAGE =
            "Código expirado ou limite de tentativas excedido. Solicite um novo código."

        const val SUCCESS_MESSAGE =
            "Senha redefinida com sucesso! Faça login com a sua nova senha."
    }
}