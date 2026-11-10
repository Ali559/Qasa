package alibarznji.qasa.vault

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The pure half of [EphemeralClipboard], kept free of Android types so the ownership rule
 * can be unit tested.
 *
 * iOS checks `UIPasteboard.changeCount` before wiping, because clearing a clip the user
 * has since replaced destroys *their* data. Android has no change count, so the equivalent
 * is value identity: only wipe when the current primary clip still holds our own text.
 */
internal object ClipboardOwnership {
    /** True when the current clip is still the one we put there. */
    fun stillOwns(ownedValue: String?, currentValue: String?): Boolean =
        ownedValue != null && ownedValue == currentValue

    /** True when an expiry should actually clear the clipboard. */
    fun shouldWipe(ownedValue: String?, currentValue: String?): Boolean =
        stillOwns(ownedValue, currentValue)
}

/**
 * Clipboard writes that expire.
 *
 * Without a TTL a copied card number sits in the system clipboard — and its history —
 * until the user overwrites it, which is a real disclosure on a shared device. iOS routes
 * these copies through the 30s ephemeral drawer (`PaymentClipboard.swift:1`); this is the
 * Android equivalent for the paths that copy payment fields.
 */
@Singleton
class EphemeralClipboard @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val handler = Handler(Looper.getMainLooper())

    private var ownedValue: String? = null
    private var pendingWipe: Runnable? = null
    private var listening = false

    // The closest Android has to `UIPasteboard.changeCount`: we are told whenever the
    // primary clip changes, so a copy the user makes elsewhere retires our claim to own
    // the clipboard before the timer can reach it.
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { onClipReplaced() }

    /**
     * Copies [value] under [label] and schedules its removal in [ttlMillis].
     *
     * A second copy supersedes the first: only the newest value is ever wiped, so an
     * expiry can never reach back and clear a copy the user has since replaced.
     */
    fun copy(label: String, value: String, ttlMillis: Long = DEFAULT_TTL_MS) {
        val manager = clipboard() ?: return
        cancelPendingWipe()
        ownedValue = value
        manager.setPrimaryClip(ClipData.newPlainText(label, value))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !listening) {
            runCatching { manager.addPrimaryClipChangedListener(clipListener) }
                .onSuccess { listening = true }
        }
        schedule(ttlMillis)
    }

    /** Wipes immediately, but only if the current clip is still the one we wrote. */
    fun clearIfOwned() {
        val owned = ownedValue
        if (owned == null) {
            cancelPendingWipe()
            return
        }
        val manager = clipboard()
        val current = manager?.let { readCurrentText(it) }
        if (manager != null && current != null) {
            // A readable clipboard is a verdict: either we still own it and wipe, or the
            // user replaced it and we must not touch it.
            if (ClipboardOwnership.shouldWipe(owned, current)) {
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        manager.clearPrimaryClip()
                    } else {
                        // clearPrimaryClip is API 28; on Android 8 overwrite with an empty clip.
                        manager.setPrimaryClip(ClipData.newPlainText("", ""))
                    }
                }
            }
            release()
        } else {
            // Android 10+ denies clipboard reads to a backgrounded app. Retrying is safe
            // because the verdict is only ever applied when we can read the clip.
            schedule(RETRY_MS)
        }
    }

    private fun onClipReplaced() {
        val manager = clipboard() ?: return
        val current = readCurrentText(manager) ?: return
        if (!ClipboardOwnership.stillOwns(ownedValue, current)) release()
    }

    private fun release() {
        ownedValue = null
        cancelPendingWipe()
        if (listening) {
            clipboard()?.let { manager ->
                runCatching { manager.removePrimaryClipChangedListener(clipListener) }
            }
            listening = false
        }
    }

    private fun schedule(delayMs: Long) {
        cancelPendingWipe()
        val wipe = Runnable { clearIfOwned() }
        pendingWipe = wipe
        handler.postDelayed(wipe, delayMs)
    }

    private fun cancelPendingWipe() {
        pendingWipe?.let { handler.removeCallbacks(it) }
        pendingWipe = null
    }

    private fun clipboard(): ClipboardManager? =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

    /** `null` means "cannot tell right now" — never conflate that with "not ours". */
    private fun readCurrentText(manager: ClipboardManager): String? = runCatching {
        manager.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
    }.getOrNull()

    companion object {
        /** iOS `EphemeralSecretDrawer` runs the same 30 seconds. */
        const val DEFAULT_TTL_MS = 30_000L
        private const val RETRY_MS = 2_000L
    }
}
