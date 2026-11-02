package com.ouros.app.feature.main.rank

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ouros.app.data.rank.RankRepository

/**
 * Fábrica de dependências de rank. Constrói o objeto com os repositórios e serviços de que ele precisa.
 */
class RankViewModelFactory(
    private val repository: RankRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RankViewModel::class.java)) {
            return RankViewModel(repository) as T
        }
        throw IllegalArgumentException("ViewModel não suportado: ${modelClass.name}")
    }
}