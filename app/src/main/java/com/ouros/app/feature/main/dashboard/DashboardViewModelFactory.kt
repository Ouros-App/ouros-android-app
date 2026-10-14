package com.ouros.app.feature.main.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ouros.app.data.dashboard.DashboardRepository

class DashboardViewModelFactory(
    private val dashboardRepository:
    DashboardRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {
        if (
            modelClass.isAssignableFrom(
                DashboardViewModel::class.java
            )
        ) {
            return DashboardViewModel(
                dashboardRepository =
                    dashboardRepository
            ) as T
        }

        throw IllegalArgumentException(
            "ViewModel não suportado: ${modelClass.name}"
        )
    }
}