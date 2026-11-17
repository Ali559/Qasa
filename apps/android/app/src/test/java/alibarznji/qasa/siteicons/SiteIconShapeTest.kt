package alibarznji.qasa.siteicons

import java.io.File
import kotlin.math.abs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * docs/site-icons.md steps 6–7 against the PNGs in `docs/site-icon-shapes` and the kinds in
 * `docs/site-icons-vectors.json` → `shapes` (edge colour within 6 per channel). The images
 * are decoded with ImageIO here (no BitmapFactory in JVM tests) and fitted with the same
 * pure [SiteIconShaper.fit] the shape step uses. Fix the code, never the vectors.
 */
class SiteIconShapeTest {
    private val root: File by lazy {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null && !File(dir, VECTORS).isFile) dir = dir.parentFile
        requireNotNull(dir) { "$VECTORS not found above ${System.getProperty("user.dir")}" }
    }

    private fun fitted(file: File): IntArray {
        // ImageIO is in the JVM that runs the tests but not in android.jar, which the test
        // sources compile against, so it's reached by reflection.
        val image = requireNotNull(Class.forName("javax.imageio.ImageIO").getMethod("read", File::class.java).invoke(null, file)) {
            "can't decode $file"
        }
        val type = image.javaClass
        val w = type.getMethod("getWidth").invoke(image) as Int
        val h = type.getMethod("getHeight").invoke(image) as Int
        val argb = type.getMethod(
            "getRGB", Int::class.java, Int::class.java, Int::class.java, Int::class.java,
            IntArray::class.java, Int::class.java, Int::class.java,
        ).invoke(image, 0, 0, w, h, null, 0, w) as IntArray
        return SiteIconShaper.fit(argb, w, h, SiteIconShaper.SIDE)
    }

    @Test fun everyShapeCase() {
        val cases = Json.parseToJsonElement(File(root, VECTORS).readText()).jsonObject.getValue("shapes").jsonArray.map { it.jsonObject }
        assertTrue(cases.isNotEmpty())
        for (case in cases) {
            val name = case.getValue("file").jsonPrimitive.content
            val shaped = SiteIconShaper.shape(fitted(File(root, "docs/site-icon-shapes/$name")))
            val kind = case.getValue("kind").jsonPrimitive.content
            assertEquals("kind of $name", kind, shaped.kind.name.lowercase())
            val edge = case["edge"]?.jsonPrimitive?.content
            if (edge == null) {
                assertNull("edge colour of $name", shaped.edgeRgb)
                continue
            }
            val want = edge.removePrefix("#").toInt(16)
            val got = requireNotNull(shaped.edgeRgb) { "no edge colour for $name" }
            for (shift in intArrayOf(16, 8, 0)) {
                val d = abs(((want shr shift) and 0xFF) - ((got shr shift) and 0xFF))
                assertTrue("edge colour of $name: want $edge, got #%06X".format(got), d <= 6)
            }
            // The edge colour fills the square's corners, fully opaque.
            assertEquals(0xFF000000.toInt() or got, shaped.pixels[0])
        }
    }

    /** The tile's ring comes from the shaped PNG alone: pixel (11, 11) see-through means glyph. */
    @Test fun ringOnlyForGlyphs() {
        val cases = Json.parseToJsonElement(File(root, VECTORS).readText()).jsonObject.getValue("shapes").jsonArray.map { it.jsonObject }
        assertTrue(cases.isNotEmpty())
        for (case in cases) {
            val name = case.getValue("file").jsonPrimitive.content
            val shaped = SiteIconShaper.shape(fitted(File(root, "docs/site-icon-shapes/$name")))
            val glyph = case.getValue("kind").jsonPrimitive.content == "glyph"
            assertEquals("ring for $name", glyph, SiteIconShaper.needsRing(shaped.pixels))
        }
        assertTrue(SiteIconShaper.needsRing(SiteIconShaper.shape(IntArray(64 * 64)).pixels))
    }

    @Test fun bleedKeepsTheImage() {
        val px = fitted(File(root, "docs/site-icon-shapes/square-solid.png"))
        val shaped = SiteIconShaper.shape(px)
        assertEquals(SiteIconShapeKind.Bleed, shaped.kind)
        assertTrue(px.contentEquals(shaped.pixels))
    }

    @Test fun glyphSitsInTheCentredBoxWithCutCorners() {
        val shaped = SiteIconShaper.shape(fitted(File(root, "docs/site-icon-shapes/wide-gradient.png")))
        assertEquals(SiteIconShapeKind.Glyph, shaped.kind)
        // Nothing outside the centred 38 × 38 box (13..50), and its very corners are cut.
        for (y in 0 until 64) for (x in 0 until 64) {
            val a = shaped.pixels[y * 64 + x] ushr 24
            if (x !in 13..50 || y !in 13..50) assertEquals("alpha at $x,$y", 0, a)
        }
        for ((x, y) in listOf(13 to 13, 50 to 13, 13 to 50, 50 to 50)) assertEquals(0, shaped.pixels[y * 64 + x] ushr 24)
    }

    @Test fun emptyImageStaysEmpty() {
        val shaped = SiteIconShaper.shape(IntArray(64 * 64))
        assertEquals(SiteIconShapeKind.Glyph, shaped.kind)
        assertTrue(shaped.pixels.all { it == 0 })
    }

    private companion object {
        const val VECTORS = "docs/site-icons-vectors.json"
    }
}
