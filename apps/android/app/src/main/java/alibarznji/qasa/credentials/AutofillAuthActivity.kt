package alibarznji.qasa.credentials

import alibarznji.qasa.R
import alibarznji.qasa.prefs.AppPreferences
import alibarznji.qasa.prefs.LocaleContext
import alibarznji.qasa.services.AutoLockMonitor
import alibarznji.qasa.theme.PrimaryButton
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.QasaTheme
import alibarznji.qasa.theme.SecondaryButton
import alibarznji.qasa.theme.SectionLabel
import alibarznji.qasa.theme.ThemedTextField
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import alibarznji.qasa.vault.BiometricGateEffect
import alibarznji.qasa.vault.BiometricPolicy
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.saveable.rememberSaveable
import alibarznji.qasa.vault.VaultSession
import alibarznji.qasa.vault.totp.Totp
import alibarznji.qasa.vault.totp.TotpClipboard
import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.os.Build
import android.os.Bundle
import android.service.autofill.Dataset
import android.view.WindowManager
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.core.content.IntentCompat
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.rememberCoroutineScope

/**
 * Every autofill dataset is authenticated through this activity (docs/autofill-plan.md
 * §3.3): the suggestion carries only a title, and the values are built here, after the
 * user picked it and, when §1.3 says so, confirmed with biometrics or the master password.
 *
 * - A locked vault unlocks here; biometrics start on their own when they are set up.
 * - A pick that needs no confirmation (an unlocked vault, a login) returns at once.
 * - The "Search Qasa" suggestion has no item: after the unlock the user picks one here.
 *
 * The framework hands back the screen's `AssistStructure`, so the fields are classified
 * again here and nothing but the item id and group travels in the intent.
 */
@AndroidEntryPoint
class AutofillAuthActivity : FragmentActivity() {
    @Inject lateinit var session: VaultSession
    @Inject lateinit var indexStore: AutofillIndexStore
    @Inject lateinit var prefs: AppPreferences
    @Inject lateinit var autoLock: AutoLockMonitor

