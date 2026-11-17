package alibarznji.qasa.siteicons

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin

/** How a fitted icon sits in its circle (docs/site-icons.md step 7). */
enum class SiteIconShapeKind { Bleed, Edge, Glyph }

/** The shaped 64 × 64 icon ([pixels], ARGB, not premultiplied), its kind and, for edge, the colour (RGB). */
class SiteIconShaped(val kind: SiteIconShapeKind, val edgeRgb: Int?, val pixels: IntArray)

/**
 * Step 7 of docs/site-icons.md, a port of `docs/site-icon-shapes/reference.py` (same
 * thresholds and geometry) over plain ARGB pixels so it runs in JVM unit tests:
 * - **bleed**: solid inside its circle; kept as it is;
 * - **edge**: a solid box whose 3–4 px ring is one colour; that colour fills the square and
 *   the box's content is fitted into the centred 48 × 48;
 * - **glyph**: the box's content fitted into the centred 38 × 38 with its corners cut to 8.
 *
 * The resampler is Lanczos-3 over premultiplied alpha, like Pillow's `resize(LANCZOS)`.
 */
object SiteIconShaper {
    const val SIDE = 64
    private const val CIRCLE_R = 30
    private const val SOLID = 0.95
    private const val ALPHA = 128
    private const val EDGE_BOX = 48
    private const val GLYPH_BOX = 38
    private const val GLYPH_RADIUS = 8
    private const val EDGE_TOLERANCE = 24
    private const val EDGE_SHARE = 0.90
    private const val MIN_BOX = 16

    private class Box(val x0: Int, val y0: Int, val x1: Int, val y1: Int) {
        val w get() = x1 - x0 + 1
        val h get() = y1 - y0 + 1
    }

    private class Classified(val kind: SiteIconShapeKind, val edgeRgb: Int?, val box: Box?)

    /** Shapes a 64 × 64 ARGB image (step 6's output). [pixels] isn't changed. */
    fun shape(pixels: IntArray): SiteIconShaped {
        require(pixels.size == SIDE * SIDE) { "expected $SIDE × $SIDE pixels" }
        val c = classify(pixels)
        if (c.kind == SiteIconShapeKind.Bleed) return SiteIconShaped(c.kind, null, pixels.copyOf())
        val out = IntArray(SIDE * SIDE) { if (c.kind == SiteIconShapeKind.Edge) (0xFF shl 24) or c.edgeRgb!! else 0 }
        val box = c.box ?: return SiteIconShaped(c.kind, c.edgeRgb, out)
        val side = if (c.kind == SiteIconShapeKind.Edge) EDGE_BOX else GLYPH_BOX
        val crop = IntArray(box.w * box.h)
        for (y in 0 until box.h) for (x in 0 until box.w) crop[y * box.w + x] = pixels[(box.y0 + y) * SIDE + box.x0 + x]
        val content = fit(crop, box.w, box.h, side)
        if (c.kind == SiteIconShapeKind.Glyph) {
            for (y in 0 until side) for (x in 0 until side) {
                if (!inRoundedRect(x, y, side, GLYPH_RADIUS)) content[y * side + x] = content[y * side + x] and 0x00FFFFFF
            }
        }
        val at = (SIDE - side) / 2
        for (y in 0 until side) for (x in 0 until side) {
            val i = (at + y) * SIDE + at + x
            out[i] = over(content[y * side + x], out[i])
        }
        return SiteIconShaped(c.kind, c.edgeRgb, out)
    }

    private fun alpha(p: Int) = p ushr 24

    /** The pixel the tile reads to tell a glyph from bleed / edge (docs/site-icons.md). */
    const val RING_PROBE = 11

    /**
     * Whether a shaped 64 × 64 icon needs the 1dp `stroke` ring: only a glyph, whose
     * corner pixel ([RING_PROBE], [RING_PROBE]) is see-through (alpha < 128). Bleed and edge
     * icons are opaque there and have no ring.
     */
    fun needsRing(pixels: IntArray): Boolean = needsRingAlpha(alpha(pixels[RING_PROBE * SIDE + RING_PROBE]))

