package alibarznji.qasa.siteicons

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Picks the icon URLs to try from a site's home page (docs/site-icons.md "The request",
 * steps 2–3). A small tolerant scan of `<link>` tags, not an HTML parser: the page is only
 * read for its icons. Pure, so the shared vectors (`pickIcons`) run on the JVM.
 */
object SiteIconPicker {
    private val LINK_TAG = Regex("""<link\b([^>]*)>""", RegexOption.IGNORE_CASE)
    private val ATTRIBUTE = Regex(
        """([A-Za-z_:][-A-Za-z0-9_:.]*)\s*(?:=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+)))?""",
    )
    private val DATA_PREFIXES = listOf("data:image/png;base64,", "data:image/x-icon;base64,")
    private val APPLE_RELS = setOf("apple-touch-icon", "apple-touch-icon-precomposed")

    private class Candidate(val url: String, val apple: Boolean, val size: Int, val order: Int)

    /**
     * The icon URLs in [html] to try, best first: `apple-touch-icon*`, then by the largest
     * of `sizes` (`any` and missing count as 0), ties in document order. Each `href` is
     * resolved against [pageUrl]; anything not `https:` (but a PNG or ICO `data:` URL) and
     * any SVG is left out. The `/favicon.ico` fallback is not included.
     */
    fun pickIcons(pageUrl: String, html: String): List<String> {
        val base = pageUrl.toHttpUrlOrNull() ?: return emptyList()
        val candidates = mutableListOf<Candidate>()
        for ((index, tag) in LINK_TAG.findAll(html).withIndex()) {
            val attrs = attributes(tag.groupValues[1])
            val rels = attrs["rel"].orEmpty().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
            val apple = rels.any { it in APPLE_RELS }
            if (!apple && "icon" !in rels) continue
            if (attrs["type"].orEmpty().trim().lowercase() == "image/svg+xml") continue
            val href = decodeEntities(attrs["href"].orEmpty().trim())
            if (href.isEmpty()) continue
            val url = resolve(base, href) ?: continue
            candidates += Candidate(url, apple, largestSize(attrs["sizes"]), index)
        }
        return candidates
            .sortedWith(compareByDescending<Candidate> { it.apple }.thenByDescending { it.size }.thenBy { it.order })
            .map { it.url }
            .distinct()
    }

    private fun resolve(base: okhttp3.HttpUrl, href: String): String? {
        if (href.startsWith("data:", ignoreCase = true)) {
            return href.takeIf { h -> DATA_PREFIXES.any { h.startsWith(it, ignoreCase = true) } }
        }
        val url = base.resolve(href) ?: return null
        if (!url.isHttps) return null
        if (url.encodedPath.lowercase().endsWith(".svg")) return null
        return url.toString()
    }

    private fun attributes(body: String): Map<String, String> {
        val out = HashMap<String, String>()
        for (m in ATTRIBUTE.findAll(body)) {
            val name = m.groupValues[1].lowercase()
            if (name in out) continue
            val value = m.groups[2]?.value ?: m.groups[3]?.value ?: m.groups[4]?.value ?: ""
            out[name] = value
        }
        return out
    }

    /** The largest edge in a `sizes` value (`16x16 48x48` → 48); `any` and junk count as 0. */
    private fun largestSize(sizes: String?): Int =
        sizes.orEmpty().lowercase().split(Regex("\\s+")).maxOfOrNull { token ->
            val parts = token.split('x')
            if (parts.size != 2) 0 else maxOf(parts[0].toIntOrNull() ?: 0, parts[1].toIntOrNull() ?: 0)
        } ?: 0

    private fun decodeEntities(s: String): String =
        if (!s.contains('&')) s else s.replace("&amp;", "&").replace("&#38;", "&").replace("&quot;", "\"").replace("&#39;", "'")
}
