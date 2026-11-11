package alibarznji.qasa.screens.auth

import alibarznji.qasa.R
import alibarznji.qasa.theme.BrandHeader
import alibarznji.qasa.theme.CardContainer
import alibarznji.qasa.theme.PrimaryButton
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.QasaIcons
import alibarznji.qasa.theme.RowChevron
import alibarznji.qasa.theme.QasaIconSize
import alibarznji.qasa.theme.ScreenScaffold
import alibarznji.qasa.theme.QasaFieldGroup
import alibarznji.qasa.theme.QasaSecureRow
import alibarznji.qasa.theme.StatusPill
import alibarznji.qasa.theme.StickyBottomBar
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import alibarznji.qasa.vault.BiometricGateEffect
import alibarznji.qasa.vault.BiometricPolicy
import alibarznji.qasa.vault.VaultSecureStore
import alibarznji.qasa.vault.VaultSession
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.rememberCoroutineScope

@HiltViewModel
class UnlockViewModel @Inject constructor(
    val session: VaultSession,
    val store: VaultSecureStore,
) : ViewModel()

/**
 * One in-flight gate attempt. [cipher] is the `CryptoObject` the prompt must authorize:
 * a DECRYPT cipher to read the sealed master password, or an ENCRYPT cipher to seal it.
 * A `null` cipher means this device has no usable gate, and no prompt is shown.
 */
private sealed interface UnlockPrompt {
    val cipher: javax.crypto.Cipher?

    data class Unlock(override val cipher: javax.crypto.Cipher?) : UnlockPrompt

    data class Capture(
        override val cipher: javax.crypto.Cipher,
        val password: String,
    ) : UnlockPrompt
}

