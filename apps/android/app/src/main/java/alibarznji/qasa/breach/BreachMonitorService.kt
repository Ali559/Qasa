package alibarznji.qasa.breach

import alibarznji.qasa.config.QasaConfig
import alibarznji.qasa.network.BreachMonitorBody
import alibarznji.qasa.network.BreachMonitorRegisterResponse
import alibarznji.qasa.network.BreachPrefixResponse
import alibarznji.qasa.network.OkResponse
import alibarznji.qasa.network.QasaApiClient
import alibarznji.qasa.network.delete
import alibarznji.qasa.network.get
import alibarznji.qasa.network.post
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class BreachCheckResult(
    val email: String,
    val breached: Boolean,
    val sources: List<String>,
)

@Singleton
class BreachMonitorService @Inject constructor(
    private val api: QasaApiClient,
) {
    fun fullHash(email: String): String? {
        val normalized = email.trim().lowercase()
        if (!normalized.contains("@")) return null
        val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun hashParts(email: String): Pair<String, String>? {
        val hex = fullHash(email) ?: return null
        if (hex.length != 64) return null
        return hex.take(5) to hex.drop(5)
    }

    suspend fun registerMonitor(email: String, labelHint: String? = null): BreachMonitorRegisterResponse =
        withContext(Dispatchers.IO) {
            val hash = fullHash(email) ?: throw IllegalArgumentException("bad email")
            api.post(QasaConfig.breachMonitorUrl, BreachMonitorBody(hash, labelHint))
        }

    suspend fun unregisterMonitor(email: String) = withContext(Dispatchers.IO) {
        val hash = fullHash(email) ?: return@withContext
        try {
            api.delete<OkResponse, BreachMonitorBody>(QasaConfig.breachMonitorUrl, BreachMonitorBody(hash))
        } catch (_: Exception) {
        }
    }

    suspend fun checkEmail(email: String): BreachCheckResult = withContext(Dispatchers.IO) {
        val parts = hashParts(email) ?: return@withContext BreachCheckResult(email, false, emptyList())
        val response: BreachPrefixResponse = api.get(QasaConfig.breachPrefixUrl(parts.first))
        val matches = response.suffixes.filter { it.suffix.equals(parts.second, ignoreCase = true) }
        BreachCheckResult(email, matches.isNotEmpty(), matches.map { it.source })
    }

    /**
     * Sequential rescan — iOS `BreachMonitorService.checkEmails` (Services/Breach/BreachMonitorService.swift:73).
     * A failed lookup is reported as "not breached" rather than dropped, so every monitored
     * email still gets a result row.
     */
    suspend fun checkEmails(emails: List<String>): List<BreachCheckResult> = emails.map { email ->
        try {
            checkEmail(email)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            BreachCheckResult(email, false, emptyList())
        }
    }
}
