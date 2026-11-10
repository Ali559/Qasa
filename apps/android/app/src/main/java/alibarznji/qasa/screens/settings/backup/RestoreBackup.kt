package alibarznji.qasa.screens.settings.backup

import alibarznji.qasa.R
import alibarznji.qasa.backup.BackupService
import alibarznji.qasa.backup.backupErrorMessage
import alibarznji.qasa.backup.mergedMessage
import alibarznji.qasa.device.DeviceRegistrationService
import alibarznji.qasa.theme.CardContainer
import alibarznji.qasa.theme.PrimaryButton
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.ScreenScaffold
import alibarznji.qasa.theme.SecondaryButton
import alibarznji.qasa.theme.SectionLabel
import alibarznji.qasa.theme.StickyBottomBar
import alibarznji.qasa.theme.ThemedTextField
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import alibarznji.qasa.vault.VaultSession
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uniffi.qasa_ffi.FfiBackupInfo
import uniffi.qasa_ffi.FfiRestoreOutcome

/** Where the restore flow is. */
sealed interface RestoreStep {
    /** No readable file yet (a failed read keeps the error and offers another pick). */
    data object Pick : RestoreStep
    data object Reading : RestoreStep
    data class Ready(val info: FfiBackupInfo) : RestoreStep
    data class Working(val info: FfiBackupInfo) : RestoreStep
    data class Done(val outcome: FfiRestoreOutcome) : RestoreStep
}

/**
 * Restore from a backup file: Settings → Backups (vault exists, merge) and the first-run
 * screen (no vault, adopt). The view model may outlive one visit (setup has no nav
 * destination), so hosts call [reset] when the flow closes.
 */
@HiltViewModel
class RestoreBackupViewModel @Inject constructor(
    private val backups: BackupService,
    private val session: VaultSession,
    private val devices: DeviceRegistrationService,
) : ViewModel() {
    var step by mutableStateOf<RestoreStep>(RestoreStep.Pick)
        private set
    var password by mutableStateOf("")
        private set

    @get:StringRes
    var error by mutableStateOf<Int?>(null)
        private set

    /** Unlocking the adopted vault after a setup restore. */
    var finishing by mutableStateOf(false)
        private set

    private var file: ByteArray? = null

    /** Reading, restoring or unlocking: the flow can't be closed. */
    val busy: Boolean
        get() = finishing || step is RestoreStep.Reading || step is RestoreStep.Working

    fun updatePassword(value: String) {
        password = value
        error = null
    }

    fun open(uri: Uri) {
        error = null
        password = ""
        file = null
        step = RestoreStep.Reading
        viewModelScope.launch {
            try {
                val bytes = backups.readFile(uri)
                val info = backups.info(bytes)
                file = bytes
                step = RestoreStep.Ready(info)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                error = backupErrorMessage(e)
                step = RestoreStep.Pick
            }
        }
    }

    fun restore() {
        val ready = step as? RestoreStep.Ready ?: return
        val bytes = file ?: return
        if (password.isEmpty()) return
        error = null
        step = RestoreStep.Working(ready.info)
        viewModelScope.launch {
            try {
                val outcome = backups.restore(bytes, password)
                file = null
                // The registration after the unlock may hit the device limit: offer to
                // replace the device that made the backup right away.
                if (outcome.adopted && outcome.restoreKey != null) devices.armReplacePrompt()
                // Only a setup restore still needs the password, to unlock the adopted vault.
                if (!outcome.adopted) password = ""
                step = RestoreStep.Done(outcome)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                error = backupErrorMessage(e)
                step = ready
            }
        }
    }

    /**
     * Setup: the device adopted the backup's vault and account; unlock it with the
     * password just typed. [onFinished] gets whether the unlock worked (if not, the
     * Unlock screen takes over once the flow closes).
     */
    fun finishSetup(onFinished: (Boolean) -> Unit) {
        if (finishing) return
        finishing = true
        val typed = password
        viewModelScope.launch {
            val unlocked = withContext(Dispatchers.Default) { session.unlockMatching(typed) != null }
            password = ""
            finishing = false
            onFinished(unlocked)
        }
    }

    fun reset() {
        step = RestoreStep.Pick
        password = ""
        error = null
        finishing = false
        file = null
    }
}

