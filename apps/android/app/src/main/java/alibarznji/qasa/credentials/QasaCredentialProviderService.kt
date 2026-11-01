package alibarznji.qasa.credentials


import android.app.PendingIntent
import android.content.Intent
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.provider.BeginGetCredentialRequest
import androidx.credentials.provider.BeginGetCredentialResponse
import androidx.credentials.provider.BeginGetPasswordOption
import androidx.credentials.provider.CredentialProviderService
import androidx.credentials.provider.PasswordCredentialEntry
import androidx.credentials.provider.ProviderClearCredentialStateRequest
import alibarznji.qasa.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Credential Manager provider — Android counterpart to iOS ASCredentialProvider.
 * Entries open MainActivity for unlock/selection (PendingIntent).
 */
@AndroidEntryPoint
class QasaCredentialProviderService : CredentialProviderService() {
    @Inject lateinit var indexStore: AutofillIndexStore

    override fun onBeginGetCredentialRequest(
        request: BeginGetCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginGetCredentialResponse, GetCredentialException>,
    ) {
        val entries = mutableListOf<PasswordCredentialEntry>()
        val pending = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        for (option in request.beginGetCredentialOptions) {
            if (option is BeginGetPasswordOption) {
                for (entry in indexStore.read().filter { it.kind == "login" }.take(10)) {
                    entries.add(
                        PasswordCredentialEntry.Builder(
                            context = this,
                            username = entry.title,
                            pendingIntent = pending,
                            beginGetPasswordOption = option,
                        ).setDisplayName(entry.subtitle).build(),
                    )
                }
            }
        }
        callback.onResult(BeginGetCredentialResponse.Builder().setCredentialEntries(entries).build())
    }

    override fun onBeginCreateCredentialRequest(
        request: androidx.credentials.provider.BeginCreateCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<androidx.credentials.provider.BeginCreateCredentialResponse, CreateCredentialException>,
    ) {
        callback.onResult(
            androidx.credentials.provider.BeginCreateCredentialResponse.Builder()
                .setCreateEntries(emptyList())
                .build(),
        )
    }

    override fun onClearCredentialStateRequest(
        request: ProviderClearCredentialStateRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<Void?, ClearCredentialException>,
    ) {
        callback.onResult(null)
    }
}
