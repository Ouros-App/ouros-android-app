package com.ouros.app.data.auth

/**
 * Componente de apoio a auth. Reúne comportamento relacionado a essa funcionalidade para manter o fluxo principal mais simples.
 */
sealed interface AuthResult<out T> {

    data class Success<T>(
        val data: T
    ) : AuthResult<T>

    data class Error(
        val message: String,
        val statusCode: Int? = null
    ) : AuthResult<Nothing>
}