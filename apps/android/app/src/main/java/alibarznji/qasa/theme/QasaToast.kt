package alibarznji.qasa.theme

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
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
                if (reduceMotion) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    (fadeIn(tween(QasaMotion.STANDARD)) + slideInVertically(tween(QasaMotion.STANDARD)) { it / 2 }) togetherWith
                        (fadeOut(tween(QasaMotion.FAST)) + slideOutVertically(tween(QasaMotion.FAST)) { it / 2 })
                }
            },
            contentAlignment = Alignment.BottomCenter,
            label = "qasaToast",
        ) { shown ->
            if (shown != null) QasaToast(shown.text)
        }
    }
}

/** Accent pill with a check: the toast's look, on its own for previews. */
@Composable
fun QasaToast(text: String, modifier: Modifier = Modifier) {
    val c = qasaColors
    val shape = RoundedCornerShape(QasaDimens.cornerRadius)
    Row(
        modifier = modifier
            .shadow(
                elevation = QasaDimens.contentGap,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.2f),
                spotColor = Color.Black.copy(alpha = 0.2f),
            )
            .background(c.accent, shape)
            .padding(horizontal = QasaDimens.gap, vertical = QasaDimens.gapSmall)
            // One node, announced politely once it appears (Material's snackbar does the same).
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(QasaDimens.gapMedium),
    ) {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = c.onAccent,
            modifier = Modifier.size(QasaIconSize.xl),
        )
        Text(
            text,
            color = c.onAccent,
            style = qasaText.bodyMediumSemibold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}
