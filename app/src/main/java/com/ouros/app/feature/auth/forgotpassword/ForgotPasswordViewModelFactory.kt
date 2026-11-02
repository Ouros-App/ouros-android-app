package com.ouros.app.feature.auth.forgotpassword

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ouros.app.data.auth.AuthRepository

/**
 * Fábrica de dependências de auth › forgotpassword. Constrói o objeto com os repositórios e serviços de que ele precisa.
 */
class ForgotPasswordViewModelFactory(
    private val authRepository:
    AuthRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {
        require(
            modelClass.isAssignableFrom(
                ForgotPasswordViewModel::class.java
            )
        )

        return ForgotPasswordViewModel(
            authRepository = authRepository
        ) as T
    }
}