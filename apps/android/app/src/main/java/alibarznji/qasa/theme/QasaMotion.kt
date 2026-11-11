package alibarznji.qasa.theme

import android.provider.Settings
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * The named timings of `design/tokens.json` `motion`, read from [QasaTokens.Motion] so they
 * match iOS: `press` 120ms ease-out, `standard` 220ms ease-out, `fast` 180ms ease-in-out,
 * `fade` 200ms ease-in-out, springs `toggle` / `toast`. Curves are the iOS ones
 * ([QasaTokens.Easings]), never M3's default `FastOutSlowIn`.
 */
object QasaMotion {
    /** 180ms ease-in-out — selection, color changes, language/theme change. */
    val FAST: Int = QasaTokens.Motion.fast.millis

    /** 220ms ease-out — sheet-sized transitions, drawer. */
    val STANDARD: Int = QasaTokens.Motion.standard.millis

    /** 220ms — drawer open (same as [STANDARD]). */
    val DRAWER: Int = QasaTokens.Motion.standard.millis

    /** 200ms ease-in-out — the reduce-motion replacement for slides. */
    val FADE: Int = QasaTokens.Motion.fade.millis

    /** `CheckToggle` thumb spring (SwiftUI `response` / `dampingFraction`). */
    val TOGGLE_RESPONSE: Float = QasaTokens.Motion.toggle.response
    val TOGGLE_DAMPING: Float = QasaTokens.Motion.toggle.dampingFraction

    /** 1100ms ease-out — health gauge trim + score count-up. */
    val GAUGE: Int = QasaTokens.Motion.gauge.millis

    /** 120ms ease-out — press feedback. */
    val PRESS: Int = QasaTokens.Motion.press.millis

    /** 0.98 — pressed buttons scale to this. */
    const val PRESS_SCALE: Float = QasaTokens.Motion.pressScale

    /** 0.85 — pressed buttons fade to this. */
    const val PRESSED_OPACITY: Float = QasaTokens.Motion.pressedOpacity

    /** 0.45 — disabled buttons. */
    const val DISABLED_OPACITY: Float = QasaTokens.Motion.disabledOpacity

    /** 0.06 — `textPrimary` wash behind a pressed row (`RowButtonStyle`). */
    const val ROW_PRESSED_OPACITY: Float = QasaTokens.Motion.rowPressedOpacity

    /** 2500ms — how long a [QasaToast] stays up. */
    const val TOAST_VISIBLE: Long = QasaTokens.Motion.toastVisibleMillis

    fun <T> pressTween() = QasaTokens.Motion.press.spec<T>()

    fun <T> fastTween() = QasaTokens.Motion.fast.spec<T>()

    fun <T> standardTween() = QasaTokens.Motion.standard.spec<T>()

    fun <T> fadeTween() = QasaTokens.Motion.fade.spec<T>()

    fun <T> drawerTween() = QasaTokens.Motion.standard.spec<T>()

    fun <T> gaugeTween() = QasaTokens.Motion.gauge.spec<T>()

    /** SwiftUI `.spring(response: 0.25, dampingFraction: 0.8)`. */
    fun <T> toggleSpring() = QasaTokens.Motion.toggle.spec<T>()

    /** SwiftUI `.spring(response: 0.35, dampingFraction: 0.85)`. */
    fun <T> toastSpring() = QasaTokens.Motion.toast.spec<T>()
}

/** This timing as a Compose tween on its iOS curve. */
fun <T> QasaTokenTween.spec(): TweenSpec<T> = tween(durationMillis = millis, easing = easing)

/** This SwiftUI spring as a Compose spring (same stiffness and damping ratio). */
fun <T> QasaTokenSpring.spec(): SpringSpec<T> = spring(dampingRatio = dampingRatio, stiffness = stiffness)

/**
 * Honours `Settings.Global.ANIMATOR_DURATION_SCALE` / iOS
 * `accessibilityReduceMotion`. iOS runs the 1100ms gauge and the toggle spring
 * regardless — a defect (§8.1).
 */
data class QasaReduceMotion(val enabled: Boolean)

val LocalQasaReduceMotion = staticCompositionLocalOf { QasaReduceMotion(false) }

val qasaReduceMotion: QasaReduceMotion
    @Composable @ReadOnlyComposable get() = LocalQasaReduceMotion.current

@Composable
fun rememberQasaReduceMotion(): QasaReduceMotion {
    val context = LocalContext.current
    return remember(context) {
        val scale = runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        }.getOrDefault(1f)
        QasaReduceMotion(scale == 0f)
    }
}

/** Provides the reduce-motion flag once, at the root. */
@Composable
fun ProvideQasaMotion(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalQasaReduceMotion provides rememberQasaReduceMotion(), content = content)
}

/**
 * Press-scale value for a tappable surface: [QasaMotion.PRESS_SCALE] while pressed, over
 * `motion.press` (120ms ease-out), as iOS `QasaButtonStyle` / `PressableStyle`. 1f when
 * motion is reduced or [enabled] is false: list rows pass `enabled = false` because iOS
 * rows (`RowButtonStyle`) only wash and never shrink.
 */
@Composable
fun rememberPressScale(interactionSource: MutableInteractionSource, enabled: Boolean = true): Float {
    val pressed by interactionSource.collectIsPressedAsState()
    val reduce = qasaReduceMotion.enabled
    return animateFloatAsState(
        targetValue = if (enabled && pressed && !reduce) QasaMotion.PRESS_SCALE else 1f,
        animationSpec = QasaMotion.pressTween(),
        label = "qasaPressScale",
    ).value
}

/**
 * Opacity of a button: [QasaMotion.DISABLED_OPACITY] when disabled, else
 * [QasaMotion.PRESSED_OPACITY] while pressed, over `motion.press`. iOS animates only the
 * pressed change; the disabled value applies at once.
 */
@Composable
fun rememberPressOpacity(interactionSource: MutableInteractionSource, enabled: Boolean = true): Float {
    val pressed by interactionSource.collectIsPressedAsState()
    val target = if (pressed && enabled) QasaMotion.PRESSED_OPACITY else 1f
    val animated = animateFloatAsState(target, QasaMotion.pressTween(), label = "qasaPressOpacity").value
    return if (enabled) animated else QasaMotion.DISABLED_OPACITY
}
