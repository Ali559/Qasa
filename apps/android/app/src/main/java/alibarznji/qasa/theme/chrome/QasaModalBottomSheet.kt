package alibarznji.qasa.theme

import alibarznji.qasa.theme.QasaDimens
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

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
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = c.background,
        contentColor = c.textPrimary,
        scrimColor = c.scrim,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(QasaDimens.cornerRadiusLarge),
        dragHandle = { BottomSheetDefaults.DragHandle(color = c.textTertiary) },
        tonalElevation = QasaDimens.stroke,
        modifier = modifier,
    ) {
        Column(
            Modifier
                .reportUserActivity(activity)
                .fillMaxWidth()
                .navigationBarsPadding(),
            content = content,
        )
    }
}
