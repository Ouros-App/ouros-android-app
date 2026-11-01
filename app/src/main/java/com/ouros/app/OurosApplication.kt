package com.ouros.app

import android.app.Application
import com.ouros.app.data.local.OfflineSyncRegistry

class OurosApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        OfflineSyncRegistry.initialize(this)
    }
}
