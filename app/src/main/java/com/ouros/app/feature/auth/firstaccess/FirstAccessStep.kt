package com.ouros.app.feature.auth.firstaccess

import androidx.annotation.LayoutRes
import com.ouros.app.R

enum class FirstAccessStep(
    @LayoutRes val layoutRes: Int
) {
    WELCOME(
        R.layout.fragment_firstaccess_welcome
    ),

    CREDENTIALS(
        R.layout.fragment_firstaccess_credentials
    ),

    CODE(
        R.layout.fragment_firstaccess_code
    ),

    PERSONAL_DATA(
        R.layout.fragment_firstaccess_personaldata
    ),

    NEW_PASSWORD(
        R.layout.fragment_firstaccess_password
    )
}