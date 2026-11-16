package alibarznji.qasa.screens.vault.detail

import alibarznji.qasa.R
import alibarznji.qasa.theme.QasaIcons
import alibarznji.qasa.theme.QasaModalBottomSheet
import alibarznji.qasa.theme.RowIconBadge
import alibarznji.qasa.theme.UnlockSheetBody
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.vault.BiometricGate
import alibarznji.qasa.vault.BiometricGateEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * iOS `ProtectedItemCover` (`design/README.md` rule 6b): the background and a lock badge,
 * nothing of the item, until it is unlocked.
 */
@Composable
internal fun ProtectedItemCover(modifier: Modifier = Modifier) {
    val c = qasaColors
    val label = stringResource(R.string.vault_unlockprotectednote)
    Box(
        modifier.fillMaxSize().background(c.background).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.scale(CoverBadgeScale)) { RowIconBadge(QasaIcons.Lock, tint = c.accent) }
    }
}

/**
 * iOS `ProtectedUnlockSheet`: the shared header, the `protected.title` message, the main
 * password as a one-row group, Unlock at the bottom. With fingerprint / face on, the prompt
 * starts as the sheet opens and a secondary button starts it again. No Cancel: dragging it
 * down, the scrim or back runs [onCancel] (the caller goes back to the list).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProtectedUnlockSheet(
    biometricsEnabled: Boolean,
    verify: (String) -> Boolean,
    onUnlocked: () -> Unit,
    onCancel: () -> Unit,
) {
    val c = qasaColors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    val canUseBiometrics = remember { biometricsEnabled && BiometricGate.canAuthenticate(context) }
    var askingBiometrics by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (canUseBiometrics) askingBiometrics = true }

    BiometricGateEffect(
        enabled = askingBiometrics,
        title = stringResource(R.string.vault_unlockprotectednote),
        onAuthenticated = {
            askingBiometrics = false
            onUnlocked()
        },
        onDismissed = { askingBiometrics = false },
    )

    fun check() {
        if (password.isEmpty() || checking) return
        checking = true
        val typed = password
        scope.launch {
            // The key derivation is slow: off the main thread.
            val ok = withContext(Dispatchers.Default) { verify(typed) }
            checking = false
            if (ok) onUnlocked() else wrong = true
        }
    }

    QasaModalBottomSheet(onDismissRequest = onCancel, title = stringResource(R.string.vault_unlockprotectednote)) {
        UnlockSheetBody(
            password = password,
            onPasswordChange = {
                password = it
                wrong = false
            },
            error = if (wrong) stringResource(R.string.auth_unlock_wrongpassword) else null,
            unlockEnabled = password.isNotEmpty() && !checking,
            onUnlock = ::check,
            onBiometrics = if (canUseBiometrics) ({ askingBiometrics = true }) else null,
        )
    }
}

/** iOS scales the lock badge by 1.2. */
private const val CoverBadgeScale = 1.2f
