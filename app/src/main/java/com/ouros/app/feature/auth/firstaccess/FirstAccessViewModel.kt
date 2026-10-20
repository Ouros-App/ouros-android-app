package com.ouros.app.feature.auth.firstaccess

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ouros.app.data.auth.AuthRepository
import com.ouros.app.data.auth.AuthResult
import com.ouros.app.data.farmowner.FarmOwnerRepository
import com.ouros.app.data.farmowner.FarmOwnerResponse
import com.ouros.app.data.farmowner.FarmOwnerUpdateRequest
import kotlinx.coroutines.launch

class FirstAccessViewModel(
    private val authRepository: AuthRepository,
    private val farmOwnerRepository: FarmOwnerRepository
) : ViewModel() {

    interface Listener {

        fun onFirstAccessStepChanged(
            step: FirstAccessStep
        )

        fun onFirstAccessLoadingChanged(
            isLoading: Boolean
        )

        fun onCredentialsErrors(
            emailMessage: String?,
            passwordMessage: String?
        )

        fun onInvalidCredentials()

        fun onCodeError(message: String?)

        fun onCodeChallengeChanged(
            maskedEmail: String,
            expiresInSeconds: Long
        )

        fun onPersonalDataErrors(
            nameMessage: String?,
            cpfMessage: String?,
            telephoneMessage: String?
        )

        fun onNewPasswordError(message: String?)

        fun onLocationPermissionRequired()

        fun onFirstAccessMessage(message: String)

        fun onFirstAccessError(message: String)

        fun onNormalAccessDetected()

        fun onFirstAccessCompleted()

        fun onFirstAccessCloseRequested()
    }

    private var listener: Listener? = null

    private var initialized = false
    private var alreadyAuthenticated = false
    private var isLoading = false

    private var currentStep =
        FirstAccessStep.WELCOME

    private var farmOwnerId: Long? = null

    private var email = ""
    private var initialPassword = ""

    private var verificationCode = ""
    private var maskedEmail = ""
    private var codeExpiresInSeconds = 0L

    private var name = ""
    private var cpf = ""
    private var telephone = ""
    private var newPassword = ""

    private var waitingForLocation = false
    private var latitude: Double? = null
    private var longitude: Double? = null

    fun setListener(listener: Listener) {
        this.listener = listener

        if (initialized) {
            refreshUi()
        }
    }

    fun clearListener(listener: Listener) {
        if (this.listener === listener) {
            this.listener = null
        }
    }

    fun initialize(
        alreadyAuthenticated: Boolean = false,
        farmOwnerId: Long? = null
    ) {
        if (initialized) {
            return
        }

        initialized = true

        this.alreadyAuthenticated =
            alreadyAuthenticated

        this.farmOwnerId = farmOwnerId

        if (alreadyAuthenticated) {
            loadAuthenticatedProfile()
        } else {
            changeStep(FirstAccessStep.WELCOME)
        }
    }

    fun refreshUi() {
        listener?.onFirstAccessStepChanged(
            currentStep
        )

        listener?.onFirstAccessLoadingChanged(
            isLoading
        )

        if (
            currentStep == FirstAccessStep.CODE &&
            maskedEmail.isNotBlank()
        ) {
            listener?.onCodeChallengeChanged(
                maskedEmail = maskedEmail,
                expiresInSeconds =
                    codeExpiresInSeconds
            )
        }
    }

    fun currentEmail(): String = email

    fun currentVerificationCode(): String {
        return verificationCode
    }

    fun currentMaskedEmail(): String {
        return maskedEmail
    }

    fun currentName(): String = name

    fun currentDocument(): String = cpf

    fun currentTelephone(): String = telephone

    fun isLoading(): Boolean = isLoading

    fun onStartClicked() {
        if (isLoading) {
            return
        }

        changeStep(FirstAccessStep.CREDENTIALS)
    }

    fun onEmailChanged(value: String) {
        email = value

        listener?.onCredentialsErrors(
            emailMessage = null,
            passwordMessage = null
        )
    }

    fun onInitialPasswordChanged(value: String) {
        initialPassword = value

        listener?.onCredentialsErrors(
            emailMessage = null,
            passwordMessage = null
        )
    }

    fun onCredentialsContinueClicked() {
        if (
            isLoading ||
            !validateCredentials()
        ) {
            return
        }

        email = email
            .trim()
            .lowercase()

        viewModelScope.launch {
            setLoading(true)

            try {
                when (
                    val result =
                        authRepository.startLogin(
                            email = email,
                            password = initialPassword
                        )
                ) {
                    is AuthResult.Error -> {
                        if (result.statusCode == 401) {
                            listener
                                ?.onInvalidCredentials()
                        } else {
                            listener
                                ?.onFirstAccessError(
                                    result.message
                                )
                        }
                    }

                    is AuthResult.Success -> {
                        maskedEmail =
                            result.data.maskedEmail

                        codeExpiresInSeconds =
                            result.data.expiresIn

                        verificationCode = ""

                        changeStep(
                            FirstAccessStep.CODE
                        )

                        listener
                            ?.onCodeChallengeChanged(
                                maskedEmail =
                                    maskedEmail,
                                expiresInSeconds =
                                    codeExpiresInSeconds
                            )
                    }
                }
            } finally {
                setLoading(false)
            }
        }
    }

    fun onVerificationCodeChanged(
        value: String
    ) {
        verificationCode =
            value.filter(Char::isDigit)
                .take(6)

        listener?.onCodeError(null)
    }

    fun onCodeContinueClicked() {
        if (
            isLoading ||
            !validateVerificationCode()
        ) {
            return
        }

        viewModelScope.launch {
            setLoading(true)

            when (
                val verification =
                    authRepository.verifyLogin(
                        verificationCode
                    )
            ) {
                is AuthResult.Error -> {
                    listener?.onCodeError(
                        verification.message
                    )
                }

                is AuthResult.Success -> {
                    alreadyAuthenticated = true

                    initialPassword = ""
                    verificationCode = ""

                    loadProfileAfterAuthentication()
                }
            }

            setLoading(false)
        }
    }

    fun onResendCodeClicked() {
        if (isLoading) {
            return
        }

        if (
            email.isBlank() ||
            initialPassword.isBlank()
        ) {
            listener?.onCodeError(
                "Volte e informe novamente o email e a senha."
            )

            return
        }

        viewModelScope.launch {
            setLoading(true)

            try {
                when (
                    val result =
                        authRepository.startLogin(
                            email = email,
                            password = initialPassword
                        )
                ) {
                    is AuthResult.Error -> {
                        listener?.onCodeError(
                            result.message
                        )
                    }

                    is AuthResult.Success -> {
                        maskedEmail =
                            result.data.maskedEmail

                        codeExpiresInSeconds =
                            result.data.expiresIn

                        verificationCode = ""

                        listener
                            ?.onCodeChallengeChanged(
                                maskedEmail =
                                    maskedEmail,
                                expiresInSeconds =
                                    codeExpiresInSeconds
                            )

                        listener
                            ?.onFirstAccessMessage(
                                "Código reenviado com sucesso."
                            )
                    }
                }
            } finally {
                setLoading(false)
            }
        }
    }

    fun onNameChanged(value: String) {
        name = value

        listener?.onPersonalDataErrors(
            nameMessage = null,
            cpfMessage = null,
            telephoneMessage = null
        )
    }

    fun onCpfChanged(value: String) {
        cpf = value

        listener?.onPersonalDataErrors(
            nameMessage = null,
            cpfMessage = null,
            telephoneMessage = null
        )
    }

    fun onTelephoneChanged(value: String) {
        telephone = value

        listener?.onPersonalDataErrors(
            nameMessage = null,
            cpfMessage = null,
            telephoneMessage = null
        )
    }

    fun onPersonalDataContinueClicked() {
        if (
            isLoading ||
            !validatePersonalData()
        ) {
            return
        }

        name = name.trim()

        cpf = cpf.filter(
            Char::isDigit
        )

        telephone = telephone.filter(
            Char::isDigit
        )

        changeStep(
            FirstAccessStep.NEW_PASSWORD
        )
    }

    fun onNewPasswordChanged(value: String) {
        newPassword = value

        listener?.onNewPasswordError(null)
    }

    fun onFinishClicked() {
        if (
            isLoading ||
            waitingForLocation ||
            !validateNewPassword()
        ) {
            return
        }

        val profileId = farmOwnerId

        if (profileId == null) {
            listener?.onFirstAccessError(
                "Não foi possível identificar o usuário."
            )

            return
        }

        if (
            latitude == null ||
            longitude == null
        ) {
            waitingForLocation = true
            listener?.onLocationPermissionRequired()
            return
        }

        submitFirstAccess(profileId)
    }

    fun onLocationAvailable(
        latitude: Double,
        longitude: Double
    ) {
        waitingForLocation = false

        if (
            latitude !in -90.0..90.0 ||
            longitude !in -180.0..180.0
        ) {
            listener?.onFirstAccessError(
                "A localização obtida é inválida. Tente novamente."
            )
            return
        }

        this.latitude = latitude
        this.longitude = longitude

        onFinishClicked()
    }

    fun onLocationUnavailable(
        message: String
    ) {
        waitingForLocation = false
        listener?.onFirstAccessError(message)
    }

    fun cancelLocationRequest() {
        waitingForLocation = false
    }

    private fun submitFirstAccess(
        profileId: Long
    ) {
        viewModelScope.launch {
            setLoading(true)
            try {
                finishFirstAccess(profileId)
            } finally {
                setLoading(false)
            }
        }
    }

    fun onBackClicked() {
        if (isLoading) {
            return
        }

        when (currentStep) {
            FirstAccessStep.WELCOME -> {
                authRepository.clearPendingLogin()

                listener
                    ?.onFirstAccessCloseRequested()
            }

            FirstAccessStep.CREDENTIALS -> {
                authRepository.clearPendingLogin()

                initialPassword = ""

                changeStep(
                    FirstAccessStep.WELCOME
                )
            }

            FirstAccessStep.CODE -> {
                authRepository.clearPendingLogin()

                verificationCode = ""
                maskedEmail = ""
                initialPassword = ""

                changeStep(
                    FirstAccessStep.CREDENTIALS
                )
            }

            FirstAccessStep.PERSONAL_DATA -> {
                if (alreadyAuthenticated) {
                    listener
                        ?.onFirstAccessCloseRequested()
                } else {
                    authRepository.clearSession()

                    initialPassword = ""
                    verificationCode = ""

                    changeStep(
                        FirstAccessStep.CREDENTIALS
                    )
                }
            }

            FirstAccessStep.NEW_PASSWORD -> {
                changeStep(
                    FirstAccessStep.PERSONAL_DATA
                )
            }
        }
    }

    private fun loadAuthenticatedProfile() {
        viewModelScope.launch {
            setLoading(true)

            when (
                val result =
                    farmOwnerRepository.getMe()
            ) {
                is AuthResult.Error -> {
                    listener?.onFirstAccessError(
                        result.message
                    )
                }

                is AuthResult.Success -> {
                    processProfile(result.data)
                }
            }

            setLoading(false)
        }
    }

    private suspend fun loadProfileAfterAuthentication() {
        when (
            val result =
                farmOwnerRepository.getMe()
        ) {
            is AuthResult.Error -> {
                listener?.onFirstAccessError(
                    result.message
                )
            }

            is AuthResult.Success -> {
                processProfile(result.data)
            }
        }
    }

    private fun processProfile(
        profile: FarmOwnerResponse
    ) {
        val profileId = profile.id
        val firstAccess = profile.firstAccess

        if (
            profileId == null ||
            firstAccess == null
        ) {
            authRepository.clearSession()

            listener?.onFirstAccessError(
                "A API retornou um perfil inválido."
            )

            return
        }

        farmOwnerId = profileId

        if (!firstAccess) {
            listener?.onNormalAccessDetected()
            return
        }

        name = profile.name.orEmpty()

        cpf = profile
            .documentNumber
            .orEmpty()

        telephone = profile
            .telephone
            .orEmpty()

        changeStep(
            FirstAccessStep.PERSONAL_DATA
        )
    }

    private suspend fun finishFirstAccess(
        profileId: Long
    ) {
        val request = FarmOwnerUpdateRequest(
            name = name.trim(),

            documentNumber =
                cpf.filter(Char::isDigit),

            telephone =
                telephone.filter(Char::isDigit),

            password = newPassword,

            firstAccess = false,

            latitude = latitude,

            longitude = longitude
        )

        when (
            val result =
                farmOwnerRepository.update(
                    id = profileId,
                    request = request
                )
        ) {
            is AuthResult.Error -> {
                listener?.onFirstAccessError(
                    result.message
                )
            }

            is AuthResult.Success -> {
                if (
                    result.data.firstAccess == false
                ) {
                    newPassword = ""

                    listener
                        ?.onFirstAccessCompleted()
                } else {
                    listener?.onFirstAccessError(
                        "A API não confirmou a finalização do primeiro acesso."
                    )
                }
            }
        }
    }

    private fun validateCredentials(): Boolean {
        val normalizedEmail =
            email.trim()

        var emailMessage: String? = null
        var passwordMessage: String? = null

        if (normalizedEmail.isBlank()) {
            emailMessage =
                "Informe o email."
        } else if (
            !EMAIL_REGEX.matches(
                normalizedEmail
            )
        ) {
            emailMessage =
                "Informe um email válido."
        }

        if (initialPassword.isBlank()) {
            passwordMessage =
                "Informe a senha de acesso."
        }

        listener?.onCredentialsErrors(
            emailMessage = emailMessage,
            passwordMessage = passwordMessage
        )

        return emailMessage == null &&
                passwordMessage == null
    }

    private fun validateVerificationCode(): Boolean {
        val message = if (
            FirstAccessValidator
                .isValidVerificationCode(
                    verificationCode
                )
        ) {
            null
        } else {
            "Informe os 6 números do código."
        }

        listener?.onCodeError(message)

        return message == null
    }

    private fun validatePersonalData(): Boolean {
        val nameMessage = if (
            FirstAccessValidator
                .isValidName(name)
        ) {
            null
        } else {
            "Informe um nome válido."
        }

        val cpfMessage = if (
            FirstAccessValidator
                .isValidCpf(cpf)
        ) {
            null
        } else {
            "Informe um CPF válido."
        }

        val telephoneMessage = if (
            FirstAccessValidator
                .isValidTelephone(
                    telephone
                )
        ) {
            null
        } else {
            "Informe um telefone com 10 a 13 números."
        }

        listener?.onPersonalDataErrors(
            nameMessage = nameMessage,
            cpfMessage = cpfMessage,
            telephoneMessage =
                telephoneMessage
        )

        return nameMessage == null &&
                cpfMessage == null &&
                telephoneMessage == null
    }

    private fun validateNewPassword(): Boolean {
        val message = when {
            newPassword.isBlank() -> {
                "Informe a nova senha."
            }

            !FirstAccessValidator
                .isValidPassword(
                    newPassword
                ) -> {
                "Use de 8 a 20 caracteres, com letra maiúscula, minúscula, número e caractere especial."
            }

            else -> null
        }

        listener?.onNewPasswordError(message)

        return message == null
    }

    private fun changeStep(
        step: FirstAccessStep
    ) {
        currentStep = step

        listener
            ?.onFirstAccessStepChanged(step)
    }

    private fun setLoading(value: Boolean) {
        isLoading = value

        listener
            ?.onFirstAccessLoadingChanged(
                value
            )
    }

    override fun onCleared() {
        authRepository.clearPendingLogin()
        listener = null

        super.onCleared()
    }

    private companion object {

        val EMAIL_REGEX =
            Regex(
                "^[A-Za-z0-9+_.-]+" +
                        "@[A-Za-z0-9.-]+$"
            )
    }
}
