package alibarznji.qasa.credentials

import alibarznji.qasa.prefs.AppPreferences
import alibarznji.qasa.prefs.LocaleContext
import alibarznji.qasa.services.AutoLockMonitor
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.QasaTheme
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.vault.VaultSession
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.credentials.GetCredentialResponse
import androidx.credentials.PasswordCredential
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.provider.PendingIntentHandler
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * A Credential Manager pick: unlocks (or confirms, per docs/autofill-plan.md §1.3) and
 * returns the login's username and password to the calling app.
 */
@AndroidEntryPoint
class CredentialPickActivity : FragmentActivity() {
    @Inject lateinit var session: VaultSession
    @Inject lateinit var indexStore: AutofillIndexStore
    @Inject lateinit var prefs: AppPreferences
    @Inject lateinit var autoLock: AutoLockMonitor

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleContext.wrap(newBase, AppPreferences.storedLanguage(newBase)))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        val request = PendingIntentHandler.retrieveProviderGetCredentialRequest(intent) ?: return cancel()
        val entryId = intent.getStringExtra(EXTRA_ENTRY_ID) ?: return cancel()
        val entry = indexStore.read().firstOrNull { it.id == entryId && it.kind == AutofillIndexStore.KIND_LOGIN } ?: return cancel()
        autoLock.lockIfExpiredInBackground()
        if (!AutofillPolicy.needsAuth(entry.kind, session.isUnlocked, prefs.autofillConfirmEveryFill.value)) return finishWith(entry)
        val label = request.callingAppInfo.packageName
        setContent {
            val theme by prefs.themeMode.collectAsState()
            val language by prefs.language.collectAsState()
            QasaTheme(themeMode = theme, language = language) {
                Column(
                    Modifier.fillMaxSize().background(qasaColors.background).safeDrawingPadding().imePadding()
                        .padding(QasaDimens.screenPadding),
                ) {
                    AutofillUnlockPane(session, label, onCancel = ::cancel, onUnlocked = {
                        autoLock.lockIfExpiredInBackground()
                        finishWith(entry)
                    })
                }
            }
        }
    }

    private fun finishWith(entry: AutofillIndexEntry) {
        val secrets = indexStore.decryptSecrets(entry)
        val password = secrets?.password?.takeIf { it.isNotEmpty() } ?: return cancel()
        val result = Intent()
        PendingIntentHandler.setGetCredentialResponse(
            result,
            GetCredentialResponse(PasswordCredential(secrets.username.orEmpty().ifEmpty { entry.title }, password)),
        )
        setResult(RESULT_OK, result)
        finish()
    }

    private fun cancel() {
        val result = Intent()
        PendingIntentHandler.setGetCredentialException(result, GetCredentialCancellationException())
        setResult(RESULT_CANCELED, result)
        finish()
    }

    companion object {
        const val EXTRA_ENTRY_ID = "entryId"
    }
}
