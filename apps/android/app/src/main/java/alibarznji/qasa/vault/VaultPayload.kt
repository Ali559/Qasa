package alibarznji.qasa.vault

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class VaultItemKind(val raw: String) {
    Login("login"),
    SecureNote("secureNote"),
    Payment("payment"),
    PersonalInfo("personalInfo"),
    IdDocument("idDocument");

    companion object {
        fun fromRaw(raw: String) = entries.find { it.raw == raw } ?: Login
    }
}

@Serializable
data class VaultEnvelope(
    val displayName: String,
    val subtitle: String,
    val collectionId: String? = null,
)

@Serializable
sealed class VaultPayload {
    abstract val kind: VaultItemKind
    abstract fun displayName(): String
    abstract fun subtitle(): String
    abstract fun collectionId(): String?

    /** Only a `protectItem` note is gated; mirrors `VaultPayload.isProtectedNote`. */
    val isProtectedNote: Boolean get() = this is SecureNote && protectItem

    @Serializable
    @SerialName("login")
    data class Login(
        val itemName: String = "",
        val email: String = "",
        val username: String = "",
        val password: String = "",
        val website: String = "",
        val note: String = "",
        val collectionId: String? = null,
        /**
         * Normalised `otpauth://totp/...` URI (see [alibarznji.qasa.vault.totp.Totp]); empty
         * means no 2FA code. iOS stores it as `LoginFields.totp: String?`. Items saved
         * before it existed decode with the default ([VaultJson] ignores unknown keys and
         * fills missing ones from defaults).
         */
        val totp: String = "",
    ) : VaultPayload() {
        override val kind get() = VaultItemKind.Login
        override fun displayName() = itemName.ifBlank { "Untitled" }
        override fun subtitle() = email.ifBlank { username }
        override fun collectionId() = collectionId
    }

    @Serializable
    @SerialName("secureNote")
    data class SecureNote(
        val title: String = "",
        val content: String = "",
        val color: String = "mint",
        val collectionId: String? = null,
        val protectItem: Boolean = false,
    ) : VaultPayload() {
        override val kind get() = VaultItemKind.SecureNote
        override fun displayName() = title.ifBlank { "Untitled" }
        override fun subtitle() = if (protectItem) "Protected" else content.take(40)
        override fun collectionId() = collectionId
    }

    @Serializable
    @SerialName("card")
    data class Card(
        val itemName: String = "",
        val accountHolder: String = "",
        val number: String = "",
        val securityCode: String = "",
        val expiryDate: String = "",
        val note: String = "",
        val color: String = "blue",
    ) : VaultPayload() {
        override val kind get() = VaultItemKind.Payment
        override fun displayName() = itemName.ifBlank { "Untitled" }
        override fun subtitle(): String {
            val digits = number.filter { it.isDigit() }
            val last4 = digits.takeLast(4)
            return if (last4.isEmpty()) "Card" else "•••• $last4"
        }
        override fun collectionId() = null
    }

    @Serializable
    @SerialName("bankAccount")
    data class BankAccount(
        val itemName: String = "",
        val accountHolder: String = "",
        val accountNumber: String = "",
        val routingNumber: String = "",
        val countryCode: String = "IQ",
        val bankName: String = "",
    ) : VaultPayload() {
        override val kind get() = VaultItemKind.Payment
        override fun displayName() = itemName.ifBlank { "Untitled" }
        override fun subtitle() = bankName.ifBlank { accountNumber }
        override fun collectionId() = null
    }

    @Serializable
    @SerialName("name")
    data class Name(
        // JSON key must not be "type": that is the sealed-class discriminator, and the clash
        // made every Name/Email/Phone save throw. Values are the iOS raw values.
        @SerialName("honorific") val type: String = "None of these",
        val firstName: String = "",
        val lastName: String = "",
        val middleName: String = "",
        val defaultUsername: String = "",
        val birthDate: String = "",
        val birthPlace: String = "",
    ) : VaultPayload() {
        override val kind get() = VaultItemKind.PersonalInfo
        override fun displayName() = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Untitled" }
        override fun subtitle() = defaultUsername
        override fun collectionId() = null
    }

