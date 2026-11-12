package alibarznji.qasa.prefs

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppLanguage(val code: String) {
    EN("en"), CKB("ckb"), AR("ar");

    /**
     * Whether the language is written right to left. Decided here, not from
     * `TextUtils.getLayoutDirectionFromLocale`: on Android 12 and older the bundled ICU
     * may not know the bare `ckb` tag's script and reports Sorani as left to right.
     */
    val isRtl: Boolean get() = this != EN
    companion object {
        fun fromCode(code: String) = entries.find { it.code == code } ?: EN
    }
}

enum class AppThemeMode { SYSTEM, LIGHT, DARK }

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("qasa.prefs", Context.MODE_PRIVATE)

    private val _language = MutableStateFlow(storedLanguage(prefs))
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    private val _theme = MutableStateFlow(
        AppThemeMode.valueOf(prefs.getString(KEY_THEME, AppThemeMode.SYSTEM.name) ?: AppThemeMode.SYSTEM.name),
    )
    val themeMode: StateFlow<AppThemeMode> = _theme.asStateFlow()

    private val _autoLockMinutes = MutableStateFlow(prefs.getInt(KEY_AUTO_LOCK, 5))
    val autoLockMinutes: StateFlow<Int> = _autoLockMinutes.asStateFlow()

    // VPN preferences (premium page only). On Android the kill switch can only be enforced
    // by the system's Always-on VPN setting, so the VPN page guides the user there;
    // auto-connect is acted on by `VpnAutoConnector`.
    private val _vpnKillSwitch = MutableStateFlow(prefs.getBoolean(KEY_VPN_KILL_SWITCH, true))
    val vpnKillSwitch: StateFlow<Boolean> = _vpnKillSwitch.asStateFlow()

    private val _vpnAutoConnect = MutableStateFlow(prefs.getBoolean(KEY_VPN_AUTO_CONNECT, false))
    val vpnAutoConnect: StateFlow<Boolean> = _vpnAutoConnect.asStateFlow()

    fun setVpnKillSwitch(on: Boolean) {
        prefs.edit().putBoolean(KEY_VPN_KILL_SWITCH, on).apply()
        _vpnKillSwitch.value = on
    }

    fun setVpnAutoConnect(on: Boolean) {
        prefs.edit().putBoolean(KEY_VPN_AUTO_CONNECT, on).apply()
        _vpnAutoConnect.value = on
    }

    /** Server chosen in the region picker; null means let the server pick. */
    private val _vpnNodeId = MutableStateFlow(prefs.getString(KEY_VPN_NODE_ID, null))
    val vpnNodeId: StateFlow<String?> = _vpnNodeId.asStateFlow()

    fun setVpnNodeId(id: String?) {
        prefs.edit().putString(KEY_VPN_NODE_ID, id).apply()
        _vpnNodeId.value = id
    }

    /**
     * Autofill asks for biometrics or the master password before every login and profile
     * fill, not only when the vault is locked (docs/autofill-plan.md §1.3). Cards always ask.
     */
    private val _autofillConfirmEveryFill = MutableStateFlow(prefs.getBoolean(KEY_AUTOFILL_CONFIRM, false))
    val autofillConfirmEveryFill: StateFlow<Boolean> = _autofillConfirmEveryFill.asStateFlow()

    fun setAutofillConfirmEveryFill(on: Boolean) {
        prefs.edit().putBoolean(KEY_AUTOFILL_CONFIRM, on).apply()
        _autofillConfirmEveryFill.value = on
    }

    /** Epoch ms until which Home doesn't ask to turn on Qasa autofill (Not now). */
    var autofillPromptSnoozedUntil: Long
        get() = prefs.getLong(KEY_AUTOFILL_PROMPT_SNOOZE, 0L)
        set(value) = prefs.edit().putLong(KEY_AUTOFILL_PROMPT_SNOOZE, value).apply()

    /**
     * The name picked on the first-launch name screen or in Settings (iOS
     * `AppPreferences.userName`). Kept trimmed, on this device only; empty means none.
     */
    private val _userName = MutableStateFlow(normalizedUserName(prefs.getString(KEY_USER_NAME, null).orEmpty()))
    val userName: StateFlow<String> = _userName.asStateFlow()

    fun setUserName(name: String) {
        val trimmed = normalizedUserName(name)
        prefs.edit().putString(KEY_USER_NAME, trimmed).apply()
        _userName.value = trimmed
    }

    var hasCompletedWelcome: Boolean
        get() = prefs.getBoolean(KEY_WELCOME, false)
        set(value) = prefs.edit().putBoolean(KEY_WELCOME, value).apply()

    /** The POST_NOTIFICATIONS system prompt has been launched once (Android 13+). */
    var notificationPermissionRequested: Boolean
        get() = prefs.getBoolean(KEY_NOTIF_REQUESTED, false)
        set(value) = prefs.edit().putBoolean(KEY_NOTIF_REQUESTED, value).apply()

    /** Epoch ms of the last notification prompt or settings offer; 0 if never. */
    var notificationPromptedAt: Long
        get() = prefs.getLong(KEY_NOTIF_PROMPTED_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_NOTIF_PROMPTED_AT, value).apply()

    /** The `notifications_enabled` value the server last accepted; `null` if never sent. */
    var lastSyncedNotificationsEnabled: Boolean?
        get() = if (prefs.contains(KEY_NOTIF_SYNCED)) prefs.getBoolean(KEY_NOTIF_SYNCED, false) else null
        set(value) = prefs.edit().apply {
            if (value == null) remove(KEY_NOTIF_SYNCED) else putBoolean(KEY_NOTIF_SYNCED, value)
        }.apply()

    fun setLanguage(lang: AppLanguage) {
        prefs.edit().putString(KEY_LANG, lang.code).apply()
        _language.value = lang
    }

    fun setThemeMode(mode: AppThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _theme.value = mode
    }

    fun setAutoLockMinutes(minutes: Int) {
        val clamped = minutes.coerceIn(1, 60)
        prefs.edit().putInt(KEY_AUTO_LOCK, clamped).apply()
        _autoLockMinutes.value = clamped
    }

    fun resetWelcome() {
        hasCompletedWelcome = false
    }

    companion object {
        /** The stored form of a typed name: surrounding spaces and newlines removed. */
        fun normalizedUserName(raw: String): String = raw.trim()

        /**
         * Read before Hilt injects anything: `MainActivity.attachBaseContext` needs the
         * language to build the localized context the whole UI resolves strings from.
         */
        fun storedLanguage(context: Context): AppLanguage =
            storedLanguage(context.getSharedPreferences("qasa.prefs", Context.MODE_PRIVATE))

        private fun storedLanguage(prefs: SharedPreferences): AppLanguage =
            prefs.getString(KEY_LANG, null)?.let(AppLanguage::fromCode) ?: inferredLanguage()

        /** Best-effort first pick from the device language list (Kurdish / Arabic / English), as on iOS. */
        private fun inferredLanguage(): AppLanguage {
            val tags = android.os.LocaleList.getDefault().toLanguageTags().split(",")
            for (tag in tags) {
                val lower = tag.lowercase()
                if (lower.startsWith("ckb") || lower.startsWith("ku")) return AppLanguage.CKB
                if (lower.startsWith("ar")) return AppLanguage.AR
                if (lower.startsWith("en")) return AppLanguage.EN
            }
            return AppLanguage.EN
        }

        private const val KEY_LANG = "qasa.language"
        private const val KEY_THEME = "qasa.theme"
        private const val KEY_AUTO_LOCK = "qasa.autoLockMinutes"
        private const val KEY_WELCOME = "qasa.welcomeCompleted"
        private const val KEY_USER_NAME = "qasa.userName"
        private const val KEY_AUTOFILL_CONFIRM = "qasa.autofill.confirmEveryFill"
        private const val KEY_AUTOFILL_PROMPT_SNOOZE = "qasa.autofill.promptSnoozedUntil"
        private const val KEY_VPN_KILL_SWITCH = "qasa.vpn.killSwitch"
        private const val KEY_VPN_AUTO_CONNECT = "qasa.vpn.autoConnect"
        private const val KEY_VPN_NODE_ID = "qasa.vpn.nodeId"
        private const val KEY_NOTIF_REQUESTED = "qasa.notifications.permissionRequested"
        private const val KEY_NOTIF_PROMPTED_AT = "qasa.notifications.promptedAt"
        private const val KEY_NOTIF_SYNCED = "qasa.notifications.lastSyncedEnabled"
    }
}
