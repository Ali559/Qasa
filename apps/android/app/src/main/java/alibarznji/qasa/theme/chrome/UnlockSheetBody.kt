package alibarznji.qasa.theme

import alibarznji.qasa.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign

/**
 * The body of the unlock sheet (`design/README.md` rules 6b and 6d), shared by a
 * protected note and the app's own unlock: the `protected.title` message, the main
 * password as a one-row group with [error] on its row, an optional [note] under it, then
 * Unlock and, when [onBiometrics] is set, the secondary `auth.unlock.biometrics` button.
 * The caller draws the sheet and its header around it.
 */
@Composable
fun UnlockSheetBody(
    password: String,
    onPasswordChange: (String) -> Unit,
    error: String?,
    unlockEnabled: Boolean,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
    onBiometrics: (() -> Unit)? = null,
    note: String? = null,
) {
    val c = qasaColors
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = QasaDimens.screenPadding)
            .padding(bottom = QasaDimens.sheetContentBottom),
        verticalArrangement = Arrangement.spacedBy(QasaDimens.sheetActionsGap),
    ) {
        Text(
            stringResource(R.string.protected_title),
            color = c.textSecondary,
            style = qasaText.dialogMessage,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Column(verticalArrangement = Arrangement.spacedBy(QasaDimens.contentGap)) {
            QasaFieldGroup {
                QasaSecureRow(
                    label = stringResource(R.string.auth_unlock_masterpassword),
                    value = password,
                    onValueChange = onPasswordChange,
                    placeholder = stringResource(R.string.auth_unlock_placeholder),
                    error = error,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (unlockEnabled) onUnlock() }),
                )
            }
            if (note != null) {
                Text(
                    note,
                    color = c.textSecondary,
                    style = qasaText.bodyMedium,
                    modifier = Modifier.padding(horizontal = QasaDimens.gapSmall),
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(QasaDimens.sheetButtonGap)) {
            PrimaryButton(
                title = stringResource(R.string.auth_unlock_button),
                onClick = onUnlock,
                enabled = unlockEnabled,
            )
            if (onBiometrics != null) {
                SecondaryButton(
                    title = stringResource(R.string.auth_unlock_biometrics),
                    onClick = onBiometrics,
                )
            }
        }
    }
}
