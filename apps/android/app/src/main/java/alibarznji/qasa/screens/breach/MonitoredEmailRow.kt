package alibarznji.qasa.screens.breach

import alibarznji.qasa.R
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.QasaIconSize
import alibarznji.qasa.theme.QasaIcons
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import alibarznji.qasa.vault.db.MonitoredEmailEntity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

// iOS `monitoredRow` literals: 14pt row padding, 32pt close circle.
private val RowPadding = 14.dp
private val CloseCircle = 32.dp

/**
 * iOS `BreachMonitorView.monitoredRow`: email 15 semibold, status line (success / danger), and a
 * 32pt chrome circle "xmark" that asks before removing. The tap target stays 48dp for TalkBack.
 */
@Composable
fun MonitoredEmailRow(item: MonitoredEmailEntity, onRemove: () -> Unit) {
    val c = qasaColors
    val shape = RoundedCornerShape(QasaDimens.cornerRadius)
    val risky = item.statusRaw.equals(STATUS_AT_RISK, ignoreCase = true)
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.cardBackground, shape)
            .border(QasaDimens.stroke, c.stroke, shape)
            .padding(RowPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.email, color = c.textPrimary, style = qasaText.bodySemibold15)
            Spacer(Modifier.height(QasaDimens.spaceSmall))
            Text(
                stringResource(if (risky) R.string.breach_atrisk else R.string.breach_secure),
                color = if (risky) c.danger else c.success,
                style = qasaText.bodySmall,
            )
        }
        Spacer(Modifier.width(QasaDimens.gapSmall))
        IconButton(onClick = onRemove, modifier = Modifier.size(QasaDimens.touchTarget)) {
            Box(
                Modifier
                    .size(CloseCircle)
                    .background(c.chrome, CircleShape)
                    .border(QasaDimens.stroke, c.strokeChrome, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    QasaIcons.Close,
                    contentDescription = stringResource(R.string.a11y_removemonitor, item.email),
                    tint = c.textSecondary,
                    modifier = Modifier.size(QasaIconSize.tiny),
                )
            }
        }
    }
}
