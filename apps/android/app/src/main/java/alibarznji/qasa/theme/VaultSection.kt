package alibarznji.qasa.theme

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/*
 * design/README.md rule 6e: each vault list section is one card (`cardBackground`, 1dp
 * `stroke`, `radius.group` 20) holding its rows, with a divider between rows from the text's
 * leading edge to the card's trailing edge. In a lazy list every row is its own item, so each
 * row draws its slice of the card: the top corners and edge when it's the first row, the
 * bottom ones when it's the last, the sides always, and the divider above it otherwise.
 */

/** Where the divider starts: `vaultRowHorizontal` + `vaultTile` + `rowIconGap` (72). */
private val VaultDividerStart = QasaTokens.Space.vaultRowHorizontal + QasaTokens.Size.vaultTile + QasaTokens.Space.rowIconGap

/**
 * This row's slice of its section card. Clips what's inside (the row's press wash) to the
 * card's corners on the first and last row.
 */
@Composable
fun Modifier.vaultSectionSegment(first: Boolean, last: Boolean): Modifier {
    val c = qasaColors
    val r = QasaTokens.Radius.group
    val top = if (first) r else 0.dp
    val bottom = if (last) r else 0.dp
    val shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
    // The ring is inset by half its width, so its corners are that much tighter (as `border`).
    val half = QasaDimens.stroke / 2
    val ringTop = if (first) r - half else 0.dp
    val ringBottom = if (last) r - half else 0.dp
    val ringShape = RoundedCornerShape(topStart = ringTop, topEnd = ringTop, bottomStart = ringBottom, bottomEnd = ringBottom)
    return this
        .clip(shape)
        .drawWithContent {
            drawOutline(shape.createOutline(size, layoutDirection, this), c.cardBackground)
            drawContent()
            val sw = QasaDimens.stroke.toPx()
            // The ring of a card that runs past this slice where the card goes on, so only
            // this slice's own edges show inside the clip.
            val above = if (first) 0f else sw * 2
            val below = if (last) 0f else sw * 2
            val ring = Size(size.width - sw, size.height + above + below - sw)
            translate(left = sw / 2, top = sw / 2 - above) {
                drawOutline(ringShape.createOutline(ring, layoutDirection, this), c.stroke, style = Stroke(sw))
            }
            if (!first) {
                val start = VaultDividerStart.toPx()
                val y = sw / 2
                val (x0, x1) = if (layoutDirection == LayoutDirection.Ltr) start to size.width else 0f to size.width - start
                drawLine(c.stroke, Offset(x0, y), Offset(x1, y), strokeWidth = sw)
            }
        }
}

/**
 * A vault list section's label (rule 6e): `sectionLabel` in `textSecondary`, indented 4,
 * `vaultSectionTop` (20) above (the list's `formGap` for the first section) and
 * `sectionLabelGap` (8) below.
 */
@Composable
fun VaultSectionLabel(text: String, first: Boolean, modifier: Modifier = Modifier) {
    SectionLabel(
        text,
        modifier = modifier.padding(
            start = QasaDimens.spaceSmall,
            end = QasaDimens.spaceSmall,
            top = if (first) QasaTokens.Space.formGap else QasaTokens.Space.vaultSectionTop,
            bottom = QasaTokens.Space.sectionLabelGap,
        ),
    )
}
