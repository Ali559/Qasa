package alibarznji.qasa.device

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
import kotlinx.coroutines.withContext

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
    var lastLimitError: DeviceLimitErrorBody? = null
        private set

    suspend fun registerIfNeeded() = withContext(Dispatchers.IO) {
        if (!session.isUnlocked) return@withContext
        val accountId = store.accountId ?: return@withContext
        val fingerprint = store.deviceFingerprint ?: return@withContext
        try {
            val response: RegisterDeviceResponse = api.post(
                QasaConfig.registerUrl,
                RegisterDeviceRequest(
                    account_id = accountId,
                    device_fingerprint = fingerprint,
                    device_name = Build.MODEL,
                    platform = "android",
                    location = null,
                ),
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
            lastLimitError = null
        } catch (e: QasaApiException.DeviceLimitExceeded) {
            lastLimitError = e.body
            devicesUsed = e.body.devices_used ?: e.body.device_limit ?: 1
            deviceLimit = e.body.device_limit ?: 1
        } catch (_: Exception) {
            // Offline unlock remains valid
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
