package com.ouros.app.feature.secondary.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ouros.app.data.farmowner.FarmOwnerRepository

class ProfileViewModelFactory(
    private val farmOwnerRepository: FarmOwnerRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {
        if (
            modelClass.isAssignableFrom(
                ProfileViewModel::class.java
            )
        ) {
            return ProfileViewModel(
                farmOwnerRepository = farmOwnerRepository
            ) as T
        }

        throw IllegalArgumentException(
            "ViewModel desconhecido: ${modelClass.name}"
        )
    }
}
