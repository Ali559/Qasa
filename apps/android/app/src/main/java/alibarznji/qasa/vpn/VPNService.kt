package alibarznji.qasa.vpn

import alibarznji.qasa.BuildConfig
import alibarznji.qasa.R
import alibarznji.qasa.config.QasaConfig
import alibarznji.qasa.network.QasaApiClient
import alibarznji.qasa.network.QasaApiException
import alibarznji.qasa.network.VPNConnectRequest
import alibarznji.qasa.network.VPNConnectResponse
import alibarznji.qasa.network.VPNNode
import alibarznji.qasa.network.VPNNodesResponse
import alibarznji.qasa.network.VPNStatusResponse
import alibarznji.qasa.network.VPNUsage
import alibarznji.qasa.network.get
import alibarznji.qasa.network.post
import alibarznji.qasa.prefs.AppPreferences
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.crypto.KeyPair
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** What the dial shows. [Connecting] covers "tunnel is up locally, no handshake yet". */
enum class VpnPhase { Disconnected, Connecting, Connected }

sealed interface VpnConnectResult {
    data object Started : VpnConnectResult
    /** Android has not let Qasa run a VPN yet; launch [intent] from an Activity, then retry. */
    data class NeedsPermission(val intent: Intent) : VpnConnectResult
    data object Failed : VpnConnectResult
}

/**
 * The device side of Qasa VPN: asks the API for a session, then runs the WireGuard tunnel
 * with wireguard-android's [GoBackend] (its own `VpnService`, declared by the library).
 *
 * - The keypair is made on the device ([WireGuardKeyStore]); only the public key is sent.
 * - "Connected" means this device saw a handshake from the node, not just that the API
 *   made a session. Until then the dial shows [VpnPhase.Connecting].
 * - Disconnect drops the tunnel first, then tells the API. If that call fails the server
 *   ends the session itself once the node stops seeing handshakes.
 */
