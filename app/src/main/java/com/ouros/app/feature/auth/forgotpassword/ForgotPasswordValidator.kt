package com.ouros.app.feature.auth.forgotpassword

object ForgotPasswordValidator {

    private val EMAIL_REGEX =
        Regex(
            "^[A-Za-z0-9+_.-]+" +
                    "@[A-Za-z0-9.-]+$"
        )

    private val CODE_REGEX =
        Regex("^\\d{6}$")

    private val PASSWORD_REGEX =
        Regex(
            "^(?=.*[a-z])" +
                    "(?=.*[A-Z])" +
                    "(?=.*\\d)" +
                    "(?=.*[^a-zA-Z0-9])" +
                    ".{8,20}$"
        )

    fun isValidEmail(
        value: String
    ): Boolean {
        return EMAIL_REGEX.matches(
            value.trim()
        )
    }

    fun isValidCode(
        value: String
    ): Boolean {
        return CODE_REGEX.matches(value)
    }

    fun isValidPassword(
        value: String
    ): Boolean {
        return PASSWORD_REGEX.matches(value)
    }
}