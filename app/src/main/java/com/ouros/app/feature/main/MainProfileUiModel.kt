package com.ouros.app.feature.main

/**
 * Modelo de apresentação de aplicativo. Agrupa os valores de estado que a interface precisa para renderizar a tela.
 */
data class MainProfileUiModel(
    val farmOwnerId: Long?,
    val name: String,
    val photoUrl: String?
)