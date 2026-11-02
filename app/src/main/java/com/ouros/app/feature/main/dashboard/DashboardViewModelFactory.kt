package com.ouros.app.feature.main.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ouros.app.data.dashboard.DashboardRepository
import java.time.LocalDate

class DashboardViewModelFactory(
    private val dashboardRepository: DashboardRepository,
    private val currentDateProvider: () -> LocalDate = { LocalDate.now() }
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
                dashboardRepository = dashboardRepository,
                currentDateProvider = currentDateProvider
            ) as T
        }

        throw IllegalArgumentException(
            "ViewModel não suportado: ${modelClass.name}"
        )
    }
}
