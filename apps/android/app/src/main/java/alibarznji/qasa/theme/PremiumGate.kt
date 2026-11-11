package alibarznji.qasa.theme

import alibarznji.qasa.R
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

/**
 * The free-tier gate on premium pages — iOS `premiumGate` in `VPNView.swift:34` and
 * `BreachMonitorView.swift:64`: a hint line and an "Upgrade to Premium" star button, no card.
 */
@Composable
fun PremiumGate(
    hint: String,
    onUpgrade: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = qasaColors
    Column(modifier.fillMaxWidth().padding(top = QasaDimens.gap)) {
        Text(hint, color = c.textSecondary, style = qasaText.bodyMedium)
        Spacer(Modifier.height(QasaDimens.gap))
        PrimaryButton(
            title = stringResource(R.string.settings_upgradepremium),
            icon = QasaIcons.Favorite,
            onClick = onUpgrade,
        )
    }
}
