package alibarznji.qasa.theme

import alibarznji.qasa.theme.QasaDimens
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

private val NoInsets = WindowInsets(0, 0, 0, 0)

/**
 * Every top-level screen's shape (§4.2):
 *
 * ```
 * Column {
 *     AppMenuHeader / BrandMenuHeader   // 64dp, chrome, 1dp strokeChrome underline
 *     ScrollView { Column(spacing 16|24) { ... }
 *         .padding(horizontal = screenPadding)
 *         .padding(top = 24, bottom = scrollTail) }
 *     StickyBottomBar?                  // chrome, 1dp strokeChrome top hairline
 * }
 * .background(background)
 * ```
 *
 * Insets are handled here so a caller cannot get them wrong: `enableEdgeToEdge()` is
 * on, so a header that forgot `statusBarsPadding()` rendered under the status bar, and
 * a body that forgot `navigationBarsPadding()` put the last card under the gesture bar.
 * The 13 screens with no sticky bar get the navigation-bar inset from here.
 *
 * Pass `applyStatusBars = false` to a header when this scaffold already consumed the
 * status-bar inset.
 */
@Composable
fun ScreenScaffold(
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    stickyBottom: (@Composable () -> Unit)? = null,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = qasaColors
    // Inside a sheet: wrap the content (the sheet caps the height and handles the insets).
    // Filling the screen there made every sub-page a full-height sheet.
    val inSheet = LocalSheetDismiss.current != null
    Column(
        modifier
            .then(if (inSheet) Modifier.fillMaxWidth() else Modifier.fillMaxSize())
            .background(c.background),
    ) {
        Box(if (inSheet) Modifier else Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
            header()
        }
        val tailInset = if (stickyBottom == null && !inSheet) WindowInsets.navigationBars else NoInsets
        val bodyMod = Modifier
            .weight(1f, fill = !inSheet)
            .fillMaxWidth()
            .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .padding(horizontal = QasaDimens.screenPadding)
            .padding(top = QasaDimens.sectionGap, bottom = QasaDimens.scrollTail)
            .windowInsetsPadding(tailInset)
        Column(modifier = bodyMod, content = content)
        stickyBottom?.invoke()
    }
}

/**
 * The one bottom action bar: every page's primary submit / save / continue sits here,
 * fixed below the scrolling body. Solid `chrome` fill, 1dp `strokeChrome` hairline on
 * top, `screenPadding` sides, and the navigation-bar + IME insets so the action stays
 * above the gesture bar and the keyboard. Stacked buttons (primary, then secondary) are
 * spaced by `contentGap`; each is [QasaDimens.buttonHeight] tall.
 */
@Composable
fun StickyBottomBar(content: @Composable ColumnScope.() -> Unit) {
    val c = qasaColors
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.chrome)
            .navigationBarsPadding()
            .imePadding(),
    ) {
        HorizontalDivider(color = c.strokeChrome, thickness = QasaDimens.stroke)
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = QasaDimens.screenPadding, vertical = QasaDimens.gapSmall),
            verticalArrangement = Arrangement.spacedBy(QasaDimens.contentGap),
            content = content,
        )
    }
}

/** [StickyBottomBar] under its descriptive name. */
@Composable
fun BottomActionBar(content: @Composable ColumnScope.() -> Unit) = StickyBottomBar(content)
