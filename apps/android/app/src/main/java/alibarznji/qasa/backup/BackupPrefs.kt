package alibarznji.qasa.backup

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Automatic backup frequency; [wire] is the value `backupIsDue` takes. */
enum class BackupFrequency(val wire: String) {
    Off("off"),
    Daily("daily"),
    Weekly("weekly"),
    Monthly("monthly"),
    ;

    companion object {
        fun fromWire(value: String?): BackupFrequency = entries.find { it.wire == value } ?: Off
    }
}

/** This device's backup settings. Nothing here is secret: the file itself is encrypted. */
data class BackupSettings(
    /** The SAF document the backups overwrite (a persisted `content://` URI). */
    val locationUri: String? = null,
    /** The document's display name, shown on the Backups screen. */
    val locationName: String? = null,
    val frequency: BackupFrequency = BackupFrequency.Off,
    val lastBackupMs: Long? = null,
    /** The last backup attempt failed; cleared by the next success or a new location. */
    val failed: Boolean = false,
    val snoozedUntilMs: Long? = null,
)

/**
 * Per-device backup preferences, in their own SharedPreferences file (like
 * [alibarznji.qasa.prefs.AppPreferences]) so Clear Local Data can drop all of them at once.
 */
@Singleton
class BackupPrefs internal constructor(private val prefs: SharedPreferences) {

    @Inject
    constructor(@ApplicationContext context: Context) :
        this(context.getSharedPreferences(FILE, Context.MODE_PRIVATE))

    private val _state = MutableStateFlow(read())
    val state: StateFlow<BackupSettings> = _state.asStateFlow()

    fun setLocation(uri: String, name: String?) {
        prefs.edit()
            .putString(KEY_URI, uri)
            .apply { if (name == null) remove(KEY_NAME) else putString(KEY_NAME, name) }
            .putBoolean(KEY_FAILED, false)
            .apply()
        publish()
    }

    fun setFrequency(frequency: BackupFrequency) {
        prefs.edit().putString(KEY_FREQUENCY, frequency.wire).apply()
        publish()
    }

    fun recordSuccess(atMs: Long) {
        prefs.edit().putLong(KEY_LAST, atMs).putBoolean(KEY_FAILED, false).apply()
        publish()
    }

    fun recordFailure() {
        prefs.edit().putBoolean(KEY_FAILED, true).apply()
        publish()
    }

    fun snoozeReminder(untilMs: Long) {
        prefs.edit().putLong(KEY_SNOOZED, untilMs).apply()
        publish()
    }

    /** Local data reset: forget every backup setting. The backup file itself is kept. */
    fun clear() {
        prefs.edit().clear().apply()
        publish()
    }

    private fun publish() {
        _state.value = read()
    }

    private fun read() = BackupSettings(
        locationUri = prefs.getString(KEY_URI, null),
        locationName = prefs.getString(KEY_NAME, null),
        frequency = BackupFrequency.fromWire(prefs.getString(KEY_FREQUENCY, null)),
        lastBackupMs = prefs.getLong(KEY_LAST, 0L).takeIf { prefs.contains(KEY_LAST) },
        failed = prefs.getBoolean(KEY_FAILED, false),
        snoozedUntilMs = prefs.getLong(KEY_SNOOZED, 0L).takeIf { prefs.contains(KEY_SNOOZED) },
    )

    companion object {
        const val FILE = "qasa.backup"
        private const val KEY_URI = "qasa.backup.locationUri"
        private const val KEY_NAME = "qasa.backup.locationName"
        private const val KEY_FREQUENCY = "qasa.backup.frequency"
        private const val KEY_LAST = "qasa.backup.lastBackupMs"
        private const val KEY_FAILED = "qasa.backup.failed"
        private const val KEY_SNOOZED = "qasa.backup.snoozedUntilMs"
    }
}
