package alibarznji.qasa.push

import alibarznji.qasa.config.QasaConfig
import alibarznji.qasa.network.DevicePrefsRequest
import alibarznji.qasa.network.QasaApiClient
import alibarznji.qasa.network.patch
import alibarznji.qasa.prefs.AppPreferences
import alibarznji.qasa.vault.VaultSecureStore
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement

/**
 * `PATCH /v1/devices/prefs`. The server applies it as a partial update (an omitted field
 * keeps its stored value), so each caller sends only what it knows changed. Every call
 * needs a license token; without one it is a no-op (the next unlock sends everything).
 */
@Singleton
class DevicePrefsSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: QasaApiClient,
    private val store: VaultSecureStore,
    private val prefs: AppPreferences,
    private val pushTokenStore: PushTokenStore,
) {
    // Outlives callers: FCM's service and a language change that recreates the activity.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Whether this device can show Qasa pushes: the OS switch for the app, which on
     * Android 13+ is also false until POST_NOTIFICATIONS is granted. The app has no
     * notifications setting of its own.
     */
    fun notificationsEnabled(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    // Resume and the permission result land together; serialize so only one PATCH goes out.
    private val enabledMutex = Mutex()

    /** After unlock: locale, the current FCM token (if any) and the real enabled state. */
    suspend fun syncAll() {
        val enabled = notificationsEnabled()
        enabledMutex.withLock {
            val sent = send(
                DevicePrefsRequest(
                    locale = prefs.language.value.code,
                    push_token = pushTokenStore.token,
                    notifications_enabled = enabled,
                ),
            )
            if (sent) prefs.lastSyncedNotificationsEnabled = enabled
        }
    }

    /**
     * App resume or a permission result: the OS switch may have flipped. Sends
     * `notifications_enabled` alone, and only if it differs from what the server last got.
     */
    fun syncNotificationsEnabledIfChanged() {
        scope.launch {
            enabledMutex.withLock {
                val enabled = notificationsEnabled()
                val hasToken = store.licenseToken != null
                if (!shouldSyncNotificationsEnabled(hasToken, enabled, prefs.lastSyncedNotificationsEnabled)) {
                    return@withLock
                }
                if (send(DevicePrefsRequest(notifications_enabled = enabled))) {
                    prefs.lastSyncedNotificationsEnabled = enabled
                }
            }
        }
    }

    /** Language change: locale only, so push token and enabled state stay as stored. */
    fun syncLocale() {
        scope.launch { send(DevicePrefsRequest(locale = prefs.language.value.code)) }
    }

    /** FCM rotated the registration token. */
    fun uploadPushToken(token: String) {
        scope.launch { send(DevicePrefsRequest(push_token = token)) }
    }

    /** `true` once the server accepted [body]; `false` without a token or on any failure. */
    private suspend fun send(body: DevicePrefsRequest): Boolean = withContext(Dispatchers.IO) {
        if (store.licenseToken == null) return@withContext false
        try {
            api.patch<Map<String, JsonElement>, DevicePrefsRequest>(QasaConfig.devicePrefsUrl, body)
            true
        } catch (_: Exception) {
            false
        }
    }
}
