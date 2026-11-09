package alibarznji.qasa.credentials

import alibarznji.qasa.vault.VaultPayload
import alibarznji.qasa.vault.VaultSession
import alibarznji.qasa.vault.crypto.VaultCrypto
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class AutofillIndexEntry(
    val id: String,
    val kind: String,
    val title: String,
    val subtitle: String,
    val websiteHost: String? = null,
    val last4: String? = null,
    val encryptedSecrets: String,
)

@Serializable
data class AutofillIndexFile(
    /** Index format; see [AutofillIndexStore.CURRENT_FORMAT_VERSION]. */
    val version: Int = 1,
    val updatedAt: Long,
    val entries: List<AutofillIndexEntry>,
)

@Serializable
data class AutofillSecrets(
    val username: String? = null,
    val password: String? = null,
    val number: String? = null,
    val securityCode: String? = null,
    val accountNumber: String? = null,
    /** The login's otpauth URI, encrypted with the password so autofill can copy its code. */
    val totp: String? = null,
)

@Singleton
class AutofillIndexStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val session: VaultSession,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val file: File get() = File(context.filesDir, "autofill-index.json")

    fun clear() {
        if (file.exists()) file.delete()
    }

    fun write(entries: List<AutofillIndexEntry>) {
        val payload = AutofillIndexFile(
            version = CURRENT_FORMAT_VERSION,
            updatedAt = System.currentTimeMillis(),
            entries = entries,
        )
        file.writeText(json.encodeToString(payload))
    }

    /** The format version the index on disk was written with; null when missing or unreadable. */
    fun storedFormatVersion(): Int? {
        if (!file.exists()) return null
        return try {
            formatVersionOf(file.readText())
        } catch (_: Exception) {
            null
        }
    }

    /** Whether the index on disk predates [CURRENT_FORMAT_VERSION] and must be rebuilt once. */
    fun isOutdated(): Boolean = needsRebuild(storedFormatVersion())

    fun read(): List<AutofillIndexEntry> {
        if (!file.exists()) return emptyList()
        return try {
            json.decodeFromString<AutofillIndexFile>(file.readText()).entries
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun entryFromPayload(id: String, payload: VaultPayload): AutofillIndexEntry? {
        val enc = try { session.requireEncKey() } catch (_: Exception) { return null }
        return when (payload) {
            is VaultPayload.Login -> {
                val secrets = AutofillSecrets(
                    username = payload.username.ifBlank { payload.email },
                    password = payload.password,
                    totp = payload.totp.ifEmpty { null },
                )
                val blob = VaultCrypto.encrypt(json.encodeToString(secrets).toByteArray(), enc)
                AutofillIndexEntry(
                    id = id,
                    kind = "login",
                    title = payload.displayName(),
                    subtitle = payload.subtitle(),
                    websiteHost = hostFrom(payload.website),
                    encryptedSecrets = android.util.Base64.encodeToString(blob, android.util.Base64.NO_WRAP),
                )
            }
            is VaultPayload.Card -> {
                val digits = payload.number.filter { it.isDigit() }
                val secrets = AutofillSecrets(number = payload.number, securityCode = payload.securityCode)
                val blob = VaultCrypto.encrypt(json.encodeToString(secrets).toByteArray(), enc)
                AutofillIndexEntry(
                    id = id,
                    kind = "card",
                    title = payload.displayName(),
                    subtitle = payload.subtitle(),
                    last4 = digits.takeLast(4).ifEmpty { null },
                    encryptedSecrets = android.util.Base64.encodeToString(blob, android.util.Base64.NO_WRAP),
                )
            }
            is VaultPayload.BankAccount -> {
                val secrets = AutofillSecrets(accountNumber = payload.accountNumber)
                val blob = VaultCrypto.encrypt(json.encodeToString(secrets).toByteArray(), enc)
                AutofillIndexEntry(
                    id = id,
                    kind = "bankAccount",
                    title = payload.displayName(),
                    subtitle = payload.subtitle(),
                    encryptedSecrets = android.util.Base64.encodeToString(blob, android.util.Base64.NO_WRAP),
                )
            }
            else -> null
        }
    }

    fun decryptSecrets(entry: AutofillIndexEntry): AutofillSecrets? {
        val plaintext = try {
            val enc = session.requireEncKey()
            val bytes = android.util.Base64.decode(entry.encryptedSecrets, android.util.Base64.NO_WRAP)
            VaultCrypto.decrypt(bytes, enc)
        } catch (_: Exception) {
            return null
        }
        return try {
            json.decodeFromString(plaintext.toString(Charsets.UTF_8))
        } catch (_: Exception) {
            null
        } finally {
            // The cleartext vault item lives in the autofill service process, which has
            // no user-facing lock. Wipe the buffer instead of waiting for the GC.
            plaintext.fill(0)
        }
    }

    private fun hostFrom(website: String): String? {
        val w = website.trim()
        if (w.isEmpty()) return null
        return try {
            val withScheme = if (w.contains("://")) w else "https://$w"
            java.net.URI(withScheme).host?.lowercase()
        } catch (_: Exception) {
            w.lowercase()
        }
    }

    companion object {
        /**
         * Bump whenever entries gain data an older index lacks. 2: logins carry their
         * otpauth URI (`AutofillSecrets.totp`) so autofill can copy the 2FA code.
         */
        const val CURRENT_FORMAT_VERSION = 2

        private val versionJson = Json { ignoreUnknownKeys = true }

        /** The `version` field of an index file (every build wrote it; absent counts as 1), or null if unreadable. */
        fun formatVersionOf(text: String): Int? = try {
            versionJson.parseToJsonElement(text).jsonObject["version"]?.jsonPrimitive?.intOrNull ?: 1
        } catch (_: Exception) {
            null
        }

        /** A missing or unreadable index counts as outdated: rebuilding it is cheap and writes the version. */
        fun needsRebuild(stored: Int?, current: Int = CURRENT_FORMAT_VERSION): Boolean =
            stored == null || stored < current
    }
}
