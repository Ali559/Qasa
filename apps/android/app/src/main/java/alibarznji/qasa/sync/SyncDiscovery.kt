package alibarznji.qasa.sync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A device showing a sync code on this Wi‑Fi. */
data class SyncPeer(
    /** The NSD service name: the other device's name (never the code). */
    val name: String,
    /** `ios`, `android`, `macos`, `windows`, `linux`, or empty when not advertised. */
    val platform: String,
    val host: String,
    val port: Int,
)

/**
 * `_qasa-sync._tcp` over [NsdManager] (plan §6.1). Hosting advertises the device name with
 * `p=android`, `v=1`; browsing resolves each found service to an address for `syncJoin`.
 * The socket and the whole session belong to the Rust core.
 */
@Singleton
class SyncDiscovery @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val nsd: NsdManager get() = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val mainExecutor: Executor get() = ContextCompat.getMainExecutor(context)

    // ---- Advertising ---------------------------------------------------------------

    private var registration: NsdManager.RegistrationListener? = null

    @Synchronized
    fun advertise(deviceName: String, port: Int) {
        stopAdvertising()
        val info = NsdServiceInfo().apply {
            serviceName = deviceName.take(63)
            serviceType = SERVICE_TYPE
            setPort(port)
            setAttribute("p", "android")
            setAttribute("v", "1")
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "register failed: $errorCode")
            }
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
        }
        registration = listener
        try {
            nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Log.w(TAG, "register failed", e)
            registration = null
        }
    }

    @Synchronized
    fun stopAdvertising() {
        val listener = registration ?: return
        registration = null
        try {
            nsd.unregisterService(listener)
        } catch (_: Exception) {
        }
    }

    // ---- Browsing ------------------------------------------------------------------

    private val _peers = MutableStateFlow<List<SyncPeer>>(emptyList())
    val peers: StateFlow<List<SyncPeer>> = _peers.asStateFlow()

    private var discovery: NsdManager.DiscoveryListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private val infoCallbacks = mutableMapOf<String, Any>()

    // Before API 34 NsdManager resolves one service at a time.
    private val resolveQueue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false

    @Synchronized
    fun startBrowsing() {
        stopBrowsing()
        _peers.value = emptyList()
        acquireMulticastLock()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "discovery failed: $errorCode")
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onServiceFound(info: NsdServiceInfo) = resolve(info)
            override fun onServiceLost(info: NsdServiceInfo) = lost(info.serviceName)
        }
        discovery = listener
        try {
            nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Log.w(TAG, "discovery failed", e)
            discovery = null
        }
    }

    @Synchronized
    fun stopBrowsing() {
        discovery?.let { listener ->
            try {
                nsd.stopServiceDiscovery(listener)
            } catch (_: Exception) {
            }
        }
        discovery = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            infoCallbacks.values.forEach { cb ->
                try {
                    nsd.unregisterServiceInfoCallback(cb as NsdManager.ServiceInfoCallback)
                } catch (_: Exception) {
                }
            }
        }
        infoCallbacks.clear()
        resolveQueue.clear()
        resolving = false
        multicastLock?.let { if (it.isHeld) it.release() }
        multicastLock = null
    }

    private fun acquireMulticastLock() {
        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifi.createMulticastLock("qasa-sync").apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (e: Exception) {
            Log.w(TAG, "multicast lock unavailable", e)
        }
    }

    @Synchronized
    private fun resolve(info: NsdServiceInfo) {
        if (discovery == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (info.serviceName in infoCallbacks) return
            val cb = object : NsdManager.ServiceInfoCallback {
                override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) = Unit
                override fun onServiceUpdated(updated: NsdServiceInfo) {
                    val address = updated.hostAddresses.preferIpv4() ?: return
                    found(updated, address)
                }
                override fun onServiceLost() = lost(info.serviceName)
                override fun onServiceInfoCallbackUnregistered() = Unit
            }
            infoCallbacks[info.serviceName] = cb
            try {
                nsd.registerServiceInfoCallback(info, mainExecutor, cb)
            } catch (e: Exception) {
                infoCallbacks.remove(info.serviceName)
            }
        } else {
            resolveQueue.addLast(info)
            resolveNext()
        }
    }

    @Suppress("DEPRECATION")
    @Synchronized
    private fun resolveNext() {
        if (resolving || discovery == null) return
        val next = resolveQueue.removeFirstOrNull() ?: return
        resolving = true
        val listener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) = done()
            override fun onServiceResolved(info: NsdServiceInfo) {
                info.host?.let { found(info, it) }
                done()
            }
            private fun done() {
                synchronized(this@SyncDiscovery) { resolving = false }
                resolveNext()
            }
        }
        try {
            nsd.resolveService(next, listener)
        } catch (_: Exception) {
            resolving = false
        }
    }

    private fun found(info: NsdServiceInfo, address: InetAddress) {
        val platform = info.attributes["p"]?.toString(Charsets.UTF_8).orEmpty()
        val host = address.hostAddress?.substringBefore('%') ?: return
        val peer = SyncPeer(name = info.serviceName, platform = platform, host = host, port = info.port)
        _peers.value = (_peers.value.filterNot { it.name == peer.name } + peer).sortedBy { it.name.lowercase() }
    }

    private fun lost(name: String) {
        _peers.value = _peers.value.filterNot { it.name == name }
    }

    private fun List<InetAddress>.preferIpv4(): InetAddress? =
        firstOrNull { it is Inet4Address } ?: firstOrNull()

    private companion object {
        const val TAG = "SyncDiscovery"
        const val SERVICE_TYPE = "_qasa-sync._tcp"
    }
}
