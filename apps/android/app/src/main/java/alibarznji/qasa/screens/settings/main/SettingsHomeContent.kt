package alibarznji.qasa.screens.settings.main

import alibarznji.qasa.R
import alibarznji.qasa.account.AccountDeletionResult
import alibarznji.qasa.screens.settings.AutoLockSheet
import alibarznji.qasa.screens.settings.SettingsViewModel
import alibarznji.qasa.screens.settings.csv.CsvImportExportRows
import alibarznji.qasa.theme.AppMenuHeader
import alibarznji.qasa.theme.CardContainer
import alibarznji.qasa.theme.NavChevronRow
import alibarznji.qasa.theme.QasaConfirmDialog
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.ScreenScaffold
import alibarznji.qasa.theme.SectionLabel
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.PersonRemove
import alibarznji.qasa.theme.RowDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch

/**
 * Settings home, in iOS order (`SettingsView.swift:27-218`): profile card, Security,
 * Linked Devices, Appearance, Data & Account, then the status line.
 */
@Composable
fun SettingsHomeContent(
    onOpenMenu: (() -> Unit)?,
    onLock: () -> Unit,
    onUpgrade: () -> Unit,
    onChangePassword: () -> Unit,
    onDevices: () -> Unit,
    onLan: () -> Unit,
    onBackups: () -> Unit,
    onSubscription: () -> Unit,
    vm: SettingsViewModel,
) {
    val c = qasaColors
    val scope = rememberCoroutineScope()
    val isPremium by vm.appSession.isPremium.collectAsState()
    var showClear by remember { mutableStateOf(false) }
    var showLockConfirm by remember { mutableStateOf(false) }
    var showDeleteAccount by remember { mutableStateOf(false) }
    var deletingAccount by remember { mutableStateOf(false) }
    // Re-read when the license changes, so the row follows registration and Clear Local Data.
    val parsedLicense by vm.license.parsed.collectAsState()
    val accountRegistered = remember(parsedLicense) { vm.accountDeletion.isRegistered }
    val deleteFailedMessage = stringResource(R.string.settings_deleteaccountfailed)
    var showAutoLock by remember { mutableStateOf(false) }
    // iOS `statusMessage`: the last import/export result, shown under the Data card.
    var statusMessage by rememberSaveable { mutableStateOf<String?>(null) }

    ScreenScaffold(
        header = { AppMenuHeader(title = stringResource(R.string.settings_title), onMenu = onOpenMenu, applyStatusBars = false) },
    ) {
        SettingsProfileCard(
            displayName = vm.appSession.displayName,
            isPremium = isPremium,
            onCta = if (isPremium) onSubscription else onUpgrade,
        )

        SectionGap()
        SettingsSection(stringResource(R.string.settings_security)) {
            SecurityRows(vm = vm, onChangePassword = onChangePassword, onAutoLock = { showAutoLock = true })
        }

        SectionGap()
        LinkedDevicesSection(vm = vm, isPremium = isPremium, onDevices = onDevices, onLan = onLan)

        SectionGap()
        SettingsSection(stringResource(R.string.settings_appearance)) {
            AppearanceRows(vm.prefs)
        }

        SectionGap()
        SettingsSection(stringResource(R.string.settings_dataaccount)) {
            NavChevronRow(
                icon = Icons.Outlined.Backup,
                title = stringResource(R.string.backup_title),
                subtitle = stringResource(R.string.backup_subtitle),
                onClick = onBackups,
            )
            RowDivider()
            CsvImportExportRows(onStatus = { statusMessage = it })
            RowDivider()
            NavChevronRow(
                icon = Icons.Outlined.DeleteForever,
                title = stringResource(R.string.settings_clearlocaldata),
                destructive = true,
                onClick = { showClear = true },
            )
            if (accountRegistered) {
                RowDivider()
                NavChevronRow(
                    icon = Icons.Outlined.PersonRemove,
                    title = stringResource(R.string.settings_deleteaccount),
                    destructive = true,
                    onClick = { if (!deletingAccount) showDeleteAccount = true },
                )
            }
            RowDivider()
            NavChevronRow(
                icon = Icons.AutoMirrored.Filled.Logout,
                title = stringResource(R.string.settings_logout),
                onClick = { showLockConfirm = true },
            )
        }

        statusMessage?.let {
            SectionGap()
            Text(it, color = c.accent, style = qasaText.labelRegular)
        }
    }

    if (showAutoLock) {
        AutoLockSheet(prefs = vm.prefs, onDismiss = { showAutoLock = false })
    }

    if (showClear) {
        QasaConfirmDialog(
            title = stringResource(R.string.settings_clearalerttitle),
            message = stringResource(R.string.settings_clearalertmessage),
            confirmLabel = stringResource(R.string.settings_clear),
            onConfirm = {
                scope.launch {
                    vm.eraser.eraseAll()
                    showClear = false
                    onLock()
                }
            },
            onDismiss = { showClear = false },
        )
    }

    if (showDeleteAccount) {
        QasaConfirmDialog(
            title = stringResource(R.string.settings_deleteaccounttitle),
            message = stringResource(R.string.settings_deleteaccountmessage),
            confirmLabel = stringResource(R.string.settings_deleteaccount),
            onConfirm = {
                showDeleteAccount = false
                deletingAccount = true
                // Desktop `deleteAccount`: server first; the vault and identity are only
                // erased once it confirmed, then the app returns to Create Vault.
                vm.deleteAccount { result ->
                    deletingAccount = false
                    if (result == AccountDeletionResult.Deleted) onLock()
                    else statusMessage = deleteFailedMessage
                }
            },
            onDismiss = { showDeleteAccount = false },
        )
    }

    if (showLockConfirm) {
        QasaConfirmDialog(
            title = stringResource(R.string.settings_lockalerttitle),
            message = stringResource(R.string.settings_lockalertmessage),
            confirmLabel = stringResource(R.string.settings_logout),
            onConfirm = {
                showLockConfirm = false
                onLock()
            },
            onDismiss = { showLockConfirm = false },
        )
    }
}

/** iOS `settingsSection(title:content:)`: the shared [alibarznji.qasa.theme.SettingsSection]. */
@Composable
internal fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    alibarznji.qasa.theme.SettingsSection(title = title, content = content)
}

/** iOS VStack spacing 24 between sections. */
@Composable
private fun SectionGap() {
    Spacer(Modifier.height(QasaDimens.sectionGap))
}
