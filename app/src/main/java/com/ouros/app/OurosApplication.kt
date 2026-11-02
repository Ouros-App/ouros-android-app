package com.ouros.app

import android.app.Application
import com.ouros.app.data.auth.AppSession
import com.ouros.app.data.local.OfflineSyncRegistry
import com.ouros.app.feature.secondary.profile.ProfileOfflineCoordinator

/** Inicializa serviços e estado compartilhados antes da primeira tela do app. */
class OurosApplication : Application() {
    lateinit var profileOfflineCoordinator: ProfileOfflineCoordinator
        private set

    override fun onCreate() {
        super.onCreate()
        // A sessão e a fila offline são globais ao processo; inicializá-las aqui evita
        // que cada Activity crie instâncias concorrentes dos mesmos serviços.
        AppSession.initialize(this)
        OfflineSyncRegistry.initialize(this)
        profileOfflineCoordinator = ProfileOfflineCoordinator(this)
        OfflineSyncRegistry.register(profileOfflineCoordinator)
    }
}
