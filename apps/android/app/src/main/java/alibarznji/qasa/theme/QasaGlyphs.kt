package alibarznji.qasa.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The iOS SF Symbols the app draws, redrawn to the same box, stroke and position so they
 * sit exactly where iOS puts them. Measured from the symbols themselves (2026-11-12); the
 * desktop draws the same paths (`components/Chevron.vue`).
 *
 * Material's `ArrowBackIos` / `ArrowForwardIos` are filled shapes drawn in one half of
 * their box, so inside the 40dp back circle the arrow sat off-centre and looked heavier.
 * Use these, with no size modifier: their own size is the iOS frame.
 */
object QasaGlyphs {
    /** `chevron.backward`, 17pt semibold (header back button): a 13x18 frame. */
    val ChevronBackward: ImageVector = chevron(
        name = "ChevronBackward",
        width = 13f,
        height = 18f,
        stroke = 2.32f,
        points = listOf(8.79f to 2.26f, 2.16f to 9f, 8.79f to 15.74f),
    )

    /** `chevron.forward`, 13pt semibold (row chevron): an 11x14 frame. */
    val ChevronForward: ImageVector = chevron(
        name = "ChevronForward",
        width = 11f,
        height = 14f,
        stroke = 1.81f,
        points = listOf(3.11f to 1.91f, 8.90f to 6.975f, 3.11f to 12.04f),
    )

    private fun chevron(
        name: String,
        width: Float,
        height: Float,
        stroke: Float,
        points: List<Pair<Float, Float>>,
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = width.dp,
        defaultHeight = height.dp,
        viewportWidth = width,
        viewportHeight = height,
        // Mirrors in RTL, like the SF Symbol's "backward" / "forward".
        autoMirror = true,
    ).path(
        // Black here; `Icon(tint = …)` recolours it.
        stroke = SolidColor(Color.Black),
        strokeLineWidth = stroke,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        val (x0, y0) = points.first()
        moveTo(x0, y0)
        points.drop(1).forEach { (x, y) -> lineTo(x, y) }
    }.build()
}
