package com.ouros.app.feature.main.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ouros.app.data.goals.GoalsRepository

class GoalsViewModelFactory(private val repository: GoalsRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GoalsViewModel::class.java)) return GoalsViewModel(repository) as T
        throw IllegalArgumentException("ViewModel não suportado: ${modelClass.name}")
    }
}