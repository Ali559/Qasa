package alibarznji.qasa.screens.vault.detail

import alibarznji.qasa.R
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.QasaIconSize
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import alibarznji.qasa.vault.totp.Totp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * The login's live 2FA code: grouped for reading ("123 456") with the seconds left in its
 * period, ticking once a second. The whole row and the copy button hand [onCopy] the
 * current code; the stored secret is never displayed. Nothing renders for a URI that no
 * longer parses.
 */
@Composable
internal fun TotpDetailRow(uri: String, onCopy: (String) -> Unit) {
    val c = qasaColors
    val params = remember(uri) { Totp.parse(uri) } ?: return
    var now by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(uri) {
        while (true) {
            val millis = System.currentTimeMillis()
            now = millis / 1000
            // Wake on the next whole second so the rollover lands on time.
            delay(1000 - millis % 1000)
        }
    }
    val code = remember(params, now / params.period) { Totp.code(params, now) }
    val remaining = Totp.secondsRemaining(params.period, now)
    val copyLabel = stringResource(R.string.copy_totp)

    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = copyLabel) { onCopy(Totp.code(params, System.currentTimeMillis() / 1000)) }
            .padding(vertical = QasaDimens.gapSmall),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.field_totp),
                color = c.textSecondary,
                style = qasaText.bodySmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { onCopy(Totp.code(params, System.currentTimeMillis() / 1000)) },
                modifier = Modifier.size(QasaIconSize.x3l),
            ) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = copyLabel, tint = c.accent, modifier = Modifier.size(QasaIconSize.sm))
            }
        }
        Spacer(Modifier.height(QasaDimens.gapTiny))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Totp.grouped(code), color = c.textPrimary, style = qasaText.hero, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(QasaDimens.contentGap))
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(CountdownSize)) {
                CircularProgressIndicator(
                    progress = { remaining.toFloat() / params.period },
                    color = if (remaining <= 5) c.danger else c.accent,
                    trackColor = c.stroke,
                    strokeWidth = QasaDimens.stroke * 2,
                    modifier = Modifier.size(CountdownSize),
                )
                Text("$remaining", color = c.textSecondary, style = qasaText.bodySmall)
            }
        }
    }
}

private val CountdownSize = 32.dp
