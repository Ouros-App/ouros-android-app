package com.ouros.app

import android.app.Application
import com.ouros.app.data.local.OfflineSyncRegistry
import com.ouros.app.feature.secondary.profile.ProfileOfflineCoordinator

class OurosApplication : Application() {
    lateinit var profileOfflineCoordinator: ProfileOfflineCoordinator
        private set

    override fun onCreate() {
        super.onCreate()
        OfflineSyncRegistry.initialize(this)
        profileOfflineCoordinator = ProfileOfflineCoordinator(this)
        OfflineSyncRegistry.register(profileOfflineCoordinator)
    }
}
