package com.ouros.app.feature.auth.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ouros.app.data.auth.AuthRepository
import com.ouros.app.data.auth.AuthResult
import com.ouros.app.data.auth.LoginStartResponse
import com.ouros.app.data.farmowner.FarmOwnerRepository
import kotlinx.coroutines.launch

class LoginViewModel(
    private val authRepository:
    AuthRepository,
    private val farmOwnerRepository:
    FarmOwnerRepository
) : ViewModel() {

    interface Listener {
        fun onLoginLoadingChanged(isLoading: Boolean)
        fun onCodeLoadingChanged(isLoading: Boolean)

        fun onEmailError(message: String?)
        fun onPasswordError(message: String?)
        fun onCodeError(message: String?)

        fun onInvalidCredentials()
        fun onLoginError(message: String)

        fun onCodeRequired(
            maskedEmail: String,
            expiresInSeconds: Long
        )
        fun onCodeResent()

        fun onFirstAccessRequired(
            farmOwnerId: Long
        )

        fun onLoginSucceeded()
    }

    private var listener: Listener? = null

    private var email = ""
    private var password = ""
    private var code = ""

    private var maskedEmail = ""
    private var codeExpiresInSeconds = 0L

    private var isLoginLoading = false
    private var isCodeLoading = false

    fun setListener(
        listener: Listener
    ) {
        this.listener = listener

        listener.onLoginLoadingChanged(
            isLoginLoading
        )

        listener.onCodeLoadingChanged(
            isCodeLoading
        )
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

    fun currentMaskedEmail(): String {
        return maskedEmail
    }

    fun currentCodeExpiresInSeconds():
            Long {

        return codeExpiresInSeconds
    }

    fun onEmailChanged(value: String) {
        email = value

        listener?.onEmailError(null)
    }

    fun onPasswordChanged(value: String) {
        password = value

        listener?.onPasswordError(null)
    }

    fun onCodeChanged(value: String) {
        code = value
            .filter(Char::isDigit)
            .take(CODE_LENGTH)

        listener?.onCodeError(null)
    }

    fun onLoginClicked() {
        if (
            isLoginLoading ||
            !validateCredentials()
        ) {
            return
        }

        email = email
            .trim()
            .lowercase()

        viewModelScope.launch {
            setLoginLoading(true)

            try {
                when (
                    val result =
                        authRepository.startLogin(
                            email = email,
                            password = password
                        )
                ) {
                    is AuthResult.Error -> {
                        if (result.statusCode == 401) {
                            listener
                                ?.onInvalidCredentials()
                        } else {
                            listener?.onLoginError(
                                result.message
                            )
                        }
                    }

                    is AuthResult.Success -> {
                        processCodeChallenge(
                            result.data
                        )
                    }
                }
            } finally {
                setLoginLoading(false)
            }
        }
    }

    fun onCodeContinueClicked() {
        if (
            isCodeLoading ||
            !validateCode()
        ) {
            return
        }

        viewModelScope.launch {
            setCodeLoading(true)

            when (
                val result =
                    authRepository.verifyLogin(
                        code = code
                    )
            ) {
                is AuthResult.Error -> {
                    listener?.onCodeError(
                        result.message
                    )
                }

                is AuthResult.Success -> {
                    password = ""
                    code = ""

                    loadFarmOwnerProfile()
                }
            }

            setCodeLoading(false)
        }
    }

    fun onResendCodeClicked() {
        if (isCodeLoading) {
            return
        }

        if (
            email.isBlank() ||
            password.isBlank()
        ) {
            listener?.onCodeError(
                "Volte e informe novamente o email e a senha."
            )

            return
        }

        viewModelScope.launch {
            setCodeLoading(true)

            try {
                when (
                    val result =
                        authRepository.startLogin(
                            email = email,
                            password = password
                        )
                ) {
                    is AuthResult.Error -> {
                        listener?.onCodeError(
                            result.message
                        )
                    }

                    is AuthResult.Success -> {
                        code = ""

                        processCodeChallenge(
                            result.data
                        )

                        listener?.onCodeResent()
                    }
                }
            } finally {
                setCodeLoading(false)
            }
        }
    }

    fun clearPendingLogin() {
        password = ""
        code = ""
        maskedEmail = ""
        codeExpiresInSeconds = 0L

        authRepository.clearPendingLogin()
    }

    private fun processCodeChallenge(
        response: LoginStartResponse
    ) {
        maskedEmail = response.maskedEmail

        codeExpiresInSeconds =
            response.expiresIn

        listener?.onCodeRequired(
            maskedEmail = maskedEmail,
            expiresInSeconds =
                codeExpiresInSeconds
        )
    }

    private suspend fun loadFarmOwnerProfile() {
        when (
            val result =
                farmOwnerRepository.getMe()
        ) {
            is AuthResult.Error -> {
                authRepository.clearSession()

                listener?.onLoginError(
                    result.message
                )
            }

            is AuthResult.Success -> {
                val profile = result.data

                val id = profile.id

                val firstAccess =
                    profile.firstAccess

                if (
                    id == null ||
                    firstAccess == null
                ) {
                    authRepository.clearSession()

                    listener?.onLoginError(
                        "A API retornou um perfil inválido."
                    )

                    return
                }

                if (firstAccess) {
                    listener
                        ?.onFirstAccessRequired(
                            id
                        )
                } else {
                    listener
                        ?.onLoginSucceeded()
                }
            }
        }
    }

    private fun validateCredentials():
            Boolean {

        val normalizedEmail =
            email.trim()

        var emailError: String? = null
        var passwordError: String? = null

        if (normalizedEmail.isBlank()) {
            emailError =
                "Informe o email."
        } else if (
            !EMAIL_REGEX.matches(
                normalizedEmail
            )
        ) {
            emailError =
                "Informe um email válido."
        }

        if (password.isBlank()) {
            passwordError =
                "Informe a senha."
        }

        listener?.onEmailError(
            emailError
        )

        listener?.onPasswordError(
            passwordError
        )

        return emailError == null &&
                passwordError == null
    }

    private fun validateCode(): Boolean {
        val error = if (
            CODE_REGEX.matches(code)
        ) {
            null
        } else {
            "Informe o código com 6 dígitos."
        }

        listener?.onCodeError(error)

        return error == null
    }

    private fun setLoginLoading(
        value: Boolean
    ) {
        isLoginLoading = value

        listener?.onLoginLoadingChanged(
            value
        )
    }

    private fun setCodeLoading(
        value: Boolean
    ) {
        isCodeLoading = value

        listener?.onCodeLoadingChanged(
            value
        )
    }

    override fun onCleared() {
        authRepository.clearPendingLogin()
        listener = null

        super.onCleared()
    }

    private companion object {

        const val CODE_LENGTH = 6

        val EMAIL_REGEX =
            Regex(
                "^[A-Za-z0-9+_.-]+" +
                        "@[A-Za-z0-9.-]+$"
            )

        val CODE_REGEX =
            Regex("^\\d{$CODE_LENGTH}$")
    }
}