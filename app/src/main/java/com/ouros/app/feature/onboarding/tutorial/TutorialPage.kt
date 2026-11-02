package com.ouros.app.feature.onboarding.tutorial

import androidx.annotation.DrawableRes

/**
 * Componente de apoio a onboarding › tutorial. Reúne comportamento relacionado a essa funcionalidade para manter o fluxo principal mais simples.
 */
data class TutorialPage(
    val title: String,
    val description: String,
    @DrawableRes val imageResId: Int
)