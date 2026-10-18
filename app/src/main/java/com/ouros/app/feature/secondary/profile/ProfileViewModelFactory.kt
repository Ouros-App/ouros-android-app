package com.ouros.app.feature.secondary.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ouros.app.data.cloudinary.CloudinaryRepository
import com.ouros.app.data.farmowner.FarmOwnerRepository

class ProfileViewModelFactory(
    private val farmOwnerRepository: FarmOwnerRepository,
    private val cloudinaryRepository: CloudinaryRepository = CloudinaryRepository()
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
                farmOwnerRepository = farmOwnerRepository,
                cloudinaryRepository = cloudinaryRepository
            ) as T
        }

        throw IllegalArgumentException(
            "ViewModel desconhecido: ${modelClass.name}"
        )
    }
}
