package alibarznji.qasa.license

import alibarznji.qasa.config.QasaConfig
import alibarznji.qasa.network.LicenseRefreshResponse
import alibarznji.qasa.network.QasaApiClient
import alibarznji.qasa.network.WellKnownLicenseKey
import alibarznji.qasa.network.get
import alibarznji.qasa.vault.VaultSecureStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class ParsedLicense(
    val accountId: String,
    val deviceId: String,
    val tier: String,
    val deviceLimit: Int,
    val expiresAtEpoch: Long,
    val entitlements: List<String>,
) {
    val isPremium: Boolean
        get() = tier.equals("premium", ignoreCase = true) &&
            expiresAtEpoch > System.currentTimeMillis() / 1000
}

@Singleton
class LicenseManager @Inject constructor(
    private val store: VaultSecureStore,
    private val api: QasaApiClient,
    private val verifier: LicenseTokenVerifier,
) {
    private val _parsed = MutableStateFlow(store.licenseToken?.let { verifier.parse(it) })
    val parsed: StateFlow<ParsedLicense?> = _parsed.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    val isPremium: Boolean get() = _parsed.value?.isPremium == true
    val deviceLimit: Int get() = _parsed.value?.deviceLimit ?: 1
    val tierLabel: String get() = _parsed.value?.tier?.replaceFirstChar { it.uppercase() } ?: "Free"

    /**
     * Returns false when the token could not be verified, in which case nothing is stored
     * and the previously verified license (if any) stays in effect.
     */
    fun applyToken(token: String): Boolean {
        val verified = verifier.parse(token)
        if (verified == null) {
            // Never persist a token we cannot verify: a bad token must not replace a good
            // one, and must not become the stored source of truth.
            _lastError.value = ERROR_TOKEN_REJECTED
            return false
        }
        store.licenseToken = token
        _parsed.value = verified
        _lastError.value = null
        return true
    }

    fun clear() {
        store.licenseToken = null
        _parsed.value = null
        _lastError.value = null
    }

    /**
     * Ensures a verifying key is available, then re-parses any stored token.
     *
     * Must run before [applyToken] is trusted, and before the UI reads [isPremium],
     * because verification fails closed without a key.
     *
     * The key is fetched once and then trusted from the cache, the same trust-on-first-use
     * shape the iOS verifier has. Re-fetching on every unlock would let whoever answers the
     * well-known endpoint swap the key out from under an installed build; key rotation is
     * still covered, but only on a signature failure in [refresh].
     */
    suspend fun bootstrapVerifyingKey(): Boolean = withContext(Dispatchers.IO) {
        val hadKey = verifier.cachedVerifyingKey() != null
        if (hadKey) {
            reloadFromStore()
            return@withContext true
        }
        val ok = refreshVerifyingKey()
        reloadFromStore()
        ok
    }

    val hasToken: Boolean get() = store.licenseToken != null

    suspend fun refresh(): Boolean = withContext(Dispatchers.IO) {
        if (store.licenseToken == null) return@withContext false

        // A refresh that ran while the verifying key was unavailable would reject a
        // perfectly valid new token and leave the user paying for premium that never
        // unlocks. Make sure the key exists first.
        if (verifier.cachedVerifyingKey() == null) {
            refreshVerifyingKey()
        }

        try {
            val response: LicenseRefreshResponse = api.get(QasaConfig.licenseRefreshUrl)
            if (applyToken(response.license_token)) return@withContext true
            // A second key attempt covers a key rotated between bootstrap and now.
            refreshVerifyingKey()
            applyToken(response.license_token)
        } catch (e: Exception) {
            _lastError.value = e.message
            false
        }
    }

    fun reloadFromStore() {
        _parsed.value = store.licenseToken?.let { verifier.parse(it) }
    }

    private fun refreshVerifyingKey(): Boolean {
        if (verifier.pinnedKeyPresent()) return verifier.cachedVerifyingKey() != null
        return try {
            val response: WellKnownLicenseKey = api.get(
                QasaConfig.licenseKeyUrl,
                authorized = false,
            )
            if (response.algorithm != "EdDSA") return false
            verifier.cacheVerifyingKey(response.public_key)
            verifier.cachedVerifyingKey() != null
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        const val ERROR_TOKEN_REJECTED = "license_token_rejected"
    }
}
