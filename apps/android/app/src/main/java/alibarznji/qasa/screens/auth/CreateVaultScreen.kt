package alibarznji.qasa.screens.auth

import alibarznji.qasa.R
import alibarznji.qasa.screens.settings.backup.RestoreBackupPage
import alibarznji.qasa.screens.settings.backup.RestoreBackupViewModel
import alibarznji.qasa.screens.settings.backup.RestoreFromBackupOption
import alibarznji.qasa.theme.BackChromeHeader
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import alibarznji.qasa.theme.BrandHeader
import alibarznji.qasa.theme.CardContainer
import alibarznji.qasa.theme.MintSwitch
import alibarznji.qasa.theme.PrimaryButton
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.QasaIconSize
import alibarznji.qasa.theme.ScreenScaffold
import alibarznji.qasa.theme.SectionLabel
import alibarznji.qasa.theme.StatusPill
import alibarznji.qasa.theme.StickyBottomBar
import alibarznji.qasa.theme.StrengthBar
import alibarznji.qasa.theme.ThemedTextField
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import alibarznji.qasa.vault.BiometricGateEffect
import alibarznji.qasa.vault.VaultSession
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import alibarznji.qasa.theme.QasaIconFrame
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.crypto.Cipher
import javax.inject.Inject

@HiltViewModel
class CreateVaultViewModel @Inject constructor(val session: VaultSession) : ViewModel() {
    fun create(password: String, biometrics: Boolean, sealedCipher: Cipher? = null) {
        session.createVault(password, biometrics, sealedCipher)
    }
}

