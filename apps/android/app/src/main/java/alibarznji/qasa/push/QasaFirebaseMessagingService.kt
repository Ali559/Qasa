package alibarznji.qasa.push

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import alibarznji.qasa.MainActivity
import alibarznji.qasa.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * FCM entrypoint — stores and uploads the token (PATCH /v1/devices/prefs) and opens Notifications on tap.
 * Without google-services.json Firebase never initializes; this service simply never receives tokens.
 */
@AndroidEntryPoint
class QasaFirebaseMessagingService : FirebaseMessagingService() {
    @Inject lateinit var pushTokenStore: PushTokenStore
    @Inject lateinit var devicePrefs: DevicePrefsSync

    /** FCM rotated the token: keep it and upload it now (no-op until a license token exists). */
    override fun onNewToken(token: String) {
        pushTokenStore.token = token
        devicePrefs.uploadPushToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: "Qasa"
        val body = message.notification?.body ?: message.data["body"] ?: ""
        val open = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_NOTIFICATIONS, true)
        }
        val pending = PendingIntent.getActivity(
            this,
            0,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, PushChannels.DEFAULT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        try {
            NotificationManagerCompat.from(this).notify(message.messageId?.hashCode() ?: title.hashCode(), notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted
        }
        // Navigation happens only when the user taps the notification (the PendingIntent →
        // MainActivity.handleOpenIntent), as on iOS. Calling requestOpenNotifications() here
        // yanked a user who was mid-task to the Notifications page on every incoming push.
    }
}
