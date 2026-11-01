package alibarznji.qasa.screens.settings.devices

import alibarznji.qasa.R
import alibarznji.qasa.network.RemoteDevice
import alibarznji.qasa.theme.PillBadge
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.QasaIconSize
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** iOS `platformIcon` (LinkedDevicesView.swift:435). */
internal fun platformIcon(platform: String): ImageVector {
    val p = platform.lowercase()
    return when {
        "ios" in p || "iphone" in p || "ipad" in p -> Icons.Filled.PhoneIphone
        "android" in p -> Icons.Filled.PhoneAndroid
        "mac" in p -> Icons.Filled.Laptop
        else -> Icons.Filled.Computer
    }
}

/** iOS `formatDate` + `RemoteDevice.activatedAt`: medium date, short time; null when unparseable. */
internal fun formatActivated(iso: String?, locale: Locale): String? {
    if (iso.isNullOrBlank()) return null
    return try {
        OffsetDateTime.parse(iso)
            .atZoneSameInstant(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale))
    } catch (_: Exception) {
        null
    }
}

/**
 * iOS `deviceCard` (LinkedDevicesView.swift:265). Free tier: the body is a tap-to-upgrade
 * button with a lock glyph and only remote devices get a plain Unlink. Premium: every card,
 * including this device, gets Log out + Unlink chips.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DeviceCard(
    device: RemoteDevice,
    isCurrent: Boolean,
    isPremium: Boolean,
    locale: Locale,
    actionsEnabled: Boolean,
    unlinkDisabled: Boolean,
    onUpgrade: () -> Unit,
    onLogout: () -> Unit,
    onUnlink: () -> Unit,
) {
    val c = qasaColors
    val shape = RoundedCornerShape(QasaDimens.cornerRadius)
    val barColor = c.accent
    val location = device.location?.trim()?.takeIf { it.isNotEmpty() }
    val activated = formatActivated(device.created_at, locale)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.cardBackground)
            .border(QasaDimens.stroke, c.stroke, shape)
            // Full-height leading accent bar for this device (iOS overlay(alignment: .leading)).
            .drawBehind {
                if (isCurrent) {
                    val w = 4.dp.toPx()
                    val x = if (layoutDirection == LayoutDirection.Rtl) size.width - w else 0f
                    drawRect(barColor, topLeft = Offset(x, 0f), size = Size(w, size.height))
                }
            }
            .padding(21.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = !isPremium, onClick = onUpgrade)
                .padding(start = QasaDimens.contentGap),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                platformIcon(device.platform),
                contentDescription = null,
                tint = c.accent,
                modifier = Modifier.size(QasaIconSize.x4l),
            )
            Spacer(Modifier.width(QasaDimens.gap))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(QasaDimens.gapTiny)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(QasaDimens.contentGap),
                    verticalArrangement = Arrangement.spacedBy(QasaDimens.spaceSmall),
                ) {
                    Text(
                        device.device_name,
                        color = c.textPrimary,
                        style = qasaText.rowTitle,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                    if (isCurrent) {
                        PillBadge(
                            text = stringResource(R.string.devices_thisdevice),
                            color = c.accentDark,
                            solid = true,
                            modifier = Modifier.align(Alignment.CenterVertically),
                        )
                    }
                    if (device.is_primary == true) {
                        PillBadge(
                            text = stringResource(R.string.devices_primary),
                            color = c.goldMuted,
                            modifier = Modifier.align(Alignment.CenterVertically),
                        )
                    }
                }
                Text(device.platform.uppercase(), color = c.textSecondary, style = qasaText.bodySmall)
                location?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.LocationOn,
                            contentDescription = null,
                            tint = c.textSecondary,
                            modifier = Modifier.size(QasaIconSize.sm),
                        )
                        Spacer(Modifier.width(QasaDimens.spaceSmall))
                        Text(it, color = c.textSecondary, style = qasaText.bodySmall)
                    }
                }
                activated?.let {
                    Text(
                        stringResource(R.string.devices_activatedat, it),
                        color = c.textTertiary,
                        style = qasaText.detailMono,
                    )
                }
            }
            if (!isPremium) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint = c.goldMuted,
                    modifier = Modifier.size(QasaIconSize.tiny),
                )
            }
        }

        if (isPremium) {
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(QasaDimens.gapMedium)) {
                ActionChip(
                    title = stringResource(R.string.devices_logout),
                    icon = Icons.AutoMirrored.Filled.Logout,
                    color = c.textSecondary,
                    border = c.strokeChrome,
                    enabled = actionsEnabled,
                    onClick = onLogout,
                )
                ActionChip(
                    title = stringResource(R.string.devices_unlink),
                    icon = Icons.Filled.Link,
                    color = if (unlinkDisabled) c.textTertiary else c.danger,
                    border = c.danger.copy(alpha = 0.35f),
                    enabled = actionsEnabled && !unlinkDisabled,
                    onClick = onUnlink,
                )
            }
        } else if (!isCurrent) {
            // Free recovery: allow unlinking a blocking remote device without upgrading.
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth()) {
                ActionChip(
                    title = stringResource(R.string.devices_unlink),
                    icon = Icons.Filled.Link,
                    color = c.textSecondary,
                    border = c.strokeChrome,
                    enabled = actionsEnabled,
                    onClick = onUnlink,
                )
            }
        }
    }
}

/** iOS `actionChip` (LinkedDevicesView.swift:404): outlined, 8pt radius, full-width share. */
@Composable
private fun RowScope.ActionChip(
    title: String,
    icon: ImageVector,
    color: Color,
    border: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(QasaDimens.radiusSmall)
    Row(
        Modifier
            .weight(1f)
            .clip(shape)
            .border(QasaDimens.stroke, border, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(QasaIconSize.sm))
        Spacer(Modifier.width(QasaDimens.gapTiny))
        Text(title, color = color, style = qasaText.bodySmallMedium)
    }
}
