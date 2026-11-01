package alibarznji.qasa.credentials

import android.app.assist.AssistStructure
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Classic AutofillService for form fill coverage.
 * Unlock happens in the main app; index is encrypted with vault enc key.
 * Full biometric unlock UI in fill path can be expanded; here we offer datasets when vault session is unlocked.
 */
@AndroidEntryPoint
class QasaAutofillService : AutofillService() {
    @Inject lateinit var indexStore: AutofillIndexStore
    @Inject lateinit var session: alibarznji.qasa.vault.VaultSession

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback,
    ) {
        if (!session.isUnlocked) {
            callback.onSuccess(null)
            return
        }
        val structure = request.fillContexts.lastOrNull()?.structure
        if (structure == null) {
            callback.onSuccess(null)
            return
        }
        val fields = mutableListOf<Pair<AutofillId, String>>()
        parseStructure(structure, fields)
        val passwordId = fields.find { it.second.contains("password", true) }?.first
        val userId = fields.find {
            it.second.contains("user", true) || it.second.contains("email", true)
        }?.first
        if (passwordId == null && userId == null) {
            callback.onSuccess(null)
            return
        }
        val response = FillResponse.Builder()
        for (entry in indexStore.read().filter { it.kind == "login" }.take(5)) {
            val secrets = indexStore.decryptSecrets(entry) ?: continue
            val presentation = RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
                setTextViewText(android.R.id.text1, entry.title)
            }
            val dataset = Dataset.Builder(presentation)
            userId?.let { dataset.setValue(it, AutofillValue.forText(secrets.username.orEmpty())) }
            passwordId?.let { dataset.setValue(it, AutofillValue.forText(secrets.password.orEmpty())) }
            response.addDataset(dataset.build())
        }
        try {
            callback.onSuccess(response.build())
        } catch (_: Exception) {
            callback.onSuccess(null)
        }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        callback.onSuccess()
    }

    private fun parseStructure(structure: AssistStructure, out: MutableList<Pair<AutofillId, String>>) {
        for (i in 0 until structure.windowNodeCount) {
            walk(structure.getWindowNodeAt(i).rootViewNode, out)
        }
    }

    private fun walk(node: AssistStructure.ViewNode, out: MutableList<Pair<AutofillId, String>>) {
        val id = node.autofillId
        val hint = listOfNotNull(node.hint, node.autofillHints?.joinToString(), node.htmlInfo?.tag).joinToString(" ")
        if (id != null && hint.isNotBlank()) out.add(id to hint)
        for (i in 0 until node.childCount) walk(node.getChildAt(i), out)
    }
}
