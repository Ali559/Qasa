package alibarznji.qasa.theme

import alibarznji.qasa.theme.QasaDimens
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** M3's `BottomSheetDefaults.DragHandle`: 22dp above and below a 4dp bar. */
private val SheetDragHandleHeight = 48.dp

/**
 * Closes the enclosing [QasaModalBottomSheet] the same way a swipe does: it slides down
 * first, then `onDismissRequest` runs. Null outside a sheet.
 */
val LocalSheetDismiss = staticCompositionLocalOf<(() -> Unit)?> { null }

/**
 * What a sheet's Cancel / Done / Save should call: the animated close when inside a
 * sheet, else [fallback]. Removing a sheet from composition directly (by flipping the
 * state that shows it) skips the slide-down, so the sheet vanished after a beat.
 */
@Composable
fun rememberSheetDismiss(fallback: () -> Unit): () -> Unit = LocalSheetDismiss.current ?: fallback

/**
 * The one sheet presentation for the whole app: add-item picker, vault editor,
 * generator, settings sub-pages, pair.
 *
 * `tonalElevation` and `shadowElevation` are pinned to 0 (§7.2): M3's default surface
 * tint derives from the partial `colorScheme.surface` and drifts off `background`,
 * so sheets did not match the canvas behind them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QasaModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
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
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = c.background,
        contentColor = c.textPrimary,
        scrimColor = c.scrim,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(QasaDimens.cornerRadiusLarge),
        dragHandle = { BottomSheetDefaults.DragHandle(color = c.textTertiary) },
        tonalElevation = QasaDimens.stroke,
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
                    // Sized to its content like an iOS sheet; with the drag handle above it,
                    // never taller than 92% of the window, so it stays clear of the status bar.
                    .heightIn(max = qasaSheetMaxHeight() - SheetDragHandleHeight)
                    .navigationBarsPadding(),
                content = content,
            )
        }
    }
}