/**
 * The restore flow body: the file's origin and a main-password field, then the result.
 * [setup] is the first-run restore (adopts the vault, then [onSetupFinished]);
 * otherwise Done closes with [onClose].
 */
@Composable
fun RestoreBackupPage(
    vm: RestoreBackupViewModel,
    setup: Boolean,
    header: @Composable () -> Unit,
    onPickFile: () -> Unit,
    onClose: () -> Unit,
    onSetupFinished: (Boolean) -> Unit = {},
) {
    val c = qasaColors
    val locale = backupLocale()
    var visible by remember { mutableStateOf(false) }
    val step = vm.step

    ScreenScaffold(
        header = header,
        stickyBottom = {
            StickyBottomBar {
                when (step) {
                    RestoreStep.Pick, RestoreStep.Reading -> SecondaryButton(
                        title = stringResource(R.string.backup_restore),
                        enabled = step == RestoreStep.Pick,
                        onClick = onPickFile,
                    )
                    is RestoreStep.Ready, is RestoreStep.Working -> PrimaryButton(
                        title = stringResource(
                            if (step is RestoreStep.Working) R.string.backup_restore_working else R.string.backup_restore_button,
                        ),
                        enabled = step is RestoreStep.Ready && vm.password.isNotEmpty(),
                        onClick = vm::restore,
                    )
                    is RestoreStep.Done -> PrimaryButton(
                        title = stringResource(if (setup) R.string.common_continue else R.string.common_done),
                        enabled = !vm.finishing,
                        onClick = { if (setup) vm.finishSetup(onSetupFinished) else onClose() },
                    )
                }
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(QasaDimens.gap)) {
            when (step) {
                RestoreStep.Pick -> Text(
                    stringResource(if (setup) R.string.backup_restoresubtitle else R.string.backup_restorehelp),
                    color = c.textSecondary,
                    style = qasaText.bodyMedium,
                )
                RestoreStep.Reading -> Progress(null)
                is RestoreStep.Ready, is RestoreStep.Working -> {
                    val info = (step as? RestoreStep.Ready)?.info ?: (step as RestoreStep.Working).info
                    Text(
                        stringResource(
                            R.string.backup_restore_fromfmt,
                            info.deviceName,
                            formatBackupDate(info.createdAtMs, locale),
                        ),
                        color = c.textPrimary,
                        style = qasaText.rowTitle,
                    )
                    Text(stringResource(R.string.backup_restore_passwordprompt), color = c.textSecondary, style = qasaText.bodyMedium)
                    Column(verticalArrangement = Arrangement.spacedBy(QasaDimens.contentGap)) {
                        SectionLabel(stringResource(R.string.auth_unlock_masterpassword))
                        ThemedTextField(
                            value = vm.password,
                            onValueChange = vm::updatePassword,
                            placeholder = stringResource(R.string.auth_unlock_placeholder),
                            label = stringResource(R.string.auth_unlock_masterpassword),
                            leadingIcon = Icons.Default.Lock,
                            isSecure = true,
                            passwordVisible = visible,
                            onToggleVisibility = { visible = !visible },
                            imeAction = ImeAction.Done,
                        )
                    }
                    if (step is RestoreStep.Working) Progress(R.string.backup_restore_working)
                }
                is RestoreStep.Done -> CardContainer {
                    val text = if (setup) {
                        stringResource(R.string.backup_restore_donefmt, step.outcome.itemsInBackup.toInt())
                    } else {
                        val m = mergedMessage(step.outcome)
                        if (m.count != null) stringResource(m.res, m.count) else stringResource(m.res)
                    }
                    Text(text, color = c.success, style = qasaText.cardTitle)
                }
            }
            vm.error?.let { Text(stringResource(it), color = c.danger, style = qasaText.bodySmallMedium) }
        }
    }
}

@Composable
private fun Progress(@StringRes label: Int?) {
    val c = qasaColors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(QasaDimens.gapSmall),
    ) {
        CircularProgressIndicator(Modifier.size(16.dp), color = c.accent, strokeWidth = 2.dp)
        if (label != null) Text(stringResource(label), color = c.textSecondary, style = qasaText.bodyMedium)
    }
}
