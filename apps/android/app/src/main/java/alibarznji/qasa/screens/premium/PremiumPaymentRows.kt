package alibarznji.qasa.screens.premium

import alibarznji.qasa.R
import alibarznji.qasa.theme.MintSwitch
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.QasaIconSize
import alibarznji.qasa.theme.QasaIcons
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role

/** What a payment row shows as its badge: brand logos, never redrawn or recoloured. */
internal sealed interface GatewayLogo {
    /** Visa and Mastercard marks on two overlapping white circles. */
    data object Cards : GatewayLogo

    /** A wallet's app icon clipped to a circle. */
    data class AppIcon(@DrawableRes val res: Int) : GatewayLogo
}

/**
 * `PaymentMethod` rows (PremiumUpgradeView.swift:20). [id] is the UI choice only; every row
 * pays through [gateway] (Wayl's hosted page takes cards and the Iraqi wallets itself).
 * [badge] is the short brand name in "Pay … via %@" (not localized).
 */
internal data class GatewayOption(
    val id: String,
    val badge: String,
    @StringRes val title: Int,
    @StringRes val subtitle: Int,
    val logo: GatewayLogo,
    val gateway: String = WAYL_GATEWAY,
)

/** The gateway id the server gets for every row. */
internal const val WAYL_GATEWAY = "wayl"

/** TODO Phase 10: four brands, all through Wayl. The first (card) is the default. */
internal val premiumGateways = listOf(
    GatewayOption("card", "Visa / Mastercard", R.string.premium_card, R.string.premium_cardsub, GatewayLogo.Cards),
    GatewayOption("fib", "FIB", R.string.premium_fib, R.string.premium_fibsub, GatewayLogo.AppIcon(R.drawable.payment_logo_fib)),
    GatewayOption("fastpay", "FastPay", R.string.premium_fastpay, R.string.premium_fastpaysub, GatewayLogo.AppIcon(R.drawable.payment_logo_fastpay)),
    GatewayOption("superqi", "SuperQi", R.string.premium_superqi, R.string.premium_superqisub, GatewayLogo.AppIcon(R.drawable.payment_logo_superqi)),
)

/** Names for the `gateway` ids payments report: Wayl today, the wallets older payments used. */
private val gatewayNames = mapOf(
    WAYL_GATEWAY to R.string.premium_wayl,
    "fib" to R.string.premium_fib,
    "fastpay" to R.string.premium_fastpay,
    "superqi" to R.string.premium_superqi,
)

/** Display name for a `gateway` the server reports; unknown ids are shown as sent. */
@Composable
internal fun gatewayDisplayName(id: String?): String = when (id) {
    null -> ""
    else -> gatewayNames[id]?.let { stringResource(it) } ?: id
}

// iOS badge is 36 x 36 with a 9pt corner.
private val BadgeSize = 36.dp

/** `autoRenewRow` (PremiumUpgradeView.swift:239): 18pt accent icon, 15/12 text, chrome card r8. */
@Composable
internal fun PremiumAutoRenewRow(checked: Boolean, chargesWallet: Boolean, onCheckedChange: (Boolean) -> Unit) {
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
        Icon(QasaIcons.Sync, contentDescription = null, tint = c.accent, modifier = Modifier.size(QasaIconSize.lg))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(QasaDimens.spaceMicro)) {
            Text(stringResource(R.string.premium_autorenew), color = c.textPrimary, style = qasaText.bodySemibold15)
            // Only a wallet the server can charge renews by itself; the rest get a payment link.
            Text(
                stringResource(if (chargesWallet) R.string.premium_autorenewdetailcharge else R.string.premium_autorenewdetail),
                color = c.textSecondary,
                style = qasaText.labelRegular,
            )
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
        // Every row pays through Wayl; say so once under the list.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(QasaDimens.spaceMicro * 2)) {
            Icon(QasaIcons.ShieldLock, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(14.dp))
            Text(stringResource(R.string.premium_viawayl), color = c.textSecondary, style = qasaText.caption)
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
        Spacer(Modifier.width(QasaDimens.gapSmall))
        Icon(
            if (selected) QasaIcons.Success else QasaIcons.Circle,
            contentDescription = null,
            tint = if (selected) c.accent else c.stroke,
            modifier = Modifier.size(QasaIconSize.xl),
        )
    }
}

/** `paymentBadge` (PremiumUpgradeView.swift): the brand's own logo in a 36dp circle. */
@Composable
private fun PaymentBadge(option: GatewayOption) {
    when (val logo = option.logo) {
        GatewayLogo.Cards -> CardsBadge()
        is GatewayLogo.AppIcon -> Image(
            painterResource(logo.res),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(BadgeSize).clip(CircleShape),
        )
    }
}

/** Visa and Mastercard on two white circles stacked like avatars, 32dp apart (68dp wide), so both marks show in full.
 * Row order follows the layout direction, so RTL mirrors the stack. */
@Composable
private fun CardsBadge() {
    Row(horizontalArrangement = Arrangement.spacedBy(CardsOverlap)) {
        LogoCircle(R.drawable.payment_logo_visa)
        LogoCircle(R.drawable.payment_logo_mastercard)
    }
}

@Composable
private fun LogoCircle(@DrawableRes res: Int) {
    Box(
        modifier = Modifier
            .size(BadgeSize)
            .clip(CircleShape)
            .background(Color.White, CircleShape)
            .border(QasaDimens.stroke, qasaColors.stroke, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(res), contentDescription = null, modifier = Modifier.width(LogoWidth))
    }
}

private val CardsOverlap = 32.dp - BadgeSize
private val LogoWidth = 24.dp
