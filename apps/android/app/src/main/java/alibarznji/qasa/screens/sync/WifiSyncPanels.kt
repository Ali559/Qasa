package alibarznji.qasa.screens.sync

import alibarznji.qasa.R
import alibarznji.qasa.sync.HostState
import alibarznji.qasa.sync.JoinResult
import alibarznji.qasa.sync.SyncFailure
import alibarznji.qasa.sync.SyncPeer
import alibarznji.qasa.sync.SyncResult
import alibarznji.qasa.sync.WifiSyncService
import alibarznji.qasa.sync.failureMessage
import alibarznji.qasa.theme.CardContainer
import alibarznji.qasa.theme.PrimaryButton
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.QasaIconSize
import alibarznji.qasa.theme.QasaIcons
import alibarznji.qasa.theme.QasaPressable
import alibarznji.qasa.theme.QasaFieldGroup
import alibarznji.qasa.theme.QasaTextRow
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** State for Sync on this network and the Pair flow's Wi‑Fi join. */
@HiltViewModel
class WifiSyncViewModel @Inject constructor(
    private val sync: WifiSyncService,
) : ViewModel() {
    val hostState: StateFlow<HostState> = sync.hostState
    val peers: StateFlow<List<SyncPeer>> = sync.peers

    var selected by mutableStateOf<SyncPeer?>(null)
        private set
    var code by mutableStateOf("")
        private set
    var joining by mutableStateOf(false)
        private set
    var joinFailure by mutableStateOf<SyncFailure?>(null)
        private set
    var joinResult by mutableStateOf<SyncResult?>(null)
        private set

    fun select(peer: SyncPeer) {
        selected = peer
        joinFailure = null
    }

    fun updateCode(value: String) {
        code = value.filter(Char::isDigit).take(6)
        joinFailure = null
    }

    fun startHost() {
        joinResult = null
        sync.startHost()
    }

    fun stopHost() = sync.stopHost()

    /** Switching between host and join: nothing from the other mode carries over. */
    fun resetModes() {
        sync.resetHost()
        joinResult = null
        joinFailure = null
        code = ""
    }

    fun startBrowsing() = sync.startBrowsing()

    fun stopBrowsing() = sync.stopBrowsing()

    fun join(peer: SyncPeer?) {
        if (peer == null || code.length != 6 || joining) return
        joining = true
        joinFailure = null
        viewModelScope.launch {
            when (val r = sync.join(peer, code)) {
                is JoinResult.Done -> {
                    joinResult = r.result
                    code = ""
                }
                is JoinResult.Failed -> joinFailure = r.failure
            }
            joining = false
        }
    }

    override fun onCleared() {
        sync.resetHost()
        sync.stopBrowsing()
    }
}

@Composable
internal fun syncFailureText(f: SyncFailure): String {
    val m = failureMessage(f)
    return if (m.arg != null) stringResource(m.res, m.arg) else stringResource(m.res)
}

