package com.ouros.app.feature.auth.firstaccess

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ouros.app.data.auth.AuthRepository
import com.ouros.app.data.farmowner.FarmOwnerRepository

class FirstAccessViewModelFactory(
    private val authRepository: AuthRepository,
    private val farmOwnerRepository:
    FarmOwnerRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {
        require(
            modelClass.isAssignableFrom(
                FirstAccessViewModel::class.java
            )
        )

        return FirstAccessViewModel(
            authRepository = authRepository,
            farmOwnerRepository =
                farmOwnerRepository
        ) as T
    }
}