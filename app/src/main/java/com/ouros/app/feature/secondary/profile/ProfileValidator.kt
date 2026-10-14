package com.ouros.app.feature.secondary.profile

object ProfileValidator {

    private val NAME_REGEX =
        Regex("^[\\p{L}]+(?:[ .'-][\\p{L}]+)*$")

    private val EMAIL_REGEX =
        Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")

    private val PHONE_REGEX =
        Regex("^\\d{10,13}$")

    private val PASSWORD_REGEX = Regex(
        "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)" +
                "(?=.*[^a-zA-Z0-9]).{8,20}$"
    )

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

    fun isValidCpf(value: String): Boolean {
        val cpf = value.filter(Char::isDigit)

        if (cpf.length != 11) {
            return false
        }

        if (cpf.all { it == cpf.first() }) {
            return false
        }

        val firstDigit = calculateCpfDigit(
            cpf = cpf,
            length = 9
        )

        if (firstDigit != cpf[9].digitToInt()) {
            return false
        }

        val secondDigit = calculateCpfDigit(
            cpf = cpf,
            length = 10
        )

        return secondDigit == cpf[10].digitToInt()
    }

    fun isValidTelephone(value: String): Boolean {
        val telephone = value.filter(Char::isDigit)
        return PHONE_REGEX.matches(telephone)
    }

    fun isValidPassword(value: String): Boolean {
        return value.isBlank() ||
                PASSWORD_REGEX.matches(value)
    }

    private fun calculateCpfDigit(
        cpf: String,
        length: Int
    ): Int {
        var sum = 0

        for (index in 0 until length) {
            val digit = cpf[index].digitToInt()
            val weight = length + 1 - index
            sum += digit * weight
        }

        val result = (sum * 10) % 11
        return if (result == 10) 0 else result
    }
}