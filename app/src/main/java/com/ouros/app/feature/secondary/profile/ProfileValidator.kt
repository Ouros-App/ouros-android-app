package com.ouros.app.feature.secondary.profile

object ProfileValidator {

    private val NAME_REGEX =
        Regex("^[\\p{L}]+(?:[ .'-][\\p{L}]+)*$")

    private val EMAIL_REGEX =
        Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")

    private val PHONE_REGEX =
        Regex("^\\d{10,13}$")

    fun isValidName(value: String): Boolean {
        val name = value.trim()

        return name.length in 2..100 &&
                NAME_REGEX.matches(name)
    }

    fun isValidEmail(value: String): Boolean {
        return EMAIL_REGEX.matches(
            value.trim().lowercase()
        )
    }

    fun isValidTelephone(value: String): Boolean {
        val telephone = value.filter(Char::isDigit)
        return PHONE_REGEX.matches(telephone)
    }

}
