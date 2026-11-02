package com.ouros.app.feature.auth.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ouros.app.data.auth.AuthRepository
import com.ouros.app.data.farmowner.FarmOwnerRepository

/**
 * Fábrica de dependências de auth › login. Constrói o objeto com os repositórios e serviços de que ele precisa.
 */
class LoginViewModelFactory(
    private val authRepository: AuthRepository,
    private val farmOwnerRepository: FarmOwnerRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {
        require(
            modelClass.isAssignableFrom(
                LoginViewModel::class.java
            )
        )

        return LoginViewModel(
            authRepository = authRepository,
            farmOwnerRepository = farmOwnerRepository
        ) as T
    }
}