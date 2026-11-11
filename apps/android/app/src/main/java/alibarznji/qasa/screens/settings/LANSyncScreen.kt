package alibarznji.qasa.screens.settings

import alibarznji.qasa.R
import alibarznji.qasa.screens.AppSession
import alibarznji.qasa.screens.sync.HostPanel
import alibarznji.qasa.screens.sync.JoinPanel
import alibarznji.qasa.screens.sync.SyncDoneCard
import alibarznji.qasa.screens.sync.WifiSyncViewModel
import alibarznji.qasa.sync.HostState
import alibarznji.qasa.theme.PrimaryButton
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.QasaPressable
import alibarznji.qasa.theme.ScreenScaffold
import alibarznji.qasa.theme.QasaSheetHeader
import alibarznji.qasa.theme.StickyBottomBar
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class LanViewModel @Inject constructor(
    val appSession: AppSession,
) : ViewModel()

/** iOS `LANSyncView.Mode`. */
private enum class LanMode { HOST, JOIN }

/**
 * Sync on this network (iOS `LANSyncView`, desktop `WifiSync.vue`), now real: Premium
 * only. "This device has the vault" shows a one-time code and waits; "Receive vault"
 * finds the other device and takes its code. Both directions merge (newest change wins),
 * so either side can host. The vault list follows Room, so it refreshes on its own.
 */
@Composable
fun LANSyncScreen(
    onDone: () -> Unit,
    onUpgrade: () -> Unit,
    vm: LanViewModel = hiltViewModel(),
    sync: WifiSyncViewModel = hiltViewModel(),
) {
    val c = qasaColors
    val isPremium by vm.appSession.isPremium.collectAsState()

    ScreenScaffold(
        header = {
            // Shown in a sheet: the shared sheet header, no Close (iOS `.sheetHeader`).
            QasaSheetHeader(stringResource(R.string.lan_title))
        },
        stickyBottom = if (isPremium) {
            null
        } else {
            {
                StickyBottomBar {
                    PrimaryButton(
                        title = stringResource(R.string.nav_upgrade),
                        icon = Icons.Filled.WorkspacePremium,
                        onClick = onUpgrade,
                    )
                }
            }
        },
    ) {
        if (!isPremium) {
            Text(stringResource(R.string.lan_requirespremium), color = c.textSecondary, style = qasaText.bodyLarge)
        } else {
            PremiumLanBody(sync)
        }
    }
}

@Composable
private fun PremiumLanBody(vm: WifiSyncViewModel) {
    val c = qasaColors
    var mode by rememberSaveable { mutableStateOf(LanMode.HOST) }
    val hostState by vm.hostState.collectAsState()
    val result = (hostState as? HostState.Done)?.result ?: vm.joinResult
    // The view model may outlive this screen (it is shown inline in Settings): stop
    // hosting and forget results when leaving.
    DisposableEffect(Unit) { onDispose { vm.resetModes() } }

    Column(verticalArrangement = Arrangement.spacedBy(QasaDimens.gap)) {
        Text(stringResource(R.string.lan_securetitle), color = c.textPrimary, style = qasaText.sectionHeadline)
        Text(stringResource(R.string.lan_securebody), color = c.textSecondary, style = qasaText.bodyMedium)

        SegmentedControl(
            options = listOf(
                LanMode.HOST to stringResource(R.string.lan_hostmode),
                LanMode.JOIN to stringResource(R.string.lan_joinmode),
            ),
            selected = mode,
            onSelect = {
                if (it != mode) {
                    vm.resetModes()
                    mode = it
                }
            },
        )

        if (result != null) SyncDoneCard(result)

        when (mode) {
            LanMode.HOST -> HostPanel(vm)
            LanMode.JOIN -> {
                Text(stringResource(R.string.sync_joinbody), color = c.textSecondary, style = qasaText.bodyMedium)
                JoinPanel(vm)
            }
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