    private lateinit var screen: ParsedScreen
    private lateinit var group: AutofillGroup

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleContext.wrap(newBase, AppPreferences.storedLanguage(newBase)))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The vault's contents are on screen: keep them out of screenshots and Recents.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        val structure = IntentCompat.getParcelableExtra(intent, AutofillManager.EXTRA_ASSIST_STRUCTURE, AssistStructure::class.java)
        group = intent.getStringExtra(EXTRA_GROUP)?.let { runCatching { AutofillGroup.valueOf(it) }.getOrNull() } ?: run { cancel(); return }
        if (structure == null) { cancel(); return }
        screen = AutofillStructureParser.parse(structure)
        autoLock.lockIfExpiredInBackground()

        val entryId = intent.getStringExtra(EXTRA_ENTRY_ID)
        val entry = entryId?.let { id -> indexStore.read().firstOrNull { it.id == id } }
        if (entryId != null && entry == null) { cancel(); return }
        val confirm = AutofillPolicy.needsAuth(entry?.kind, session.isUnlocked, prefs.autofillConfirmEveryFill.value)
        if (entry != null && !confirm) {
            finishWith(entry)
            return
        }
        setContent {
            val theme by prefs.themeMode.collectAsState()
            val language by prefs.language.collectAsState()
            QasaTheme(themeMode = theme, language = language) {
                AuthFlow(initial = entry, needsAuth = confirm)
            }
        }
    }

    @Composable
    private fun AuthFlow(initial: AutofillIndexEntry?, needsAuth: Boolean) {
        var authed by remember { mutableStateOf(!needsAuth) }
        val c = qasaColors
        Column(
            Modifier
                .fillMaxSize()
                .background(c.background)
                .safeDrawingPadding()
                .imePadding()
                .padding(QasaDimens.screenPadding),
        ) {
            if (!authed) {
                AutofillUnlockPane(session, screen.caller.label, onCancel = ::cancel, onUnlocked = {
                    autoLock.lockIfExpiredInBackground()
                    authed = true
                    if (initial != null) finishWith(initial)
                })
            } else if (initial == null) {
                PickerPane(onPick = ::finishWith)
            }
        }
    }

    /** "Search Qasa": every item of the group, the caller's own logins first. */
    @Composable
    private fun ColumnScope.PickerPane(onPick: (AutofillIndexEntry) -> Unit) {
        val c = qasaColors
        var query by remember { mutableStateOf("") }
        val all = remember { candidates() }
        val shown = all.filter { query.isBlank() || it.title.contains(query, true) || it.subtitle.contains(query, true) || (it.websiteHost?.contains(query, true) == true) }
        Text(stringResource(R.string.autofill_title), color = c.textPrimary, style = qasaText.displayLarge)
        Spacer(Modifier.height(QasaDimens.contentGap))
        Text(stringResource(R.string.autofill_fillinto, screen.caller.label), color = c.textSecondary, style = qasaText.bodyMedium)
        Spacer(Modifier.height(QasaDimens.gap))
        ThemedTextField(value = query, onValueChange = { query = it }, placeholder = stringResource(R.string.autofill_search))
        Spacer(Modifier.height(QasaDimens.gap))
        if (shown.isEmpty()) {
            Text(stringResource(R.string.autofill_nomatches), color = c.textPrimary, style = qasaText.rowTitle)
            Text(stringResource(R.string.autofill_nomatcheshint), color = c.textSecondary, style = qasaText.bodyMedium)
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
            items(shown, key = { it.id }) { entry ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(QasaDimens.cornerRadius))
                        .clickable { onPick(entry) }
                        .padding(vertical = QasaDimens.contentGap),
                ) {
                    Text(entry.title, color = c.textPrimary, style = qasaText.rowTitle)
                    AutofillPresentation.hint(entry).takeIf { it.isNotBlank() }?.let {
                        Text(it, color = c.textSecondary, style = qasaText.bodyMedium)
                    }
                }
                HorizontalDivider(color = c.stroke)
            }
        }
        Spacer(Modifier.height(QasaDimens.gap))
        SecondaryButton(title = stringResource(R.string.common_cancel), onClick = ::cancel, modifier = Modifier.fillMaxWidth())
    }

    private fun candidates(): List<AutofillIndexEntry> {
        val entries = indexStore.read()
        return when (group) {
            AutofillGroup.Login -> {
                val logins = entries.filter { it.kind == AutofillIndexStore.KIND_LOGIN }
                val ranked = AutofillMatcher.rank(logins, screen.caller) { it.website ?: it.websiteHost }
                ranked + (logins - ranked.toSet()).sortedBy { it.title.lowercase() }
            }
            AutofillGroup.Card -> entries.filter { it.kind == AutofillIndexStore.KIND_CARD }
            AutofillGroup.Identity -> entries.filter { it.kind in AutofillIndexStore.IDENTITY_KINDS }
                .sortedBy { AutofillIndexStore.IDENTITY_KINDS.indexOf(it.kind) }
        }
    }

    /** Builds the real dataset for [entry] and hands it to the framework, which fills it. */
    private fun finishWith(entry: AutofillIndexEntry) {
        val dataset = AutofillDatasets.filled(this, indexStore, screen, group, entry)
        if (dataset == null) { cancel(); return }
        setResult(RESULT_OK, Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset))
        finish()
    }

    private fun cancel() {
        setResult(RESULT_CANCELED)
        finish()
    }

    companion object {
        private const val EXTRA_ENTRY_ID = "entryId"
        private const val EXTRA_GROUP = "group"

        /**
         * The dataset's authentication: carries only the index id (null for "Search Qasa")
         * and the group, never a secret. Explicit, so nothing else can receive it; mutable,
         * because the framework adds the assist structure to it.
         */
        fun intentSender(context: Context, group: AutofillGroup, entryId: String?): IntentSender {
            val intent = Intent(context, AutofillAuthActivity::class.java)
                .putExtra(EXTRA_GROUP, group.name)
                .putExtra(EXTRA_ENTRY_ID, entryId)
            val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            return PendingIntent.getActivity(
                context,
                (group.name + (entryId ?: "")).hashCode(),
                intent,
                mutable or PendingIntent.FLAG_CANCEL_CURRENT,
            ).intentSender
        }
    }
}

/** Turns a pick into the dataset the framework fills (shared by the service and the activity). */
object AutofillDatasets {
    fun filled(
        context: Context,
        indexStore: AutofillIndexStore,
        screen: ParsedScreen,
        group: AutofillGroup,
        entry: AutofillIndexEntry,
    ): Dataset? {
        // A login only ever fills where it matches, even when picked from "Search Qasa"
        // on an app it was never saved for: then the user chose it, which is the point.
        val secrets = indexStore.decryptSecrets(entry) ?: return null
        val fields = screen.form.group(group)
        val profile = if (group == AutofillGroup.Identity) {
            val entries = indexStore.read()
            AutofillIndexStore.IDENTITY_KINDS
                .filter { it != entry.kind }
                .mapNotNull { kind -> entries.firstOrNull { it.kind == kind } }
                .mapNotNull(indexStore::decryptSecrets)
        } else {
            emptyList()
        }
        val values = AutofillValues.valuesFor(fields, secrets, profile).toMutableMap()
        if (group == AutofillGroup.Login) {
            secrets.totp?.let(Totp::currentCode)?.let { code ->
                val otp = fields.firstOrNull { it.type == AutofillFieldType.OneTimeCode }
                if (otp != null) values[otp.id] = FieldValue.Text(code) else TotpClipboard.copy(context, code)
            }
        }
        if (values.isEmpty()) return null
        @Suppress("DEPRECATION") // Dataset.Builder() without a presentation: the values are filled at once.
        val builder = Dataset.Builder()
        for ((id, value) in values) {
            val autofillValue = when (value) {
                is FieldValue.Text -> AutofillValue.forText(value.text)
                is FieldValue.ListIndex -> AutofillValue.forList(value.index)
            }
            @Suppress("DEPRECATION")
            builder.setValue(id, autofillValue)
        }
        return builder.build()
    }
}

