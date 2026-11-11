package alibarznji.qasa.theme

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay

/** One toast. [id] makes a repeat of the same text restart the timer. */
@Immutable
data class QasaToastMessage(val id: Long, val text: String)

/**
 * The app's one transient confirmation ("Gmail added"). Owned by the root
 * ([QasaToastHost] in `RootNav`), so a toast shown from a sheet stays up after the sheet
 * closes. Main-thread only, like any Compose state.
 */
@Stable
class QasaToastState {
    var current by mutableStateOf<QasaToastMessage?>(null)
        private set

    private var nextId = 0L

    /** Shows [text], replacing whatever toast is up. */
    fun show(text: String) {
        nextId += 1
        current = QasaToastMessage(nextId, text)
    }

    /** Hides the toast now, e.g. when the vault locks and the text may name an item. */
    fun clear() {
        current = null
    }

    internal fun dismiss(id: Long) {
        if (current?.id == id) current = null
    }
}

/**
 * Where screens find the toast. The default is a detached state with no host, so a
 * preview or a surface outside `RootNav` (autofill) simply shows nothing.
 */
val LocalQasaToast = staticCompositionLocalOf { QasaToastState() }

/**
 * Bottom-anchored toast slot: above the tab bar or sticky action bar and the navigation
 * bar, centred, auto-dismissed after [QasaMotion.TOAST_VISIBLE] (stretched to the user's
 * accessibility timeout when they set one). Slides and fades in, or just appears when
 * motion is reduced.
 */
@Composable
fun QasaToastHost(state: QasaToastState, modifier: Modifier = Modifier) {
    val message = state.current
    val accessibility = LocalAccessibilityManager.current
    LaunchedEffect(message?.id) {
        val shown = message ?: return@LaunchedEffect
        val timeout = accessibility?.calculateRecommendedTimeoutMillis(
            QasaMotion.TOAST_VISIBLE,
            containsIcons = true,
            containsText = true,
            containsControls = false,
        ) ?: QasaMotion.TOAST_VISIBLE
        delay(timeout)
        state.dismiss(shown.id)
    }
    val reduceMotion = qasaReduceMotion.enabled
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = QasaDimens.screenPadding)
            .padding(bottom = QasaDimens.toastBottomClearance),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedContent(
            targetState = message,
            contentKey = { it?.id },
            transitionSpec = {
                // iOS: `.move(edge: .bottom) + .opacity` on the toast spring; with reduce
                // motion, a 200ms ease-in-out fade only (`motion.fade`).
                if (reduceMotion) {
                    fadeIn(QasaMotion.fadeTween()) togetherWith fadeOut(QasaMotion.fadeTween())
                } else {
                    (fadeIn(QasaMotion.toastSpring()) + slideInVertically(QasaMotion.toastSpring()) { it }) togetherWith
                        (fadeOut(QasaMotion.toastSpring()) + slideOutVertically(QasaMotion.toastSpring()) { it })
                }
            },
            contentAlignment = Alignment.BottomCenter,
            label = "qasaToast",
        ) { shown ->
            if (shown != null) QasaToast(shown.text)
        }
    }
}

/**
 * iOS `ToastView`: accent fill, [QasaDimens.controlRadius] corners, 16 × 12 padding, at
 * least 44dp tall, a 17dp check 10dp before `type.toast` (14 semibold) `onAccent` text on up
 * to two lines, and a soft black 18% shadow 6dp down (`shadow.toast`).
 */
@Composable
fun QasaToast(text: String, modifier: Modifier = Modifier) {
    val c = qasaColors
    val shape = RoundedCornerShape(QasaDimens.controlRadius)
    Row(
        modifier = modifier
            .dropShadow(
                shape = shape,
                shadow = Shadow(
                    // SwiftUI's shadow radius is half the CSS-style blur Compose takes
                    // (tokens.css draws the same shadow with a 28px blur).
                    radius = QasaTokens.Shadow.toastRadius * 2,
                    color = QasaTokens.Shadow.toastColor,
                    offset = DpOffset(QasaTokens.Shadow.toastOffsetX, QasaTokens.Shadow.toastOffsetY),
                ),
            )
            .background(c.accent, shape)
            .heightIn(min = TOAST_MIN_HEIGHT)
            .padding(horizontal = QasaDimens.toastHorizontal, vertical = QasaDimens.toastVertical)
            // One node, announced politely once it appears (Material's snackbar does the same).
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(QasaDimens.gapMedium),
    ) {
        Icon(
            QasaIcons.Success,
            contentDescription = null,
            tint = c.onAccent,
            modifier = Modifier.size(QasaIconSize.menu),
        )
        Text(
            text,
            color = c.onAccent,
            style = qasaText.toast,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/** iOS `ToastView.frame(minHeight:)`. */
private val TOAST_MIN_HEIGHT = QasaTokens.Size.toastMinHeight
