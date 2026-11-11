package alibarznji.qasa.theme

import alibarznji.qasa.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Closes the enclosing [QasaModalBottomSheet] the same way a swipe does: it slides down
 * first, then `onDismissRequest` runs. Null outside a sheet.
 */
val LocalSheetDismiss = staticCompositionLocalOf<(() -> Unit)?> { null }

/**
 * What a sheet's own Done / Save should call: the animated close when inside a sheet,
 * else [fallback]. Removing a sheet from composition directly (by flipping the state that
 * shows it) skips the slide-down, so the sheet vanished after a beat.
 */
@Composable
fun rememberSheetDismiss(fallback: () -> Unit): () -> Unit = LocalSheetDismiss.current ?: fallback

/**
 * The one sheet presentation for the whole app (`design/README.md` rule 4):
 *
 * - [QasaDimens.sheetRadius] (28) top corners, `background` fill, no tonal tint or shadow.
 * - A 36 × 5 fully rounded `textTertiary` grabber, 5dp from the top edge (drawn here; M3's
 *   `DragHandle` is a 48dp-tall slot with a 32 × 4 bar).
 * - With a [title]: `type.sheetTitle` (17 semibold) `textPrimary`, centered, its top 20dp
 *   from the sheet's top edge, then 16dp to the content. [titleTrailing] is the rare extra
 *   action beside it (e.g. "Mark all read"); there is never a Cancel in the header: the sheet
 *   closes by dragging, tapping the scrim, or back.
 * - Without a title the content starts 20dp from the top, where a title would be.
 * - Sized to its content, never taller than 92% of the window.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QasaModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    titleTrailing: (@Composable () -> Unit)? = null,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = qasaColors
    // The sheet is its own window, so MainActivity.onUserInteraction never sees it.
    val activity = LocalUserActivity.current
    val scope = rememberCoroutineScope()
    val onDismiss by rememberUpdatedState(onDismissRequest)
    val animatedDismiss: () -> Unit = remember(sheetState) {
        {
            scope.launch { sheetState.hide() }.invokeOnCompletion {
                if (!sheetState.isVisible) onDismiss()
            }
        }
    }
    val sheetShape = RoundedCornerShape(topStart = QasaDimens.sheetRadius, topEnd = QasaDimens.sheetRadius)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = c.background,
        contentColor = c.textPrimary,
        scrimColor = c.scrim,
        shape = sheetShape,
        dragHandle = { SheetGrabber(onDismiss = animatedDismiss) },
        tonalElevation = 0.dp,
        // Bottom insets only (gesture bar, keyboard). The default also pads the top by the
        // status bar, and its share changed as the sheet moved: the sheet crept up for a
        // second after opening, and a fling at the top of its scroll set it bouncing.
        contentWindowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal) },
        modifier = modifier,
    ) {
        CompositionLocalProvider(LocalSheetDismiss provides animatedDismiss) {
            Column(
                Modifier
                    .reportUserActivity(activity)
                    .fillMaxWidth()
                    // Sized to its content like an iOS sheet; with the grabber slot above it,
                    // never taller than 92% of the window, so it stays clear of the status bar.
                    .heightIn(max = qasaSheetMaxHeight() - QasaDimens.sheetTitleTop)
                    .navigationBarsPadding(),
            ) {
                if (title != null) QasaSheetHeader(title, trailing = titleTrailing)
                content()
            }
        }
    }
}

/**
 * The grabber slot: [QasaDimens.sheetTitleTop] (20) tall, so a title or the first element
 * below it starts 20dp from the sheet's top edge; the 36 × 5 bar sits 5dp from the top.
 * TalkBack gets a "Close" action on it, as on M3's handle.
 */
@Composable
private fun SheetGrabber(onDismiss: () -> Unit) {
    val c = qasaColors
    val closeLabel = stringResource(R.string.common_close)
    Box(
        Modifier
            .fillMaxWidth()
            .height(QasaDimens.sheetTitleTop)
            .semantics {
                contentDescription = closeLabel
                onClick(label = closeLabel) {
                    onDismiss()
                    true
                }
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            Modifier
                .padding(top = QasaDimens.sheetGrabberTop)
                .size(QasaDimens.sheetGrabberWidth, QasaDimens.sheetGrabberHeight)
                .background(c.textTertiary, RoundedCornerShape(QasaTokens.Radius.sheetGrabber)),
        )
    }
}

/**
 * iOS `SheetHeader` (`Controls.swift`): the sheet title, `type.sheetTitle` (17 semibold)
 * `textPrimary`, centered on one line with room for a 44dp control on both sides, then
 * [QasaDimens.sheetTitleBottom] (16) to the content. Drawn directly under the grabber slot,
 * so its top sits 20dp from the sheet's top edge. [onBack] adds the 40dp back circle at
 * the start (a drilled-in page inside one sheet); [trailing] is a rare extra action at the
 * end. Never a Cancel / Close / Done.
 *
 * [QasaModalBottomSheet] draws it for its `title`; a page that changes title while the
 * sheet stays up (add-item drill-in, the item editor) draws it itself.
 */
@Composable
fun QasaSheetHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = qasaColors
    Box(
        modifier
            .fillMaxWidth()
            .padding(bottom = QasaDimens.sheetTitleBottom),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title,
            color = c.textPrimary,
            style = qasaText.sheetTitle,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                // iOS pads the title by screenPadding + 44 so it stays centered on the sheet.
                .padding(horizontal = QasaDimens.screenPadding + TitleControlRoom)
                .semantics { heading() },
        )
        if (onBack != null) {
            Box(Modifier.align(Alignment.CenterStart).padding(start = QasaDimens.screenPadding)) {
                HeaderBackButton(onBack)
            }
        }
        if (trailing != null) {
            Box(Modifier.align(Alignment.CenterEnd).padding(end = QasaDimens.screenPadding)) { trailing() }
        }
    }
}

/** iOS `SheetHeader`: room for a trailing control on each side of the centered title. */
private val TitleControlRoom = QasaTokens.Space.sheetTitleSideInset
