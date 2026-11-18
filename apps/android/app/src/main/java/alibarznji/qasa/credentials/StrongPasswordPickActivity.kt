package alibarznji.qasa.credentials

import android.app.PendingIntent
import android.app.assist.AssistStructure
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
 * The "Use a strong password" row's authentication (R4, docs/autofill-hardening.md): no UI,
 * no unlock (a fresh random password is no vault data). It exists so Qasa learns of the pick
 * the moment it happens: the offered password is marked picked in [GeneratedPasswordStore],
 * then the values go back to the framework, which fills them. The fill event history stays
 * as a fallback (QasaAutofillService.recordStrongPasswordPicks).
 *
 * Only the dataset id travels in the intent; the password is read back from the encrypted
 * store, and the fields are classified again from the screen's `AssistStructure`.
 */
@AndroidEntryPoint
class StrongPasswordPickActivity : ComponentActivity() {
    @Inject lateinit var generated: GeneratedPasswordStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dataset = runCatching { pickedDataset() }.getOrNull()
        if (dataset != null) {
            setResult(RESULT_OK, Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset))
        } else {
            setResult(RESULT_CANCELED)
        }
        finish()
        @Suppress("DEPRECATION") // overrideActivityTransition is API 34.
        overridePendingTransition(0, 0)
    }

    private fun pickedDataset(): Dataset? {
        val id = intent.getStringExtra(EXTRA_DATASET_ID) ?: return null
        val structure = IntentCompat.getParcelableExtra(intent, AutofillManager.EXTRA_ASSIST_STRUCTURE, AssistStructure::class.java)
            ?: return null
        var entry: GeneratedPassword? = null
        generated.update { current ->
            val (picked, next) = GeneratedPasswordLog.pick(current, id, System.currentTimeMillis())
            entry = picked
            next
        }
        val password = entry?.password ?: return null
        val form = AutofillStructureParser.parse(structure).form
        // R7: the fallback on a login password fills only that field (no confirm is known).
        val targets = IntentCompat.getParcelableArrayListExtra(intent, EXTRA_TARGETS, AutofillId::class.java)
        val values = if (targets.isNullOrEmpty()) {
            AutofillOffers.strongPasswordValues(form, password)
        } else {
            AutofillOffers.fallbackStrongPasswordValues(form, targets, password)
        }
        if (values.isEmpty()) return null
        @Suppress("DEPRECATION") // Dataset.Builder() without a presentation: the values are filled at once.
        val builder = Dataset.Builder().setId(id)
        @Suppress("DEPRECATION")
        for ((field, value) in values) builder.setValue(field, AutofillValue.forText(value))
        return builder.build()
    }

    companion object {
        private const val EXTRA_DATASET_ID = "datasetId"
        private const val EXTRA_TARGETS = "targets"

        /**
         * Carries only the dataset id, never the password. One per dataset (the id is the
         * request code); explicit, so nothing else can receive it; mutable, because the
         * framework adds the assist structure to it (as [AutofillAuthActivity.intentSender]).
         * [targets]: R7's fallback, the one login password to fill; empty for the new fields.
         */
        fun intentSender(context: Context, datasetId: String, targets: List<AutofillId> = emptyList()): IntentSender {
            val intent = Intent(context, StrongPasswordPickActivity::class.java).putExtra(EXTRA_DATASET_ID, datasetId)
            if (targets.isNotEmpty()) intent.putParcelableArrayListExtra(EXTRA_TARGETS, ArrayList(targets))
            val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            return PendingIntent.getActivity(context, datasetId.hashCode(), intent, mutable or PendingIntent.FLAG_CANCEL_CURRENT).intentSender
        }
    }
}
