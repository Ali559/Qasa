package alibarznji.qasa.screens.auth

import alibarznji.qasa.R
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.QasaMotion
import alibarznji.qasa.theme.QasaSheetHeader
import alibarznji.qasa.theme.QasaTokens
import alibarznji.qasa.theme.UnlockSheetBody
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaReduceMotion
import alibarznji.qasa.theme.qasaText
import alibarznji.qasa.vault.BiometricGateEffect
import alibarznji.qasa.vault.BiometricPolicy
import alibarznji.qasa.vault.VaultSecureStore
import alibarznji.qasa.vault.VaultSession
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

/**
 * The app's unlock (`design/README.md` rule 6d, option B): the splash (the app tile centred
 * where the launch screen put it, the wordmark under it) and nothing of the vault. With
 * fingerprint / face on and the 14-day check not due, the system prompt starts by itself
 * once the window has settled; only when it is cancelled or fails (or there is no
 * biometric unlock) does the unlock sheet rise, and the tile moves up above it. The sheet
 * can't be dismissed: there is nothing behind it, and back leaves the app as before.
 */
@Composable
fun UnlockScreen(
    onUnlocked: () -> Unit,
    vm: UnlockViewModel = hiltViewModel(),
) {
    val c = qasaColors
    var password by remember { mutableStateOf("") }
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
    // Option B: the sheet stays down while the first prompt can run, and rises when it is
    // cancelled or fails, or straight away when there is no biometric unlock to offer.
    // Saveable, so a rotation does not lower a sheet the user is typing in.
    var sheetUp by rememberSaveable { mutableStateOf(!vm.session.biometricUnlockAllowed()) }
    LaunchedEffect(autoPromptArmed) {
        if (!autoPromptArmed) return@LaunchedEffect
        // `BiometricPrompt` must be shown from a resumed host.
        lifecycleOwner.lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
        // Let the window settle (the launch screen hands over, the splash is drawn) so the
        // prompt does not land on a frame that is still changing.
        delay(PromptSettleMillis)
        lifecycleOwner.lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
        // Consumed whether or not it prompts: one chance per appearance, never a retry loop.
        autoPromptArmed = false
        if (prompt != null || busy) return@LaunchedEffect
        if (!vm.session.biometricUnlockAllowed()) {
            sheetUp = true
            return@LaunchedEffect
        }
        // No cipher means no usable gate: never fall back to a presence-only prompt here.
        val cipher = vm.session.biometricUnlockCipher()
        if (cipher == null) {
            sheetUp = true
            return@LaunchedEffect
        }
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
                    runUnlock({ cipher != null && vm.session.unlockWithBiometricCipher(cipher) }) {
                        error = failedMessage
                        sheetUp = true
                    }
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
                is UnlockPrompt.Unlock -> {
                    error = failedMessage
                    sheetUp = true
                }
                null -> Unit
            }
        },
    )

    fun onUnlockTapped() {
        // The secret is sealed at unlock time, not at create time: a password can only be
        // cached behind a cipher the hardware authorized, and the user is holding the
        // password right now.
        val captureCipher = if (vm.session.needsBiometricCapture) vm.session.biometricCaptureCipher() else null
        if (captureCipher != null) {
            prompt = UnlockPrompt.Capture(captureCipher, password)
        } else {
            unlockWithPassword(password)
        }
    }

    UnlockSplash(sheetUp = sheetUp) {
        UnlockSheetBody(
            password = password,
            onPasswordChange = { password = it; error = null },
            // The wrong-password message: danger label and stripe, the text on its row.
            error = error,
            unlockEnabled = canUnlock && !busy,
            onUnlock = ::onUnlockTapped,
            onBiometrics = if (biometricAllowed) {
                { if (prompt == null) prompt = UnlockPrompt.Unlock(vm.session.biometricUnlockCipher()) }
            } else {
                null
            },
            note = if (showMasterPasswordDue) {
                stringResource(R.string.auth_unlock_masterpasswordduefmt, BiometricPolicy.MASTER_PASSWORD_INTERVAL_DAYS)
            } else {
                null
            },
        )
    }
}

