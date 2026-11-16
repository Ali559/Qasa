package alibarznji.qasa.siteicons

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Which host a login's icon is asked from, and which hosts are never asked
 * (docs/site-icons.md "Which host" / "Never asked"). Pure: the shared vectors
 * (`docs/site-icons-vectors.json` → `host`) run against it on the JVM.
 */
object SiteIconRules {
    private const val ANDROID_APP_SCHEME = "androidapp://"

    /** Hosts under these never leave the device: local networks, reserved and onion names. */
    private val PRIVATE_SUFFIXES = listOf(
        ".local", ".localhost", ".lan", ".home", ".internal", ".intranet",
        ".corp", ".home.arpa", ".test", ".example", ".invalid", ".onion",
    )

    private val IPV4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")

    /**
     * The host asked for [website]'s icon, or null when none is ever asked. Read the way
     * autofill reads a website: trimmed; with `://` the scheme must be http or https;
     * without one it is taken as `https://<website>`; lowercased, one trailing `.` and a
     * leading `www.` dropped.
     */
    fun hostFor(website: String): String? {
        val trimmed = website.trim()
        if (trimmed.isEmpty()) return null
        val withScheme = if (trimmed.contains("://")) {
            val scheme = trimmed.substringBefore("://").lowercase()
            if (scheme != "http" && scheme != "https") return null
            trimmed
        } else {
            "https://$trimmed"
        }
        val url = withScheme.toHttpUrlOrNull() ?: return null
        val host = url.host.lowercase().removeSuffix(".").removePrefix("www.")
        return host.takeIf(::isAskable)
    }

    /** Whether [host] (already lowercased) may be asked: a dotted internet name, not an IP. */
    fun isAskable(host: String): Boolean {
        if (host.isEmpty() || !host.contains('.')) return false
        // IPv6 literals (OkHttp hands them back without brackets) and IPv4 literals.
        if (host.contains(':') || host.startsWith('[') || IPV4.matches(host)) return false
        if (host.all { it.isDigit() || it == '.' }) return false
        return PRIVATE_SUFFIXES.none { host.endsWith(it) || host == it.removePrefix(".") }
    }

    /** Whether a request (the first, a redirect's target or an icon URL) may go to [url]. */
    fun isAllowedTarget(url: HttpUrl): Boolean =
        url.isHttps && isAskable(url.host.lowercase().removeSuffix("."))

    /** The package of an Android app login (`androidapp://<package>`), or null. */
    fun androidPackage(website: String): String? {
        val trimmed = website.trim()
        if (!trimmed.startsWith(ANDROID_APP_SCHEME, ignoreCase = true)) return null
        return trimmed.substring(ANDROID_APP_SCHEME.length)
            .substringBefore('/')
            .takeIf { it.isNotEmpty() && it.all { c -> c.isLetterOrDigit() || c == '.' || c == '_' } }
    }
}
