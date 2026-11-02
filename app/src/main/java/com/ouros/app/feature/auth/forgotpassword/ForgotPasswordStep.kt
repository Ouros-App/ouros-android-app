package com.ouros.app.feature.auth.forgotpassword

/**
 * Componente de apoio a auth › forgotpassword. Reúne comportamento relacionado a essa funcionalidade para manter o fluxo principal mais simples.
 */
enum class ForgotPasswordStep {
    EMAIL,
    CODE,
    NEW_PASSWORD
}