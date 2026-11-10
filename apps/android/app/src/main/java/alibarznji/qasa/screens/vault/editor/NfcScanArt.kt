package alibarznji.qasa.screens.vault.editor

import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaReduceMotion
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.asin
import kotlin.math.sqrt

/** Which chip the sheet reads; picks the drawing, the copy and the reader. */
enum class NfcDocument { Card, IdCard, Passport }

/** One loop of the scan picture, as in the approved preview (`nfc-preview.html`). */
internal const val NFC_LOOP_MS = 3200

/** The preview's SVG viewBox; everything below is drawn in these units. */
private const val VIEW_W = 300f
private const val VIEW_H = 220f

/**
 * The scan picture: the document, a phone that moves onto it and back on a 3.2 s loop
 * (approach 0–35 %, hold to 78 %, back), three read waves, a tick on the phone screen and
 * a glow under the document. A port of the preview's SVG and CSS keyframes: each value
 * is the same keyframe list with the same per-segment easing.
 *
 * [loop] is the 0→1 phase from the sheet's single infinite transition, read only while
 * drawing, so a frame redraws the canvas without recomposing or relaying out anything.
 * With reduce motion on there is no loop and the picture holds the preview's
 * `prefers-reduced-motion` pose: phone on the document, the first wave showing.
 *
 * The drawing is not mirrored in RTL: it shows a physical card, whose chip is on the left
 * in every locale.
 */
@Composable
internal fun NfcScanArt(document: NfcDocument, loop: State<Float>?, contentDescription: String, modifier: Modifier = Modifier) {
    val c = qasaColors
    val dark = c.background.luminance() < 0.5f
    val palette = remember(c, dark) {
        val doc = c.cardBackgroundLight
        val phone = if (dark) Color.Black.copy(alpha = 0.45f).compositeOver(c.background) else c.textPrimary
        ArtPalette(
            accent = c.accent,
            doc = doc,
            docLine = c.accent.copy(alpha = 0.32f).compositeOver(doc),
            chip = c.gold.copy(alpha = 0.75f).compositeOver(doc),
            phone = phone,
            phoneScreen = c.accent.copy(alpha = 0.25f).compositeOver(phone),
        )
    }
    Canvas(
        modifier
            .fillMaxWidth()
            .widthIn(max = 300.dp)
            .aspectRatio(VIEW_W / VIEW_H)
            .semantics { this.contentDescription = contentDescription },
    ) {
        val frame = loop?.value?.let { ArtFrame.at(it) } ?: ArtFrame.STILL
        val s = size.width / VIEW_W
        scale(s, s, pivot = Offset.Zero) {
            drawDocument(document, palette, frame.glow)
            drawPhone(palette, frame)
            // On top of the phone, as on iOS: under it, the resting phone hid them.
            drawWaves(palette, frame)
        }
    }
}

/** Drives [NfcScanArt]; null when motion is reduced (no frames at all). */
@Composable
internal fun rememberNfcLoop(transition: InfiniteTransition): State<Float>? {
    if (qasaReduceMotion.enabled) return null
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(NFC_LOOP_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "nfc-loop",
    )
}

/** The status dot's 1.2 s blink (`@keyframes blink`), on the same transition. */
@Composable
internal fun rememberNfcBlink(transition: InfiniteTransition): State<Float> {
    if (qasaReduceMotion.enabled) return remember { mutableFloatStateOf(1f) }
    return transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(600, easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)), RepeatMode.Reverse),
        label = "nfc-blink",
    )
}

private class ArtPalette(
    val accent: Color,
    val doc: Color,
    val docLine: Color,
    val chip: Color,
    val phone: Color,
    val phoneScreen: Color,
)