/** "This device has the vault": the big code, the countdown, Stop, or Start hosting. */
@Composable
internal fun HostPanel(vm: WifiSyncViewModel) {
    val c = qasaColors
    val state by vm.hostState.collectAsState()
    val waiting = state as? HostState.Waiting

    Column(verticalArrangement = Arrangement.spacedBy(QasaDimens.gapSmall)) {
        if (waiting != null) {
            var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(waiting.expiresAtMs) {
                while (true) {
                    now = System.currentTimeMillis()
                    delay(1_000)
                }
            }
            val secondsLeft = ((waiting.expiresAtMs - now) / 1_000).coerceAtLeast(0)
            CardContainer {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(QasaDimens.contentGap),
                ) {
                    Text(stringResource(R.string.sync_hosttitle), color = c.textSecondary, style = qasaText.bodySmallMedium)
                    val grouped = WifiSyncService.groupCode(waiting.code)
                    Text(
                        grouped,
                        color = c.accent,
                        style = qasaText.styleForSize(40, FontWeight.Bold).copy(
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 4.sp,
                        ),
                        modifier = Modifier.semantics {
                            // Read digit by digit, not as "one hundred twenty-three".
                            contentDescription = waiting.code.toCharArray().joinToString(" ")
                        },
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(QasaDimens.contentGap),
                    ) {
                        CircularProgressIndicator(Modifier.size(14.dp), color = c.accent, strokeWidth = 2.dp)
                        Text(stringResource(R.string.sync_waiting), color = c.textSecondary, style = qasaText.bodySmall)
                    }
                    Text(
                        stringResource(R.string.sync_codeexpires, secondsLeft),
                        color = c.textSecondary,
                        style = qasaText.bodySmall,
                    )
                    Text(
                        stringResource(R.string.sync_hostbody),
                        color = c.textSecondary,
                        style = qasaText.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            waiting.failure?.let { FailureText(syncFailureText(it)) }
            QasaPressable(onClick = vm::stopHost, modifier = Modifier.fillMaxWidth(), role = Role.Button) {
                Text(
                    stringResource(R.string.lan_stop),
                    color = c.danger,
                    style = qasaText.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = QasaDimens.gapSmall),
                )
            }
        } else {
            (state as? HostState.Failed)?.let { FailureText(syncFailureText(it.failure)) }
            PrimaryButton(
                title = stringResource(R.string.lan_starthost),
                icon = QasaIcons.Wifi,
                onClick = vm::startHost,
            )
        }
    }
}

/**
 * "Receive vault": live list of devices showing a code, the code field, Sync. Browses
 * while on screen. Used by Sync on this network and the Pair flow.
 */
@Composable
internal fun JoinPanel(vm: WifiSyncViewModel) {
    val c = qasaColors
    val peers by vm.peers.collectAsState()
    DisposableEffect(Unit) {
        vm.startBrowsing()
        onDispose { vm.stopBrowsing() }
    }
    // A selection that disappeared is dropped; a single device is picked for you.
    val selected = vm.selected?.takeIf { s -> peers.any { it.name == s.name } }
        ?.let { s -> peers.first { it.name == s.name } }
        ?: peers.singleOrNull()

    Column(verticalArrangement = Arrangement.spacedBy(QasaDimens.gapSmall)) {
        PeerList(peers = peers, selected = selected, onSelect = vm::select)
        if (peers.isEmpty()) {
            Text(stringResource(R.string.sync_nopeers), color = c.textSecondary, style = qasaText.bodySmall)
        }
        QasaFieldGroup {
            QasaTextRow(
                label = stringResource(R.string.pair_codelabel),
                value = vm.code,
                onValueChange = vm::updateCode,
                // A failed join shows on the code row: danger label and stripe, text under the card.
                error = vm.joinFailure?.let { syncFailureText(it) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, autoCorrectEnabled = false),
            )
        }
        PrimaryButton(
            title = stringResource(if (vm.joining) R.string.sync_connecting else R.string.sync_syncbutton),
            icon = if (vm.joining) null else QasaIcons.Wifi,
            enabled = selected != null && vm.code.length == 6 && !vm.joining,
            onClick = { vm.join(selected) },
        )
    }
}

@Composable
private fun PeerList(peers: List<SyncPeer>, selected: SyncPeer?, onSelect: (SyncPeer) -> Unit) {
    val c = qasaColors
    val shape = RoundedCornerShape(QasaDimens.cornerRadius)
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.cardBackground, shape)
            .border(QasaDimens.stroke, c.stroke, shape),
    ) {
        if (peers.isEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(QasaDimens.gap),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(QasaDimens.gapSmall),
            ) {
                CircularProgressIndicator(Modifier.size(16.dp), color = c.accent, strokeWidth = 2.dp)
                Text(stringResource(R.string.sync_searching), color = c.textSecondary, style = qasaText.bodyMedium)
            }
        }
        peers.forEachIndexed { i, peer ->
            if (i > 0) HorizontalDivider(color = c.stroke)
            val isSelected = peer.name == selected?.name
            QasaPressable(
                onClick = { onSelect(peer) },
                role = Role.RadioButton,
                modifier = Modifier.fillMaxWidth().semantics { this.selected = isSelected },
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (isSelected) c.accent.copy(alpha = 0.08f) else c.cardBackground)
                        .padding(horizontal = QasaDimens.gap, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(QasaDimens.gapSmall),
                ) {
                    Icon(
                        platformIcon(peer.platform),
                        contentDescription = null,
                        tint = c.accent,
                        modifier = Modifier.size(QasaIconSize.md),
                    )
                    Text(
                        peer.name,
                        color = c.textPrimary,
                        style = if (isSelected) qasaText.rowTitle else qasaText.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private fun platformIcon(platform: String) = when (platform) {
    "ios" -> QasaIcons.Device
    "android" -> QasaIcons.Device
    "macos" -> QasaIcons.Laptop
    "windows", "linux" -> QasaIcons.Desktop
    else -> QasaIcons.Wifi
}

@Composable
internal fun FailureText(text: String) {
    Text(
        text,
        color = qasaColors.danger,
        style = qasaText.bodySmallMedium,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** The success card after a sync: "Synced with …", "N changes here, M on the other device." */
@Composable
internal fun SyncDoneCard(result: SyncResult) {
    val c = qasaColors
    CardContainer {
        Text(
            stringResource(R.string.sync_donetitle, result.peerName),
            color = c.success,
            style = qasaText.cardTitle,
        )
        Text(
            stringResource(R.string.sync_donebody, result.received, result.sent),
            color = c.textSecondary,
            style = qasaText.bodyMedium,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
