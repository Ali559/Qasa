package alibarznji.qasa.theme

import alibarznji.qasa.R
import alibarznji.qasa.theme.qasaColors
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * The Android counterpart of the iOS confirmation `.alert`s: every destructive or
 * account-changing action goes through this, so a single tap never deletes, unlinks or
 * cancels on its own.
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
    val c = qasaColors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.cardBackground,
        titleContentColor = c.textPrimary,
        textContentColor = c.textSecondary,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (destructive) c.danger else c.accent)
            }
        },
        dismissButton = dismissLabel?.let { label ->
            { TextButton(onClick = onDismiss) { Text(label, color = c.accent) } }
        },
        title = { Text(title) },
        text = { Text(message) },
    )
}
