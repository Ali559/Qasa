package alibarznji.qasa

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import alibarznji.qasa.notifications.NotificationService
import alibarznji.qasa.prefs.AppLanguage
import alibarznji.qasa.prefs.AppPreferences
import alibarznji.qasa.prefs.LocaleContext
import alibarznji.qasa.screens.AppSession
import alibarznji.qasa.screens.RootNav
import alibarznji.qasa.services.AppBootstrap
import alibarznji.qasa.services.AutoLockMonitor
import alibarznji.qasa.theme.LocalUserActivity
import alibarznji.qasa.theme.QasaTheme
import alibarznji.qasa.theme.UserActivitySink
import androidx.compose.runtime.CompositionLocalProvider
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
// `BiometricPrompt` needs a `FragmentActivity`; `ComponentActivity` is not one, so the
// whole app's activity hosts have to extend it or the gate can never open (§6.1).
class MainActivity : FragmentActivity() {
    @Inject lateinit var prefs: AppPreferences
    @Inject lateinit var autoLock: AutoLockMonitor
    @Inject lateinit var appSession: AppSession
    @Inject lateinit var bootstrap: AppBootstrap
    @Inject lateinit var notifications: NotificationService

    /** One instance, so the static local never invalidates the tree. */
    private val userActivity = UserActivitySink { autoLock.noteActivity() }

    /** The language the current resources were built with; a different pick recreates. */
    private lateinit var appliedLanguage: AppLanguage

    override fun attachBaseContext(newBase: Context) {
        appliedLanguage = AppPreferences.storedLanguage(newBase)
        super.attachBaseContext(LocaleContext.wrap(newBase, appliedLanguage))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Recents thumbnails, screenshots, screen recording and the task-switcher preview
        // all read the window surface; without this every vault screen is captured.
        // TODO: re-enable before release. Turned off temporarily so screens can be
        // screenshotted for the UI redesign.
        // window.setFlags(
        //     WindowManager.LayoutParams.FLAG_SECURE,
        //     WindowManager.LayoutParams.FLAG_SECURE,
        // )
        handleOpenIntent(intent)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                prefs.language.collect { lang ->
                    if (lang != appliedLanguage) {
                        bootstrap.onLanguageChanged()
                        recreate()
                    }
                }
            }
        }
        enableEdgeToEdge()
        setContent {
            val theme by prefs.themeMode.collectAsState()
            val language by prefs.language.collectAsState()
            // Sheets and the IME bypass onUserInteraction below; they report through this.
            CompositionLocalProvider(LocalUserActivity provides userActivity) {
                QasaTheme(themeMode = theme, language = language) {
                    RootNav()
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleOpenIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        // Premium may have ended (or renewed) while the app was in the background.
        bootstrap.onAppForeground()
    }

    override fun onResume() {
        super.onResume()
        autoLock.onAppForeground()
        autoLock.noteActivity()
        // After the auto-lock check, so a vault that just locked is not backed up.
        bootstrap.onAppResumed()
        // Unread badge on foreground (silent without a license token).
        lifecycleScope.launch { notifications.refreshUnreadCount() }
        // Back from system settings (or the permission prompt) the OS switch may differ.
        bootstrap.onNotificationStateMayHaveChanged()
    }

    override fun onPause() {
        autoLock.onAppBackground()
        super.onPause()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        autoLock.noteActivity()
    }

    private fun handleOpenIntent(intent: android.content.Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_NOTIFICATIONS, false) == true) {
            appSession.requestOpenNotifications()
        }
    }

    companion object {
        const val EXTRA_OPEN_NOTIFICATIONS = "alibarznji.qasa.OPEN_NOTIFICATIONS"
    }
}
