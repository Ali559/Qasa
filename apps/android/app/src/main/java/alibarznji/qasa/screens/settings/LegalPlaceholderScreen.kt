package alibarznji.qasa.screens.settings

import alibarznji.qasa.R
import alibarznji.qasa.theme.AppMenuHeader
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.ScreenScaffold
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Placeholder publish date for legal copy until real docs ship (iOS `lastUpdated`). */
private val LegalLastUpdated: LocalDate = LocalDate.of(2026, 8, 25)

/**
 * Port of iOS `LegalPlaceholderView`: plain page (no card) with a 24 bold title, the
 * localized "Last updated" line, then [bodyRes] (`legal_termsbody` / `legal_privacybody`),
 * chosen by the caller exactly as iOS `AppShellView` passes `bodyText`.
 */
@Composable
fun LegalPlaceholderScreen(
    title: String,
    @StringRes bodyRes: Int,
    onOpenMenu: (() -> Unit)? = null,
) {
    val c = qasaColors
    val locale = LocalConfiguration.current.locales[0]
    // iOS `.abbreviated` date ≈ java MEDIUM ("Aug 25, 2026").
    val formattedDate = LegalLastUpdated.format(
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale),
    )
    ScreenScaffold(
        header = { AppMenuHeader(title = title, onMenu = onOpenMenu, applyStatusBars = false) },
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(QasaDimens.gap)) {
            Text(title, color = c.textPrimary, style = qasaText.brandStandalone)
            Text(
                stringResource(R.string.legal_lastupdated, formattedDate),
                color = c.textTertiary,
                style = qasaText.bodySmall,
            )
            Text(
                stringResource(bodyRes),
                color = c.textSecondary,
                // iOS `.lineSpacing(4)` adds 4pt between lines of 15pt text.
                style = qasaText.bodyLegal.copy(lineHeight = 23.sp),
            )
        }
    }
}
