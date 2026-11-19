package dev.pallav.phonesaar

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo

// Finds the wireless debugging pairing and connect ports advertised on this network.
class Nsd(context: Context, private val onPort: (pairing: Boolean, host: String, port: Int) -> Unit) {
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val listeners = ArrayList<NsdManager.DiscoveryListener>()

    fun start() {
        listOf(PAIRING to true, CONNECT to false).forEach { (type, pairing) ->
            val listener = object : NsdManager.DiscoveryListener {
                override fun onServiceFound(info: NsdServiceInfo) = resolve(info, pairing)
                override fun onStartDiscoveryFailed(type: String?, code: Int) {}
                override fun onStopDiscoveryFailed(type: String?, code: Int) {}
                override fun onDiscoveryStarted(type: String?) {}
                override fun onDiscoveryStopped(type: String?) {}
                override fun onServiceLost(info: NsdServiceInfo?) {}
            }
            runCatching { nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener) }.onSuccess { listeners.add(listener) }
        }
    }

    fun stop() {
        listeners.forEach { runCatching { nsd.stopServiceDiscovery(it) } }
        listeners.clear()
    }

    @Suppress("DEPRECATION")
    private fun resolve(info: NsdServiceInfo, pairing: Boolean) {
        runCatching {
            nsd.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo?, code: Int) {}
                override fun onServiceResolved(info: NsdServiceInfo) {
                    onPort(pairing, info.host?.hostAddress ?: "", info.port)
                }
            })
        }
    }

    private companion object {
        const val PAIRING = "_adb-tls-pairing._tcp"
        const val CONNECT = "_adb-tls-connect._tcp"
    }
}
