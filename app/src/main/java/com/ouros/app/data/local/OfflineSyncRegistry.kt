package com.ouros.app.data.local

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Contrato para cada área reenviar operações locais quando a conexão voltar. */
fun interface OfflineSynchronizer {
    suspend fun synchronize()
}

/** Observa conectividade e coordena sincronizadores sem bloquear a interface. */
object OfflineSyncRegistry {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()
    private val synchronizers = CopyOnWriteArrayList<WeakReference<OfflineSynchronizer>>()
    private var connectivityManager: ConnectivityManager? = null

    fun initialize(context: Context) {
        if (connectivityManager != null) return
        val manager = context.getSystemService(ConnectivityManager::class.java)
        connectivityManager = manager
        runCatching {
            manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = synchronizeAll()
            })
        }
        if (isOnline()) synchronizeAll()
    }

    fun register(synchronizer: OfflineSynchronizer) {
        synchronizers.removeAll { it.get() == null }
        if (synchronizers.none { it.get() === synchronizer }) {
            synchronizers += WeakReference(synchronizer)
        }
        if (isOnline()) scope.launch { synchronize(synchronizer) }
    }

    fun isOnline(): Boolean {
        val manager = connectivityManager ?: return true
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        // INTERNET sozinho indica apenas uma rede configurada; VALIDATED confirma
        // que o Android conseguiu alcançar a internet pública.
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun synchronizeAll() {
        synchronizers.removeAll { it.get() == null }
        synchronizers.mapNotNull { it.get() }.forEach { synchronizer ->
            scope.launch { synchronize(synchronizer) }
        }
    }

    private suspend fun synchronize(synchronizer: OfflineSynchronizer) {
        syncMutex.withLock {
            runCatching { synchronizer.synchronize() }
        }
    }
}
