package alibarznji.qasa.screens.settings.main

import alibarznji.qasa.R
import alibarznji.qasa.screens.settings.SettingsViewModel
import alibarznji.qasa.theme.CardContainer
import alibarznji.qasa.theme.NavChevronRow
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.QasaIcons
import alibarznji.qasa.theme.SectionLabel
import alibarznji.qasa.theme.ToggleRow
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import alibarznji.qasa.theme.RowDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource

/**
 * Security card rows (`SettingsView.swift:35-78`): Change Master Password, Biometric Unlock,
 * Password AutoFill, Auto-lock "N min".
 */
@Composable
internal fun SecurityRows(
    vm: SettingsViewModel,
    onChangePassword: () -> Unit,
    onAutoLock: () -> Unit,
) {
    val c = qasaColors
    val context = LocalContext.current
    val autoLock by vm.prefs.autoLockMinutes.collectAsState()
    var biometrics by remember { mutableStateOf(vm.store.biometricsEnabled) }

    NavChevronRow(
        icon = QasaIcons.Login,
        title = stringResource(R.string.settings_changemasterpassword),
        onClick = onChangePassword,
    )
    RowDivider()
    ToggleRow(
        icon = QasaIcons.Fingerprint,
        title = stringResource(R.string.settings_biometricunlock),
        // Seal-state subtitle, shown on both apps (iOS `SettingsView.biometricSubtitle`).
        // The toggle is the preference; the sealed copy behind the hardware gate is
        // written at unlock, so say so instead of implying it is already cached.
        subtitle = when {
            !vm.session.biometricsEnabled -> ""
            vm.session.biometricUnlockAvailable() -> stringResource(R.string.settings_biometricsealed)
            !vm.store.biometricGateAvailable -> stringResource(R.string.settings_biometricnogate)
            else -> stringResource(R.string.settings_biometricpending)
        },
        checked = biometrics,
        onCheckedChange = {
            biometrics = it
            vm.session.setBiometricsEnabled(it)
        },
    )
    RowDivider()
    NavChevronRow(
        icon = QasaIcons.Type,
        title = stringResource(R.string.settings_autofill),
        // iOS's `settings.autofillSubtitle` walks through iOS Settings ("Passwords → AutoFill
        // Passwords"); on Android the row opens the system autofill picker instead.
        subtitle = stringResource(R.string.settings_autofillsubtitleandroid),
        onClick = { alibarznji.qasa.credentials.AutofillSettings.openPicker(context) },
    )
    RowDivider()
    val confirmEveryFill by vm.prefs.autofillConfirmEveryFill.collectAsState()
    ToggleRow(
        icon = QasaIcons.Protected,
        title = stringResource(R.string.settings_autofillconfirm),
        subtitle = stringResource(R.string.settings_autofillconfirmsubtitle),
        checked = confirmEveryFill,
        onCheckedChange = vm.prefs::setAutofillConfirmEveryFill,
    )
    RowDivider()
    NavChevronRow(
        icon = QasaIcons.AutoLock,
        title = stringResource(R.string.settings_autolock),
        trailingText = stringResource(R.string.settings_autolockminutesfmt, autoLock),
        onClick = onAutoLock,
    )
}

/**
 * Linked Devices section (`SettingsView.swift:80-125`): "N Active" header, this device,
 * Sync on this network. (The in-memory "Show pairing code" row is gone: a new device now
 * joins over Wi‑Fi from Sync on this network.)
 */
@Composable
internal fun LinkedDevicesSection(
    vm: SettingsViewModel,
    isPremium: Boolean,
    onDevices: () -> Unit,
    onLan: () -> Unit,
) {
    val c = qasaColors
    val context = LocalContext.current
    var linkedDeviceCount by remember { mutableIntStateOf(0) }

    // iOS `.task { fetchDevices() }`: a failed fetch just leaves the count at 0.
    LaunchedEffect(Unit) {
        linkedDeviceCount = runCatching { vm.devices.fetchDevices().size }.getOrDefault(0)
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SectionLabel(stringResource(R.string.settings_linkeddevices), Modifier.weight(1f))
        Text(
            stringResource(R.string.settings_activecount, linkedDeviceCount),
            color = c.accent,
            style = qasaText.sectionLabel,
        )
    }
    Spacer(Modifier.height(QasaDimens.contentGap))
    CardContainer {
        NavChevronRow(
            icon = QasaIcons.Device,
            iconColor = c.accent,
            title = remember { deviceName(context) },
            subtitle = stringResource(R.string.settings_thisdevice),
            onClick = onDevices,
        )
        RowDivider()
        NavChevronRow(
            icon = QasaIcons.Wifi,
            title = stringResource(R.string.settings_syncnetwork),
            subtitle = stringResource(if (isPremium) R.string.settings_premiumlansync else R.string.settings_requirespremium),
            onClick = onLan,
        )
    }
}

/** Android counterpart of `UIDevice.current.name`: the user-set device name, else the model. */
private fun deviceName(context: Context): String =
    Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
        ?.takeIf { it.isNotBlank() }
        ?: Build.MODEL
