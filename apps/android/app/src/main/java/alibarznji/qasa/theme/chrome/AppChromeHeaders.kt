package alibarznji.qasa.theme

import alibarznji.qasa.R
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.QasaIconFrame
import alibarznji.qasa.theme.QasaIconSize
import alibarznji.qasa.theme.qasaText
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp

/**
 * The shared chrome primitive. iOS hand-rolls this `VStack` four times
 * (`AppMenuHeader.swift:41-81` copies 28 of 39 lines); Android's version is the
 * better shape and every header variant goes through it.
 *
 * One title slot for all four: 20 bold, as iOS `AppMenuHeader.swift:24`
 * (`.qasa(20, weight: .bold)`); every cancellation action is 17 semibold.
 * Row spacing 12, then `Spacer(minLength: 8)` before the trailing slot.
 *
 * [leading] is nullable because `KasoBrandHeader` (`Components.swift:11-20`) has no
 * leading control: its lockup starts at the 16pt edge with no 12pt gap in front.
 */
@Composable
private fun HeaderChrome(
    applyStatusBars: Boolean,
    leading: (@Composable () -> Unit)?,
    title: @Composable RowScope.() -> Unit,
    trailing: (@Composable RowScope.() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val c = qasaColors
    Column(
        modifier
            .fillMaxWidth()
            .background(c.chrome)
            .then(if (applyStatusBars) Modifier.statusBarsPadding() else Modifier),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(QasaDimens.headerHeight)
                .padding(horizontal = QasaDimens.gap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(QasaDimens.gapSmall))
            }
            title()
            Spacer(Modifier.weight(1f))
            if (trailing != null) {
                Spacer(Modifier.width(QasaDimens.contentGap))
                trailing(this)
            }
        }
        // No divider: the calm redesign lets the header sit on the page background.
    }
}

/**
 * Shield 16 semibold + wordmark at `titleSize` heavy, both `accent`. iOS sets the
 * wordmark in the system face (`.system(size:weight: .heavy)`), so it stays sans
 * ExtraBold (W800) in ckb/ar too instead of picking up Rudaw. [gap] is 12 in
 * `KasoBrandHeader` and 10 in `BrandMenuHeader`.
 */
@Composable
private fun BrandLockup(titleSize: Int, gap: Dp) {
    val c = qasaColors
    Icon(
        Icons.Default.Shield,
        contentDescription = null,
        tint = c.accent,
        modifier = Modifier.size(QasaIconSize.md),
    )
    Spacer(Modifier.width(gap))
    Text(
        stringResource(R.string.brand_name),
        color = c.accent,
        style = qasaText.styleForSize(titleSize, FontWeight.ExtraBold).copy(fontFamily = FontFamily.SansSerif),
        maxLines = 1,
    )
}

@Composable
private fun PageTitle(title: String) {
    Text(
        title,
        color = qasaColors.textPrimary,
        style = qasaText.pageTitle.copy(fontWeight = FontWeight.Bold),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * `KasoBrandHeader` — `Components.swift:4`. Leading-aligned brand lockup (12 gap)
 * for auth screens, `titleSize` default 24.
 */
@Composable
fun BrandHeader(
    titleSize: Int = 24,
    applyStatusBars: Boolean = true,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    HeaderChrome(
        applyStatusBars = applyStatusBars,
        leading = null,
        title = { BrandLockup(titleSize, gap = QasaDimens.gapSmall) },
        trailing = trailing,
    )
}

/** `BrandMenuHeader` — `AppMenuHeader.swift:41`. Shield glyph + wordmark at `titleSize` (default 20) heavy `accent`, 10 apart. */
@Composable
fun BrandMenuHeader(
    onMenu: (() -> Unit)?,
    titleSize: Int = 20,
    applyStatusBars: Boolean = true,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    HeaderChrome(
        applyStatusBars = applyStatusBars,
        leading = onMenu?.let { back -> { BackButton(back) } },
        title = { BrandLockup(titleSize, gap = QasaDimens.gapMedium) },
        trailing = trailing,
    )
}

/**
 * `AppMenuHeader` — `AppMenuHeader.swift:3`. Height 64, `chrome` background, 16pt
 * horizontal padding, 1pt `strokeChrome` bottom border. Menu button 40x40 circle.
 * Title 20 bold, `textPrimary`, single line.
 */
@Composable
fun AppMenuHeader(
    title: String,
    onMenu: (() -> Unit)?,
    applyStatusBars: Boolean = true,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    HeaderChrome(
        applyStatusBars = applyStatusBars,
        leading = onMenu?.let { back -> { BackButton(back) } },
        title = { PageTitle(title) },
        trailing = trailing,
    )
}

/**
 * `ToolbarItem(placement: .cancellationAction)` for pushed screens. iOS pairs this
 * with a `navigationTitle`, so Android's variant shares the 20 bold title
 * slot instead of inventing an 18pt one.
 */
@Composable
fun BackChromeHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    applyStatusBars: Boolean = true,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    HeaderChrome(
        applyStatusBars = applyStatusBars,
        leading = { CancelAction(onClick = onBack) },
        title = { PageTitle(title) },
        trailing = trailing,
        modifier = modifier,
    )
}

/** Sheet cancellation + inline title. Same slot as `BackChromeHeader` on purpose. */
@Composable
fun SheetChromeHeader(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    HeaderChrome(
        applyStatusBars = false,
        leading = {
            CancelAction(onClick = onDismiss, label = dismissLabel ?: stringResource(R.string.common_cancel))
        },
        title = { PageTitle(title) },
        trailing = trailing,
        modifier = modifier,
    )
}

/**
 * 40x40 circle, `cardBackground` fill, 17pt glyph. The menu drawer became bottom tabs, so
 * pages one level below a tab get a back arrow here (mirrored in RTL); tab roots pass no
 * `onMenu` and show no leading control.
 */
@Composable
private fun BackButton(onClick: () -> Unit) {
    val c = qasaColors
    QasaPressable(
        onClick = onClick,
        contentDescription = stringResource(R.string.common_back),
        modifier = Modifier.size(QasaIconFrame.toggleDisc),
    ) {
        Box(
            Modifier
                .background(c.cardBackground, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = c.textPrimary,
                modifier = Modifier.size(QasaIconSize.menu),
            )
        }
    }
}

/** Mint cancellation action at the 17 semibold chrome-button slot. */
@Composable
private fun CancelAction(onClick: () -> Unit, label: String? = null) {
    val c = qasaColors
    val text = label ?: stringResource(R.string.common_cancel)
    QasaPressable(
        onClick = onClick,
        contentDescription = text,
    ) {
        Text(text, color = c.accent, style = qasaText.ctaLabel)
    }
}
