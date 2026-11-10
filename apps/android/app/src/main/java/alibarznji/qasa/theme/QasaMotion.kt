package alibarznji.qasa.theme

import android.provider.Settings
import androidx.compose.animation.core.Spring
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

/** iOS uses five durations; Android had none (`docs/design.md` §8.1). */
object QasaMotion {
    /** 180ms — selection, language/theme change. */
    const val FAST = 180

    /** 200ms — drawer close, plan dot, plan page. */
    const val STANDARD = 200

    /** 220ms — drawer open. */
    const val DRAWER = 220

    /** `CheckToggle` thumb spring. */
    const val TOGGLE_RESPONSE = 0.25f
    const val TOGGLE_DAMPING = 0.8f

    /** 1100ms — health gauge trim + score count-up. */
    const val GAUGE = 1100

    /** 120ms — press-scale feedback. */
    const val PRESS = 120

    const val PRESS_SCALE = 0.97f

    /** 2500ms — how long a [QasaToast] stays up. */
    const val TOAST_VISIBLE = 2500L

    fun toggleSpring() = spring<Float>(Spring.DampingRatioNoBouncy, TOGGLE_RESPONSE, TOGGLE_DAMPING)

    fun fastTween() = tween<Float>(FAST)

    fun standardTween() = tween<Float>(STANDARD)

    fun drawerTween() = tween<Float>(DRAWER)

    fun gaugeTween() = tween<Float>(GAUGE)
}

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
 * Press-scale value for a tappable surface. 1f when motion is reduced or [enabled] is
 * false. Buttons and the FAB scale (§8.3); list rows pass `enabled = false` because iOS
 * defines no `ButtonStyle`, so its rows only highlight and never shrink on tap.
 */
@Composable
fun rememberPressScale(interactionSource: MutableInteractionSource, enabled: Boolean = true): Float {
    val pressed by interactionSource.collectIsPressedAsState()
    val reduce = qasaReduceMotion.enabled
    return animateFloatAsState(
        targetValue = if (enabled && pressed && !reduce) QasaMotion.PRESS_SCALE else 1f,
        animationSpec = tween(QasaMotion.PRESS),
        label = "qasaPressScale",
    ).value
}
