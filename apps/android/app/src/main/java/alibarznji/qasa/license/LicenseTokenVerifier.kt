package alibarznji.qasa.license

import alibarznji.qasa.vault.VaultSecureStore
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Where the fetched verifying key lives between runs. */
interface LicenseKeyCache {
    fun read(): String?
    fun write(base64: String)
    fun clear()
}

class StoreLicenseKeyCache @Inject constructor(
    private val store: VaultSecureStore,
) : LicenseKeyCache {
    override fun read(): String? = store.licenseVerifyingKey
    override fun write(base64: String) {
        store.licenseVerifyingKey = base64
    }

    override fun clear() {
        store.licenseVerifyingKey = null
    }
}

/**
 * Ed25519 verification for server-issued license tokens.
 *
 * Previously the client decoded the JWT payload and trusted it verbatim, so anyone could
 * hand-write `{"tier":"premium"}` and unlock breach monitoring, VPN and LAN sync offline
 * with no server contact at all.
 *
 * The verifying key is pinned at build time when `QASA_LICENSE_PUBLIC_KEY` is supplied,
 * and otherwise fetched once from `/.well-known/qasa-license-key` and cached. A pinned key
 * always wins, so a server-side key rotation is surfaced instead of silently trusted.
 */
@Singleton
class LicenseTokenVerifier(
    private val cache: LicenseKeyCache,
    private val pinnedKeyB64: String?,
) {
    @Inject
    constructor(store: VaultSecureStore) : this(
        cache = StoreLicenseKeyCache(store),
        pinnedKeyB64 = pinnedKeyFromBuildConfig(),
    )

    private val pinned = pinnedKeyB64?.trim()?.takeIf { it.isNotEmpty() }

    fun pinnedKeyPresent(): Boolean = pinned != null

    fun cachedVerifyingKey(): ByteArray? {
        pinned?.let { return decodeBase64(it) }
        val cached = cache.read() ?: return null
        return decodeBase64(cached)
    }

    fun cacheVerifyingKey(base64: String) {
        // Never overwrite a pinned key: the pinned one is the only one we trust, and
        // learning a new key from the server would defeat it.
        if (pinned != null) return
        // A key that is not exactly 32 raw bytes can never verify anything, so caching it
        // would only poison the cache and make every later token fail with no way back.
        if (decodeBase64(base64)?.size != ED25519_KEY_BYTES) return
        cache.write(base64)
    }

    fun clearCachedVerifyingKey() = cache.clear()

    /**
     * Verifies signature and expiry, then decodes the claims.
     *
     * Fails closed: any structural problem, a missing key, a non-EdDSA algorithm
     * (including the classic `alg: none` downgrade) or a bad signature yields `null`.
     */
    fun parse(token: String): ParsedLicense? {
        val parts = token.split(".")
        if (parts.size != 3) return null
        if (parts[0].isEmpty() || parts[1].isEmpty() || parts[2].isEmpty()) return null

        // Reject the classic downgrade before doing anything else.
        val header = decodeObject(parts[0]) ?: return null
        if (header["alg"]?.jsonPrimitive?.contentOrNull != "EdDSA") return null

        val signature = decodeBase64Url(parts[2]) ?: return null
        if (signature.size != Ed25519.SIGNATURE_SIZE) return null

        val verifyingKey = cachedVerifyingKey() ?: return null
        if (verifyingKey.size != Ed25519.KEY_SIZE) return null

        val signingInput = "${parts[0]}.${parts[1]}".toByteArray(StandardCharsets.UTF_8)
        if (!Ed25519.verify(publicKey = verifyingKey, message = signingInput, signature = signature)) {
            return null
        }

        // Signature is valid — now decode the claims.
        val payload = decodeObject(parts[1]) ?: return null
        val sub = payload.string("sub") ?: return null
        val did = payload.string("did") ?: return null
        val tier = payload.string("tier") ?: return null
        val exp = payload["exp"]?.jsonPrimitive?.longOrNull ?: return null
        if (exp <= System.currentTimeMillis() / 1000) return null

        val entitlements = payload["entitlements"]
            ?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            .orEmpty()

        return ParsedLicense(
            accountId = sub,
            deviceId = did,
            tier = tier,
            deviceLimit = (payload["device_limit"]?.jsonPrimitive?.intOrNull ?: 1).coerceAtLeast(1),
            expiresAtEpoch = exp,
            entitlements = entitlements,
        )
    }

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }

    private fun decodeObject(segment: String): JsonObject? {
        val bytes = decodeBase64Url(segment) ?: return null
        return try {
            json.parseToJsonElement(String(bytes, StandardCharsets.UTF_8)).jsonObject
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeBase64(value: String): ByteArray? = try {
        Base64.getDecoder().decode(value)
    } catch (_: Exception) {
        null
    }

    private fun decodeBase64Url(value: String): ByteArray? = try {
        Base64.getUrlDecoder().decode(value)
    } catch (_: Exception) {
        null
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }

        /** Raw Ed25519 public key length; anything else can never verify a signature. */
        const val ED25519_KEY_BYTES = 32

        fun pinnedKeyFromBuildConfig(): String? = alibarznji.qasa.BuildConfig.QASA_LICENSE_PUBLIC_KEY
    }
}
