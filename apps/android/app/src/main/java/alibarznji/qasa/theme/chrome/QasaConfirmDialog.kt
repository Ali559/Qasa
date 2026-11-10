package alibarznji.qasa.theme

import alibarznji.qasa.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions

/**
 * The one confirmation surface: a [QasaModalBottomSheet] sized to its content with a
 * title, a message, optional extra [content] (progress, an error line), then a full-width
 * primary button (`danger` when [destructive]) and a secondary one, both
 * [QasaDimens.buttonHeight] tall. The Android counterpart of the iOS `.alert`s: every
 * destructive or account-changing action goes through this, so a single tap never
 * deletes, unlinks or cancels on its own.
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
                .padding(horizontal = QasaDimens.screenPadding)
                .padding(top = QasaDimens.spaceSmall, bottom = QasaDimens.gap),
        ) {
            Text(
                title,
                color = c.textPrimary,
                style = qasaText.pageTitle,
                modifier = Modifier.semantics { heading() },
            )
            if (!message.isNullOrBlank()) {
                Spacer(Modifier.height(QasaDimens.contentGap))
                Text(message, color = c.textSecondary, style = qasaText.bodyLegal)
            }
            if (content != null) {
                Spacer(Modifier.height(QasaDimens.gap))
                Column(verticalArrangement = Arrangement.spacedBy(QasaDimens.contentGap), content = content)
            }
            Spacer(Modifier.height(QasaDimens.sectionGap))
            Column(verticalArrangement = Arrangement.spacedBy(QasaDimens.contentGap)) {
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
 * A [QasaConfirmSheet] around one text field: "New collection", "Rename collection".
 * [onConfirm] receives the trimmed text; the confirm button is disabled while it is blank.
 */
@Composable
fun QasaTextInputSheet(
    title: String,
    initial: String,
    placeholder: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    val trimmed = text.trim()
    QasaConfirmSheet(
        title = title,
        message = null,
        confirmLabel = confirmLabel,
        onConfirm = { if (trimmed.isNotEmpty()) onConfirm(trimmed) },
        onDismiss = onDismiss,
        destructive = false,
        confirmEnabled = trimmed.isNotEmpty(),
    ) {
        ThemedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = placeholder,
            imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions(onDone = { if (trimmed.isNotEmpty()) onConfirm(trimmed) }),
        )
    }
}