    @Serializable
    @SerialName("personalEmail")
    data class PersonalEmail(
        val itemName: String = "",
        @SerialName("emailType") val type: String = "Personal",
        val email: String = "",
    ) : VaultPayload() {
        override val kind get() = VaultItemKind.PersonalInfo
        override fun displayName() = itemName.ifBlank { email.ifBlank { "Untitled" } }
        override fun subtitle() = email
        override fun collectionId() = null
    }

    @Serializable
    @SerialName("phone")
    data class Phone(
        val itemName: String = "",
        @SerialName("phoneType") val type: String = "Cell",
        val countryCode: String = "IQ",
        val number: String = "",
    ) : VaultPayload() {
        override val kind get() = VaultItemKind.PersonalInfo
        override fun displayName() = itemName.ifBlank { number.ifBlank { "Untitled" } }
        override fun subtitle() = number
        override fun collectionId() = null
    }

    @Serializable
    @SerialName("address")
    data class Address(
        val itemName: String = "",
        val address: String = "",
        val city: String = "",
        val zipCode: String = "",
        val countryCode: String = "IQ",
        val recipient: String = "",
        val building: String = "",
        val floor: String = "",
        val apartment: String = "",
        val doorCode: String = "",
        val phoneItemId: String? = null,
    ) : VaultPayload() {
        override val kind get() = VaultItemKind.PersonalInfo
        override fun displayName() = itemName.ifBlank { "Untitled" }
        override fun subtitle() = listOf(city, countryCode).filter { it.isNotBlank() }.joinToString(", ")
        override fun collectionId() = null
    }

    @Serializable
    @SerialName("company")
    data class Company(
        val companyName: String = "",
        val jobTitle: String = "",
    ) : VaultPayload() {
        override val kind get() = VaultItemKind.PersonalInfo
        override fun displayName() = companyName.ifBlank { "Untitled" }
        override fun subtitle() = jobTitle
        override fun collectionId() = null
    }

    @Serializable
    @SerialName("website")
    data class Website(
        val itemName: String = "",
        val websiteURL: String = "",
    ) : VaultPayload() {
        override val kind get() = VaultItemKind.PersonalInfo
        override fun displayName() = itemName.ifBlank { "Untitled" }
        override fun subtitle() = websiteURL
        override fun collectionId() = null
    }

    @Serializable
    @SerialName("idCard")
    data class IdCard(
        val name: String = "",
        val countryCode: String = "IQ",
        val number: String = "",
        val issueDate: String = "",
        val expiryDate: String = "",
        val issuePlace: String = "",
    ) : VaultPayload() {
        override val kind get() = VaultItemKind.IdDocument
        override fun displayName() = name.ifBlank { "Untitled" }
        override fun subtitle() = number
        override fun collectionId() = null
    }

    @Serializable
    @SerialName("passport")
    data class Passport(
        val name: String = "",
        val countryCode: String = "IQ",
        val number: String = "",
        val issueDate: String = "",
        val expiryDate: String = "",
        val issuePlace: String = "",
    ) : VaultPayload() {
        override val kind get() = VaultItemKind.IdDocument
        override fun displayName() = name.ifBlank { "Untitled" }
        override fun subtitle() = number
        override fun collectionId() = null
    }

    @Serializable
    @SerialName("driverLicense")
    data class DriverLicense(
        val name: String = "",
        val countryCode: String = "IQ",
        val number: String = "",
        val issueDate: String = "",
        val expiryDate: String = "",
        val issuePlace: String = "",
    ) : VaultPayload() {
        override val kind get() = VaultItemKind.IdDocument
        override fun displayName() = name.ifBlank { "Untitled" }
        override fun subtitle() = number
        override fun collectionId() = null
    }
}

object VaultJson {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
    }

    fun encodePayload(payload: VaultPayload): ByteArray =
        json.encodeToString(VaultPayload.serializer(), payload).toByteArray(Charsets.UTF_8)

    fun decodePayload(bytes: ByteArray): VaultPayload =
        json.decodeFromString(VaultPayload.serializer(), bytes.toString(Charsets.UTF_8))

    fun encodeEnvelope(env: VaultEnvelope): ByteArray =
        json.encodeToString(VaultEnvelope.serializer(), env).toByteArray(Charsets.UTF_8)

    fun decodeEnvelope(bytes: ByteArray): VaultEnvelope =
        json.decodeFromString(VaultEnvelope.serializer(), bytes.toString(Charsets.UTF_8))
}
