package com.ouros.app.data.auth

sealed interface AuthResult<out T> {

    data class Success<T>(
        val data: T
    ) : AuthResult<T>

    data class Error(
        val message: String,
        val statusCode: Int? = null
    ) : AuthResult<Nothing>
}