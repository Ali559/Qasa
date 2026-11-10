package alibarznji.qasa.services

import alibarznji.qasa.prefs.AppPreferences
import alibarznji.qasa.vault.VaultSession
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Locks after [AppPreferences.autoLockMinutes] of inactivity, or when returning from
 * background past that threshold — mirrors iOS AutoLockMonitor.
 *
 * It also owns [shielded], the privacy-shield state. That lives here rather than in a
 * lifecycle callback so the vault UI and the credential-provider host read one source,
 * exactly as the iOS `scenePhase`-driven shield is documented to (§6.7).
 */
@Singleton
class AutoLockMonitor @Inject constructor(
    private val prefs: AppPreferences,
    private val session: VaultSession,
    private val bootstrap: AppBootstrap,
) {
    private var pollJob: Job? = null
    private var onLock: (() -> Unit)? = null
    private var lastActivityAtMs: Long = System.currentTimeMillis()
    private var backgroundedAtMs: Long? = null
    private val scope = CoroutineScope(Dispatchers.Main.immediate)

    private val _shielded = MutableStateFlow(true)
    private val _isForeground = MutableStateFlow(false)

    /**
     * True while the window must be covered. Starts `true` so a cold start never paints a
     * frame of vault content before `onResume` gets a chance to clear it.
     */
    val shielded: StateFlow<Boolean> = _shielded.asStateFlow()

    fun start(onLock: () -> Unit) {
        this.onLock = onLock
        noteActivity()
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                delay(15_000)
                evaluateWhileActive()
            }
        }
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
        onLock = null
        backgroundedAtMs = null
    }

    fun noteActivity() {
        lastActivityAtMs = System.currentTimeMillis()
    }

    fun onAppForeground() {
        _isForeground.value = true
        val bg = backgroundedAtMs
        backgroundedAtMs = null
        if (bg != null && System.currentTimeMillis() - bg >= timeoutMs) {
            // The lock lands first; the shell is already the unlock screen by the time
            // the shield lifts, so nothing is exposed in the gap.
            fireLock()
        } else {
            noteActivity()
        }
        _shielded.value = false
    }

    fun onAppBackground() {
        _isForeground.value = false
        backgroundedAtMs = System.currentTimeMillis()
        // Raised in `onPause`, before the system takes its Recents/snapshot frame.
        _shielded.value = true
    }

    /**
     * Autofill runs while the app is in the background, where the poll above never locks.
     * Before a fill trusts an unlocked vault, the background time counts against the
     * timeout as it would on return to the app; a vault unlocked from autofill alone starts
     * that clock now.
     */
    fun lockIfExpiredInBackground() {
        if (_isForeground.value || !session.isUnlocked) return
        val bg = backgroundedAtMs
        if (bg == null) {
            backgroundedAtMs = System.currentTimeMillis()
        } else if (System.currentTimeMillis() - bg >= timeoutMs) {
            backgroundedAtMs = null
            fireLock()
        }
    }

    /** The shield's OK affordance: clears the overlay once the window is interactive again. */
    fun dismissShield() {
        if (_isForeground.value) _shielded.value = false
    }

    fun requestLock() {
        fireLock()
    }

    private val timeoutMs: Long
        get() = prefs.autoLockMinutes.value * 60_000L

    private fun evaluateWhileActive() {
        if (!session.isUnlocked) return
        if (backgroundedAtMs != null) return
        if (System.currentTimeMillis() - lastActivityAtMs >= timeoutMs) {
            fireLock()
        }
    }

    private fun fireLock() {
        if (!session.isUnlocked) return
        bootstrap.onLogout()
        onLock?.invoke()
    }
}