@Composable
fun UnlockScreen(
    onUnlocked: () -> Unit,
    vm: UnlockViewModel = hiltViewModel(),
) {
    val c = qasaColors
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var prompt by remember { mutableStateOf<UnlockPrompt?>(null) }
    val canUnlock = password.isNotBlank()
    val failedMessage = stringResource(R.string.auth_unlock_biometricsfailed)
    val wrongPasswordMessage = stringResource(R.string.auth_unlock_wrongpassword)

    // Deriving the key (310,000 PBKDF2 rounds) takes a moment: off the main thread, or a
    // slow phone shows "Qasa isn't responding" while it runs.
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    fun runUnlock(work: () -> Boolean, onFail: () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            val ok = withContext(Dispatchers.Default) { work() }
            busy = false
            if (ok) {
                password = ""
                onUnlocked()
            } else {
                onFail()
            }
        }
    }

    fun unlockWithPassword(candidate: String) {
        runUnlock({ vm.session.unlockMatching(candidate) != null }) { error = wrongPasswordMessage }
    }

    // ---- Biometric auto-prompt, once per appearance ---------------------------------
    //
    // `autoPromptArmed` is saveable, so a rotation or locale recreate keeps it consumed and
    // never re-prompts. It is re-armed only by a real return to the foreground (an ON_START
    // that follows an ON_STOP this observer saw), and not when the stop was caused by the
    // prompt itself: the device-credential fallback is its own activity, so cancelling it
    // stops and restarts us, and re-arming there would loop.
    var autoPromptArmed by rememberSaveable { mutableStateOf(true) }
    // Bumped on every foreground return so the interval check below is re-read: the screen
    // can sit locked across the 14-day boundary.
    var foregroundTick by remember { mutableIntStateOf(0) }
    val promptInFlight = rememberUpdatedState(prompt != null)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        // Adding the observer replays ON_CREATE/ON_START/ON_RESUME; those must not re-arm.
        var sawStop = false
        var stoppedDuringPrompt = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    sawStop = true
                    stoppedDuringPrompt = promptInFlight.value
                }
                Lifecycle.Event.ON_START -> if (sawStop) {
                    sawStop = false
                    foregroundTick++
                    if (!stoppedDuringPrompt) autoPromptArmed = true
                    stoppedDuringPrompt = false
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val masterPasswordDue = remember(foregroundTick, busy) { vm.session.masterPasswordDue }
    val biometricAllowed = remember(foregroundTick, busy, prompt) { vm.session.biometricUnlockAllowed() }
    val showMasterPasswordDue = masterPasswordDue && vm.session.biometricsEnabled
    LaunchedEffect(autoPromptArmed) {
        if (!autoPromptArmed) return@LaunchedEffect
        // `BiometricPrompt` must be shown from a resumed host.
        lifecycleOwner.lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
        // Consumed whether or not it prompts: one chance per appearance, never a retry loop.
        autoPromptArmed = false
        if (prompt != null || busy || !vm.session.biometricUnlockAllowed()) return@LaunchedEffect
        // No cipher means no usable gate: never fall back to a presence-only prompt here.
        val cipher = vm.session.biometricUnlockCipher() ?: return@LaunchedEffect
        prompt = UnlockPrompt.Unlock(cipher)
    }

    BiometricGateEffect(
        enabled = prompt != null,
        title = stringResource(R.string.auth_unlock_biometrics),
        subtitle = stringResource(R.string.auth_unlock_biometricshint),
        cipher = prompt?.cipher,
        onAuthenticated = { cipher ->
            when (val attempt = prompt) {
                is UnlockPrompt.Unlock -> {
                    runUnlock({ cipher != null && vm.session.unlockWithBiometricCipher(cipher) }) { error = failedMessage }
                }
                is UnlockPrompt.Capture -> {
                    // Unlock first so only the variant that verified gets sealed (sealing
                    // the password requires the same authorization as reading it). Sealing
                    // is optional, so a failed seal still lets the user in.
                    runUnlock({
                        val used = vm.session.unlockMatching(attempt.password)
                        if (used != null && cipher != null) vm.session.captureBiometricSecret(cipher, used)
                        used != null
                    }) { error = wrongPasswordMessage }
                }
                null -> Unit
            }
            prompt = null
        },
        onDismissed = {
            val attempt = prompt
            prompt = null
            when (attempt) {
                // Sealing is an optional convenience, not the unlock itself: a declined
                // or failed prompt must not lock the user out of a password they know.
                is UnlockPrompt.Capture -> unlockWithPassword(attempt.password)
                is UnlockPrompt.Unlock -> error = failedMessage
                null -> Unit
            }
        },
    )

    ScreenScaffold(
        header = { BrandHeader(applyStatusBars = false) { StatusPill(stringResource(R.string.brand_localoffline)) } },
        stickyBottom = {
            StickyBottomBar {
                PrimaryButton(
                    title = stringResource(R.string.auth_unlock_button),
                    icon = QasaIcons.Unlock,
                    enabled = canUnlock && !busy,
                    onClick = {
                        // The secret is sealed at unlock time, not at create time: a
                        // password can only be cached behind a cipher the hardware
                        // authorized, and the user is holding the password right now.
                        val captureCipher = if (vm.session.needsBiometricCapture) {
                            vm.session.biometricCaptureCipher()
                        } else {
                            null
                        }
                        if (captureCipher != null) {
                            prompt = UnlockPrompt.Capture(captureCipher, password)
                        } else {
                            unlockWithPassword(password)
                        }
                    },
                )
            }
        },
    ) {
        Text(stringResource(R.string.auth_unlock_title), color = c.textPrimary, style = qasaText.displayLarge)
        Spacer(Modifier.height(QasaDimens.contentGap))
        Text(
            stringResource(R.string.auth_unlock_subtitle),
            color = c.textSecondary,
            style = qasaText.bodyMedium
        )
        Spacer(Modifier.height(QasaDimens.screenPadding))
        QasaFieldGroup {
            QasaSecureRow(
                label = stringResource(R.string.auth_unlock_masterpassword),
                value = password,
                onValueChange = { password = it; error = null },
                // The wrong-password message: danger label and stripe, the text under the card.
                error = error,
                revealed = visible,
                onToggleReveal = { visible = !visible },
            )
        }
        if (showMasterPasswordDue) {
            Spacer(Modifier.height(QasaDimens.contentGap))
            Text(
                stringResource(R.string.auth_unlock_masterpasswordduefmt, BiometricPolicy.MASTER_PASSWORD_INTERVAL_DAYS),
                color = c.textSecondary,
                style = qasaText.bodyMedium,
            )
        }
        if (biometricAllowed) {
            Spacer(Modifier.height(QasaDimens.gap))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(QasaDimens.cornerRadius))
                    .background(c.cardBackground)
                    .border(QasaDimens.stroke, c.stroke, RoundedCornerShape(QasaDimens.cornerRadius))
                    .clickable {
                        if (prompt == null) {
                            prompt = UnlockPrompt.Unlock(vm.session.biometricUnlockCipher())
                        }
                    }
                    .padding(QasaDimens.gap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(QasaIconSize.x6l)
                        .clip(CircleShape)
                        .background(c.cardBackgroundLight),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        QasaIcons.Fingerprint,
                        contentDescription = null,
                        tint = c.accent,
                        modifier = Modifier.size(QasaIconSize.xxl),
                    )
                }
                Spacer(Modifier.width(QasaDimens.gapSmall))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.auth_unlock_biometrics), color = c.textPrimary, style = qasaText.rowTitle)
                    Text(stringResource(R.string.auth_unlock_biometricshint), color = c.textSecondary, style = MaterialTheme.typography.labelMedium)
                }
                RowChevron()
            }
        }
        Spacer(Modifier.height(QasaDimens.gap))
        // iOS: shield + "Local unlock only" title above the body.
        CardContainer(background = c.cardBackgroundLight) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(QasaIcons.Shield, null, tint = c.accent)
                Spacer(Modifier.width(QasaDimens.contentGap))
                Text(stringResource(R.string.auth_unlock_localonly), color = c.textPrimary, style = qasaText.rowTitle)
            }
            Spacer(Modifier.height(QasaDimens.contentGap))
            Text(
                stringResource(R.string.auth_unlock_localonlybody),
                color = c.textSecondary,
                style = qasaText.bodyMedium
            )
        }
        Spacer(Modifier.height(QasaDimens.heroArtHeight))
    }
}
