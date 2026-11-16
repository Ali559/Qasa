package alibarznji.qasa.siteicons

import java.io.IOException
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.Buffer

/** What asking a site for its icon found. */
sealed interface SiteIconFetchResult {
    /** A usable icon, already normalised to a 64 × 64 PNG. */
    class Icon(val png: ByteArray) : SiteIconFetchResult

    /** The site answered but had no usable icon (or redirected somewhere it may not). */
    data object None : SiteIconFetchResult

    /** Offline, timeout, TLS error. */
    data object Failure : SiteIconFetchResult
}

/**
 * Asks a site, and only that site, for its icon (docs/site-icons.md "The request"). Its own
 * client: HTTPS only, no cookies, no cache, no credentials, redirects followed by hand (at
 * most 3, each target re-checked), 8 s per request, `User-Agent: Mozilla/5.0 (compatible)`
 * and no `Accept-Language`. Names that resolve to a local or private address are refused
 * too, so a public name pointing at a router never makes the phone touch the local network.
 *
 * [normalise] turns downloaded bytes into the 64 × 64 PNG, or null when they aren't a
 * usable image ([SiteIconImages.normalise] on the device).
 */
class SiteIconFetcher(
    private val normalise: (ByteArray) -> ByteArray?,
    private val client: OkHttpClient = defaultClient(),
) {
    fun fetch(host: String): SiteIconFetchResult {
        if (!SiteIconRules.isAskable(host)) return SiteIconFetchResult.None
        val home = "https://$host/".toHttpUrlOrNull() ?: return SiteIconFetchResult.None
        val page = try {
            get(home, "text/html", HTML_LIMIT, truncate = true)
        } catch (_: IOException) {
            return SiteIconFetchResult.Failure
        }
        // The home page redirected somewhere that is never asked (not https, a local name).
        if (page !is Fetched.Body) return SiteIconFetchResult.None
        val candidates = buildList {
            if (page.ok) {
                addAll(SiteIconPicker.pickIcons(page.url.toString(), page.bytes.toString(Charsets.UTF_8)))
            }
            add("https://$host/favicon.ico")
        }.distinct()
        var networkFailure = false
        for (candidate in candidates) {
            val bytes = try {
                download(candidate)
            } catch (_: IOException) {
                networkFailure = true
                null
            } ?: continue
            val png = runCatching { normalise(bytes) }.getOrNull() ?: continue
            return SiteIconFetchResult.Icon(png)
        }
        return if (networkFailure) SiteIconFetchResult.Failure else SiteIconFetchResult.None
    }

    private fun download(candidate: String): ByteArray? {
        if (candidate.startsWith("data:", ignoreCase = true)) {
            val encoded = candidate.substringAfter(',', "")
            return runCatching { java.util.Base64.getMimeDecoder().decode(encoded) }.getOrNull()
                ?.takeIf { it.isNotEmpty() && it.size <= IMAGE_LIMIT }
        }
        val url = candidate.toHttpUrlOrNull() ?: return null
        val got = get(url, "image/*", IMAGE_LIMIT, truncate = false)
        return (got as? Fetched.Body)?.takeIf { it.ok }?.bytes
    }

    private sealed interface Fetched {
        class Body(val url: HttpUrl, val ok: Boolean, val bytes: ByteArray) : Fetched

        /** Refused: a redirect to a target not allowed, too many redirects, or too large. */
        data object Refused : Fetched
    }

    /** GET [start], following up to [MAX_REDIRECTS] allowed redirects by hand. */
    private fun get(start: HttpUrl, accept: String, limit: Int, truncate: Boolean): Fetched {
        var url = start
        repeat(MAX_REDIRECTS + 1) {
            if (!SiteIconRules.isAllowedTarget(url)) return Fetched.Refused
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", accept)
                .get()
                .build()
            call(request).use { response ->
                if (response.isRedirect) {
                    val next = response.header("Location")?.let(url::resolve) ?: return Fetched.Refused
                    url = next
                    return@repeat
                }
                val bytes = readCapped(response, limit, truncate) ?: return Fetched.Refused
                return Fetched.Body(url, response.isSuccessful, bytes)
            }
        }
        return Fetched.Refused
    }

    private fun call(request: Request): Response {
        val call: Call = client.newCall(request)
        return call.execute()
    }

    /** At most [limit] bytes; beyond it the HTML is cut there and an image refused. */
    private fun readCapped(response: Response, limit: Int, truncate: Boolean): ByteArray? {
        val body = response.body ?: return ByteArray(0)
        val source = body.source()
        val buffer = Buffer()
        while (buffer.size <= limit) {
            if (source.read(buffer, 8192) == -1L) break
        }
        if (buffer.size > limit) {
            if (!truncate) return null
            return buffer.readByteArray(limit.toLong())
        }
        return buffer.readByteArray()
    }

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (compatible)"
        const val HTML_LIMIT = 512 * 1024
        const val IMAGE_LIMIT = 256 * 1024
        const val MAX_REDIRECTS = 3
        private const val TIMEOUT_SECONDS = 8L

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .cache(null)
            .dns(PublicOnlyDns)
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    /** System DNS with local, private and link-local answers dropped. */
    private object PublicOnlyDns : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val usable = Dns.SYSTEM.lookup(hostname).filterNot(::isLocal)
            if (usable.isEmpty()) throw UnknownHostException("$hostname has no public address")
            return usable
        }

        private fun isLocal(a: InetAddress): Boolean =
            a.isLoopbackAddress || a.isSiteLocalAddress || a.isLinkLocalAddress || a.isAnyLocalAddress ||
                a.isMulticastAddress ||
                // IPv6 unique local (fc00::/7) and IPv4 shared / CGNAT space (100.64.0.0/10).
                (a is Inet6Address && (a.address[0].toInt() and 0xFE) == 0xFC) ||
                (a.address.size == 4 && a.address[0].toInt() == 100 && (a.address[1].toInt() and 0xC0) == 64)
    }
}