@Composable
fun CreateVaultScreen(
    onCreated: () -> Unit,
    /** True while the Link Device flow covers this screen (it may adopt a vault). */
    onPairOpenChanged: (Boolean) -> Unit = {},
    vm: CreateVaultViewModel = hiltViewModel(),
    restoreVm: RestoreBackupViewModel = hiltViewModel(),
) {
    val c = qasaColors
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var biometrics by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showPair by remember { mutableStateOf(false) }
    // Restore from a backup file: adopts the backup's vault and account, like Link Device.
    var showRestore by remember { mutableStateOf(false) }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        restoreVm.open(uri)
        showRestore = true
    }
    // Non-null while the seal prompt is in flight. Creating the vault navigates away, so
    // the password has to be sealed first or the prompt is torn down mid-attempt.
    var sealCipher by remember { mutableStateOf<Cipher?>(null) }

    // Both flows may adopt a vault; Root keeps this screen until they close.
    val covered = showPair || showRestore
    DisposableEffect(covered) {
        onPairOpenChanged(covered)
        onDispose { onPairOpenChanged(false) }
    }
    if (showPair) {
        BackHandler { showPair = false }
        PairExistingVaultScreen(onDone = { showPair = false })
        return
    }
    if (showRestore) {
        val close = {
            if (!restoreVm.busy) {
                restoreVm.reset()
                showRestore = false
            }
        }
        BackHandler(onBack = close)
        RestoreBackupPage(
            vm = restoreVm,
            setup = true,
            header = {
                BackChromeHeader(
                    title = stringResource(R.string.backup_restore_title),
                    onBack = close,
                    applyStatusBars = false,
                )
            },
            onPickFile = { restoreLauncher.launch(arrayOf("*/*")) },
            onClose = close,
            onSetupFinished = { unlocked ->
                restoreVm.reset()
                showRestore = false
                // Unlocked: the same post-unlock bootstrap as a vault adopted over Wi‑Fi
                // (device registration under the adopted account, search reindex,
                // autofill index). Otherwise the Unlock screen takes over.
                if (unlocked) onCreated()
            },
        )
        return
    }

    BiometricGateEffect(
        enabled = sealCipher != null,
        title = stringResource(R.string.auth_unlock_biometrics),
        subtitle = stringResource(R.string.auth_unlock_biometricshint),
        cipher = sealCipher,
        onAuthenticated = { cipher ->
            // Only an authorized cipher can seal the password; a null one means the
            // platform did not hand back a usable CryptoObject, so biometrics stay off.
            vm.create(password, biometrics = true, sealedCipher = cipher)
            password = ""
            confirm = ""
            sealCipher = null
            onCreated()
        },
        onDismissed = {
            // Declining the prompt still creates the vault, just without a cached copy.
            vm.create(password, biometrics = false)
            password = ""
            confirm = ""
            sealCipher = null
            onCreated()
        },
    )

    val strengthLabel: String
    val filled: Int
    val strengthColor = when {
        password.isEmpty() -> {
            strengthLabel = ""
            filled = 0
            c.stroke
        }
        password.length < 4 -> {
            strengthLabel = stringResource(R.string.auth_create_weak)
            filled = 1
            c.danger
        }
        password.length < 8 -> {
            strengthLabel = stringResource(R.string.auth_create_fair)
            filled = 2
            c.goldMuted
        }
        else -> {
            strengthLabel = stringResource(R.string.auth_create_strong)
            filled = 3
            c.success
        }
    }

    val canCreate = password.length >= 8 && password == confirm
    val tooShortMessage = stringResource(R.string.settings_weakmasterpassword)
    val mismatchMessage = stringResource(R.string.auth_create_mismatch)

    ScreenScaffold(
        header = { BrandHeader(applyStatusBars = false) { StatusPill(stringResource(R.string.brand_localoffline)) } },
        stickyBottom = {
            StickyBottomBar {
                PrimaryButton(
                    title = stringResource(R.string.auth_create_buttonlocal),
                    icon = Icons.Default.Key,
                    enabled = canCreate,
                    onClick = {
                        when {
                            password.length < 8 -> error = tooShortMessage
                            password != confirm -> error = mismatchMessage
                            // Seal first, then create: switching to the vault shell
                            // navigates away and would tear down an in-flight prompt.
                            biometrics -> {
                                val cipher = vm.session.biometricCaptureCipher()
                                if (cipher != null) sealCipher = cipher else {
                                    vm.create(password, biometrics = false)
                                    onCreated()
                                }
                            }
                            else -> {
                                vm.create(password, biometrics = false)
                                onCreated()
                            }
                        }
                    },
                )
            }
        },
    ) {
        Text(stringResource(R.string.auth_create_title), color = c.textPrimary, style = qasaText.displayLarge)
        Spacer(Modifier.height(QasaDimens.contentGap))
        Text(
            stringResource(R.string.auth_create_subtitle),
            color = c.textSecondary,
            style = qasaText.bodyMedium
        )
        TextButton(onClick = { showPair = true }) {
            Text(stringResource(R.string.auth_create_pairexisting), color = c.accent, fontWeight = FontWeight.SemiBold)
        }
        RestoreFromBackupOption(onClick = { restoreLauncher.launch(arrayOf("*/*")) })
        Spacer(Modifier.height(QasaDimens.contentGap))
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
        if (password.isNotEmpty()) {
            Spacer(Modifier.height(QasaDimens.contentGap))
            Row(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.auth_create_strength), color = c.textSecondary, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f))
                Text(strengthLabel, color = c.accent, style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(QasaDimens.spaceSmall))
            StrengthBar(segments = 3, filled = filled, color = strengthColor)
        }
        Spacer(Modifier.height(QasaDimens.gap))
        SectionLabel(stringResource(R.string.auth_create_confirmpassword))
        Spacer(Modifier.height(QasaDimens.contentGap))
        ThemedTextField(
            value = confirm,
            onValueChange = { confirm = it; error = null },
            placeholder = stringResource(R.string.auth_create_reenter),
            // iOS `lock.rotation`.
            leadingIcon = Icons.Default.LockReset,
            isSecure = true,
        )
        error?.let {
            Spacer(Modifier.height(QasaDimens.contentGap))
            Text(it, color = c.danger, style = qasaText.bodySmallMedium)
        }
        Spacer(Modifier.height(QasaDimens.gap))
        CardContainer {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // iOS: 40pt `cardBackgroundLight` disc with a accent touchid glyph.
                Box(
                    Modifier.size(QasaIconFrame.toggleDisc).clip(CircleShape).background(c.cardBackgroundLight),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null, tint = c.accent)
                }
                Spacer(Modifier.width(QasaDimens.gapSmall))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.auth_create_enablebiometrics), color = c.textPrimary, style = qasaText.bodyLarge)
                    Text(
                        stringResource(R.string.auth_create_biometricshint),
                        color = c.textSecondary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                MintSwitch(checked = biometrics, onCheckedChange = { biometrics = it })
            }
        }
        Spacer(Modifier.height(QasaDimens.gapSmall))
        CardContainer(background = c.cardBackgroundLight) {
            Box(Modifier.fillMaxWidth()) {
                Icon(
                    Icons.Default.Shield,
                    contentDescription = null,
                    tint = c.accent.copy(alpha = 0.14f),
                    modifier = Modifier
                        .size(QasaIconSize.watermark)
                        .align(Alignment.TopEnd),
                )
                Column(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, null, tint = c.accent)
                        Spacer(Modifier.width(QasaDimens.contentGap))
                        Text(stringResource(R.string.auth_create_zeroknowledge), color = c.textPrimary, style = qasaText.rowTitle)
                    }
                    Spacer(Modifier.height(QasaDimens.gapSmall))
                    Text(
                        stringResource(R.string.auth_create_zeroknowledgebody),
                        color = c.textSecondary,
                        style = qasaText.bodyMedium
                    )
                }
            }
        }
        Spacer(Modifier.height(QasaDimens.heroArtHeight))
    }
}
