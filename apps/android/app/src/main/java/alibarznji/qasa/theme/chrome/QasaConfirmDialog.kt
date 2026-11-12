package alibarznji.qasa.theme

import alibarznji.qasa.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions

/**
 * iOS `confirmSheet` (`SelfSizingSheet` + `ConfirmSheetContent`): a [QasaModalBottomSheet]
 * sized to its content. From 20dp below the sheet's top edge (`space.sheetContentTop`):
 * an optional icon badge, the title (`type.dialogTitle`, 19 semibold) and message
 * (`type.dialogMessage`, 15, `textSecondary`) centered 10dp apart, optional extra
 * [content] (progress, an error line, a field), then 20dp to the full-width confirm button
 * (`danger` when [destructive]) and the secondary cancel 10dp below it; 16dp at the bottom.
 * Every destructive or account-changing action goes through this, so a single tap never
 * deletes, unlinks or cancels on its own. A confirmation keeps its two buttons: they are
 * the dialog's two answers.
 *
 * [onConfirm] runs straight away (callers close the sheet by clearing their state, or keep
 * it up to show a failure); Cancel and a swipe slide the sheet down, then [onDismiss].
 * While [enabled] is false (work in flight) the buttons are disabled and the sheet cannot
 * be swiped away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QasaConfirmSheet(
    title: String,
    message: String?,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    /** `null` for an informational sheet with a single button. */
    dismissLabel: String? = stringResource(R.string.common_cancel),
    destructive: Boolean = true,
    enabled: Boolean = true,
    confirmEnabled: Boolean = true,
    icon: ImageVector? = null,
    /** Title-to-message gap (10, as on iOS). */
    textGap: Dp = QasaDimens.sheetTextGap,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val c = qasaColors
    val canDismiss by rememberUpdatedState(enabled)
    val state = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || canDismiss },
    )
    QasaModalBottomSheet(
        onDismissRequest = { if (canDismiss) onDismiss() },
        sheetState = state,
    ) {
        val close = rememberSheetDismiss(onDismiss)
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = QasaDimens.screenPadding)
                .padding(bottom = QasaDimens.sheetContentBottom),
            verticalArrangement = Arrangement.spacedBy(QasaDimens.sheetActionsGap),
        ) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(textGap),
            ) {
                if (icon != null) {
                    // iOS `IconBadge(...).scaleEffect(1.2).padding(.bottom, 4)`: the scale is
                    // drawn only, the layout keeps the 36dp badge.
                    Box(
                        Modifier
                            .padding(bottom = QasaDimens.spaceSmall)
                            .scale(CONFIRM_ICON_SCALE),
                    ) {
                        RowIconBadge(icon, tint = if (destructive) c.danger else c.accent)
                    }
                }
                Text(
                    title,
                    color = c.textPrimary,
                    style = qasaText.dialogTitle,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().semantics { heading() },
                )
                if (!message.isNullOrBlank()) {
                    Text(
                        message,
                        color = c.textSecondary,
                        style = qasaText.dialogMessage,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (content != null) {
                Column(verticalArrangement = Arrangement.spacedBy(QasaDimens.sheetButtonGap), content = content)
            }
            Column(verticalArrangement = Arrangement.spacedBy(QasaDimens.sheetButtonGap)) {
                PrimaryButton(
                    title = confirmLabel,
                    onClick = onConfirm,
                    enabled = enabled && confirmEnabled,
                    destructive = destructive,
                )
                if (dismissLabel != null) {
                    SecondaryButton(title = dismissLabel, onClick = close, enabled = enabled)
                }
            }
        }
    }
}

/** iOS scales the confirmation badge by 1.2. */
private const val CONFIRM_ICON_SCALE = QasaTokens.Motion.confirmIconScale

/**
 * Kept for existing call sites: the confirmation is now a [QasaConfirmSheet], not an
 * `AlertDialog`.
 */
@Composable
fun QasaConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    /** `null` for an informational alert with a single button. */
    dismissLabel: String? = stringResource(R.string.common_cancel),
    destructive: Boolean = true,
) {
    QasaConfirmSheet(
        title = title,
        message = message,
        confirmLabel = confirmLabel,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        dismissLabel = dismissLabel,
        destructive = destructive,
    )
}

/**
 * iOS `textPromptSheet` (`TextPromptSheetContent`): one text field in a sheet, for "New
 * collection", "Rename collection" and "Enter setup key". The shared [QasaSheetHeader]
 * title, an optional message, the field and the confirm button, 20dp apart. No Cancel: the
 * sheet closes by dragging down, the scrim or back, then [onDismiss].
 * [onConfirm] receives the trimmed text; the confirm button is disabled while it is blank.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QasaTextInputSheet(
    title: String,
    initial: String,
    placeholder: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    /** Optional help text under the title (the 2FA setup-key prompt). */
    message: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val c = qasaColors
    var text by rememberSaveable { mutableStateOf(initial) }
    val trimmed = text.trim()
    QasaModalBottomSheet(onDismissRequest = onDismiss, title = title) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = QasaDimens.screenPadding)
                .padding(bottom = QasaDimens.sheetContentBottom),
            verticalArrangement = Arrangement.spacedBy(QasaDimens.sheetActionsGap),
        ) {
            if (!message.isNullOrBlank()) {
                Text(
                    message,
                    color = c.textSecondary,
                    style = qasaText.dialogMessage,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            ThemedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = placeholder,
                keyboardOptions = keyboardOptions,
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(onDone = { if (trimmed.isNotEmpty()) onConfirm(trimmed) }),
            )
            PrimaryButton(
                title = confirmLabel,
                onClick = { if (trimmed.isNotEmpty()) onConfirm(trimmed) },
                enabled = trimmed.isNotEmpty(),
            )
        }
    }
}
