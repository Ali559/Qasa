package alibarznji.qasa.vault.totp

import java.net.URLDecoder
import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 2FA (TOTP, RFC 6238) codes for logins. Shared contract with iOS: the login stores a
 * normalised `otpauth://totp/...` URI in `totp`, built by [normalize] from a scanned QR
 * code or a pasted key, and both apps derive the same codes from it.
 */
object Totp {
    enum class Algorithm(val uriName: String, val macName: String) {
        SHA1("SHA1", "HmacSHA1"),
        SHA256("SHA256", "HmacSHA256"),
        SHA512("SHA512", "HmacSHA512"),
    }

    /** A parsed setup. [secretBase32] is the cleaned form that goes back into the URI. */
    class Params(
        val secret: ByteArray,
        val secretBase32: String,
        val algorithm: Algorithm = Algorithm.SHA1,
        val digits: Int = 6,
        val period: Int = 30,
        val issuer: String? = null,
        val label: String? = null,
    )

    private const val SCHEME = "otpauth://"
    private const val MIN_SECRET_BYTES = 10
    private val DIGITS = 6..8
    private val PERIOD = 15..120

    /**
     * The URI to store for [input], or null when it isn't a valid TOTP setup. A URI keeps
     * its label and parameters with the secret re-emitted cleaned; a bare key becomes
     * `otpauth://totp/Qasa?secret=<KEY>`. With [requireUri] (a scanned QR code) a bare
     * key is rejected too.
     */
    fun normalize(input: String, requireUri: Boolean = false): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        if (isOtpauthUri(trimmed)) return normalizeUri(trimmed)
        if (requireUri) return null
        val secret = cleanSecret(trimmed)
        decodeSecret(secret) ?: return null
        return "${SCHEME}totp/Qasa?secret=$secret"
    }

    /** True for anything with the `otpauth://` scheme, TOTP or not (the QR "is it 2FA" check). */
    fun isOtpauthUri(raw: String): Boolean = raw.trim().startsWith(SCHEME, ignoreCase = true)

    /** Parses a URI or bare key (the same rules as [normalize]); null when invalid. */
    fun parse(input: String): Params? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        if (isOtpauthUri(trimmed)) return parseUri(trimmed)?.first
        val secret = cleanSecret(trimmed)
        val bytes = decodeSecret(secret) ?: return null
        return Params(bytes, secret)
    }

    /** The code at [unixSeconds]: RFC 6238 over an RFC 4226 dynamic truncation. */
    fun code(params: Params, unixSeconds: Long): String {
        val counter = Math.floorDiv(unixSeconds, params.period.toLong())
        val mac = Mac.getInstance(params.algorithm.macName)
        mac.init(SecretKeySpec(params.secret, params.algorithm.macName))
        val hash = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array())
        val offset = hash.last().toInt() and 0x0f
        val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
            ((hash[offset + 1].toInt() and 0xff) shl 16) or
            ((hash[offset + 2].toInt() and 0xff) shl 8) or
            (hash[offset + 3].toInt() and 0xff)
        var modulus = 1
        repeat(params.digits) { modulus *= 10 }
        return (binary % modulus).toString().padStart(params.digits, '0')
    }

    /** Current code for a stored URI, or null when it no longer parses. */
    fun currentCode(uri: String, unixSeconds: Long = System.currentTimeMillis() / 1000): String? =
        parse(uri)?.let { code(it, unixSeconds) }

    /** Seconds left before the code at [unixSeconds] rolls over. */
    fun secondsRemaining(period: Int, unixSeconds: Long): Int =
        period - Math.floorMod(unixSeconds, period.toLong()).toInt()

    /** "123 456" / "1234 5678": two groups, for reading the code aloud or retyping it. */
    fun grouped(code: String): String {
        if (code.length < 6) return code
        val split = code.length / 2
        return code.substring(0, split) + " " + code.substring(split)
    }

    // Parsing

    private fun normalizeUri(raw: String): String? {
        val (_, parts) = parseUri(raw) ?: return null
        val query = parts.query.joinToString("&") { (key, value) ->
            if (key.equals("secret", ignoreCase = true)) "secret=${parts.cleanSecret}" else "$key=$value"
        }
        return "${SCHEME}totp/${parts.rawLabel}?$query"
    }

    /** What [normalizeUri] re-emits: the raw label and raw `key=value` pairs, in order. */
    private class UriParts(val rawLabel: String, val query: List<Pair<String, String>>, val cleanSecret: String)

    private fun parseUri(raw: String): Pair<Params, UriParts>? {
        val rest = raw.substring(SCHEME.length)
        val hostEnd = rest.indexOfFirst { it == '/' || it == '?' }.let { if (it < 0) rest.length else it }
        // Only time-based codes: `hotp` (counter-based) and anything else are rejected.
        if (!rest.substring(0, hostEnd).equals("totp", ignoreCase = true)) return null
        val afterHost = rest.substring(hostEnd)
        val queryStart = afterHost.indexOf('?')
        val path = if (queryStart < 0) afterHost else afterHost.substring(0, queryStart)
        val rawLabel = path.removePrefix("/")
        val rawQuery = if (queryStart < 0) "" else afterHost.substring(queryStart + 1)

        val pairs = rawQuery.split('&').filter { it.isNotEmpty() }.map { pair ->
            val eq = pair.indexOf('=')
            if (eq < 0) pair to "" else pair.substring(0, eq) to pair.substring(eq + 1)
        }
        fun param(name: String): String? =
            pairs.firstOrNull { it.first.equals(name, ignoreCase = true) }?.second?.let(::decode)

        val secret = cleanSecret(param("secret") ?: return null)
        val bytes = decodeSecret(secret) ?: return null
        val algorithm = param("algorithm")?.let { value ->
            Algorithm.entries.firstOrNull { it.uriName.equals(value.trim(), ignoreCase = true) } ?: return null
        } ?: Algorithm.SHA1
        val digits = param("digits")?.let { it.trim().toIntOrNull()?.takeIf { d -> d in DIGITS } ?: return null } ?: 6
        val period = param("period")?.let { it.trim().toIntOrNull()?.takeIf { p -> p in PERIOD } ?: return null } ?: 30
        val params = Params(
            secret = bytes,
            secretBase32 = secret,
            algorithm = algorithm,
            digits = digits,
            period = period,
            issuer = param("issuer")?.takeIf { it.isNotBlank() },
            label = decode(rawLabel).takeIf { it.isNotBlank() },
        )
        return params to UriParts(rawLabel, pairs, secret)
    }

    private fun decode(value: String): String =
        runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)

    /** Spaces and dashes stripped, uppercased, `=` padding dropped. */
    internal fun cleanSecret(raw: String): String =
        raw.filterNot { it.isWhitespace() || it == '-' || it == '=' }.uppercase()

    /** Decoded key bytes, or null for a bad character or a key under 10 bytes. */
    private fun decodeSecret(clean: String): ByteArray? =
        base32Decode(clean)?.takeIf { it.size >= MIN_SECRET_BYTES }

    /** RFC 4648 base32 without padding; null on any character outside A–Z and 2–7. */
    internal fun base32Decode(input: String): ByteArray? {
        val out = java.io.ByteArrayOutputStream(input.length * 5 / 8)
        var buffer = 0
        var bits = 0
        for (ch in input) {
            val value = when (ch) {
                in 'A'..'Z' -> ch - 'A'
                in '2'..'7' -> ch - '2' + 26
                else -> return null
            }
            buffer = ((buffer shl 5) or value) and 0xfff
            bits += 5
            if (bits >= 8) {
                bits -= 8
                out.write((buffer shr bits) and 0xff)
            }
        }
        return out.toByteArray()
    }
}
