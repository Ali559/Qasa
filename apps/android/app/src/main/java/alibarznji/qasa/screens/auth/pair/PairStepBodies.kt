package alibarznji.qasa.screens.auth.pair

import alibarznji.qasa.R
import alibarznji.qasa.screens.sync.JoinPanel
import alibarznji.qasa.screens.sync.WifiSyncViewModel
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.QasaIconSize
import alibarznji.qasa.theme.SectionLabel
import alibarznji.qasa.theme.ThemedTextField
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * The main path for a new device (desktop `PairExisting.vue`): find the other device on
 * this Wi‑Fi and type the code it shows. The vault and account arrive through the
 * code-authenticated channel, still encrypted. The account-id paste stays as an
 * advanced link underneath.
 */
@Composable
internal fun PairWifiBody(sync: WifiSyncViewModel, onAdvanced: () -> Unit) {
    val c = qasaColors
    Column(verticalArrangement = Arrangement.spacedBy(QasaDimens.gapSmall)) {
        Text(stringResource(R.string.pair_headline), color = c.textPrimary, style = qasaText.sectionHeadline)
        Text(stringResource(R.string.pair_phonebody), color = c.textSecondary, style = qasaText.bodyMedium)
        JoinPanel(sync)
        TextButton(onClick = onAdvanced) {
            Text(stringResource(R.string.pair_accounthint), color = c.accent, style = qasaText.bodySmallMedium)
        }
    }
}

/** The advanced path: paste the other device's account id. */
@Composable
internal fun PairAccountIdBody(
    accountId: String,
    onAccountIdChange: (String) -> Unit,
    errorMessage: String?,
) {
    val c = qasaColors
    Column(verticalArrangement = Arrangement.spacedBy(QasaDimens.gapSmall)) {
        Text(stringResource(R.string.pair_adoptbody), color = c.textSecondary, style = qasaText.bodyMedium)
        SectionLabel(stringResource(R.string.pair_accountlabel))
        ThemedTextField(
            value = accountId,
            onValueChange = onAccountIdChange,
            placeholder = stringResource(R.string.pair_accountplaceholder),
            label = stringResource(R.string.pair_accountlabel),
            leadingIcon = Icons.Default.Key,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Ascii,
            ),
        )
        if (errorMessage != null) {
            Text(errorMessage, color = c.danger, style = qasaText.bodySmallMedium)
        }
    }
}

/** The vault arrived over Wi‑Fi: unlock it with the same master password. */
@Composable
internal fun PairAdoptedBody(peerName: String) {
    val c = qasaColors
    Column(verticalArrangement = Arrangement.spacedBy(QasaDimens.gapSmall)) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = c.success,
            modifier = Modifier.size(48.dp),
        )
        Text(
            stringResource(R.string.sync_adoptedtitle, peerName),
            color = c.textPrimary,
            style = qasaText.sectionHeadline,
        )
        Text(
            stringResource(R.string.sync_adoptedbody, peerName),
            color = c.textSecondary,
            style = qasaText.bodyMedium,
        )
    }
}

/** `premiumBody` (`PairExistingVaultView.swift:134`): gold gradient card, no button. */
@Composable
internal fun PairPremiumBody() {
    val c = qasaColors
    val shape = RoundedCornerShape(QasaDimens.cornerRadius)
    Column(
        verticalArrangement = Arrangement.spacedBy(QasaDimens.gap),
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(listOf(c.gold.copy(alpha = 0.12f), c.gold.copy(alpha = 0.04f))),
                shape,
            )
            .border(QasaDimens.stroke, c.gold.copy(alpha = 0.25f), shape)
            .padding(21.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(QasaDimens.gapMedium),
        ) {
            Icon(Icons.Default.Star, contentDescription = null, tint = c.gold, modifier = Modifier.size(QasaIconSize.md))
            Text(
                stringResource(R.string.pair_premiumtitle),
                color = c.gold,
                style = qasaText.styleForSize(20, FontWeight.Bold),
            )
        }
        Text(stringResource(R.string.pair_premiumbody), color = c.textSecondary, style = qasaText.bodyLegal)
    }
}

/** `successBody` (`PairExistingVaultView.swift:166`). */
@Composable
internal fun PairSuccessBody() {
    val c = qasaColors
    Column(verticalArrangement = Arrangement.spacedBy(QasaDimens.gapSmall)) {
        Text(stringResource(R.string.pair_successtitle), color = c.accent, style = qasaText.sectionHeadline)
        Text(stringResource(R.string.pair_successbody), color = c.textSecondary, style = qasaText.bodyMedium)
    }
}
