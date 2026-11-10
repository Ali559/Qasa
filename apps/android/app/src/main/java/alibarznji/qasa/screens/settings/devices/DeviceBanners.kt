package alibarznji.qasa.screens.settings.devices

import alibarznji.qasa.R
import alibarznji.qasa.network.RestoreKeyStatus
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.SecondaryButton
import alibarznji.qasa.theme.QasaIconSize
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** iOS `upgradeHintBanner` (LinkedDevicesView.swift:476): the whole card opens Premium. */
@Composable
internal fun UpgradeHintBanner(onUpgrade: () -> Unit) {
    val c = qasaColors
    val shape = RoundedCornerShape(QasaDimens.cornerRadius)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(c.gold.copy(alpha = 0.1f), c.gold.copy(alpha = 0.05f))))
            .border(QasaDimens.stroke, c.gold.copy(alpha = 0.2f), shape)
            .clickable(onClick = onUpgrade)
            .padding(21.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.size(QasaIconSize.x7l).clip(CircleShape).background(c.gold.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Star, contentDescription = null, tint = c.gold)
        }
        Spacer(Modifier.width(QasaDimens.gap))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(QasaDimens.spaceSmall)) {
            Text(stringResource(R.string.devices_needmore), color = c.gold, style = qasaText.rowTitle)
            Text(stringResource(R.string.devices_upgradehint), color = c.textSecondary, style = qasaText.bodyMedium)
        }
    }
}

/** iOS `limitExceededBanner` (LinkedDevicesView.swift:458): title + server message, no button. */
@Composable
internal fun LimitExceededBanner(
    message: String,
    restoreKey: RestoreKeyStatus? = null,
    onReplace: () -> Unit = {},
) {
    val c = qasaColors
    val shape = RoundedCornerShape(QasaDimens.cornerRadius)
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.dangerBackground.copy(alpha = 0.2f), shape)
            .border(QasaDimens.stroke, c.danger.copy(alpha = 0.4f), shape)
            .padding(QasaDimens.cardPadding),
        verticalArrangement = Arrangement.spacedBy(QasaDimens.contentGap),
    ) {
        Text(stringResource(R.string.devices_limitreached), color = c.danger, style = qasaText.rowTitle)
        Text(message, color = c.textSecondary, style = qasaText.bodyMedium)
        // A restored backup's key: what it can do about the full slot.
        when (restoreKey) {
            RestoreKeyStatus.Replaceable -> TextButton(onClick = onReplace) {
                Text(stringResource(R.string.backup_replace_button), color = c.accent, style = qasaText.rowTitle)
            }
            RestoreKeyStatus.Unknown ->
                Text(stringResource(R.string.backup_replace_unknown), color = c.textSecondary, style = qasaText.bodyMedium)
            RestoreKeyStatus.NotEnough ->
                Text(stringResource(R.string.backup_replace_notenough), color = c.textSecondary, style = qasaText.bodyMedium)
            null -> Unit
        }
    }
}

/** iOS `infoNote` (LinkedDevicesView.swift:517). */
@Composable
internal fun DevicesInfoNote() {
    val c = qasaColors
    val shape = RoundedCornerShape(QasaDimens.radiusSmall)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = QasaDimens.contentGap)
            .background(c.cardMuted, shape)
            .border(QasaDimens.stroke, c.strokeChrome, shape)
            .padding(QasaDimens.cardPadding),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Filled.Info, contentDescription = null, tint = c.info, modifier = Modifier.size(QasaIconSize.lg))
        Spacer(Modifier.width(QasaDimens.gapSmall))
        Text(
            stringResource(R.string.devices_infonote),
            color = c.textSecondary,
            style = qasaText.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

/** iOS "Unlink all other devices" (LinkedDevicesView.swift:78): the shared destructive secondary button. */
@Composable
internal fun UnlinkAllButton(enabled: Boolean, onClick: () -> Unit) {
    SecondaryButton(
        title = stringResource(R.string.devices_unlinkallothers),
        onClick = onClick,
        enabled = enabled,
        destructive = true,
        icon = Icons.Filled.LinkOff,
    )
}