@Singleton
class VPNService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: QasaApiClient,
    private val keys: WireGuardKeyStore,
    private val prefs: AppPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val backend by lazy { GoBackend(context) }
    /** Serialises connect / disconnect / status so they never interleave on the backend. */
    private val lock = Mutex()
    private var monitor: Job? = null

    /** Set while Qasa itself is taking the tunnel down, so that is not treated as a drop. */
    @Volatile private var stopping = false
    /** The user (or always-on / auto-connect) wants the tunnel on; a disconnect clears it. */
    @Volatile private var wantUp = false
    @Volatile private var tunnelUpSince = 0L
    @Volatile private var reconnectsLeft = MAX_AUTO_RECONNECTS

    private val tunnel = object : Tunnel {
        override fun getName() = TUNNEL_NAME
        override fun onStateChange(newState: Tunnel.State) {
            // Revoked by the system or replaced by another VPN app.
            if (newState == Tunnel.State.DOWN && !stopping) scope.launch { onTunnelLost() }
        }
    }

    private val _phase = MutableStateFlow(VpnPhase.Disconnected)
    val phase = _phase.asStateFlow()
    /** The tunnel is on (connecting or connected); the dial's on/off state. */
    private val _connected = MutableStateFlow(false)
    val connected = _connected.asStateFlow()
    private val _nodeName = MutableStateFlow<String?>(null)
    val nodeName = _nodeName.asStateFlow()
    private val _city = MutableStateFlow<String?>(null)
    val city = _city.asStateFlow()
    private val _nodeId = MutableStateFlow<String?>(null)
    val nodeId = _nodeId.asStateFlow()
    private val _assignedIP = MutableStateFlow<String?>(null)
    val assignedIP = _assignedIP.asStateFlow()
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError = _lastError.asStateFlow()
    /** The dial shows a spinner and ignores taps while a call is in flight. */
    private val _isBusy = MutableStateFlow(false)
    val isBusy = _isBusy.asStateFlow()
    private val _nodes = MutableStateFlow<List<VPNNode>>(emptyList())
    val nodes = _nodes.asStateFlow()
    private val _usage = MutableStateFlow<VPNUsage?>(null)
    val usage = _usage.asStateFlow()
    /** Android's "Block connections without VPN" for Qasa; null when unknown (tunnel off). */
    private val _systemKillSwitch = MutableStateFlow<Boolean?>(null)
    val systemKillSwitch = _systemKillSwitch.asStateFlow()

    init {
        // Always-on VPN (system settings) starts the library's service with no tunnel.
        GoBackend.setAlwaysOnCallback { scope.launch { connect() } }
    }

    /** True when Android still has to ask the user before Qasa can start a VPN. */
    fun needsPermission(): Boolean = VpnService.prepare(context) != null

    fun reportPermissionDenied() {
        _lastError.value = context.getString(R.string.vpn_permissiondenied)
    }

    suspend fun connect(nodeId: String? = prefs.vpnNodeId.value): VpnConnectResult = withContext(Dispatchers.IO) {
        VpnService.prepare(context)?.let { return@withContext VpnConnectResult.NeedsPermission(it) }
        wantUp = true
        lock.withLock { connectLocked(nodeId) }
    }

    private suspend fun connectLocked(nodeId: String?): VpnConnectResult {
        _isBusy.value = true
        try {
            // A tunnel that never handshook routes the API call into nowhere; drop it first.
            if (isTunnelUp() && _phase.value != VpnPhase.Connected) setTunnelDown()
            val keyPair = keys.keyPair()
            val response = requestSession(keyPair, nodeId)
            val cfg = response.config ?: throw QasaApiException.Decoding(IllegalStateException("no config"))
            val excluded = if (BuildConfig.DEBUG) context.packageName else null
            val config = WireGuardConfigFactory.build(cfg, keyPair, excluded)
            // On a running tunnel this reconfigures in place (switching servers).
            backend.setState(tunnel, Tunnel.State.UP, config)
            if (_phase.value != VpnPhase.Connected) {
                _phase.value = VpnPhase.Connecting
                tunnelUpSince = System.currentTimeMillis()
            }
            _connected.value = true
            _nodeName.value = response.node?.name
            _city.value = response.node?.city
            _nodeId.value = response.node?.id
            _assignedIP.value = cfg.client_ip
            response.usage?.let { _usage.value = it }
            _lastError.value = null
            startMonitor()
            return VpnConnectResult.Started
        } catch (e: Exception) {
            Log.w(TAG, "connect failed", e)
            _lastError.value = describe(e)
            if (!isTunnelUp()) clearSession()
            return VpnConnectResult.Failed
        } finally {
            _isBusy.value = false
        }
    }

    /** A saved node that went away (offline, full, retired) falls back to auto-select once. */
    private fun requestSession(keyPair: KeyPair, nodeId: String?): VPNConnectResponse {
        val publicKey = keyPair.publicKey.toBase64()
        return try {
            api.post(QasaConfig.vpnConnectUrl, VPNConnectRequest(publicKey, nodeId))
        } catch (e: QasaApiException.HttpStatus) {
            if (nodeId == null || e.code != 400) throw e
            prefs.setVpnNodeId(null)
            api.post(QasaConfig.vpnConnectUrl, VPNConnectRequest(publicKey, null))
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        wantUp = false
        lock.withLock {
            _isBusy.value = true
            try {
                setTunnelDown()
                clearSession()
                _lastError.value = null
                releaseServerSession()
            } finally {
                _isBusy.value = false
            }
        }
    }

    /** Region picker: save the choice and, if the tunnel is on, move to it now. */
    suspend fun selectNode(id: String?) {
        prefs.setVpnNodeId(id)
        if (_connected.value && id != _nodeId.value) connect(id)
    }

    suspend fun loadNodes() = withContext(Dispatchers.IO) {
        runCatching { api.get<VPNNodesResponse>(QasaConfig.vpnNodesUrl) }
            .onSuccess { _nodes.value = it.nodes }
            .onFailure { Log.w(TAG, "nodes failed", it) }
    }

    /** Screen open: reconcile with the server and pick the monitor back up. */
    suspend fun refreshStatus() = withContext(Dispatchers.IO) {
        lock.withLock {
            val localUp = isTunnelUp()
            try {
                val status: VPNStatusResponse = api.get(QasaConfig.vpnStatusUrl)
                status.usage?.let { _usage.value = it }
                if (localUp) {
                    if (status.connected) applyStatus(status)
                } else {
                    clearSession()
                    // A session left from before the app was killed: let the node drop it.
                    if (status.connected) releaseServerSession()
                }
                if (!localUp) _lastError.value = null
            } catch (e: Exception) {
                if (!localUp) _lastError.value = describe(e)
            }
            if (localUp) {
                _connected.value = true
                if (_phase.value == VpnPhase.Disconnected) _phase.value = VpnPhase.Connecting
                startMonitor()
            }
        }
    }

    /** Sign-out / clear device: tunnel off, session released, key and region forgotten. */
    suspend fun forget() {
        disconnect()
        keys.clear()
        prefs.setVpnNodeId(null)
        _usage.value = null
        _nodes.value = emptyList()
    }

    // --- monitor ---------------------------------------------------------------------

    private fun startMonitor() {
        monitor?.cancel()
        monitor = scope.launch {
            var tick = 0
            while (isActive) {
                if (!isTunnelUp()) {
                    if (!stopping) onTunnelLost()
                    break
                }
                readLocalStats()
                if (tick > 0 && tick % SERVER_CHECK_TICKS == 0) {
                    if (!checkServer()) break
                }
                tick++
                delay(MONITOR_INTERVAL_MS)
            }
        }
    }

    private fun readLocalStats() {
        val stats = runCatching { backend.getStatistics(tunnel) }.getOrNull() ?: return
        val lastHandshake = stats.peers().maxOfOrNull { stats.peer(it)?.latestHandshakeEpochMillis() ?: 0L } ?: 0L
        val now = System.currentTimeMillis()
        if (lastHandshake > 0 && now - lastHandshake < HANDSHAKE_FRESH_MS) {
            if (_phase.value != VpnPhase.Connected) {
                _phase.value = VpnPhase.Connected
                _lastError.value = null
                reconnectsLeft = MAX_AUTO_RECONNECTS
            }
        } else {
            _phase.value = VpnPhase.Connecting
            if (now - maxOf(tunnelUpSince, lastHandshake) > NO_HANDSHAKE_WARN_MS) {
                _lastError.value = context.getString(R.string.vpn_nohandshake)
            }
        }
    }

    /** Returns false when the monitor should stop (the session is gone). */
    private suspend fun checkServer(): Boolean {
        _systemKillSwitch.value = runCatching { backend.isLockdownEnabled() }.getOrNull()
        val status = try {
            api.get<VPNStatusResponse>(QasaConfig.vpnStatusUrl)
        } catch (e: QasaApiException.Forbidden) {
            endSession(R.string.vpn_sessionended) // Premium lapsed or device unlinked.
            return false
        } catch (e: QasaApiException.Unauthorized) {
            endSession(R.string.vpn_sessionended)
            return false
        } catch (e: Exception) {
            return true // Offline or tunnel not passing traffic yet; local stats still apply.
        }
        status.usage?.let { _usage.value = it }
        if (status.connected) {
            applyStatus(status)
            return true
        }
        // The server ended the session (no handshakes for too long, or expiry). Try again
        // once so a brief outage does not leave the user unprotected.
        if (reconnectsLeft > 0) {
            reconnectsLeft--
            scope.launch { lock.withLock { if (wantUp) connectLocked(prefs.vpnNodeId.value) } }
        } else {
            endSession(R.string.vpn_sessionended)
        }
        return false
    }

    private suspend fun endSession(messageRes: Int) {
        wantUp = false
        lock.withLock {
            setTunnelDown()
            clearSession()
            _lastError.value = context.getString(messageRes)
        }
    }

    private suspend fun onTunnelLost() {
        lock.withLock {
            if (isTunnelUp()) return
            clearSession()
            releaseServerSession()
        }
    }

    // --- helpers ---------------------------------------------------------------------

    private fun applyStatus(status: VPNStatusResponse) {
        status.node?.let { _nodeName.value = it }
        status.city?.let { _city.value = it }
        status.node_id?.let { _nodeId.value = it }
        status.assigned_ip?.let { _assignedIP.value = it }
    }

    private fun isTunnelUp(): Boolean =
        runCatching { backend.getState(tunnel) == Tunnel.State.UP }.getOrDefault(false)

    private fun setTunnelDown() {
        stopping = true
        try {
            monitor?.cancel()
            runCatching { backend.setState(tunnel, Tunnel.State.DOWN, null) }
                .onFailure { Log.w(TAG, "tunnel down failed", it) }
        } finally {
            stopping = false
        }
    }

    private fun clearSession() {
        monitor?.cancel()
        _phase.value = VpnPhase.Disconnected
        _connected.value = false
        _nodeName.value = null
        _city.value = null
        _nodeId.value = null
        _assignedIP.value = null
        _systemKillSwitch.value = null
        tunnelUpSince = 0L
    }

    private fun releaseServerSession() {
        try {
            api.postRaw(QasaConfig.vpnDisconnectUrl, "{}", authorized = true)
        } catch (e: QasaApiException.HttpStatus) {
            if (e.code != 404) Log.w(TAG, "disconnect: ${e.code}")
        } catch (e: Exception) {
            Log.w(TAG, "disconnect failed; the server ends the session on its own", e)
        }
    }

    private fun describe(e: Exception): String = when (e) {
        is QasaApiException.HttpStatus ->
            if (e.code == 400) context.getString(R.string.vpn_noservers) else e.message.orEmpty()
        is QasaApiException.Network -> context.getString(R.string.vpn_networkerror)
        else -> e.message ?: e.javaClass.simpleName
    }

    private companion object {
        const val TAG = "QasaVPN"
        const val TUNNEL_NAME = "qasa"
        const val MONITOR_INTERVAL_MS = 2_000L
        /** Every 30 s. */
        const val SERVER_CHECK_TICKS = 15
        /** Same window the server uses for `tunnel_up`. */
        const val HANDSHAKE_FRESH_MS = 3 * 60_000L
        const val NO_HANDSHAKE_WARN_MS = 45_000L
        const val MAX_AUTO_RECONNECTS = 1
    }
}
