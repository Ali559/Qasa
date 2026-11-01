package alibarznji.qasa.screens.vault.detail

import alibarznji.qasa.R
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.QasaIconSize
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

/**
 * iOS `detailRow` (`VaultItemDetailView.swift:239`): label + eye toggle for secrets, the
 * value (masked, locked, or plain), and the whole row copies on tap.
 *
 * @param locked a protected note's secret before its biometric gate has been answered.
 * @param shown the eye toggle state for a secret row.
 */
@Composable
internal fun DetailFieldRow(
    field: DetailField,
    locked: Boolean,
    shown: Boolean,
    onToggleShown: () -> Unit,
    onCopy: () -> Unit,
) {
    val c = qasaColors
    val hidden = field.secret && !shown && !locked
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.gen_copy), onClick = onCopy)
            .padding(vertical = QasaDimens.gapSmall),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(field.label),
                color = c.textSecondary,
                style = qasaText.bodySmall,
                modifier = Modifier.weight(1f),
            )
            if (field.secret) {
                // A locked row's eye answers the gate first (handled by the caller).
                IconButton(onClick = onToggleShown, modifier = Modifier.size(QasaIconSize.x3l)) {
                    Icon(
                        if (hidden || locked) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = stringResource(
                            if (hidden || locked) R.string.a11y_showpassword else R.string.a11y_hidepassword,
                        ),
                        tint = c.accent,
                        modifier = Modifier.size(QasaIconSize.sm),
                    )
                }
            }
        }
        Spacer(Modifier.height(QasaDimens.gapTiny))
        when {
            locked -> Text(
                stringResource(R.string.vault_lockedauthenticate),
                color = c.textSecondary,
                style = qasaText.bodyMedium15,
            )
            hidden -> Text(
                "•".repeat(field.value.length.coerceIn(8, 16)),
                color = c.textPrimary,
                style = qasaText.bodyMono,
            )
            else -> Text(
                field.value,
                color = c.textPrimary,
                style = if (field.secret) qasaText.bodyMono else qasaText.bodyMedium15,
            )
        }
    }
}