/** Every animated value for one instant of the loop. */
private class ArtFrame(
    /** 1 = phone away (translate 70, −46, rotate −8°), 0 = on the document. */
    val phoneAway: Float,
    val waveAlpha: FloatArray,
    val waveScale: FloatArray,
    /** False in the still pose: the SVG's own `translate(-6 0)` then applies to the waves. */
    val waveShifted: Boolean,
    val tickAlpha: Float,
    val tickScale: Float,
    val glow: Float,
) {
    companion object {
        /** `prefers-reduced-motion`: animations off, `.wave.w1 { opacity: .7 }`. */
        val STILL = ArtFrame(0f, floatArrayOf(0.7f, 0f, 0f), floatArrayOf(1f, 1f, 1f), waveShifted = true, 0f, 1f, 0f)

        private val PHONE_EASE = CubicBezierEasing(0.45f, 0.05f, 0.25f, 1f)
        private val EASE_OUT = CubicBezierEasing(0f, 0f, 0.58f, 1f)

        private val PHONE = floatArrayOf(0f, 1f, 0.35f, 0f, 0.78f, 0f, 1f, 1f)
        private val WAVE_ALPHA = floatArrayOf(0f, 0f, 0.38f, 0f, 0.48f, 0.9f, 0.70f, 0f, 1f, 0f)
        // 100 % sets no transform, so it eases back to the element's own scale(1).
        private val WAVE_SCALE = floatArrayOf(0f, 0.6f, 0.38f, 0.6f, 0.70f, 1.35f, 1f, 1f)
        private val TICK_ALPHA = floatArrayOf(0f, 0f, 0.68f, 0f, 0.74f, 1f, 0.80f, 1f, 0.90f, 1f, 1f, 0f)
        private val TICK_SCALE = floatArrayOf(0f, 0.5f, 0.68f, 0.5f, 0.74f, 1.08f, 0.80f, 1f, 0.90f, 1f, 1f, 1f)
        private val GLOW = floatArrayOf(0f, 0f, 0.40f, 0f, 0.55f, 0.55f, 0.78f, 0.55f, 1f, 0f)

        /** `.w2` / `.w3` start 0.18 s and 0.36 s later. */
        private val WAVE_DELAY = floatArrayOf(0f, 0.18f, 0.36f).map { it * 1000f / NFC_LOOP_MS }

        fun at(t: Float): ArtFrame {
            val wa = FloatArray(3)
            val ws = FloatArray(3)
            for (i in 0 until 3) {
                val local = ((t - WAVE_DELAY[i]) % 1f + 1f) % 1f
                wa[i] = keyframes(local, WAVE_ALPHA, EASE_OUT)
                ws[i] = keyframes(local, WAVE_SCALE, EASE_OUT)
            }
            return ArtFrame(
                phoneAway = keyframes(t, PHONE, PHONE_EASE),
                waveAlpha = wa,
                waveScale = ws,
                waveShifted = false,
                tickAlpha = keyframes(t, TICK_ALPHA, EASE_OUT),
                tickScale = keyframes(t, TICK_SCALE, EASE_OUT),
                glow = keyframes(t, GLOW, EASE_OUT),
            )
        }

        /** CSS keyframes: [stops] is offset, value, offset, value…; [easing] per segment. */
        private fun keyframes(t: Float, stops: FloatArray, easing: Easing): Float {
            if (t <= stops[0]) return stops[1]
            var i = 0
            while (i + 2 < stops.size) {
                val t0 = stops[i]
                val t1 = stops[i + 2]
                if (t <= t1) {
                    val f = if (t1 > t0) easing.transform((t - t0) / (t1 - t0)) else 1f
                    return stops[i + 1] + (stops[i + 3] - stops[i + 1]) * f
                }
                i += 2
            }
            return stops[stops.size - 1]
        }
    }
}

private fun DrawScope.roundRect(color: Color, x: Float, y: Float, w: Float, h: Float, r: Float, alpha: Float = 1f) =
    drawRoundRect(color, Offset(x, y), Size(w, h), CornerRadius(r, r), alpha = alpha)

private fun DrawScope.roundRectStroke(color: Color, x: Float, y: Float, w: Float, h: Float, r: Float, width: Float) =
    drawRoundRect(color, Offset(x, y), Size(w, h), CornerRadius(r, r), style = Stroke(width))

