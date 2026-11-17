package alibarznji.qasa.siteicons

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Decodes a downloaded icon and normalises it (docs/site-icons.md "The request", steps 5–7):
 * PNG, ICO (its largest image), JPEG, GIF (first frame) and WebP through [BitmapFactory];
 * anything under 16 × 16 is refused; the result is fitted into 64 × 64 (aspect kept,
 * centred, transparent padding), shaped for its circle ([SiteIconShaper]) and encoded as PNG.
 */
object SiteIconImages {
    const val SIZE_PX = 64
    private const val MIN_PX = 16

    fun normalise(bytes: ByteArray): ByteArray? {
        val bitmap = decode(bytes) ?: return null
        try {
            if (bitmap.width < MIN_PX || bitmap.height < MIN_PX) return null
            return fit(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    /** An installed app's icon at the same 64 × 64, shaped like a site's, for `androidapp://` logins. */
    fun fromDrawable(drawable: Drawable): Bitmap {
        val out = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        drawable.setBounds(0, 0, SIZE_PX, SIZE_PX)
        drawable.draw(canvas)
        applyShape(out)
        return out
    }

    /**
     * A cached PNG written before step 7 existed, shaped now (docs/site-icons.md "Cache");
     * null when it can't be decoded.
     */
    fun reshape(png: ByteArray): ByteArray? {
        val decoded = BitmapFactory.decodeByteArray(png, 0, png.size) ?: return null
        try {
            if (decoded.width != SIZE_PX || decoded.height != SIZE_PX) return fit(decoded)
            val copy = decoded.copy(Bitmap.Config.ARGB_8888, true) ?: return null
            return try {
                applyShape(copy)
                encode(copy)
            } finally {
                copy.recycle()
            }
        } finally {
            decoded.recycle()
        }
    }

    /** Step 7 in place on a mutable 64 × 64 bitmap. */
    private fun applyShape(bitmap: Bitmap) {
        val pixels = IntArray(SIZE_PX * SIZE_PX)
        bitmap.getPixels(pixels, 0, SIZE_PX, 0, 0, SIZE_PX, SIZE_PX)
        val shaped = SiteIconShaper.shape(pixels)
        bitmap.setPixels(shaped.pixels, 0, SIZE_PX, 0, 0, SIZE_PX, SIZE_PX)
    }

    private fun encode(bitmap: Bitmap): ByteArray =
        ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

    private fun decode(bytes: ByteArray): Bitmap? {
        // An ICO holds several images: decode the largest one ourselves when it's a PNG or
        // a plain bitmap entry, else let the platform decoder pick.
        icoLargestPng(bytes)?.let { png -> BitmapFactory.decodeByteArray(png, 0, png.size)?.let { return it } }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        // Large images are sampled down while decoding; 64 px is all that's kept.
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= SIZE_PX * 2 && bounds.outHeight / (sample * 2) >= SIZE_PX * 2) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    /** The largest entry of an ICO file when that entry is an embedded PNG, else null. */
    private fun icoLargestPng(bytes: ByteArray): ByteArray? {
        if (bytes.size < 6) return null
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (b.getShort(0).toInt() != 0 || b.getShort(2).toInt() != 1) return null
        val count = b.getShort(4).toInt() and 0xFFFF
        if (count == 0 || 6 + count * 16 > bytes.size) return null
        var best: Pair<Int, IntRange>? = null
        for (i in 0 until count) {
            val at = 6 + i * 16
            val w = (bytes[at].toInt() and 0xFF).let { if (it == 0) 256 else it }
            val size = b.getInt(at + 8)
            val offset = b.getInt(at + 12)
            if (size <= 0 || offset < 0 || offset.toLong() + size > bytes.size) continue
            if (best == null || w > best.first) best = w to (offset until offset + size)
        }
        val range = best?.second ?: return null
        val entry = bytes.copyOfRange(range.first, range.last + 1)
        val isPng = entry.size > 8 && entry[0] == 0x89.toByte() && entry[1] == 'P'.code.toByte() &&
            entry[2] == 'N'.code.toByte() && entry[3] == 'G'.code.toByte()
        return entry.takeIf { isPng }
    }

    private fun fit(source: Bitmap): ByteArray {
        val out = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
        val scale = minOf(SIZE_PX.toFloat() / source.width, SIZE_PX.toFloat() / source.height)
        val w = source.width * scale
        val h = source.height * scale
        val left = (SIZE_PX - w) / 2f
        val top = (SIZE_PX - h) / 2f
        Canvas(out).drawBitmap(
            source,
            Rect(0, 0, source.width, source.height),
            RectF(left, top, left + w, top + h),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
        )
        return try {
            applyShape(out)
            encode(out)
        } finally {
            out.recycle()
        }
    }
}
