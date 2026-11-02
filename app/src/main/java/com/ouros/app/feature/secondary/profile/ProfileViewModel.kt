package com.ouros.app.feature.secondary.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ouros.app.data.auth.AuthResult
import com.ouros.app.data.cloudinary.CloudinaryRepository
import com.ouros.app.data.farmowner.FarmOwnerRepository
import com.ouros.app.data.farmowner.FarmOwnerResponse
import com.ouros.app.data.farmowner.FarmOwnerUpdateRequest
import com.ouros.app.data.local.OfflineSyncRegistry
import java.io.File
import kotlinx.coroutines.launch

sealed class ProfilePhotoChange {
    object KeepCurrent : ProfilePhotoChange()
    data class UploadNew(val file: File) : ProfilePhotoChange()
    object Remove : ProfilePhotoChange()
}

/**
 * Estado e ações de apresentação de profile. Executa operações assíncronas e publica resultados para a interface, mantendo o estado fora dos componentes visuais.
 */
class ProfileViewModel(
    private val farmOwnerRepository: FarmOwnerRepository,
    private val cloudinaryRepository: CloudinaryRepository = CloudinaryRepository(),
    private val offlineCoordinator: OfflineProfileSaver? = null,
    private val isOnline: () -> Boolean = OfflineSyncRegistry::isOnline
) : ViewModel() {

    interface Listener {

        fun onProfileLoadingChanged(isLoading: Boolean)

        fun onProfileLoaded(profile: FarmOwnerResponse)

        fun onProfileErrors(
            nameMessage: String?,
            emailMessage: String?,
            telephoneMessage: String?
        )

        fun onProfileSaved(profile: FarmOwnerResponse)

        fun onProfileSavedOffline(profile: FarmOwnerResponse) = onProfileSaved(profile)

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
        telephone: String,
        photoChange: ProfilePhotoChange = ProfilePhotoChange.KeepCurrent
    ) {
        if (isLoading) {
            return
        }

        if (
            !validate(
                name = name,
                email = email,
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

        viewModelScope.launch {
            setLoading(true)

            try {
                val request = FarmOwnerUpdateRequest(
                    name = name.trim(),
                    email = email.trim().lowercase(),
                    telephone = telephone.filter(Char::isDigit),
                    photoUrl = when (photoChange) {
                        is ProfilePhotoChange.Remove -> ""
                        else -> profile?.photoUrl
                    }
                )

                if (!isOnline()) {
                    val coordinator = offlineCoordinator
                    if (coordinator == null) {
                        listener?.onProfileError("Não foi possível salvar o perfil neste aparelho.")
                        return@launch
                    }
                    val savedProfile = runCatching {
                        coordinator.savePending(
                            ownerId = farmOwnerId,
                            currentProfile = profile!!,
                            request = request,
                            photoChange = photoChange
                        )
                    }.getOrElse { error ->
                        listener?.onProfileError(
                            error.message ?: "Não foi possível salvar o perfil neste aparelho."
                        )
                        return@launch
                    }
                    profile = savedProfile
                    listener?.onProfileSavedOffline(savedProfile)
                    return@launch
                }

                var resolvedPhotoUrl: String? = profile?.photoUrl

                when (photoChange) {
                    is ProfilePhotoChange.UploadNew -> {
                        val uploadResult = cloudinaryRepository.uploadImage(photoChange.file)
                        if (uploadResult.isFailure) {
                            val error = uploadResult.exceptionOrNull()
                            listener?.onProfileError(
                                error?.message ?: "Falha ao enviar a foto de perfil. As alterações não foram salvas."
                            )
                            return@launch
                        }
                        resolvedPhotoUrl = uploadResult.getOrNull()
                    }

                    is ProfilePhotoChange.Remove -> {
                        resolvedPhotoUrl = ""
                    }

                    is ProfilePhotoChange.KeepCurrent -> {
                        // Mantém resolvedPhotoUrl como profile?.photoUrl
                    }
                }

                val onlineRequest = request.copy(photoUrl = resolvedPhotoUrl)

                when (
                    val result =
                        farmOwnerRepository.update(
                            id = farmOwnerId,
                            request = onlineRequest
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
            telephoneMessage = telephoneMessage
        )

        return nameMessage == null &&
                emailMessage == null &&
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
