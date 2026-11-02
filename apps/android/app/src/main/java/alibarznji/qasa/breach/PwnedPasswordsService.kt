package alibarznji.qasa.breach

import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Breached-password check against HIBP Pwned Passwords (free, no API key). k-anonymity:
 * each password is SHA-1 hashed on device and only the first 5 hex characters leave the
 * phone. The Qasa server is not involved. Mirrors iOS `PwnedPasswordsService`.
 */
@Singleton
class PwnedPasswordsService @Inject constructor() {
    // Its own client: no Qasa auth header, no JSON Accept, and nothing logged.
    private val client = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    /** Range responses for this process, keyed by prefix. Breach data changes slowly. */
    private val cache = mutableMapOf<String, Map<String, Int>>()
    private val cacheLock = Mutex()

    /**
     * Breach count per password, for passwords seen at least once. Throws when any range
     * can't be fetched, so callers never show a partial "all clear".
     */
    suspend fun breachCounts(passwords: List<String>): Map<String, Int> {
        val byPrefix = groupByPrefix(passwords)
        val counts = mutableMapOf<String, Int>()
        for ((prefix, entries) in byPrefix) {
            counts += matchRange(range(prefix), entries)
        }
        return counts
    }

    private suspend fun range(prefix: String): Map<String, Int> {
        cacheLock.withLock { cache[prefix] }?.let { return it }
        val parsed = withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(RANGE_URL + prefix)
                // Padding hides how many suffixes the prefix really has.
                .header("Add-Padding", "true")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.code != 200) throw IOException("Pwned Passwords ${response.code}")
                parseRange(response.body?.string().orEmpty())
            }
        }
        cacheLock.withLock { cache[prefix] = parsed }
        return parsed
    }

    internal companion object {
        private const val RANGE_URL = "https://api.pwnedpasswords.com/range/"

        /** Uppercase hex SHA-1 of the UTF-8 password, as the range API expects. */
        fun sha1Hex(password: String): String =
            MessageDigest.getInstance("SHA-1").digest(password.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02X".format(it) }

        /** Distinct passwords grouped by 5-char hash prefix, each with its 35-char suffix. */
        fun groupByPrefix(passwords: List<String>): Map<String, List<Pair<String, String>>> =
            passwords.toSet()
                .map { password -> sha1Hex(password).let { hex -> Triple(hex.take(5), password, hex.drop(5)) } }
                .groupBy({ it.first }, { it.second to it.third })

        /** `SUFFIX:COUNT` per line. Padding rows have a count of 0 and are kept here, dropped by [matchRange]. */
        fun parseRange(body: String): Map<String, Int> {
            val result = mutableMapOf<String, Int>()
            body.lineSequence().forEach { line ->
                val parts = line.trim().split(":")
                if (parts.size != 2) return@forEach
                val count = parts[1].trim().toIntOrNull() ?: return@forEach
                result[parts[0].trim().uppercase()] = count
            }
            return result
        }

        /** Password → count for the entries (password, suffix) found in [range] with a count above 0. */
        fun matchRange(range: Map<String, Int>, entries: List<Pair<String, String>>): Map<String, Int> =
            entries.mapNotNull { (password, suffix) ->
                range[suffix]?.takeIf { it > 0 }?.let { password to it }
            }.toMap()
    }
}
