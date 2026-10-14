package com.ouros.app.feature.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ouros.app.data.farmowner.FarmOwnerRepository
import com.ouros.app.data.session.FarmSessionStore

class MainViewModelFactory(
    private val farmOwnerRepository: FarmOwnerRepository,
    private val farmSessionStore: FarmSessionStore
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return MainViewModel(
                farmOwnerRepository = farmOwnerRepository,
                farmSessionStore = farmSessionStore
            ) as T
        }

        throw IllegalArgumentException(
            "ViewModel desconhecido: ${modelClass.name}"
        )
    }
}