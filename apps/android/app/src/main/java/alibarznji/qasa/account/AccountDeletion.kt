package alibarznji.qasa.account

import alibarznji.qasa.config.QasaConfig
import alibarznji.qasa.network.QasaApiClient
import alibarznji.qasa.services.LocalDataEraser
import alibarznji.qasa.vault.VaultSecureStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Body of `DELETE /v1/account` (server `account.rs` `DeleteAccountRequest`). */
@Serializable
data class DeleteAccountRequest(val confirm_account_id: String)

/** One HTTP call, kept as data so tests pin the method, path and body. */
data class AccountDeletionCall(val method: String, val url: String, val bodyJson: String)

object AccountDeletionRequest {
    const val METHOD = "DELETE"
    const val PATH = "/v1/account"

    /**
     * The server deletes nothing unless the body repeats the account id the license token
     * belongs to (desktop `account_delete` sends the same body).
     */
    fun build(apiBaseUrl: String, accountId: String): AccountDeletionCall = AccountDeletionCall(
        method = METHOD,
        url = apiBaseUrl.trimEnd('/') + PATH,
        bodyJson = Json.encodeToString(DeleteAccountRequest.serializer(), DeleteAccountRequest(accountId)),
    )
}

sealed interface AccountDeletionResult {
    data object Deleted : AccountDeletionResult
    data object NoAccount : AccountDeletionResult
    data class Failed(val cause: Exception) : AccountDeletionResult
}

/** What [deleteAccount] needs from the app; a fake in tests. */
interface AccountDeletionPorts {
    fun accountId(): String?

    /** Sends [call] authenticated like the other account calls; throws on any non-2xx. */
    suspend fun send(call: AccountDeletionCall)

    /** Forgets the identity (account id, license, device fingerprint) and erases the vault. */
    suspend fun eraseLocal()
}

/**
 * Deletes the account on the server, then everything on this phone. Local data is only
 * touched once the server confirmed: a failed call leaves the vault and identity as they
 * were, so the user can retry.
 */
suspend fun deleteAccount(ports: AccountDeletionPorts, apiBaseUrl: String): AccountDeletionResult {
    val accountId = ports.accountId() ?: return AccountDeletionResult.NoAccount
    try {
        ports.send(AccountDeletionRequest.build(apiBaseUrl, accountId))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        return AccountDeletionResult.Failed(e)
    }
    ports.eraseLocal()
    return AccountDeletionResult.Deleted
}

@Singleton
class AccountDeletionService @Inject constructor(
    private val api: QasaApiClient,
    private val store: VaultSecureStore,
    private val eraser: LocalDataEraser,
) : AccountDeletionPorts {

    /** An account the server knows: an id and the license token it issued at registration. */
    val isRegistered: Boolean
        get() = store.accountId != null && store.licenseToken != null

    override fun accountId(): String? = store.accountId

    override suspend fun send(call: AccountDeletionCall) {
        withContext(Dispatchers.IO) {
            api.raw(call.url, call.method, call.bodyJson, authorized = true)
        }
    }

    override suspend fun eraseLocal() = eraser.eraseAll()

    suspend fun delete(): AccountDeletionResult = deleteAccount(this, QasaConfig.apiBaseUrl)
}
