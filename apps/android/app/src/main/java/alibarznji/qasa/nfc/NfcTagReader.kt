package alibarznji.qasa.nfc

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.IsoDep
import android.os.Bundle
import android.util.Log
import java.io.IOException

/** Whether this phone can read a chip right now. */
enum class NfcAvailability { Missing, Off, Ready }

fun nfcAvailability(context: Context): NfcAvailability {
    val adapter = NfcAdapter.getDefaultAdapter(context) ?: return NfcAvailability.Missing
    return if (adapter.isEnabled) NfcAvailability.Ready else NfcAvailability.Off
}

/** The hosting activity behind a Compose `LocalContext` (which may be a wrapper). */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Reader mode on the hosting activity: while enabled, every NFC-A/B tag that comes near
 * goes to [onTag] on a binder thread (where the blocking reads belong) instead of starting
 * another app or the system's "no app supports this tag" message. NDEF checks and the
 * platform sound are skipped; the sheet gives the feedback (status line and a haptic on
 * success).
 *
 * The sheet keeps reader mode on for as long as it is open, also while it is not ready to
 * read (details missing, after a failure): a card resting on the phone would otherwise go
 * to the system dispatcher the moment reader mode turned off.
 */
object NfcTagReader {
    private const val TAG = "QasaNfc"

    /** Extra connects after the link drops mid-read (the chip often answers again at once). */
    private const val RETRIES = 2

    fun enable(activity: Activity, onTag: (Tag) -> Unit): Boolean {
        val adapter = NfcAdapter.getDefaultAdapter(activity) ?: return false
        if (!adapter.isEnabled) return false
        val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS
        // Check presence less often than the default 125 ms: a passport read is many
        // round trips and a presence check in between slows it and can drop the link.
        val extras = Bundle().apply { putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 1000) }
        // Throws IllegalStateException when the activity is not in the foreground.
        val result = runCatching { adapter.enableReaderMode(activity, { tag -> onTag(tag) }, flags, extras) }
        result.exceptionOrNull()?.let { Log.w(TAG, "reader mode not enabled", it) }
        return result.isSuccess
    }

    fun disable(activity: Activity) {
        runCatching { NfcAdapter.getDefaultAdapter(activity)?.disableReaderMode(activity) }
    }

    /** Payment card: PAN and expiry. Blocking; call off the main thread. */
    fun readCard(tag: Tag): EmvCard = withIsoDep(tag) { EmvReader(it).read() }

    /** Passport / ID card DG1 over BAC. Blocking; call off the main thread. */
    fun readMrtd(tag: Tag, mrzInfo: String): MrzData = withIsoDep(tag) { MrtdReader(it, Bac(mrzInfo)).readDg1() }

    /**
     * Connects and runs [block] from the start; when the link drops ([NfcFailure.Lost]) it
     * reconnects and starts over, up to [RETRIES] more times. A read is a few dozen short
     * exchanges and BAC starts fresh each time, so restarting is always safe. Only the
     * failure kind and status words are logged, never card data.
     */
    private fun <T> withIsoDep(tag: Tag, block: (CardChannel) -> T): T {
        val iso = IsoDep.get(tag) ?: throw NfcReadException(NfcFailure.Unsupported, "not ISO-DEP")
        Log.i(TAG, "tag ${tag.techList.joinToString { it.substringAfterLast('.') }}, max ${iso.maxTransceiveLength}")
        var attempt = 0
        try {
            while (true) {
                try {
                    return attemptOnce(iso, block)
                } catch (e: NfcReadException) {
                    Log.w(TAG, "attempt ${attempt + 1}: ${e.failure} ${e.message}", e.cause)
                    if (e.failure != NfcFailure.Lost || attempt >= RETRIES) throw e
                    attempt++
                    runCatching { iso.close() }
                }
            }
        } finally {
            runCatching { iso.close() }
        }
    }

    private fun <T> attemptOnce(iso: IsoDep, block: (CardChannel) -> T): T {
        try {
            if (!iso.isConnected) iso.connect()
            // Generous: some ID card chips take over a second for a 3DES step.
            iso.timeout = 10_000
            return block(
                CardChannel { cmd ->
                    try {
                        iso.transceive(cmd)
                    } catch (e: TagLostException) {
                        throw NfcReadException(NfcFailure.Lost, cause = e)
                    } catch (e: IOException) {
                        throw NfcReadException(NfcFailure.Lost, cause = e)
                    }
                },
            )
        } catch (e: NfcReadException) {
            throw e
        } catch (e: IOException) {
            throw NfcReadException(NfcFailure.Lost, cause = e)
        } catch (e: SecurityException) {
            // Tag went stale (out of date) between discovery and connect.
            throw NfcReadException(NfcFailure.Lost, cause = e)
        } catch (e: TlvException) {
            throw NfcReadException(NfcFailure.Unsupported, cause = e)
        } catch (e: IllegalStateException) {
            throw NfcReadException(NfcFailure.Lost, cause = e)
        }
    }
}
