package alibarznji.qasa.push

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores FCM token when Firebase is configured (google-services.json present).
 * Without Firebase, token remains null — same graceful degrade as iOS without GoogleService-Info.
 */
@Singleton
class PushTokenStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("qasa.fcm", Context.MODE_PRIVATE)

    var token: String?
        get() = prefs.getString("token", null)
        set(value) = prefs.edit().putString("token", value).apply()
}
