package alibarznji.qasa.screens.settings.subscription

import alibarznji.qasa.R
import alibarznji.qasa.network.SubscriptionDTO
import alibarznji.qasa.screens.premium.premiumGateways
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.ToggleRow
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import java.text.NumberFormat
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * iOS `formattedDate` (ManageSubscriptionView.swift:336): abbreviated date in the app
 * locale, "—" when missing, the raw string when it does not parse.
 */
internal fun formatSubscriptionDate(iso: String?, locale: Locale): String {
    if (iso.isNullOrBlank()) return "—"
    return try {
        OffsetDateTime.parse(iso)
            .atZoneSameInstant(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
    } catch (_: Exception) {
        iso
    }
}

/** The configuration locale is the in-app language (`prefs/LocaleContext.kt`). */
@Composable
internal fun appLocale(): Locale = LocalConfiguration.current.locales[0]

/**
 * iOS `detailsCard` (ManageSubscriptionView.swift:117): plan name + Active/Cancelling
 * capsule, amount, started, renews/expires, cancelled date, then the explanatory line.
 */
@Composable
internal fun SubscriptionDetailsCard(sub: SubscriptionDTO, isCancelled: Boolean) {
    val c = qasaColors
    val locale = appLocale()
    val shape = RoundedCornerShape(QasaDimens.cornerRadius)
    // iOS `SubscriptionPlan.from(planId:)`: anything that is not yearly reads as monthly.
    val planName = stringResource(
        if (sub.plan_id == "premium_yearly") R.string.sub_planyearly else R.string.sub_planmonthly,
    )
    val pillColor = if (isCancelled) c.gold else c.accent
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.cardBackgroundLight, shape)
            .border(QasaDimens.stroke, c.stroke, shape)
            .padding(QasaDimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(QasaDimens.gap),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(planName, color = c.textPrimary, style = qasaText.cardTitleBold, modifier = Modifier.weight(1f))
            Text(
                stringResource(if (isCancelled) R.string.sub_cancelling else R.string.sub_active),
                color = pillColor,
                style = if (qasaText.usesRudaw) qasaText.detailSemibold else qasaText.detailMono,
                modifier = Modifier
                    .border(QasaDimens.stroke, pillColor, RoundedCornerShape(50))
                    .padding(horizontal = QasaDimens.contentGap, vertical = QasaDimens.spaceSmall),
            )
        }
        val amount = NumberFormat.getIntegerInstance(locale).format(sub.amount_iqd)
        DetailRow(stringResource(R.string.sub_amount), "$amount ${stringResource(R.string.sub_iqd)}")
        DetailRow(stringResource(R.string.sub_started), formatSubscriptionDate(sub.started_at, locale))
        DetailRow(stringResource(R.string.sub_renewsexpires), formatSubscriptionDate(sub.expires_at, locale))
        if (isCancelled && sub.cancelled_at != null) {
            DetailRow(stringResource(R.string.sub_cancelled), formatSubscriptionDate(sub.cancelled_at, locale))
        }
        Text(
            stringResource(if (isCancelled) R.string.sub_accesscontinues else R.string.sub_premiumincludes),
            color = c.textSecondary,
            style = qasaText.bodySmall,
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    val c = qasaColors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = c.textSecondary, style = qasaText.bodySmall)
        Spacer(Modifier.width(QasaDimens.gapSmall))
        Text(
            value,
            color = c.textPrimary,
            style = qasaText.bodySmallMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * iOS `autoRenewCard` (ManageSubscriptionView.swift:172): chrome background, 8pt radius.
 * With auto-renew on, [SubscriptionDTO.renewal] says whether a saved wallet will really be
 * charged; [onRemovePaymentMethod] is offered while one is saved.
 */
@Composable
internal fun AutoRenewCard(
    sub: SubscriptionDTO,
    checked: Boolean,
    enabled: Boolean,
    isCancelled: Boolean,
    onChange: (Boolean) -> Unit,
    onRemovePaymentMethod: () -> Unit,
) {
    val c = qasaColors
    val locale = appLocale()
    val shape = RoundedCornerShape(QasaDimens.radiusSmall)
    val renewal = sub.renewal
    val subtitle = when {
        isCancelled -> stringResource(R.string.sub_autorenewoffcancelled)
        checked && renewal?.will_renew == true -> {
            val gateway = renewal.gateway
            val gatewayName = premiumGateways.firstOrNull { it.id == gateway }?.let { stringResource(it.title) }
                ?: gateway.orEmpty()
            stringResource(
                R.string.sub_renewsvia,
                formatSubscriptionDate(sub.expires_at, locale),
                NumberFormat.getIntegerInstance(locale).format(renewal.amount_iqd ?: sub.amount_iqd),
                gatewayName,
            )
        }
        checked && renewal != null -> stringResource(R.string.sub_renewalnotsetup)
        else -> stringResource(R.string.sub_autorenewon)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.chrome, shape)
            .border(QasaDimens.stroke, c.stroke, shape)
            .padding(horizontal = QasaDimens.gap, vertical = QasaDimens.contentGap),
    ) {
        ToggleRow(
            title = stringResource(R.string.sub_autorenew),
            subtitle = subtitle,
            checked = checked,
            enabled = enabled,
            icon = Icons.Filled.Autorenew,
            iconColor = c.accent,
            onCheckedChange = onChange,
        )
        if (renewal?.gateway != null) {
            TextButton(onClick = onRemovePaymentMethod, enabled = enabled) {
                Text(stringResource(R.string.sub_removepaymentmethod), color = c.danger, style = qasaText.bodySmallMedium)
            }
        }
    }
}

/** iOS `cancelButton` (ManageSubscriptionView.swift:209): danger-tinted outlined bar. */
@Composable
internal fun CancelSubscriptionButton(enabled: Boolean, onClick: () -> Unit) {
    val c = qasaColors
    val shape = RoundedCornerShape(QasaDimens.radiusSmall)
    Text(
        stringResource(R.string.sub_cancelsubscription),
        color = c.danger,
        style = qasaText.bodySemibold15,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.danger.copy(alpha = 0.08f))
            .border(QasaDimens.stroke, c.danger.copy(alpha = 0.35f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = QasaDimens.padTight),
    )
}
