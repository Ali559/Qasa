package alibarznji.qasa.screens.settings

import alibarznji.qasa.R
import alibarznji.qasa.screens.AppSession
import alibarznji.qasa.sync.LANVaultSyncService
import alibarznji.qasa.theme.PrimaryButton
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.QasaPressable
import alibarznji.qasa.theme.ScreenScaffold
import alibarznji.qasa.theme.SheetChromeHeader
import alibarznji.qasa.theme.ThemedTextField
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import alibarznji.qasa.vault.VaultSecureStore
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class LanViewModel @Inject constructor(
    val sync: LANVaultSyncService,
    val store: VaultSecureStore,
    val appSession: AppSession,
) : ViewModel()

/** iOS `LANSyncView.Mode`. */
private enum class LanMode { HOST, JOIN }

private const val PlaceholderPrefix = "qasa-vault-placeholder:"

/**
 * Port of iOS `LANSyncView`: free tier gets the requirement line + Upgrade; Premium gets
 * the title/body, a Host/Join segmented control, the shared code field, the status line,
 * one action button for the selected mode, and Stop.
 */
@Composable
fun LANSyncScreen(
    onDone: () -> Unit,
    onUpgrade: () -> Unit,
    vm: LanViewModel = hiltViewModel(),
) {
    val c = qasaColors
    val isPremium by vm.appSession.isPremium.collectAsState()

    ScreenScaffold(
        header = {
            SheetChromeHeader(
                title = stringResource(R.string.lan_title),
                onDismiss = onDone,
                dismissLabel = stringResource(R.string.common_close),
            )
        },
    ) {
        if (!isPremium) {
            Column(verticalArrangement = Arrangement.spacedBy(QasaDimens.gap)) {
                Text(stringResource(R.string.lan_requirespremium), color = c.textSecondary, style = qasaText.bodyLarge)
                PrimaryButton(
                    title = stringResource(R.string.nav_upgrade),
                    icon = Icons.Filled.WorkspacePremium,
                    onClick = onUpgrade,
                )
            }
        } else {
            PremiumLanBody(vm)
        }
    }
}

@Composable
private fun PremiumLanBody(vm: LanViewModel) {
    val c = qasaColors
    val context = LocalContext.current
    val status by vm.sync.statusMessage.collectAsState()
    var code by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf(LanMode.HOST) }
    var validationMessage by remember { mutableStateOf<String?>(null) }

    // iOS `runSync()` (LANSyncView.swift:87-113).
    fun runSync() {
        if (!vm.appSession.isPremium.value) {
            validationMessage = context.getString(R.string.lan_requirespremium)
            return
        }
        val trimmed = code.trim()
        if (trimmed.length < 4) {
            validationMessage = context.getString(R.string.lan_entercode)
            return
        }
        validationMessage = null
        when (mode) {
            LanMode.HOST -> {
                val blob = "$PlaceholderPrefix${vm.store.accountId.orEmpty()}".toByteArray()
                vm.sync.startHost(trimmed, blob)
            }
            LanMode.JOIN -> vm.sync.startBrowse(trimmed) { data ->
                val s = data.toString(Charsets.UTF_8)
                if (s.startsWith(PlaceholderPrefix)) {
                    vm.store.accountId = s.substringAfterLast(":")
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(QasaDimens.gap)) {
        Text(stringResource(R.string.lan_securetitle), color = c.textPrimary, style = qasaText.sectionHeadline)
        Text(stringResource(R.string.lan_securebody), color = c.textSecondary, style = qasaText.bodyMedium)

        SegmentedControl(
            options = listOf(
                LanMode.HOST to stringResource(R.string.lan_hostmode),
                LanMode.JOIN to stringResource(R.string.lan_joinmode),
            ),
            selected = mode,
            onSelect = { mode = it },
        )

        ThemedTextField(
            value = code,
            onValueChange = { code = it },
            placeholder = stringResource(R.string.pair_codeplaceholder),
            leadingIcon = Icons.Filled.Tag,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )

        Text(
            validationMessage ?: status,
            color = c.accent,
            style = qasaText.labelRegular.copy(fontFamily = FontFamily.Monospace),
        )

        PrimaryButton(
            title = stringResource(if (mode == LanMode.HOST) R.string.lan_starthost else R.string.lan_findhost),
            icon = Icons.Filled.Wifi,
            onClick = ::runSync,
        )

        QasaPressable(onClick = { vm.sync.stop() }) {
            Text(stringResource(R.string.lan_stop), color = c.danger, style = qasaText.bodyLarge)
        }
    }
}

/** iOS `.pickerStyle(.segmented)`: equal-width segments, the selected one filled. */
@Composable
private fun <T> SegmentedControl(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val c = qasaColors
    val outer = RoundedCornerShape(QasaDimens.radiusSmall)
    val inner = RoundedCornerShape(QasaDimens.radiusSmall - QasaDimens.spaceMicro)
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.cardBackground, outer)
            .border(QasaDimens.stroke, c.stroke, outer)
            .padding(QasaDimens.spaceMicro),
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            QasaPressable(
                onClick = { onSelect(value) },
                modifier = Modifier.weight(1f),
                role = Role.Tab,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(if (isSelected) c.cardBackgroundLight else c.cardBackground, inner)
                        .padding(vertical = QasaDimens.contentGap, horizontal = QasaDimens.spaceSmall),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        color = if (isSelected) c.textPrimary else c.textSecondary,
                        style = if (isSelected) qasaText.bodySmallSemibold else qasaText.bodySmall,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