private fun DrawScope.drawDocument(document: NfcDocument, p: ArtPalette, glow: Float) {
    when (document) {
        NfcDocument.Card, NfcDocument.IdCard -> {
            if (glow > 0f) roundRect(p.accent, 38f, 70f, 224f, 140f, 16f, alpha = glow)
            roundRect(p.doc, 44f, 76f, 212f, 128f, 14f)
            roundRectStroke(p.docLine, 44f, 76f, 212f, 128f, 14f, 2f)
            if (document == NfcDocument.Card) {
                roundRect(p.chip, 66f, 112f, 34f, 26f, 5f)
                roundRect(p.docLine, 66f, 160f, 120f, 9f, 4.5f)
                roundRect(p.docLine, 66f, 178f, 60f, 7f, 3.5f)
                roundRect(p.docLine, 196f, 176f, 40f, 12f, 6f)
            } else {
                roundRect(p.docLine, 62f, 96f, 54f, 66f, 8f)
                drawCircle(p.doc, 12f, Offset(89f, 120f))
                val shoulders = Path().apply {
                    moveTo(71f, 156f)
                    quadraticTo(89f, 136f, 107f, 156f)
                    close()
                }
                drawPath(shoulders, p.doc)
                roundRect(p.docLine, 130f, 100f, 100f, 9f, 4.5f)
                roundRect(p.docLine, 130f, 118f, 74f, 9f, 4.5f)
                roundRect(p.docLine, 130f, 136f, 88f, 9f, 4.5f)
                roundRect(p.docLine, 62f, 176f, 176f, 8f, 4f, alpha = 0.7f)
            }
        }
        NfcDocument.Passport -> {
            if (glow > 0f) roundRect(p.accent, 66f, 40f, 168f, 176f, 14f, alpha = glow)
            roundRect(p.doc, 72f, 46f, 156f, 164f, 10f)
            roundRectStroke(p.docLine, 72f, 46f, 156f, 164f, 10f, 2f)
            roundRect(p.docLine, 72f, 46f, 12f, 164f, 4f, alpha = 0.6f)
            drawCircle(p.docLine, 28f, Offset(156f, 110f), style = Stroke(3f))
            roundRectStroke(p.docLine, 128f, 170f, 56f, 16f, 3f, 2.5f)
            roundRect(p.docLine, 149f, 175f, 14f, 6f, 3f)
        }
    }
}

/** The SVG's three arcs: `M x y a r r 0 0 1 0 chord`, each scaled about its own box. */
private val WAVES = listOf(Triple(150f, 92f, 26f) to 36f, Triple(162f, 82f, 40f) to 56f, Triple(174f, 72f, 54f) to 76f)

private fun DrawScope.drawWaves(p: ArtPalette, f: ArtFrame) {
    WAVES.forEachIndexed { i, (start, chord) ->
        val alpha = f.waveAlpha[i]
        if (alpha <= 0f) return@forEachIndexed
        val (x0, y0, r) = start
        val half = chord / 2f
        val cx = x0 - sqrt(r * r - half * half)
        val cy = y0 + half
        val angle = Math.toDegrees(asin(half / r).toDouble()).toFloat()
        // transform-box: fill-box; transform-origin: center → the arc's bounding box.
        val pivot = Offset((x0 + cx + r) / 2f, cy)
        translate(left = if (f.waveShifted) -6f else 0f) {
            scale(f.waveScale[i], pivot = pivot) {
                drawArc(
                    color = p.accent,
                    startAngle = -angle,
                    sweepAngle = 2f * angle,
                    useCenter = false,
                    topLeft = Offset(cx - r, cy - r),
                    size = Size(2f * r, 2f * r),
                    alpha = alpha,
                    style = Stroke(3f, cap = StrokeCap.Round),
                )
            }
        }
    }
}

private val TICK = Path().apply {
    moveTo(139f, 104f)
    relativeLineTo(8f, 8f)
    relativeLineTo(16f, -18f)
}

private fun DrawScope.drawPhone(p: ArtPalette, f: ArtFrame) {
    val a = f.phoneAway
    withTransform({
        translate(70f * a, -46f * a)
        rotate(-8f * a, pivot = Offset(151f, 110f))
    }) {
        roundRect(p.phone, 112f, 44f, 78f, 132f, 14f)
        roundRect(p.phoneScreen, 118f, 54f, 66f, 112f, 8f)
        roundRect(p.phoneScreen, 140f, 47f, 22f, 4f, 2f)
        if (f.tickAlpha > 0f) {
            scale(f.tickScale, pivot = Offset(151f, 103f)) {
                drawPath(
                    TICK,
                    Color.White,
                    alpha = f.tickAlpha,
                    style = Stroke(4f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
    }
}
