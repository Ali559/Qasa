package alibarznji.qasa.credentials

import alibarznji.qasa.vault.totp.Totp
import alibarznji.qasa.vault.totp.TotpClipboard
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.os.Build
import android.os.Bundle
import android.service.autofill.Dataset
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import androidx.activity.ComponentActivity
import androidx.core.content.IntentCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Picking a login that has a 2FA code: the autofill dataset is "authenticated" through
 * this invisible activity so the code is copied when the user actually chooses the login,
 * not when the field merely asks for suggestions. It rebuilds the dataset from the
 * encrypted index, copies the current code (sensitive, like a password copy) so it can be
 * pasted on the next screen, and hands the dataset back to the framework to fill.
 */
@AndroidEntryPoint
class AutofillTotpActivity : ComponentActivity() {
    @Inject lateinit var indexStore: AutofillIndexStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dataset = buildDataset()
        if (dataset == null) {
            setResult(RESULT_CANCELED)
        } else {
            setResult(RESULT_OK, Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset))
        }
        finish()
    }

    private fun buildDataset(): Dataset? {
        val entryId = intent.getStringExtra(EXTRA_ENTRY_ID) ?: return null
        val entry = indexStore.read().firstOrNull { it.id == entryId } ?: return null
        // Fails closed when the vault locked between the suggestion and the tap.
        val secrets = indexStore.decryptSecrets(entry) ?: return null
        val userId = IntentCompat.getParcelableExtra(intent, EXTRA_USER_ID, AutofillId::class.java)
        val passwordId = IntentCompat.getParcelableExtra(intent, EXTRA_PASSWORD_ID, AutofillId::class.java)
        if (userId == null && passwordId == null) return null
        secrets.totp?.let(Totp::currentCode)?.let { TotpClipboard.copy(this, it) }
        val dataset = Dataset.Builder()
        userId?.let { dataset.setValue(it, AutofillValue.forText(secrets.username.orEmpty())) }
        passwordId?.let { dataset.setValue(it, AutofillValue.forText(secrets.password.orEmpty())) }
        return dataset.build()
    }

    companion object {
        private const val EXTRA_ENTRY_ID = "entryId"
        private const val EXTRA_USER_ID = "userId"
        private const val EXTRA_PASSWORD_ID = "passwordId"

        /** The dataset's authentication: carries only the index id and the field ids, no secrets. */
        fun intentSender(
            context: Context,
            entryId: String,
            userId: AutofillId?,
            passwordId: AutofillId?,
        ): IntentSender {
            val intent = Intent(context, AutofillTotpActivity::class.java)
                .putExtra(EXTRA_ENTRY_ID, entryId)
                .putExtra(EXTRA_USER_ID, userId)
                .putExtra(EXTRA_PASSWORD_ID, passwordId)
            // The framework adds the assist structure to the intent, so it must be mutable;
            // it is explicit, so nothing else can receive it.
            val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            return PendingIntent.getActivity(
                context,
                entryId.hashCode(),
                intent,
                mutable or PendingIntent.FLAG_CANCEL_CURRENT,
            ).intentSender
        }
    }
}
