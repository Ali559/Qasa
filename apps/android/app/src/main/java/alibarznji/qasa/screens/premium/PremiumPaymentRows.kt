package alibarznji.qasa.screens.premium

import alibarznji.qasa.R
import alibarznji.qasa.theme.MintSwitch
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.QasaIconSize
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role

/**
 * `PaymentMethod` rows (PremiumUpgradeView.swift:20). `badge` is the wallet brand name (iOS
 * `badgeText`, not localized); it is also the short name in "Pay … via %@".
 */
internal data class GatewayOption(
    val id: String,
    val badge: String,
    @StringRes val title: Int,
    @StringRes val subtitle: Int,
    /** The wallet's own app icon (from assets/payment-logos), same as iOS `logoAsset`. */
    @DrawableRes val logo: Int,
)

internal val premiumGateways = listOf(
    GatewayOption("fib", "FIB", R.string.premium_fib, R.string.premium_fibsub, R.drawable.wallet_fib),
    GatewayOption("fastpay", "FastPay", R.string.premium_fastpay, R.string.premium_fastpaysub, R.drawable.wallet_fastpay),
    GatewayOption("superqi", "SuperQi", R.string.premium_superqi, R.string.premium_superqisub, R.drawable.wallet_superqi),
)

// iOS badge is 36 x 36 with a 9pt corner.
private val BadgeSize = 36.dp
private val BadgeRadius = 9.dp

/** `autoRenewRow` (PremiumUpgradeView.swift:239): 18pt accent icon, 15/12 text, chrome card r8. */
@Composable
internal fun PremiumAutoRenewRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val c = qasaColors
    val shape = RoundedCornerShape(QasaDimens.radiusSmall)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.chrome, shape)
            .border(QasaDimens.stroke, c.stroke, shape)
            .padding(QasaDimens.gap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(QasaDimens.gapSmall),
    ) {
        Icon(Icons.Filled.Autorenew, contentDescription = null, tint = c.accent, modifier = Modifier.size(QasaIconSize.lg))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(QasaDimens.spaceMicro)) {
            Text(stringResource(R.string.premium_autorenew), color = c.textPrimary, style = qasaText.bodySemibold15)
            Text(stringResource(R.string.premium_autorenewdetail), color = c.textSecondary, style = qasaText.labelRegular)
        }
        MintSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** `paymentSection` (PremiumUpgradeView.swift:266): 14pt label, 16pt gap, rows 12pt apart. */
@Composable
internal fun PremiumPaymentSection(selectedId: String, onSelect: (String) -> Unit) {
    val c = qasaColors
    Column(verticalArrangement = Arrangement.spacedBy(QasaDimens.gap)) {
        Text(stringResource(R.string.premium_selectpayment), color = c.textSecondary, style = qasaText.bodyMedium)
        Column(verticalArrangement = Arrangement.spacedBy(QasaDimens.gapSmall)) {
            premiumGateways.forEach { g ->
                PaymentRow(g, selected = g.id == selectedId, onClick = { onSelect(g.id) })
            }
        }
    }
}

/** `paymentRow` (PremiumUpgradeView.swift:281): badge, name/subtitle, trailing check. */
@Composable
private fun PaymentRow(option: GatewayOption, selected: Boolean, onClick: () -> Unit) {
    val c = qasaColors
    val shape = RoundedCornerShape(QasaDimens.radiusSmall)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) c.accent.copy(alpha = 0.05f) else c.chrome, shape)
            .border(QasaDimens.stroke, if (selected) c.accent else c.stroke, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(QasaDimens.cardPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PaymentBadge(option)
        Spacer(Modifier.width(QasaDimens.gap))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(QasaDimens.spaceMicro)) {
            Text(stringResource(option.title), color = c.textPrimary, style = qasaText.bodyMediumSemibold)
            Text(stringResource(option.subtitle), color = c.textSecondary, style = qasaText.sectionLabel)
        }
        Icon(
            if (selected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
            contentDescription = null,
            tint = if (selected) c.accent else c.stroke,
            modifier = Modifier.size(QasaIconSize.xl),
        )
    }
}

/** `paymentBadge` (PremiumUpgradeView.swift): the wallet's own app icon, 36dp, r9. */
@Composable
private fun PaymentBadge(option: GatewayOption) {
    Image(
        painter = painterResource(option.logo),
        contentDescription = null,
        modifier = Modifier.size(BadgeSize).clip(RoundedCornerShape(BadgeRadius)),
    )
}
