package alibarznji.qasa.device

import alibarznji.qasa.backup.restoreKeyBase64
import alibarznji.qasa.config.QasaConfig
import alibarznji.qasa.license.LicenseManager
import alibarznji.qasa.network.DeviceLinkCheckRequest
import alibarznji.qasa.network.DeviceLinkCheckResponse
import alibarznji.qasa.network.DeviceListResponse
import alibarznji.qasa.network.DeviceLimitErrorBody
import alibarznji.qasa.network.QasaApiClient
import alibarznji.qasa.network.QasaApiException
import alibarznji.qasa.network.RegisterDeviceRequest
import alibarznji.qasa.network.RegisterDeviceResponse
import alibarznji.qasa.network.RemoteDevice
import alibarznji.qasa.network.RestoreKeyStatus
import alibarznji.qasa.network.delete
import alibarznji.qasa.network.get
import alibarznji.qasa.network.post
import alibarznji.qasa.vault.VaultSecureStore
import alibarznji.qasa.vault.VaultSession
import android.os.Build
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The register body. [pendingRestoreKey] is the key of a backup this device restored
 * (sent as base64); [replaceDevice] asks the server to unlink the device that made it.
 */
fun buildRegisterRequest(
    accountId: String,
    fingerprint: String,
    deviceName: String,
    pendingRestoreKey: ByteArray?,
    replaceDevice: Boolean = false,
): RegisterDeviceRequest = RegisterDeviceRequest(
    account_id = accountId,
    device_fingerprint = fingerprint,
    device_name = deviceName,
    platform = "android",
    location = null,
    restore_key = pendingRestoreKey?.let(::restoreKeyBase64),
    replace_device = if (replaceDevice && pendingRestoreKey != null) true else null,
)

@Singleton
class DeviceRegistrationService @Inject constructor(
    private val api: QasaApiClient,
    private val store: VaultSecureStore,
    private val session: VaultSession,
    private val license: LicenseManager,
) {
    var devicesUsed: Int = 0
        private set
    var deviceLimit: Int = 1
        private set
    var currentDeviceId: String? = null
        private set
    private val _limitError = MutableStateFlow<DeviceLimitErrorBody?>(null)

    /** The last 403 `device_limit_exceeded`, with the restore key verdict; null once registered. */
    val limitError: StateFlow<DeviceLimitErrorBody?> = _limitError.asStateFlow()

    val lastLimitError: DeviceLimitErrorBody? get() = _limitError.value

    private val _replacePrompt = MutableStateFlow<RemoteDevice?>(null)

    /** Non-null while the "Replace your old device?" confirmation should show. */
    val replacePrompt: StateFlow<RemoteDevice?> = _replacePrompt.asStateFlow()

    private val _replacedNotice = MutableStateFlow<String?>(null)

    /** The old device's name after a replace succeeded; shown once, then [consumeReplacedNotice]. */
    val replacedNotice: StateFlow<String?> = _replacedNotice.asStateFlow()

    /** Set by a setup restore: the next limit hit with a replaceable key prompts by itself. */
    @Volatile
    private var autoPromptArmed = false

    private val mutex = Mutex()

    /** A setup restore adopted a backup with a restore key. */
    fun armReplacePrompt() {
        autoPromptArmed = true
    }

    /** The banner's button, or the automatic prompt: shows the confirmation if replaceable. */
    fun showReplacePrompt() {
        val err = _limitError.value ?: return
        if (err.restoreKeyStatus == RestoreKeyStatus.Replaceable) _replacePrompt.value = err.replaceable_device
    }

    /** "Not now": the banner in Linked Devices keeps offering it. */
    fun dismissReplacePrompt() {
        _replacePrompt.value = null
    }

    fun consumeReplacedNotice() {
        _replacedNotice.value = null
    }

    suspend fun registerIfNeeded() {
        register(replaceDevice = false)
    }

    /**
     * Registers again with the pending key and `replace_device: true`. True on success,
     * after which the caller continues like a normal successful registration.
     */
    suspend fun replaceOldDevice(): Boolean = register(replaceDevice = true)

    /** True on a 201. A limit error is kept in [limitError]; other failures are offline. */
    private suspend fun register(replaceDevice: Boolean): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!session.isUnlocked) return@withLock false
            val accountId = store.accountId ?: return@withLock false
            val fingerprint = store.deviceFingerprint ?: return@withLock false
            val pending = store.pendingRestoreKey
            if (replaceDevice && pending == null) return@withLock false
            try {
                val response: RegisterDeviceResponse = api.post(
                    QasaConfig.registerUrl,
                    buildRegisterRequest(accountId, fingerprint, Build.MODEL, pending, replaceDevice),
                    authorized = false,
                )
                if (!license.applyToken(response.license_token)) {
                    // Registration succeeded but the token would not verify. Keep the
                    // previous verified license rather than dropping to free, and let the
                    // refresh path retry after a key rotation.
                    Log.w(TAG, "Registration returned a license token that failed verification")
                }
                devicesUsed = response.devices_used
                deviceLimit = response.device_limit
                currentDeviceId = response.device_id
                // The key did its job (or this device had a slot anyway): it is one use.
                if (pending != null) store.pendingRestoreKey = null
                autoPromptArmed = false
                _replacePrompt.value = null
                _limitError.value = null
                response.replaced_device?.let { _replacedNotice.value = it.device_name }
                true
            } catch (e: QasaApiException.DeviceLimitExceeded) {
                _limitError.value = e.body
                devicesUsed = e.body.devices_used ?: e.body.device_limit ?: 1
                deviceLimit = e.body.device_limit ?: 1
                if (autoPromptArmed && e.body.restoreKeyStatus == RestoreKeyStatus.Replaceable) {
                    autoPromptArmed = false
                    _replacePrompt.value = e.body.replaceable_device
                }
                false
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                // Offline unlock remains valid
                false
            }
        }
    }

    suspend fun fetchDevices(): List<RemoteDevice> = withContext(Dispatchers.IO) {
        api.get<DeviceListResponse>(QasaConfig.devicesUrl).devices
    }

    suspend fun unlinkDevice(id: String) = withContext(Dispatchers.IO) {
        api.deleteEmpty("${QasaConfig.devicesUrl}/$id")
    }

    suspend fun unlinkOtherDevices(): List<RemoteDevice> = withContext(Dispatchers.IO) {
        api.deleteEmpty(QasaConfig.devicesOthersUrl)
        fetchDevices()
    }

    suspend fun checkLinkEligibility(accountId: String): DeviceLinkCheckResponse = withContext(Dispatchers.IO) {
        api.post(
            QasaConfig.devicesLinkCheckUrl,
            DeviceLinkCheckRequest(account_id = accountId.lowercase()),
            authorized = false,
        )
    }

    companion object {
        private const val TAG = "DeviceRegistration"
    }
}
