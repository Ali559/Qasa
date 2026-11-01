package alibarznji.qasa.push

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches FCM token when Firebase is configured. No-ops without google-services.json
 * (parity with iOS without GoogleService-Info.plist).
 */
@Singleton
class PushTokenRegistrar @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: PushTokenStore,
    private val devicePrefs: DevicePrefsSync,
) {
    /**
     * The token arrives asynchronously, often after unlock's `syncAll` already ran, so a
     * token that differs from the stored one is uploaded here too.
     */
    fun refreshTokenIfAvailable() {
        if (FirebaseApp.getApps(context).isEmpty()) return
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                if (token.isNullOrBlank() || token == store.token) return@addOnSuccessListener
                store.token = token
                devicePrefs.uploadPushToken(token)
            }
            .addOnFailureListener { /* keep prior token */ }
    }
}
