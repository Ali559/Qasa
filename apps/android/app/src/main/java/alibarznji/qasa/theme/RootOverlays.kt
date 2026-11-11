package alibarznji.qasa.theme

import alibarznji.qasa.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** iOS banner `.padding(.horizontal, 14)`. */
private val BannerPadH = 14.dp

/** iOS's default `ProgressView` spinner is ~20pt with a thin stroke. */
private val SpinnerSize = 20.dp
private val SpinnerStroke = 2.dp

/**
 * "Securing vault…" card drawn over the root while the vault bootstraps after create or
 * unlock — `qasaApp.swift` `.overlay { if isBootstrapping { ProgressView(...) } }`.
 * Padding 24, `cardBackground`, radius 12; a spinner over the label, as `ProgressView`
 * with a title renders it.
 */
@Composable
fun SecuringVaultCard(modifier: Modifier = Modifier) {
    val c = qasaColors
    Column(
        modifier = modifier
            .background(c.cardBackground, RoundedCornerShape(QasaDimens.cornerRadius))
            .padding(QasaDimens.sectionGap)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(QasaDimens.contentGap),
    ) {
        CircularProgressIndicator(
            color = c.textSecondary,
            strokeWidth = SpinnerStroke,
            modifier = Modifier.size(SpinnerSize),
        )
        Text(
            stringResource(R.string.auth_securingvault),
            color = c.textPrimary,
            style = qasaText.bodyLarge,
        )
    }
}

/**
 * Top banner for a release build whose API URL is not `https` — `qasaApp.swift`
 * `.safeAreaInset(edge: .top)`. 8 gap, 12 medium `danger`, 14/10 padding,
 * `cardBackground`, full width. It owns the status-bar inset, so the caller must
 * consume that inset for the content below it.
 */
@Composable
fun ApiMisconfiguredBanner(host: String, modifier: Modifier = Modifier) {
    val c = qasaColors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(c.cardBackground)
            .statusBarsPadding()
            .padding(horizontal = BannerPadH, vertical = QasaDimens.gapMedium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(QasaDimens.contentGap),
    ) {
        Icon(
            QasaIcons.WifiOff,
            contentDescription = null,
            tint = c.danger,
            modifier = Modifier.size(QasaIconSize.sm),
        )
        Text(
            stringResource(R.string.error_apihostmisconfigured, host),
            color = c.danger,
            style = qasaText.labelRegular.copy(fontWeight = FontWeight.Medium),
            modifier = Modifier.weight(1f),
        )
    }
}
