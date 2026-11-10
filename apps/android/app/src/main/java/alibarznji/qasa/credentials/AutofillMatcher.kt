package alibarznji.qasa.credentials

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Who is asking for a fill: a web page in a browser we trust to report its domain, or an
 * app identified by its package name.
 */
sealed interface AutofillCaller {
    /** What the unlock screen shows ("Fill into …"). */
    val label: String

    data class Web(val host: String, val browserPackage: String) : AutofillCaller {
        override val label: String get() = host
    }

    data class App(val packageName: String) : AutofillCaller {
        override val label: String get() = packageName
    }
}

/**
 * Which logins belong to a caller (docs/autofill-plan.md §1.4): the host scores of iOS
 * `AutofillContextMatcher` and the desktop bridge (`matcher.rs`), with registrable domains
 * from the Public Suffix List that OkHttp ships.
 */
object AutofillMatcher {
    /** Same registrable domain or better, as on the desktop (`FILL_THRESHOLD`). */
    const val FILL_THRESHOLD = 60

    /**
     * Browsers whose `webDomain` we believe. Any other app can put any domain on a WebView,
     * so for those the package name decides.
     */
    val trustedBrowsers: Set<String> = setOf(
        "com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary",
        "org.mozilla.firefox", "org.mozilla.firefox_beta", "org.mozilla.fenix", "org.mozilla.focus",
        "com.microsoft.emmx", "com.brave.browser", "com.brave.browser_beta",
        "com.sec.android.app.sbrowser", "com.sec.android.app.sbrowser.beta",
        "com.opera.browser", "com.opera.mini.native", "com.vivaldi.browser",
        "com.duckduckgo.mobile.android", "com.kiwibrowser.browser", "com.ecosia.android",
        "org.chromium.chrome", "com.google.android.apps.chrome",
    )

    /** Package labels that say nothing about whose app it is. */
    private val genericLabels = setOf(
        "com", "org", "net", "io", "co", "app", "apps", "android", "mobile", "www", "client",
        "prod", "release", "lite", "main", "free", "pro", "beta", "official", "global",
    )

    fun caller(packageName: String, webDomain: String?): AutofillCaller {
        val host = webDomain?.let(::hostOf)
        return if (host != null && packageName in trustedBrowsers) AutofillCaller.Web(host, packageName) else AutofillCaller.App(packageName)
    }

    /** The lowercased host of an `http(s)` URL or bare host, without `www.`. */
    fun hostOf(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"
        val url = withScheme.toHttpUrlOrNull() ?: return null
        return url.host.lowercase().trimEnd('.').removePrefix("www.").ifEmpty { null }
    }

    /** IP addresses and single-label hosts are their own site. */
    fun registrableDomain(host: String): String {
        if (!host.contains('.') || host.all { it.isDigit() || it == '.' } || host.contains(':')) return host
        return "https://$host".toHttpUrlOrNull()?.topPrivateDomain() ?: host
    }

    /** iOS `scoreEntry`: exact host 100, subdomain of the same site 80, same site 60, containment 30. */
    fun hostScore(itemHost: String, pageHost: String): Int {
        if (itemHost == pageHost) return 100
        val sameSite = registrableDomain(itemHost) == registrableDomain(pageHost)
        return when {
            sameSite && (itemHost.endsWith(".$pageHost") || pageHost.endsWith(".$itemHost")) -> 80
            sameSite -> 60
            itemHost.contains(pageHost) || pageHost.contains(itemHost) -> 30
            else -> 0
        }
    }

    /**
     * A login's score for an app: 100 when the item names the app (`androidapp://<package>`),
     * 60 when the site's name is one of the package's labels (`instagram.com` and
     * `com.instagram.android`), else 0. The unlock screen still shows the package, so a
     * look-alike app is visible before anything is filled.
     */
    fun appScore(itemWebsite: String, packageName: String): Int {
        val website = itemWebsite.trim().lowercase()
        if (website.startsWith("androidapp://")) {
            return if (website.removePrefix("androidapp://").trimEnd('/') == packageName.lowercase()) 100 else 0
        }
        val host = hostOf(website) ?: return 0
        val siteLabel = registrableDomain(host).substringBefore('.')
        if (siteLabel.length < 3 || siteLabel in genericLabels) return 0
        val labels = packageName.lowercase().split('.').filter { it !in genericLabels }
        return if (siteLabel in labels) 60 else 0
    }

    fun score(itemWebsite: String?, caller: AutofillCaller): Int {
        val website = itemWebsite?.takeIf { it.isNotBlank() } ?: return 0
        return when (caller) {
            is AutofillCaller.Web -> hostOf(website)?.let { hostScore(it, caller.host) } ?: 0
            is AutofillCaller.App -> appScore(website, caller.packageName)
        }
    }

    /** The entries a caller may be offered, best first. */
    fun <T> rank(entries: List<T>, caller: AutofillCaller, website: (T) -> String?): List<T> =
        entries.map { it to score(website(it), caller) }
            .filter { it.second >= FILL_THRESHOLD }
            .sortedByDescending { it.second }
            .map { it.first }
}
