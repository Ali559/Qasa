package alibarznji.qasa.credentials

import android.app.PendingIntent
import android.content.Intent
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.provider.BeginCreateCredentialRequest
import androidx.credentials.provider.BeginCreateCredentialResponse
import androidx.credentials.provider.BeginGetCredentialRequest
import androidx.credentials.provider.BeginGetCredentialResponse
import androidx.credentials.provider.BeginGetPasswordOption
import androidx.credentials.provider.CallingAppInfo
import androidx.credentials.provider.CredentialProviderService
import androidx.credentials.provider.PasswordCredentialEntry
import androidx.credentials.provider.ProviderClearCredentialStateRequest
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Credential Manager provider, the Android counterpart of the iOS credential provider.
 * Offers the logins that match the calling app or site ([AutofillMatcher]); a pick opens
 * [CredentialPickActivity], which unlocks and returns the password.
 */
@AndroidEntryPoint
class QasaCredentialProviderService : CredentialProviderService() {
    @Inject lateinit var indexStore: AutofillIndexStore

    override fun onBeginGetCredentialRequest(
        request: BeginGetCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginGetCredentialResponse, GetCredentialException>,
    ) {
        val caller = callerOf(request.callingAppInfo)
        val logins = indexStore.read().filter { it.kind == AutofillIndexStore.KIND_LOGIN }
        val matches = caller?.let { c -> AutofillMatcher.rank(logins, c) { it.website ?: it.websiteHost } }.orEmpty()
        val entries = mutableListOf<PasswordCredentialEntry>()
        for (option in request.beginGetCredentialOptions.filterIsInstance<BeginGetPasswordOption>()) {
            for (entry in matches.take(10)) {
                val intent = Intent(this, CredentialPickActivity::class.java).putExtra(CredentialPickActivity.EXTRA_ENTRY_ID, entry.id)
                val pending = PendingIntent.getActivity(
                    this,
                    entry.id.hashCode(),
                    intent,
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                entries += PasswordCredentialEntry.Builder(
                    context = this,
                    // The username stays in the encrypted part of the index; the title names the login.
                    username = entry.title,
                    pendingIntent = pending,
                    beginGetPasswordOption = option,
                ).setDisplayName(AutofillPresentation.hint(entry)).build()
            }
        }
        callback.onResult(BeginGetCredentialResponse.Builder().setCredentialEntries(entries).build())
    }

    /** A privileged browser passes the page's origin; any other caller is its package. */
    private fun callerOf(info: CallingAppInfo?): AutofillCaller? {
        info ?: return null
        val origin = runCatching { info.origin }.getOrNull()
        return if (origin != null && info.packageName in AutofillMatcher.trustedBrowsers) {
            AutofillMatcher.hostOf(origin)?.let { AutofillCaller.Web(it, info.packageName) }
        } else {
            AutofillCaller.App(info.packageName)
        }
    }

    override fun onBeginCreateCredentialRequest(
        request: BeginCreateCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginCreateCredentialResponse, CreateCredentialException>,
    ) {
        callback.onResult(BeginCreateCredentialResponse.Builder().setCreateEntries(emptyList()).build())
    }

    override fun onClearCredentialStateRequest(
        request: ProviderClearCredentialStateRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<Void?, ClearCredentialException>,
    ) {
        callback.onResult(null)
    }
}
