package alibarznji.qasa.vpn

import alibarznji.qasa.license.LicenseManager
import alibarznji.qasa.prefs.AppPreferences
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * "Auto-connect on public or untrusted Wi-Fi". While the switch is on, joining such a Wi-Fi
 * network connects the VPN once. Disconnecting by hand on that network is respected until
 * the device joins another one.
 *
 * It runs while the app's process is alive; a connected tunnel keeps the process alive.
 * It never asks for the VPN permission itself: that needs the VPN page to be opened once.
 */
@Singleton
class VpnAutoConnector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: AppPreferences,
    private val vpn: VPNService,
    private val license: LicenseManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private var callback: ConnectivityManager.NetworkCallback? = null
    /** The Wi-Fi network we already acted on. */
    @Volatile private var handled: Network? = null

    fun start() {
        scope.launch { prefs.vpnAutoConnect.collect { on -> if (on) register() else unregister() } }
    }

    private fun register() {
        if (callback != null || connectivity == null) return
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                if (network == handled) return
                handled = network
                if (WifiTrust.isPublic(securityType(caps))) scope.launch { maybeConnect() }
            }

            override fun onLost(network: Network) {
                if (network == handled) handled = null
            }
        }
        runCatching { connectivity.registerNetworkCallback(request, cb) }
            .onSuccess { callback = cb }
            .onFailure { Log.w(TAG, "network callback failed", it) }
    }

    private fun unregister() {
        callback?.let { runCatching { connectivity?.unregisterNetworkCallback(it) } }
        callback = null
        handled = null
    }

    private suspend fun maybeConnect() {
        if (!license.isPremium) return
        if (vpn.connected.value || vpn.isBusy.value) return
        if (vpn.needsPermission()) return
        Log.i(TAG, "public Wi-Fi joined; connecting")
        vpn.connect()
    }

    private fun securityType(caps: NetworkCapabilities): Int? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (caps.transportInfo as? WifiInfo)?.currentSecurityType
        } else {
            null
        }

    private companion object {
        const val TAG = "QasaVPNAuto"
    }
}

/** Which Wi-Fi counts as "public or untrusted". Pure, so it is unit tested. */
object WifiTrust {
    // `WifiInfo.SECURITY_TYPE_*` (API 31); inlined so this also runs on older devices.
    private const val UNKNOWN = -1
    private const val OPEN = 0
    private const val WEP = 1
    private const val OWE = 6

    /**
     * Open, WEP and OWE (encrypted but unauthenticated) networks count as public. Below
     * Android 12 the type cannot be read ([securityType] null), so every Wi-Fi counts; an
     * unknown type also errs on the side of connecting.
     */
    fun isPublic(securityType: Int?): Boolean =
        securityType == null || securityType in setOf(UNKNOWN, OPEN, WEP, OWE)
}
