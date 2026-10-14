package com.ouros.app.feature.onboarding.tutorial

import androidx.annotation.DrawableRes

data class TutorialPage(
    val title: String,
    val description: String,
    @DrawableRes val imageResId: Int
)