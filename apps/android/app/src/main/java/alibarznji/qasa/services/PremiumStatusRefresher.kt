package alibarznji.qasa.services

import alibarznji.qasa.license.LicenseManager
import alibarznji.qasa.payments.SubscriptionService
import alibarznji.qasa.screens.AppSession
import alibarznji.qasa.vault.VaultSession
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** When to re-sync `AppSession.isPremium` while the vault stays unlocked. Pure. */
object PremiumRefreshPolicy {
    /** A foreground re-sync runs at most this often. */
    const val FOREGROUND_THROTTLE_MS = 15 * 60 * 1000L

    /** The expiry check runs this long after the expiry, so the server has moved on. */
    const val EXPIRY_GRACE_MS = 60 * 1000L

    /**
     * When the known expiry is already behind us (the server had not renewed yet, or we
     * were offline), the next check waits this long instead of spinning.
     */
    const val PASSED_EXPIRY_RETRY_MS = 15 * 60 * 1000L

    /**
     * The earliest known end of Premium: the license token's `exp` and the subscription's
     * `expires_at` (the token lives 30 days and only says what the tier was at issue).
     */
    fun effectiveExpiryMs(tokenExpMs: Long?, subscriptionExpiryMs: Long?): Long? =
        listOfNotNull(tokenExpMs, subscriptionExpiryMs).minOrNull()

    /** Delay until the expiry re-sync, or null when nothing needs scheduling (Free, no expiry). */
    fun expiryCheckDelayMs(nowMs: Long, isPremium: Boolean, expiryMs: Long?): Long? {
        if (!isPremium || expiryMs == null) return null
        val due = expiryMs + EXPIRY_GRACE_MS
        return if (due > nowMs) due - nowMs else PASSED_EXPIRY_RETRY_MS
    }

    /**
     * Whether coming back to the foreground re-syncs. [lastSyncMs] null means the unlock
     * bootstrap has not synced yet; it owns the first sync. Otherwise: every
     * [FOREGROUND_THROTTLE_MS], and always once the known expiry passed since the last sync.
     */
    fun shouldRefreshOnForeground(nowMs: Long, lastSyncMs: Long?, expiryMs: Long?): Boolean {
        if (lastSyncMs == null) return false
        if (expiryMs != null && lastSyncMs < expiryMs && nowMs >= expiryMs) return true
        return nowMs - lastSyncMs >= FOREGROUND_THROTTLE_MS
    }

    /**
     * A subscription expiry worth scheduling on: parsed from ISO-8601, and still ahead.
     * One already past (a stale record, a renewal the server has not booked) would only
     * make the check retry for nothing; the token expiry and foreground re-syncs cover it.
     */
    fun subscriptionExpiryMs(iso: String?, nowMs: Long): Long? {
        if (iso.isNullOrBlank()) return null
        val ms = try {
            OffsetDateTime.parse(iso).toInstant().toEpochMilli()
        } catch (_: Exception) {
            return null
        }
        return ms.takeIf { it > nowMs }
    }
}

/**
 * Keeps `AppSession.isPremium` current mid-session: a re-sync at the known Premium expiry,
 * and on return to the foreground (throttled). Offline-safe: a failed refresh keeps the
 * stored, verified license, so a paid user is not dropped to Free by a bad connection.
 */
@Singleton
class PremiumStatusRefresher @Inject constructor(
    private val session: VaultSession,
    private val license: LicenseManager,
    private val subscriptions: SubscriptionService,
    private val appSession: AppSession,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()

    @Volatile private var lastSyncMs: Long? = null
    @Volatile private var subscriptionExpiryMs: Long? = null
    @Volatile private var timer: Job? = null

    init {
        // A purchase, cancel or device change applies a new token outside this class:
        // move the expiry check to follow it.
        scope.launch {
            license.parsed.collect {
                if (lastSyncMs != null) mutex.withLock { schedule() }
            }
        }
    }

    /**
     * The unlock bootstrap just synced `AppSession`: remember when, and schedule the expiry
     * check. The subscription lookup runs in the background so it never holds up the unlock.
     */
    fun onUnlockSynced() {
        lastSyncMs = now()
        subscriptionExpiryMs = null
        scope.launch {
            mutex.withLock {
                if (!session.isUnlocked) return@withLock
                subscriptionExpiryMs = if (license.isPremium) fetchSubscriptionExpiry() else null
                schedule()
            }
        }
    }

    /** Activity `onStart`. */
    fun onForeground() {
        if (!session.isUnlocked) return
        if (!PremiumRefreshPolicy.shouldRefreshOnForeground(now(), lastSyncMs, knownExpiryMs())) return
        scope.launch { resync() }
    }

    /** Lock, logout, erase: nothing runs until the next unlock. */
    fun stop() {
        timer?.cancel()
        timer = null
        lastSyncMs = null
        subscriptionExpiryMs = null
    }

    private suspend fun resync() = mutex.withLock {
        if (!session.isUnlocked) return@withLock
        // `refresh` only replaces the stored token with a newer verified one; on failure
        // the stored token stays and is re-read, never cleared.
        if (!license.refresh()) license.reloadFromStore()
        if (!session.isUnlocked) return@withLock
        appSession.syncFromLicense(license)
        lastSyncMs = now()
        subscriptionExpiryMs = if (license.isPremium) {
            fetchSubscriptionExpiry() ?: subscriptionExpiryMs?.takeIf { it > now() }
        } else {
            null
        }
        schedule()
    }

    private suspend fun schedule() {
        val current = currentCoroutineContext()[Job]
        timer?.takeIf { it != current }?.cancel()
        timer = null
        if (!session.isUnlocked) return
        val wait = PremiumRefreshPolicy.expiryCheckDelayMs(now(), license.isPremium, knownExpiryMs())
            ?: return
        timer = scope.launch {
            delay(wait)
            resync()
        }
    }

    private fun knownExpiryMs(): Long? {
        if (!license.isPremium) return null
        val tokenExpMs = license.parsed.value?.expiresAtEpoch?.let { it * 1000 }
        return PremiumRefreshPolicy.effectiveExpiryMs(tokenExpMs, subscriptionExpiryMs)
    }

    private suspend fun fetchSubscriptionExpiry(): Long? = try {
        PremiumRefreshPolicy.subscriptionExpiryMs(subscriptions.current()?.expires_at, now())
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        // Offline or no subscription endpoint: the token expiry still schedules a check.
        null
    }

    private fun now(): Long = System.currentTimeMillis()
}