    /** [needsRing] from that pixel's alpha alone. */
    fun needsRingAlpha(alpha: Int): Boolean = alpha < ALPHA

    private fun classify(px: IntArray): Classified {
        var inside = 0
        var solidInside = 0
        for (y in 0 until SIDE) for (x in 0 until SIDE) {
            val dx = x + 0.5 - 32
            val dy = y + 0.5 - 32
            if (dx * dx + dy * dy <= CIRCLE_R * CIRCLE_R) {
                inside++
                if (alpha(px[y * SIDE + x]) >= ALPHA) solidInside++
            }
        }
        if (solidInside >= SOLID * inside) return Classified(SiteIconShapeKind.Bleed, null, null)
        var opaque = 0
        var x0 = SIDE
        var y0 = SIDE
        var x1 = -1
        var y1 = -1
        for (y in 0 until SIDE) for (x in 0 until SIDE) {
            if (alpha(px[y * SIDE + x]) >= ALPHA) {
                opaque++
                x0 = min(x0, x)
                x1 = max(x1, x)
                y0 = min(y0, y)
                y1 = max(y1, y)
            }
        }
        if (opaque == 0) return Classified(SiteIconShapeKind.Glyph, null, null)
        val box = Box(x0, y0, x1, y1)
        if (box.w >= MIN_BOX && box.h >= MIN_BOX && opaque >= SOLID * box.w * box.h) {
            var ring = 0
            val rgb = ArrayList<Int>()
            for (y in y0..y1) for (x in x0..x1) {
                val d = minOf(x - x0, x1 - x, y - y0, y1 - y)
                if (d in 3..4) {
                    ring++
                    val p = px[y * SIDE + x]
                    if (alpha(p) >= ALPHA) rgb.add(p and 0xFFFFFF)
                }
            }
            if (rgb.size >= EDGE_SHARE * ring) {
                // Per channel, the value at index n / 2 of the sorted list.
                val mid = IntArray(3) { ch ->
                    val shift = 16 - 8 * ch
                    rgb.map { (it shr shift) and 0xFF }.sorted()[rgb.size / 2]
                }
                val close = rgb.count { c ->
                    (0 until 3).all { ch -> abs(((c shr (16 - 8 * ch)) and 0xFF) - mid[ch]) <= EDGE_TOLERANCE }
                }
                if (close >= EDGE_SHARE * rgb.size) {
                    return Classified(SiteIconShapeKind.Edge, (mid[0] shl 16) or (mid[1] shl 8) or mid[2], box)
                }
            }
        }
        return Classified(SiteIconShapeKind.Glyph, null, box)
    }

    /** PIL's `rounded_rectangle([0, 0, side - 1, side - 1], radius)`: corner circles centred `radius` in. */
    private fun inRoundedRect(x: Int, y: Int, side: Int, radius: Int): Boolean {
        val far = side - 1 - radius
        val cx = when {
            x < radius -> radius
            x > far -> far
            else -> return true
        }
        val cy = when {
            y < radius -> radius
            y > far -> far
            else -> return true
        }
        val dx = x - cx
        val dy = y - cy
        return dx * dx + dy * dy <= radius * radius
    }

