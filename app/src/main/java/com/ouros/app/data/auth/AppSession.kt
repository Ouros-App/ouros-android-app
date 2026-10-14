package com.ouros.app.data.auth

object AppSession {

    val sessionManager: SessionManager by lazy {
        SessionManager()
    }
}