/**
 * The splash and, when [sheetUp], the unlock sheet over it. The tile sits centred on the
 * whole window (where the Android 12+ launch screen draws `ic_splash_icon`), and moves up
 * to the middle of the room above the sheet as it rises (`standard`; at once with reduce
 * motion). The sheet is the app's sheet look (rule 6b) drawn in this window rather than
 * as a dialog, because it can't be dragged away and back must reach the activity.
 */
@Composable
private fun UnlockSplash(sheetUp: Boolean, sheet: @Composable () -> Unit) {
    val c = qasaColors
    val reduceMotion = qasaReduceMotion.enabled
    val density = LocalDensity.current
    var sheetHeightPx by remember { mutableIntStateOf(0) }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(c.background),
    ) {
        val windowHeightPx = constraints.maxHeight
        val topInsetPx = WindowInsets.statusBars.getTop(density)
        val iconPx = with(density) { SplashIconSize.toPx() }
        val groupPx = with(density) { (SplashIconSize + SplashGap + SplashWordmarkRoom).toPx() }
        // Down: the tile's centre on the window's centre. Up: the tile and wordmark centred
        // in the room between the status bar and the sheet, never under the status bar.
        val centredTop = (windowHeightPx - iconPx) / 2f
        val raisedTop = (topInsetPx + (windowHeightPx - sheetHeightPx - topInsetPx - groupPx) / 2f)
            .coerceAtLeast(topInsetPx.toFloat())
        val iconTop by animateFloatAsState(
            targetValue = if (sheetUp && sheetHeightPx > 0) raisedTop else centredTop,
            animationSpec = if (reduceMotion) snap() else QasaMotion.standardTween(),
            label = "unlockSplashIcon",
        )
        Column(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, iconTop.roundToInt()) },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painterResource(R.drawable.ic_qasa_app_icon),
                contentDescription = null,
                modifier = Modifier.size(SplashIconSize),
            )
            Spacer(Modifier.height(SplashGap))
            Text(stringResource(R.string.brand_name), color = c.accent, style = qasaText.brandStandalone)
        }
        AnimatedVisibility(
            visible = sheetUp,
            enter = fadeIn(if (reduceMotion) QasaMotion.fadeTween() else QasaMotion.standardTween()),
            exit = androidx.compose.animation.fadeOut(QasaMotion.fadeTween()),
        ) {
            Box(Modifier.fillMaxSize().background(c.scrim))
        }
        AnimatedVisibility(
            visible = sheetUp,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = if (reduceMotion) {
                fadeIn(QasaMotion.fadeTween())
            } else {
                slideInVertically(QasaMotion.standardTween()) { it }
            },
        ) {
            UnlockSheetSurface(Modifier.onSizeChanged { sheetHeightPx = it.height }, sheet)
        }
    }
}

/** The app's sheet look (`QasaModalBottomSheet`): 28 top corners, the grabber, the header. */
@Composable
private fun UnlockSheetSurface(modifier: Modifier, content: @Composable () -> Unit) {
    val c = qasaColors
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = QasaDimens.sheetRadius, topEnd = QasaDimens.sheetRadius))
            .background(c.background)
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Box(Modifier.fillMaxWidth().height(QasaDimens.sheetTitleTop), contentAlignment = Alignment.TopCenter) {
            Box(
                Modifier
                    .padding(top = QasaDimens.sheetGrabberTop)
                    .size(QasaDimens.sheetGrabberWidth, QasaDimens.sheetGrabberHeight)
                    .background(c.textTertiary, RoundedCornerShape(QasaTokens.Radius.sheetGrabber)),
            )
        }
        QasaSheetHeader(stringResource(R.string.auth_unlock_sheettitle))
        content()
    }
}

/** `design/README.md` rule 6d: the system prompt starts once the window has settled. */
private const val PromptSettleMillis = 450L

/** The app tile on the splash, as on the launch screen (`ic_splash_icon`). */
private val SplashIconSize = 96.dp

/** Between the tile and the wordmark. */
private val SplashGap = 14.dp

/** The wordmark line's height, for centring the tile and wordmark above the sheet. */
private val SplashWordmarkRoom = 32.dp
