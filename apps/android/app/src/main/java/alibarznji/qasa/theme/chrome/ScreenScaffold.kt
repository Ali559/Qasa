package alibarznji.qasa.theme

import alibarznji.qasa.theme.QasaDimens
import androidx.compose.foundation.background
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
 *     StickyBottomBar?                  // chrome @ 92%, 1dp stroke top divider
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
 * `chrome @ 92%`, 1pt `stroke` top divider, 17pt padding, `navigationBarsPadding()`.
 *
 * `imePadding()` is applied too: with `enableEdgeToEdge()` the keyboard previously
 * covered the primary action in Unlock, Create, ChangeMasterPassword and Pair (§6.1).
 */
@Composable
fun StickyBottomBar(content: @Composable () -> Unit) {
    val c = qasaColors
    Column(
        Modifier
            .background(c.chrome.copy(alpha = 0.92f))
            .navigationBarsPadding()
            .imePadding(),
    ) {
        HorizontalDivider(color = c.stroke, thickness = QasaDimens.stroke)
        Box(
            Modifier.padding(
                horizontal = QasaDimens.screenPadding,
                vertical = QasaDimens.cardPadding,
            ),
        ) {
            content()
        }
    }
}
