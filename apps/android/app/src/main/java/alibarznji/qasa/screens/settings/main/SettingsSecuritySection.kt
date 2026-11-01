package alibarznji.qasa.screens.settings.main

import alibarznji.qasa.R
import alibarznji.qasa.screens.auth.pair.PairingSessionStore
import alibarznji.qasa.screens.settings.SettingsViewModel
import alibarznji.qasa.theme.CardContainer
import alibarznji.qasa.theme.NavChevronRow
import alibarznji.qasa.theme.QasaDimens
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.HorizontalDivider
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
import java.util.UUID

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
        icon = Icons.Outlined.Password,
        title = stringResource(R.string.settings_changemasterpassword),
        onClick = onChangePassword,
    )
    HorizontalDivider(color = c.stroke)
    ToggleRow(
        icon = Icons.Filled.Fingerprint,
        iconColor = c.textPrimary,
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
    HorizontalDivider(color = c.stroke)
    NavChevronRow(
        icon = Icons.Filled.TextFields,
        title = stringResource(R.string.settings_autofill),
        // iOS's `settings.autofillSubtitle` walks through iOS Settings ("Passwords → AutoFill
        // Passwords"); on Android the row opens the system autofill picker instead.
        subtitle = stringResource(R.string.settings_autofillsubtitleandroid),
        onClick = { openAutofillSettings(context) },
    )
    HorizontalDivider(color = c.stroke)
    NavChevronRow(
        icon = Icons.Outlined.Schedule,
        title = stringResource(R.string.settings_autolock),
        trailingText = stringResource(R.string.settings_autolockminutesfmt, autoLock),
        onClick = onAutoLock,
    )
}

/**
 * Linked Devices section (`SettingsView.swift:80-125`): "N Active" header, this device,
 * Show pairing code, Sync on this network.
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
    // Like iOS, the code is only shown after a tap in this visit; it stays valid for 10 min.
    var pairingCode by remember { mutableStateOf<String?>(null) }

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
            icon = Icons.Filled.PhoneAndroid,
            iconColor = c.accent,
            title = remember { deviceName(context) },
            subtitle = stringResource(R.string.settings_thisdevice),
            onClick = onDevices,
        )
        HorizontalDivider(color = c.stroke)
        NavChevronRow(
            icon = Icons.Filled.QrCode,
            title = stringResource(R.string.settings_showpairingcode),
            subtitle = pairingCode?.let { stringResource(R.string.settings_pairingcodefmt, it) }
                ?: stringResource(R.string.settings_pairingsubtitle),
            onClick = {
                // iOS only publishes a well-formed UUID account id.
                val id = vm.store.accountId?.takeIf { runCatching { UUID.fromString(it) }.isSuccess }
                if (id != null) pairingCode = PairingSessionStore.shared.publish(id)
            },
        )
        HorizontalDivider(color = c.stroke)
        NavChevronRow(
            icon = Icons.Filled.Wifi,
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

private fun openAutofillSettings(context: Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
            },
        )
    } catch (_: Exception) {
        context.startActivity(Intent(Settings.ACTION_SETTINGS))
    }
}
