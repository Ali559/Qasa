package alibarznji.qasa.screens.settings.main

import alibarznji.qasa.R
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
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material3.HorizontalDivider
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
    onSubscription: () -> Unit,
    vm: SettingsViewModel,
) {
    val c = qasaColors
    val scope = rememberCoroutineScope()
    val isPremium by vm.appSession.isPremium.collectAsState()
    var showClear by remember { mutableStateOf(false) }
    var showLockConfirm by remember { mutableStateOf(false) }
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
            CsvImportExportRows(onStatus = { statusMessage = it })
            HorizontalDivider(color = c.stroke)
            NavChevronRow(
                icon = Icons.Outlined.DeleteForever,
                title = stringResource(R.string.settings_clearlocaldata),
                destructive = true,
                onClick = { showClear = true },
            )
            HorizontalDivider(color = c.stroke)
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
                    vm.repo.clearAll()
                    vm.session.wipeVault()
                    // Same scope as iOS `VaultAuthStore.resetVaultAuth`: the licence goes too,
                    // so a cleared device does not keep premium attached to a deleted vault.
                    vm.license.clear()
                    vm.appSession.syncFromLicense(vm.license)
                    showClear = false
                    onLock()
                }
            },
            onDismiss = { showClear = false },
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

/** iOS `settingsSection(title:content:)`: label, 8pt, card. */
@Composable
internal fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    SectionLabel(title)
    Spacer(Modifier.height(QasaDimens.contentGap))
    CardContainer(content = content)
}

/** iOS VStack spacing 24 between sections. */
@Composable
private fun SectionGap() {
    Spacer(Modifier.height(QasaDimens.sectionGap))
}