/** What a suggestion shows: never a secret, only a title and a masked hint. */
object AutofillPresentation {
    fun hint(entry: AutofillIndexEntry): String = when (entry.kind) {
        AutofillIndexStore.KIND_CARD -> entry.last4?.let { "•••• $it" } ?: entry.subtitle
        AutofillIndexStore.KIND_LOGIN -> entry.subtitle.ifBlank { entry.websiteHost.orEmpty() }
        else -> entry.subtitle
    }
}

/**
 * Unlock (or confirm) before a fill: biometrics start on their own when set up, the master
 * password is always there. Shared by autofill and Credential Manager picks.
 */
@Composable
internal fun ColumnScope.AutofillUnlockPane(
    session: VaultSession,
    callerLabel: String,
    onUnlocked: () -> Unit,
    onCancel: () -> Unit,
) {
    val c = qasaColors
    // Unlocked already: this is the per-fill confirmation (cards, or "Confirm every fill").
    val title = stringResource(if (session.isUnlocked) R.string.autofill_confirmtitle else R.string.autofill_unlocktitle)
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Biometrics start on their own, like the iOS sheet; the password stays below. A
    // non-null cipher is one prompt in flight. Only once per sheet: `autoPrompted` is
    // saveable, so a rotation does not prompt again. When the master password is due
    // (BiometricPolicy) there is no cipher, so no prompt and no biometric button.
    var autoPrompted by rememberSaveable { mutableStateOf(false) }
    var cipher by remember {
        mutableStateOf(if (autoPrompted) null else session.biometricUnlockCipher())
    }
    SideEffect { autoPrompted = true }
    var biometricAllowed by remember { mutableStateOf(session.biometricUnlockAllowed()) }
    val showMasterPasswordDue = remember { session.biometricsEnabled && session.masterPasswordDue }
    val wrong = stringResource(R.string.autofill_wrongpassword)
    val failed = stringResource(R.string.autofill_biometricfailed)
    // The key derivation takes a moment: off the main thread.
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    fun runUnlock(work: () -> Boolean, onFail: () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            val ok = withContext(Dispatchers.Default) { work() }
            busy = false
            if (ok) {
                password = ""
                onUnlocked()
            } else {
                onFail()
            }
        }
    }

    BiometricGateEffect(
        enabled = cipher != null,
        title = title,
        subtitle = stringResource(R.string.autofill_fillinto, callerLabel),
        cipher = cipher,
        onAuthenticated = { authorized ->
            cipher = null
            runUnlock({ authorized != null && session.unlockWithBiometricCipher(authorized) }) {
                error = failed
                biometricAllowed = session.biometricUnlockAllowed()
            }
        },
        onDismissed = { cipher = null },
    )

    fun submit() {
        val typed = password
        runUnlock({ session.unlockMatching(typed) != null }) { error = wrong }
    }

    Text(title, color = c.textPrimary, style = qasaText.displayLarge)
    Spacer(Modifier.height(QasaDimens.contentGap))
    Text(stringResource(R.string.autofill_fillinto, callerLabel), color = c.textSecondary, style = qasaText.bodyMedium)
    Spacer(Modifier.height(QasaDimens.screenPadding))
    SectionLabel(stringResource(R.string.auth_unlock_masterpassword))
    Spacer(Modifier.height(QasaDimens.contentGap))
    ThemedTextField(
        value = password,
        onValueChange = { password = it; error = null },
        placeholder = stringResource(R.string.auth_unlock_placeholder),
        leadingIcon = Icons.Default.Lock,
        isSecure = true,
        passwordVisible = visible,
        onToggleVisibility = { visible = !visible },
    )
    error?.let {
        Spacer(Modifier.height(QasaDimens.contentGap))
        Text(it, color = c.danger, style = qasaText.bodySmallMedium)
    }
    if (showMasterPasswordDue) {
        Spacer(Modifier.height(QasaDimens.contentGap))
        Text(
            stringResource(R.string.auth_unlock_masterpasswordduefmt, BiometricPolicy.MASTER_PASSWORD_INTERVAL_DAYS),
            color = c.textSecondary,
            style = qasaText.bodyMedium,
        )
    }
    Spacer(Modifier.height(QasaDimens.gap))
    PrimaryButton(
        title = stringResource(R.string.autofill_unlock),
        icon = Icons.Default.LockOpen,
        enabled = password.isNotBlank() && !busy,
        onClick = ::submit,
        modifier = Modifier.fillMaxWidth(),
    )
    if (biometricAllowed) {
        Spacer(Modifier.height(QasaDimens.contentGap))
        SecondaryButton(
            title = stringResource(R.string.auth_unlock_biometrics),
            onClick = { if (cipher == null) cipher = session.biometricUnlockCipher() },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Spacer(Modifier.height(QasaDimens.contentGap))
    SecondaryButton(title = stringResource(R.string.common_cancel), onClick = onCancel, modifier = Modifier.fillMaxWidth())
}
