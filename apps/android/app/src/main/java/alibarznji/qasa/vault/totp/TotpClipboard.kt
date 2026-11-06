package alibarznji.qasa.vault.totp

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle

/**
 * Copies a 2FA code the way the app copies passwords: a plain clip, flagged sensitive so
 * Android 13+ hides the preview (same as `RotatePasswordSheet.copyPassword`). The code is
 * only good for one period, so there is no expiry timer on top.
 */
internal object TotpClipboard {
    fun copy(context: Context, code: String) {
        val clip = ClipData.newPlainText("2FA code", code)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
        }
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(clip)
    }
}
