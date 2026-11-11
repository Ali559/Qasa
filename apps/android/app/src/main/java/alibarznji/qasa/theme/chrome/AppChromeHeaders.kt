package alibarznji.qasa.theme

import alibarznji.qasa.R
import androidx.compose.ui.res.painterResource
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.fillMaxSize

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
                .padding(horizontal = QasaDimens.headerHorizontal),
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
 * Shield 16 semibold + wordmark at `titleSize` heavy, both `accent`, in the app face
 * like every other string. [gap] is 12 in
 * `KasoBrandHeader` and 10 in `BrandMenuHeader`.
 */
@Composable
private fun BrandLockup(titleSize: Int, gap: Dp) {
    val c = qasaColors
    // The Qaf Keyhole mark (res/drawable/ic_qasa_mark.xml, from assets/brand/generate.py).
    Icon(
        painterResource(R.drawable.ic_qasa_mark),
        contentDescription = null,
        tint = c.accent,
        // iOS `QasaMark(size: 22)`.
        modifier = Modifier.size(QasaIconSize.xxl),
    )
    Spacer(Modifier.width(gap))
    Text(
        stringResource(R.string.brand_name),
        color = c.accent,
        style = qasaText.styleForSize(titleSize, FontWeight.Bold),
        maxLines = 1,
    )
}

@Composable
private fun PageTitle(title: String) {
    Text(
        title,
        color = qasaColors.textPrimary,
        style = qasaText.headerTitle,
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
        leading = onMenu?.let { back -> { HeaderBackButton(back) } },
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
    // Inside a sheet the page header is the sheet's header (iOS `.sheetHeader`): centered
    // 17 semibold, no back / Cancel (the sheet closes by drag, scrim tap or back).
    if (LocalSheetDismiss.current != null) {
        QasaSheetHeader(title, trailing = trailing?.asSheetTrailing())
        return
    }
    HeaderChrome(
        applyStatusBars = applyStatusBars,
        leading = onMenu?.let { back -> { HeaderBackButton(back) } },
        title = { PageTitle(title) },
        trailing = trailing,
    )
}

/**
 * A pushed page's header: the [HeaderBackButton] circle, then the 20 bold title (iOS
 * `AppMenuHeader(onMenu:)`). Inside a sheet it becomes the sheet's [QasaSheetHeader]; with
 * [backInSheet] (a page pushed inside the sheet) it keeps a back circle there.
 */
@Composable
fun BackChromeHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    applyStatusBars: Boolean = true,
    backInSheet: Boolean = false,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    if (LocalSheetDismiss.current != null) {
        QasaSheetHeader(
            title,
            modifier = modifier,
            onBack = if (backInSheet) onBack else null,
            trailing = trailing?.asSheetTrailing(),
        )
        return
    }
    HeaderChrome(
        applyStatusBars = applyStatusBars,
        leading = { HeaderBackButton(onBack) },
        title = { PageTitle(title) },
        trailing = trailing,
        modifier = modifier,
    )
}

/** A header's `RowScope` trailing slot, for the sheet header's plain slot. */
private fun (@Composable RowScope.() -> Unit).asSheetTrailing(): @Composable () -> Unit = {
    Row(verticalAlignment = Alignment.CenterVertically) { this@asSheetTrailing(this) }
}

/**
 * iOS `HeaderBackButton`: a 40dp `cardBackground` circle with a 17dp `textPrimary` back
 * chevron (mirrored in RTL). Pages one level below a tab and drilled-in sheet pages.
 */
@Composable
internal fun HeaderBackButton(onClick: () -> Unit) {
    val c = qasaColors
    val interaction = remember { MutableInteractionSource() }
    val alpha = rememberPressOpacity(interaction)
    val label = stringResource(R.string.common_back)
    Box(
        Modifier
            .size(BACK_BUTTON_SIZE)
            .alpha(alpha)
            .background(c.cardBackground, CircleShape)
            .clip(CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        // iOS `chevron.backward` 17 semibold in its 13x18 frame, centred like SwiftUI does.
        Icon(QasaGlyphs.ChevronBackward, contentDescription = null, tint = c.textPrimary)
    }
}

/** iOS `HeaderBackButton` frame. */
private val BACK_BUTTON_SIZE = QasaTokens.Size.headerBackButton
