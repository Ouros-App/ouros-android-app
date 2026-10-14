package com.ouros.app.feature.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ouros.app.data.auth.AuthResult
import com.ouros.app.data.farmowner.FarmOwnerRepository
import com.ouros.app.data.session.FarmSessionStore
import kotlinx.coroutines.launch

class MainViewModel(
    private val farmOwnerRepository: FarmOwnerRepository,
    private val farmSessionStore: FarmSessionStore
) : ViewModel() {

    interface Listener {
        fun onMainLoadingChanged(isLoading: Boolean)
        fun onMainProfileLoaded(profile: MainProfileUiModel)
        fun onMainProfileError(message: String)
        fun onMainSessionExpired()
    }

    private var listener: Listener? = null
    private var cachedProfile: MainProfileUiModel? = null
    private var isLoading = false

    fun setListener(listener: Listener) {
        this.listener = listener

        listener.onMainLoadingChanged(isLoading)

        cachedProfile?.let {
            listener.onMainProfileLoaded(it)
        }
    }

    fun clearListener(listener: Listener) {
        if (this.listener === listener) {
            this.listener = null
        }
    }

    fun loadProfile(forceRefresh: Boolean = false) {
        if (isLoading) {
            return
        }

        if (!forceRefresh) {
            cachedProfile?.let {
                listener?.onMainProfileLoaded(it)
                return
            }
        }

        viewModelScope.launch {
            setLoading(true)

            when (val result = farmOwnerRepository.getMe()) {
                is AuthResult.Success -> {
                    val farmOwner = result.data

                    val name = farmOwner.name
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }

                    if (name == null) {
                        listener?.onMainProfileError(
                            "O perfil não possui um nome válido."
                        )

                        setLoading(false)
                        return@launch
                    }

                    farmSessionStore.saveAccount(
                        ownerId = farmOwner.id,
                        farmId = farmOwner.farmId
                    )

                    val profile = MainProfileUiModel(
                        farmOwnerId = farmOwner.id,
                        name = name,
                        photoUrl = farmOwner.photoUrl
                    )

                    cachedProfile = profile
                    listener?.onMainProfileLoaded(profile)
                }

                is AuthResult.Error -> {
                    if (result.statusCode == 401) {
                        listener?.onMainSessionExpired()
                    } else {
                        listener?.onMainProfileError(
                            result.message
                        )
                    }
                }
            }

            setLoading(false)
        }
    }

    fun clearProfileCache() {
        cachedProfile = null
    }

    private fun setLoading(value: Boolean) {
        isLoading = value
        listener?.onMainLoadingChanged(value)
    }
}