    /** Straight-alpha source-over of [src] onto [dst]. */
    private fun over(src: Int, dst: Int): Int {
        val sa = alpha(src)
        if (sa == 255) return src
        if (sa == 0) return dst
        val da = alpha(dst)
        val sA = sa / 255.0
        val dA = da / 255.0
        val outA = sA + dA * (1 - sA)
        if (outA <= 0) return 0
        fun ch(shift: Int): Int {
            val s = (src shr shift) and 0xFF
            val d = (dst shr shift) and 0xFF
            return ((s * sA + d * dA * (1 - sA)) / outA).let { round(it).toInt().coerceIn(0, 255) }
        }
        return (round(outA * 255).toInt() shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /**
     * reference.py `fit`: [src] ([w] × [h]) scaled to fit a [box] × [box] square, aspect kept,
     * centred, transparent padding. Python's `round` (half to even) for the size.
     */
    fun fit(src: IntArray, w: Int, h: Int, box: Int): IntArray {
        val s = min(box.toDouble() / w, box.toDouble() / h)
        val nw = max(1, round(w * s).toInt())
        val nh = max(1, round(h * s).toInt())
        val scaled = resize(src, w, h, nw, nh)
        val out = IntArray(box * box)
        val ox = (box - nw) / 2
        val oy = (box - nh) / 2
        for (y in 0 until nh) for (x in 0 until nw) {
            val tx = ox + x
            val ty = oy + y
            if (tx in 0 until box && ty in 0 until box) out[ty * box + tx] = scaled[y * nw + x]
        }
        return out
    }

    /** Lanczos-3 resize over premultiplied RGBA (separable, like Pillow). */
    fun resize(src: IntArray, w: Int, h: Int, nw: Int, nh: Int): IntArray {
        if (w == nw && h == nh) return src.copyOf()
        // Premultiplied channels as doubles: a, r, g, b.
        val pre = DoubleArray(w * h * 4)
        for (i in 0 until w * h) {
            val p = src[i]
            val a = alpha(p) / 255.0
            pre[i * 4] = a * 255
            pre[i * 4 + 1] = ((p shr 16) and 0xFF) * a
            pre[i * 4 + 2] = ((p shr 8) and 0xFF) * a
            pre[i * 4 + 3] = (p and 0xFF) * a
        }
        val horizontal = DoubleArray(nw * h * 4)
        val wx = weights(w, nw)
        for (y in 0 until h) for (x in 0 until nw) {
            val (start, ws) = wx[x]
            for (ch in 0 until 4) {
                var sum = 0.0
                for (k in ws.indices) sum += pre[(y * w + start + k) * 4 + ch] * ws[k]
                horizontal[(y * nw + x) * 4 + ch] = sum
            }
        }
        val out = IntArray(nw * nh)
        val wy = weights(h, nh)
        for (y in 0 until nh) for (x in 0 until nw) {
            val (start, ws) = wy[y]
            val v = DoubleArray(4)
            for (ch in 0 until 4) {
                var sum = 0.0
                for (k in ws.indices) sum += horizontal[((start + k) * nw + x) * 4 + ch] * ws[k]
                v[ch] = sum.coerceIn(0.0, 255.0)
            }
            val a = round(v[0]).toInt()
            out[y * nw + x] = if (a == 0) {
                0
            } else {
                val f = 255.0 / v[0]
                fun c(value: Double) = round(value * f).toInt().coerceIn(0, 255)
                (a shl 24) or (c(v[1]) shl 16) or (c(v[2]) shl 8) or c(v[3])
            }
        }
        return out
    }

    /** For each output pixel: the first source index and its normalised weights. */
    private fun weights(inSize: Int, outSize: Int): Array<Pair<Int, DoubleArray>> {
        val scale = inSize.toDouble() / outSize
        val filterScale = max(scale, 1.0)
        val support = 3.0 * filterScale
        return Array(outSize) { i ->
            val center = (i + 0.5) * scale
            val lo = max(0, floor(center - support).toInt())
            val hi = min(inSize, ceil(center + support).toInt())
            val ws = DoubleArray(hi - lo) { k -> lanczos((lo + k + 0.5 - center) / filterScale) }
            val total = ws.sum()
            if (total != 0.0) for (k in ws.indices) ws[k] /= total
            lo to ws
        }
    }

    private fun lanczos(x: Double): Double {
        if (x == 0.0) return 1.0
        if (x <= -3.0 || x >= 3.0) return 0.0
        val px = PI * x
        return 3.0 * sin(px) * sin(px / 3.0) / (px * px)
    }
}
