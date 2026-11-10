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
    /** The item's website as saved (3+): matching needs `androidapp://` links too. */
    val website: String? = null,
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
    // Format 3: what card and identity forms ask for (docs/autofill-plan.md §3.1).
    val holder: String? = null,
    /** `MM/YY`, the canonical card expiry. */
    val expiry: String? = null,
    val honorific: String? = null,
    val firstName: String? = null,
    val middleName: String? = null,
    val lastName: String? = null,
    val email: String? = null,
    /** ISO country code of the phone number (`IQ`). */
    val phoneCountry: String? = null,
    val phoneNumber: String? = null,
    val street: String? = null,
    val line2: String? = null,
    val city: String? = null,
    val postalCode: String? = null,
    /** ISO country code of the address. */
    val country: String? = null,
    val organization: String? = null,
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
        fun entry(
            kind: String,
            secrets: AutofillSecrets,
            subtitle: String = payload.subtitle(),
            website: String? = null,
            last4: String? = null,
        ): AutofillIndexEntry {
            val blob = VaultCrypto.encrypt(json.encodeToString(secrets).toByteArray(), enc)
            return AutofillIndexEntry(
                id = id,
                kind = kind,
                title = payload.displayName(),
                subtitle = subtitle,
                websiteHost = website?.let(::hostFrom),
                website = website?.trim()?.ifEmpty { null },
                last4 = last4,
                encryptedSecrets = android.util.Base64.encodeToString(blob, android.util.Base64.NO_WRAP),
            )
        }
        return when (payload) {
            is VaultPayload.Login -> entry(
                KIND_LOGIN,
                AutofillSecrets(
                    username = payload.username.ifBlank { payload.email },
                    password = payload.password,
                    totp = payload.totp.ifEmpty { null },
                ),
                website = payload.website,
            )
            is VaultPayload.Card -> {
                val digits = payload.number.filter { it.isDigit() }
                entry(
                    KIND_CARD,
                    AutofillSecrets(
                        number = payload.number,
                        securityCode = payload.securityCode,
                        holder = payload.accountHolder.ifBlank { null },
                        expiry = payload.expiryDate.ifBlank { null },
                    ),
                    last4 = digits.takeLast(4).ifEmpty { null },
                )
            }
            is VaultPayload.BankAccount -> entry(KIND_BANK, AutofillSecrets(accountNumber = payload.accountNumber))
            is VaultPayload.Name -> entry(
                KIND_NAME,
                AutofillSecrets(
                    honorific = payload.type.takeIf { it != "None of these" && it.isNotBlank() },
                    firstName = payload.firstName.ifBlank { null },
                    middleName = payload.middleName.ifBlank { null },
                    lastName = payload.lastName.ifBlank { null },
                ),
                subtitle = "",
            )
            is VaultPayload.PersonalEmail -> entry(KIND_EMAIL, AutofillSecrets(email = payload.email.ifBlank { null }))
            is VaultPayload.Phone -> entry(
                KIND_PHONE,
                AutofillSecrets(phoneCountry = payload.countryCode, phoneNumber = payload.number.ifBlank { null }),
                // The index's plain part is readable while locked: only the last digits.
                subtitle = payload.number.filter { it.isDigit() }.takeLast(2).let { if (it.isEmpty()) "" else "•••• $it" },
            )
            is VaultPayload.Address -> entry(
                KIND_ADDRESS,
                AutofillSecrets(
                    street = payload.address.ifBlank { null },
                    line2 = listOf(payload.building, payload.floor, payload.apartment).filter { it.isNotBlank() }.joinToString(", ").ifEmpty { null },
                    city = payload.city.ifBlank { null },
                    postalCode = payload.zipCode.ifBlank { null },
                    country = payload.countryCode.ifBlank { null },
                    holder = payload.recipient.ifBlank { null },
                ),
            )
            is VaultPayload.Company -> entry(KIND_COMPANY, AutofillSecrets(organization = payload.companyName.ifBlank { null }))
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
         * otpauth URI (`AutofillSecrets.totp`) so autofill can copy the 2FA code. 3: card
         * holder and expiry, names, email, phone, addresses and companies, and the raw
         * website for app matching.
         */
        const val CURRENT_FORMAT_VERSION = 3

        const val KIND_LOGIN = "login"
        const val KIND_CARD = "card"
        const val KIND_BANK = "bankAccount"
        const val KIND_NAME = "name"
        const val KIND_EMAIL = "email"
        const val KIND_PHONE = "phone"
        const val KIND_ADDRESS = "address"
        const val KIND_COMPANY = "company"

        /** Identity kinds, in the order a profile is assembled from them. */
        val IDENTITY_KINDS = listOf(KIND_ADDRESS, KIND_NAME, KIND_EMAIL, KIND_PHONE, KIND_COMPANY)

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
