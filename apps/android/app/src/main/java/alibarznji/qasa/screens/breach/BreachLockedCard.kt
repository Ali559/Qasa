package alibarznji.qasa.screens.breach

import alibarznji.qasa.R
import alibarznji.qasa.theme.CardContainer
import alibarznji.qasa.theme.PrimaryButton
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.QasaIconSize
import alibarznji.qasa.theme.QasaIcons
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

/**
 * Where breach results would be, on a Free account: what the Premium check does and the
 * same star "upgrade" button as [alibarznji.qasa.theme.PremiumGate], which opens the
 * Premium screen.
 */
@Composable
internal fun BreachLockedCard(onUpgrade: () -> Unit, modifier: Modifier = Modifier) {
    val c = qasaColors
    CardContainer(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(QasaIcons.Lock, null, tint = c.gold, modifier = Modifier.size(QasaIconSize.lg))
            Spacer(Modifier.width(QasaDimens.contentGap))
            Text(stringResource(R.string.breached_lockedtitle), color = c.textPrimary, style = qasaText.rowTitle)
        }
        Spacer(Modifier.height(QasaDimens.gapMedium))
        Text(stringResource(R.string.breached_lockedbody), color = c.textSecondary, style = qasaText.bodyMedium)
        Spacer(Modifier.height(QasaDimens.gap))
        PrimaryButton(title = stringResource(R.string.breached_unlock), icon = QasaIcons.Favorite, onClick = onUpgrade)
    }
}
