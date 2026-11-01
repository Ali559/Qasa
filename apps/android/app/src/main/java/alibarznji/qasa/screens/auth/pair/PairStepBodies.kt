package alibarznji.qasa.screens.auth.pair

import alibarznji.qasa.R
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
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** `introBody` (`PairExistingVaultView.swift:79`). */
@Composable
internal fun PairIntroBody() {
    val c = qasaColors
    Column(verticalArrangement = Arrangement.spacedBy(QasaDimens.gapSmall)) {
        Text(stringResource(R.string.pair_headline), color = c.textPrimary, style = qasaText.sectionHeadline)
        Text(stringResource(R.string.pair_body), color = c.textSecondary, style = qasaText.bodyMedium)
    }
}

/** `enterCodeBody` (`PairExistingVaultView.swift:94`): code first, account id as the advanced path. */
@Composable
internal fun PairEnterCodeBody(
    code: String,
    onCodeChange: (String) -> Unit,
    accountId: String,
    onAccountIdChange: (String) -> Unit,
    errorMessage: String?,
) {
    val c = qasaColors
    Column(verticalArrangement = Arrangement.spacedBy(QasaDimens.gapSmall)) {
        SectionLabel(stringResource(R.string.pair_codelabel))
        ThemedTextField(
            value = code,
            onValueChange = onCodeChange,
            placeholder = stringResource(R.string.pair_codeplaceholder),
            label = stringResource(R.string.pair_codelabel),
            leadingIcon = Icons.Default.Tag,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, autoCorrectEnabled = false),
        )
        Text(stringResource(R.string.pair_accounthint), color = c.textSecondary, style = qasaText.labelRegular)
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
