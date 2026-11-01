package alibarznji.qasa.vault

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * The single `BiometricPrompt` entry point.
 *
 * There is deliberately no API here that returns "authenticated" without the system
 * prompt actually running: every privileged read in the app goes through this, and every
 * failure path reports `onDismissed` so callers fail closed.
 */
object BiometricGate {
    const val ALLOWED_AUTHENTICATORS = BIOMETRIC_STRONG or DEVICE_CREDENTIAL

    fun canAuthenticate(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(ALLOWED_AUTHENTICATORS) ==
            BiometricManager.BIOMETRIC_SUCCESS

    /** `BiometricPrompt` requires a `FragmentActivity`; returns false for any other host. */
    fun hostActivity(context: Context): FragmentActivity? {
        var current = context
        while (current is android.content.ContextWrapper) {
            if (current is FragmentActivity) return current
            current = current.baseContext
        }
        return null
    }
}

/**
 * Runs a real `BiometricPrompt` once, while [enabled] holds.
 *
 * [cipher] is passed through as a `CryptoObject` when the caller needs an authorized
 * cipher — that is how the master password is read out of the hardware-gated key, and
 * how it is written in. Without a cipher this is a pure presence check, which is what the
 * protected-note gate uses.
 *
 * Both callbacks are terminal for one attempt: [onDismissed] covers cancellation, lockout,
 * and an unenrolled sensor, so no screen can treat "no result" as success.
 */
@Composable
fun BiometricGateEffect(
    enabled: Boolean,
    title: String,
    subtitle: String? = null,
    cipher: javax.crypto.Cipher? = null,
    onAuthenticated: (javax.crypto.Cipher?) -> Unit,
    onDismissed: () -> Unit,
) {
    val context = LocalContext.current
    val onAuthenticatedState = rememberUpdatedState(onAuthenticated)
    val onDismissedState = rememberUpdatedState(onDismissed)
    // Whether this attempt requires a `CryptoObject`. Tracked as state rather than read
    // from the captured `cipher` so a re-composition cannot leave the callback judging a
    // stale requirement.
    val requiresCipher = rememberUpdatedState(cipher != null)

    val prompt = remember(context) {
        val host = BiometricGate.hostActivity(context) ?: return@remember null
        BiometricPrompt(
            host,
            ContextCompat.getMainExecutor(host),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (requiresCipher.value && result.cryptoObject?.cipher == null) {
                        // A cipher was required and the platform did not supply one:
                        // treat as a failure rather than an unverified success.
                        onDismissedState.value()
                    } else {
                        onAuthenticatedState.value(result.cryptoObject?.cipher)
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onDismissedState.value()
                }

                override fun onAuthenticationFailed() {
                    // A single unrecognised finger keeps the sheet open; the terminal
                    // paths are `onAuthenticationError` (lockout, cancel) above.
                }
            },
        )
    }

    LaunchedEffect(enabled, title, cipher, prompt) {
        if (!enabled) return@LaunchedEffect
        val activePrompt = prompt ?: run {
            onDismissedState.value()
            return@LaunchedEffect
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply { subtitle?.let { setSubtitle(it) } }
            // No negative button: it is illegal to combine one with DEVICE_CREDENTIAL,
            // and the system sheet already offers the device credential.
            .setAllowedAuthenticators(BiometricGate.ALLOWED_AUTHENTICATORS)
            .build()
        val crypto = cipher?.let { BiometricPrompt.CryptoObject(it) }
        runCatching {
            if (crypto != null) activePrompt.authenticate(info, crypto)
            else activePrompt.authenticate(info)
        }.onFailure { onDismissedState.value() }
    }
}
