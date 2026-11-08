package alibarznji.qasa.sync

import alibarznji.qasa.R
import alibarznji.qasa.vault.VaultRepository
import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uniffi.qasa_ffi.FfiOutcome
import uniffi.qasa_ffi.FfiSyncException
import uniffi.qasa_ffi.SyncHost
import uniffi.qasa_ffi.syncJoin

enum class SyncFailureReason { WrongCode, DifferentVault, NoVault, Version, Expired, Io, Other }

data class SyncFailure(val reason: SyncFailureReason, val attemptsLeft: Int = 0)

data class SyncResult(
    val peerName: String,
    val peerPlatform: String,
    /** This device took the other's vault and account. */
    val adopted: Boolean,
    val received: Int,
    val sent: Int,
)

sealed interface HostState {
    data object Idle : HostState

    /** Showing [code]; [failure] is the last wrong attempt while attempts remain. */
    data class Waiting(val code: String, val expiresAtMs: Long, val failure: SyncFailure? = null) : HostState
    data class Done(val result: SyncResult) : HostState
    data class Failed(val failure: SyncFailure) : HostState
}

sealed interface JoinResult {
    data class Done(val result: SyncResult) : JoinResult
    data class Failed(val failure: SyncFailure) : JoinResult
}

/** A message resource and its optional `{0}` argument (desktop `failureKey`). */
data class SyncMessage(@StringRes val res: Int, val arg: Int? = null)

/** Desktop `sync/api.ts` `failureKey`: the message for a failed sync. */
fun failureMessage(f: SyncFailure): SyncMessage = when (f.reason) {
    SyncFailureReason.WrongCode ->
        if (f.attemptsLeft > 0) SyncMessage(R.string.sync_wrongcode, f.attemptsLeft) else SyncMessage(R.string.sync_wrongcodefinal)
    SyncFailureReason.DifferentVault -> SyncMessage(R.string.sync_differentvault)
    SyncFailureReason.NoVault -> SyncMessage(R.string.sync_novault)
    SyncFailureReason.Version -> SyncMessage(R.string.sync_version)
    SyncFailureReason.Expired -> SyncMessage(R.string.sync_expired)
    SyncFailureReason.Io -> SyncMessage(R.string.sync_io)
    SyncFailureReason.Other -> SyncMessage(R.string.sync_failed)
}

fun FfiSyncException.toFailure(): SyncFailure = when (this) {
    is FfiSyncException.WrongCode -> SyncFailure(SyncFailureReason.WrongCode, attemptsLeft.toInt())
    is FfiSyncException.DifferentVault -> SyncFailure(SyncFailureReason.DifferentVault)
    is FfiSyncException.NoVault -> SyncFailure(SyncFailureReason.NoVault)
    is FfiSyncException.Version -> SyncFailure(SyncFailureReason.Version)
    is FfiSyncException.Expired -> SyncFailure(SyncFailureReason.Expired)
    is FfiSyncException.Io -> SyncFailure(SyncFailureReason.Io)
    is FfiSyncException.Stopped,
    is FfiSyncException.Store,
    is FfiSyncException.Unexpected,
    -> SyncFailure(SyncFailureReason.Other)
}

private fun FfiOutcome.toResult() = SyncResult(
    peerName = peerName,
    peerPlatform = peerPlatform,
    adopted = adopted,
    received = received.toInt(),
    sent = sent.toInt(),
)

/**
 * Wi‑Fi vault sync (plan §6): hosting shows a one-time code and advertises this device
 * over NSD; joining picks a found device and types its code. The Rust core (`qasa-ffi`)
 * runs the code-authenticated session; [VaultSyncStore] is this device's side of it.
 */
@Singleton
class WifiSyncService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: VaultSyncStore,
    private val discovery: SyncDiscovery,
    private val repository: VaultRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _host = MutableStateFlow<HostState>(HostState.Idle)
    val hostState: StateFlow<HostState> = _host.asStateFlow()

    val peers: StateFlow<List<SyncPeer>> get() = discovery.peers

    private var host: SyncHost? = null
    private var hostJob: Job? = null

    @Synchronized
    fun startHost() {
        if (hostJob?.isActive == true) return
        _host.value = HostState.Idle
        hostJob = scope.launch { runHost() }
    }

    private suspend fun runHost() {
        val session = try {
            SyncHost()
        } catch (e: FfiSyncException) {
            _host.value = HostState.Failed(e.toFailure())
            return
        } catch (e: Throwable) {
            // UnsatisfiedLinkError when the native library is missing from this build.
            Log.w(TAG, "sync host unavailable", e)
            _host.value = HostState.Failed(SyncFailure(SyncFailureReason.Other))
            return
        }
        synchronized(this) { host = session }
        val deadline = System.currentTimeMillis() + CODE_LIFETIME_MS
        val code = session.code()
        // Rust restarts its 2-minute clock on each `serve`; the code's life is one window.
        val expiry = scope.launch {
            delay(CODE_LIFETIME_MS)
            session.stop()
        }
        discovery.advertise(VaultSyncStore.deviceName(context), session.port().toInt())
        _host.value = HostState.Waiting(code, deadline)
        try {
            while (true) {
                try {
                    val outcome = session.serve(store)
                    repository.refreshAfterSync()
                    _host.value = HostState.Done(outcome.toResult())
                    return
                } catch (e: FfiSyncException.WrongCode) {
                    if (e.attemptsLeft > 0u && System.currentTimeMillis() < deadline) {
                        _host.value = HostState.Waiting(code, deadline, e.toFailure())
                        continue
                    }
                    _host.value = HostState.Failed(e.toFailure())
                    return
                } catch (e: FfiSyncException.Stopped) {
                    _host.value = if (System.currentTimeMillis() >= deadline) {
                        HostState.Failed(SyncFailure(SyncFailureReason.Expired))
                    } else {
                        HostState.Idle
                    }
                    return
                } catch (e: FfiSyncException) {
                    _host.value = HostState.Failed(e.toFailure())
                    return
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "host failed", e)
            _host.value = HostState.Failed(SyncFailure(SyncFailureReason.Other))
        } finally {
            expiry.cancel()
            discovery.stopAdvertising()
            synchronized(this) { host = null }
            session.destroy()
        }
    }

    /** Stop hosting; a session in progress is cut at the next accept. */
    @Synchronized
    fun stopHost() {
        host?.stop()
        if (_host.value is HostState.Waiting) _host.value = HostState.Idle
    }

    /** Clears a finished host result, e.g. when the screen switches mode. */
    fun resetHost() {
        stopHost()
        _host.value = HostState.Idle
    }

    fun startBrowsing() = discovery.startBrowsing()

    fun stopBrowsing() = discovery.stopBrowsing()

    /** Joins [peer] with [code]. Blocks on IO for the whole session. */
    suspend fun join(peer: SyncPeer, code: String): JoinResult = withContext(Dispatchers.IO) {
        try {
            val outcome = syncJoin(peer.host, peer.port.toUShort(), code, store)
            repository.refreshAfterSync()
            JoinResult.Done(outcome.toResult())
        } catch (e: FfiSyncException) {
            JoinResult.Failed(e.toFailure())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.w(TAG, "join failed", e)
            JoinResult.Failed(SyncFailure(SyncFailureReason.Other))
        }
    }

    companion object {
        private const val TAG = "WifiSyncService"
        const val CODE_LIFETIME_MS = 120_000L

        /** `123 456`. */
        fun groupCode(code: String): String =
            if (code.length == 6) "${code.take(3)} ${code.drop(3)}" else code
    }
}
