package alibarznji.qasa.credentials

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.autofill.AutofillManager

/** The system autofill choice: whether Qasa is it, and the picker to make it so. */
object AutofillSettings {
    /** True only when Qasa is the selected autofill service (not just any service). */
    fun isQasaEnabled(context: Context): Boolean =
        runCatching { context.getSystemService(AutofillManager::class.java)?.hasEnabledAutofillServices() == true }
            .getOrDefault(false)

    /** Android's "Autofill service" picker with Qasa offered; system settings if a device lacks it. */
    fun openPicker(context: Context) {
        val intent = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
