package com.ouros.app.feature.secondary.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ouros.app.data.auth.AuthResult
import com.ouros.app.data.farmowner.FarmOwnerRepository
import com.ouros.app.data.farmowner.FarmOwnerResponse
import com.ouros.app.data.farmowner.FarmOwnerUpdateRequest
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val farmOwnerRepository: FarmOwnerRepository
) : ViewModel() {

    interface Listener {

        fun onProfileLoadingChanged(isLoading: Boolean)

        fun onProfileLoaded(profile: FarmOwnerResponse)

        fun onProfileErrors(
            nameMessage: String?,
            emailMessage: String?,
            passwordMessage: String?,
            cpfMessage: String?,
            telephoneMessage: String?
        )

        fun onProfileSaved(profile: FarmOwnerResponse)

        fun onProfileError(message: String)

        fun onProfileSessionExpired()
    }

    private var listener: Listener? = null
    private var profile: FarmOwnerResponse? = null
    private var isLoading = false
    private var hasLoaded = false

    fun setListener(listener: Listener) {
        this.listener = listener

        listener.onProfileLoadingChanged(isLoading)
        profile?.let(listener::onProfileLoaded)
    }

    fun clearListener(listener: Listener) {
        if (this.listener === listener) {
            this.listener = null
        }
    }

    fun loadProfile() {
        if (isLoading) {
            return
        }

        if (hasLoaded) {
            profile?.let {
                listener?.onProfileLoaded(it)
            }
            return
        }

        viewModelScope.launch {
            setLoading(true)

            try {
                when (
                    val result =
                        farmOwnerRepository.getMe()
                ) {
                    is AuthResult.Success -> {
                        profile = result.data
                        hasLoaded = true

                        listener?.onProfileLoaded(
                            result.data
                        )
                    }

                    is AuthResult.Error -> {
                        handleError(
                            message = result.message,
                            statusCode = result.statusCode
                        )
                    }
                }
            } finally {
                setLoading(false)
            }
        }
    }

    fun saveProfile(
        name: String,
        email: String,
        password: String,
        cpf: String,
        telephone: String
    ) {
        if (isLoading) {
            return
        }

        if (
            !validate(
                name = name,
                email = email,
                password = password,
                cpf = cpf,
                telephone = telephone
            )
        ) {
            return
        }

        val farmOwnerId = profile?.id

        if (farmOwnerId == null) {
            listener?.onProfileError(
                "Não foi possível identificar o usuário."
            )
            return
        }

        val request = FarmOwnerUpdateRequest(
            name = name.trim(),
            email = email.trim().lowercase(),
            documentNumber =
                cpf.filter(Char::isDigit),
            telephone =
                telephone.filter(Char::isDigit),
            password = password.takeIf {
                it.isNotBlank()
            }
        )

        viewModelScope.launch {
            setLoading(true)

            try {
                when (
                    val result =
                        farmOwnerRepository.update(
                            id = farmOwnerId,
                            request = request
                        )
                ) {
                    is AuthResult.Success -> {
                        profile = result.data

                        listener?.onProfileSaved(
                            result.data
                        )
                    }

                    is AuthResult.Error -> {
                        handleError(
                            message = result.message,
                            statusCode = result.statusCode
                        )
                    }
                }
            } finally {
                setLoading(false)
            }
        }
    }

    private fun validate(
        name: String,
        email: String,
        password: String,
        cpf: String,
        telephone: String
    ): Boolean {
        val nameMessage = if (
            ProfileValidator.isValidName(name)
        ) {
            null
        } else {
            "Informe um nome válido."
        }

        val emailMessage = if (
            ProfileValidator.isValidEmail(email)
        ) {
            null
        } else {
            "Informe um e-mail válido."
        }

        val passwordMessage = when {
            password.isBlank() -> null

            !ProfileValidator.isValidPassword(password) -> {
                "Use de 8 a 20 caracteres, com letra maiúscula, minúscula, número e caractere especial."
            }

            else -> null
        }

        val cpfMessage = if (
            ProfileValidator.isValidCpf(cpf)
        ) {
            null
        } else {
            "Informe um CPF válido."
        }

        val telephoneMessage = if (
            ProfileValidator.isValidTelephone(telephone)
        ) {
            null
        } else {
            "Informe um telefone com 10 a 13 números."
        }

        listener?.onProfileErrors(
            nameMessage = nameMessage,
            emailMessage = emailMessage,
            passwordMessage = passwordMessage,
            cpfMessage = cpfMessage,
            telephoneMessage = telephoneMessage
        )

        return nameMessage == null &&
                emailMessage == null &&
                passwordMessage == null &&
                cpfMessage == null &&
                telephoneMessage == null
    }

    private fun handleError(
        message: String,
        statusCode: Int?
    ) {
        if (statusCode == 401) {
            listener?.onProfileSessionExpired()
        } else {
            listener?.onProfileError(message)
        }
    }

    private fun setLoading(value: Boolean) {
        isLoading = value
        listener?.onProfileLoadingChanged(value)
    }
}