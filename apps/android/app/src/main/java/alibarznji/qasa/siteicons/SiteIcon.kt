package alibarznji.qasa.siteicons

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * A login's site icon ready to draw: the shaped 64 × 64 [bitmap] and whether its tile draws
 * the 1dp `stroke` ring ([ring]: a glyph only; docs/site-icons.md "What a tile shows"),
 * decided once from the PNG when it's loaded.
 */
@Immutable
class SiteIcon(val bitmap: ImageBitmap, val ring: Boolean) {
    companion object {
        /** Reads pixel (11, 11) of the shaped icon ([SiteIconShaper.needsRing]). */
        fun from(bitmap: Bitmap): SiteIcon {
            val probe = SiteIconShaper.RING_PROBE
            val x = (probe * bitmap.width / SiteIconShaper.SIDE).coerceIn(0, bitmap.width - 1)
            val y = (probe * bitmap.height / SiteIconShaper.SIDE).coerceIn(0, bitmap.height - 1)
            val ring = SiteIconShaper.needsRingAlpha(bitmap.getPixel(x, y) ushr 24)
            return SiteIcon(bitmap.asImageBitmap(), ring)
        }
    }
}
