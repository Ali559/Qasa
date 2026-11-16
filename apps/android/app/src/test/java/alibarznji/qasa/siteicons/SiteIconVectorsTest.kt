package alibarznji.qasa.siteicons

import alibarznji.qasa.theme.siteLetterFor
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shared site-icon cases (`docs/site-icons-vectors.json`, spec `docs/site-icons.md`)
 * as Android runs them: the host asked, the tile letter and the icon URLs picked from a
 * page. iOS, the desktop and the extension run the same file. Fix the code, never the vectors.
 */
class SiteIconVectorsTest {
    private val vectors: JsonObject by lazy {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null && !File(dir, VECTORS).isFile) dir = dir.parentFile
        requireNotNull(dir) { "$VECTORS not found above ${System.getProperty("user.dir")}" }
        Json.parseToJsonElement(File(dir, VECTORS).readText()).jsonObject
    }

    private fun cases(key: String) = vectors.getValue(key).jsonArray.map { it.jsonObject }

    @Test fun host() {
        val all = cases("host")
        assertTrue(all.isNotEmpty())
        for (case in all) {
            val website = case.getValue("website").jsonPrimitive.content
            val expected = case.getValue("host").let { if (it is JsonNull) null else it.jsonPrimitive.content }
            assertEquals("host for \"$website\"", expected, SiteIconRules.hostFor(website))
        }
    }

    @Test fun letter() {
        val all = cases("letter")
        assertTrue(all.isNotEmpty())
        for (case in all) {
            val name = case.getValue("name").jsonPrimitive.content
            assertEquals("letter for \"$name\"", case.getValue("letter").jsonPrimitive.content, siteLetterFor(name))
        }
    }

    @Test fun pickIcons() {
        val all = cases("pickIcons")
        assertTrue(all.isNotEmpty())
        for ((index, case) in all.withIndex()) {
            val expected = case.getValue("icons").jsonArray.map { it.jsonPrimitive.content }
            val actual = SiteIconPicker.pickIcons(
                case.getValue("url").jsonPrimitive.content,
                case.getValue("html").jsonPrimitive.content,
            )
            assertEquals("pickIcons case $index", expected, actual)
        }
    }

    @Test fun androidAppLoginsNameTheirPackage() {
        assertEquals("com.instagram.android", SiteIconRules.androidPackage("androidapp://com.instagram.android"))
        assertEquals(null, SiteIconRules.androidPackage("https://instagram.com"))
    }

    @Test fun pngAndIcoDataUrlsAreKeptOthersAreNot() {
        val html = """<link rel="icon" href="data:image/png;base64,AAAA"><link rel="icon" href="data:image/svg+xml;base64,AAAA"><link rel="icon" href="data:image/x-icon;base64,BBBB">"""
        assertEquals(
            listOf("data:image/png;base64,AAAA", "data:image/x-icon;base64,BBBB"),
            SiteIconPicker.pickIcons("https://example.com/", html),
        )
    }

    private companion object {
        const val VECTORS = "docs/site-icons-vectors.json"
    }
}
