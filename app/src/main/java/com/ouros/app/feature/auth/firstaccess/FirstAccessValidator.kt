package com.ouros.app.feature.auth.firstaccess

object FirstAccessValidator {

    private val NAME_REGEX =
        Regex("^[\\p{L}]+(?:[ .'-][\\p{L}]+)*$")

    private val CPF_REGEX =
        Regex("^\\d{11}$")

    private val PHONE_REGEX =
        Regex("^\\d{10,13}$")

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

    fun isValidName(value: String): Boolean {
        val name = value.trim()

        return name.length in 2..100 &&
                NAME_REGEX.matches(name)
    }

    fun isValidCpf(value: String): Boolean {
        val cpf = value.filter(Char::isDigit)

        if (!CPF_REGEX.matches(cpf)) {
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

    fun isValidVerificationCode(
        value: String
    ): Boolean {
        return CODE_REGEX.matches(value)
    }

    fun isValidPassword(value: String): Boolean {
        return PASSWORD_REGEX.matches(value)
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

        return if (result == 10) {
            0
        } else {
            result
        }
    }
}