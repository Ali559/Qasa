package alibarznji.qasa.backup

import alibarznji.qasa.R
import alibarznji.qasa.config.QasaConfig
import alibarznji.qasa.network.QasaApiClient
import alibarznji.qasa.sync.VaultSyncStore
import alibarznji.qasa.vault.VaultRepository
import alibarznji.qasa.vault.VaultSecureStore
import alibarznji.qasa.vault.VaultSession
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.annotation.StringRes
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileOutputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import uniffi.qasa_ffi.FfiBackupException
import uniffi.qasa_ffi.FfiBackupInfo
import uniffi.qasa_ffi.FfiRestoreOutcome
import uniffi.qasa_ffi.backupCreate
import uniffi.qasa_ffi.backupFileName
import uniffi.qasa_ffi.backupInfo
import uniffi.qasa_ffi.backupIsDue
import uniffi.qasa_ffi.backupNewRestoreKey
import uniffi.qasa_ffi.backupRestoreKeyHash
import uniffi.qasa_ffi.backupReminderSnoozeMs
import uniffi.qasa_ffi.backupRestore
import uniffi.qasa_ffi.backupShouldRemind

/** Outcome of one backup run. */
enum class BackupRunResult { Done, NoLocation, Locked, Failed, Busy }

/** The message for a failed restore or an unreadable backup file. */
@StringRes
fun backupErrorMessage(e: Throwable): Int = when (e) {
    is FfiBackupException.NotABackup -> R.string.backup_error_notabackup
    is FfiBackupException.NewerVersion -> R.string.backup_error_newerversion
    is FfiBackupException.WrongPassword -> R.string.backup_error_wrongpassword
    is FfiBackupException.DifferentVault -> R.string.backup_error_differentvault
    is FfiBackupException.NoVault -> R.string.backup_error_novault
    // A local store failure or a file that could not be read at all.
    else -> R.string.backup_error_unreadable
}

/** The message after a successful restore into an existing vault. */
data class RestoreMessage(@StringRes val res: Int, val count: Int? = null)

fun mergedMessage(outcome: FfiRestoreOutcome): RestoreMessage =
    if (outcome.written == 0u) {
        RestoreMessage(R.string.backup_restore_uptodate)
    } else {
        RestoreMessage(R.string.backup_restore_mergedfmt, outcome.written.toInt())
    }

/**
 * Encrypted vault backups (`core/qasa-backup`): one file per device at a document the
 * user picked once with the system file picker (any provider, Google Drive included),
 * overwritten by every backup. The file is built by the Rust core from the same
 * [VaultSyncStore] Wi‑Fi sync uses, so its payload conversion rules apply unchanged.
 *
 * Every FFI call blocks (restore runs PBKDF2), so all of them run on [Dispatchers.IO].
 * One backup or restore at a time.
 */
@Singleton
class BackupService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: BackupPrefs,
    private val store: VaultSyncStore,
    private val session: VaultSession,
    private val repository: VaultRepository,
    private val secrets: VaultSecureStore,
    private val api: QasaApiClient,
) {
    /** The restore key rotation's network and FFI side. */
    private val rotationPorts = object : RestoreKeyRotationPorts {
        override fun hasLicense(): Boolean = secrets.licenseToken != null
        override fun newKey(): ByteArray = backupNewRestoreKey()
        override fun hash(key: ByteArray): ByteArray = backupRestoreKeyHash(key)
        override suspend fun putHash(request: RestoreKeyHashRequest) {
            val body = api.json.encodeToString(RestoreKeyHashRequest.serializer(), request)
            api.rawQuick(QasaConfig.restoreKeyUrl, "PUT", body, authorized = true, timeoutMs = RESTORE_KEY_TIMEOUT_MS)
        }
        override fun log(message: String, error: Throwable) {
            Log.w(TAG, message, error)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val _running = MutableStateFlow(false)

    /** True while a backup (manual or automatic) is being written. */
    val running: StateFlow<Boolean> = _running.asStateFlow()

    val settings: StateFlow<BackupSettings> get() = prefs.state

    /** `Qasa backup - <device>.qasabackup`, the name suggested in the picker. */
    fun suggestedFileName(): String = try {
        backupFileName(VaultSyncStore.deviceName(context))
    } catch (e: Throwable) {
        Log.w(TAG, "backupFileName unavailable", e)
        "Qasa backup.qasabackup"
    }

    fun setFrequency(frequency: BackupFrequency) = prefs.setFrequency(frequency)

    /**
     * Keeps [uri] (from `ACTION_CREATE_DOCUMENT`) as the backup location, with a
     * persisted read/write grant so later backups can overwrite it after a restart.
     */
    suspend fun setLocation(uri: Uri) = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            resolver.takePersistableUriPermission(uri, flags)
        } catch (e: SecurityException) {
            // Not persistable (an unusual provider): this session can still write; the
            // next backup after a restart fails and asks for the location again.
            Log.w(TAG, "location grant not persistable", e)
        }
        val previous = prefs.state.value.locationUri?.let(Uri::parse)
        if (previous != null && previous != uri) releaseGrant(previous)
        prefs.setLocation(uri.toString(), displayName(uri))
    }

    /** Back up now, or the automatic run. Never throws. */
    suspend fun backUpNow(): BackupRunResult = withContext(Dispatchers.IO) {
        if (!mutex.tryLock()) return@withContext BackupRunResult.Busy
        try {
            _running.value = true
            runBackup()
        } finally {
            _running.value = false
            mutex.unlock()
        }
    }

    /**
     * Unlock and app foreground: runs a backup in the background when one is due and a
     * location is set. Failures only set the error flag shown on the Backups screen.
     */
    fun runIfDue() {
        val s = prefs.state.value
        if (!session.isUnlocked || s.locationUri == null || s.frequency == BackupFrequency.Off) return
        val due = try {
            backupIsDue(s.frequency.wire, s.lastBackupMs, System.currentTimeMillis())
        } catch (e: Throwable) {
            Log.w(TAG, "backupIsDue unavailable", e)
            false
        }
        if (due && !_running.value) scope.launch { backUpNow() }
    }

    /** Whether Home shows the "back up your vault" card. */
    fun shouldRemind(s: BackupSettings, itemCount: Int, nowMs: Long): Boolean = try {
        backupShouldRemind(s.lastBackupMs, itemCount.coerceAtLeast(0).toUInt(), s.snoozedUntilMs, nowMs)
    } catch (e: Throwable) {
        false
    }

    fun snoozeReminder() {
        val snooze = try {
            backupReminderSnoozeMs()
        } catch (e: Throwable) {
            DEFAULT_SNOOZE_MS
        }
        prefs.snoozeReminder(System.currentTimeMillis() + snooze)
    }

    /** Reads a picked backup file. Throws on IO failure. */
    suspend fun readFile(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("cannot open backup file")
    }

    /** Throws [FfiBackupException] when [file] is not a backup this build can read. */
    suspend fun info(file: ByteArray): FfiBackupInfo = withContext(Dispatchers.IO) { backupInfo(file) }

    /**
     * Restores [file] with [password]: adopts it on a device without a vault, merges into
     * the same vault, refuses a different one. Throws [FfiBackupException].
     */
    suspend fun restore(file: ByteArray, password: String): FfiRestoreOutcome = withContext(Dispatchers.IO) {
        mutex.withLock {
            val outcome = backupRestore(file, password, store)
            // The adopted account's registration (right after the unlock that follows)
            // presents this key, so it may take the place of the device that made the file.
            if (outcome.adopted) outcome.restoreKey?.let { secrets.pendingRestoreKey = it }
            // Same follow-up as a Wi‑Fi sync: search tokens and the autofill index.
            try {
                repository.refreshAfterSync()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "refresh after restore failed", e)
            }
            outcome
        }
    }

    /** Clear Local Data / account deletion: forget the settings, keep the file. */
    fun clearSettings() {
        prefs.state.value.locationUri?.let { releaseGrant(Uri.parse(it)) }
        prefs.clear()
    }

    private suspend fun runBackup(): BackupRunResult {
        val uri = prefs.state.value.locationUri?.let(Uri::parse) ?: return BackupRunResult.NoLocation
        if (!session.isUnlocked) return BackupRunResult.Locked
        // Never fails the backup: on any error the previous key (or none) is used.
        val restoreKey = try {
            prepareRestoreKey(secrets, rotationPorts)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.w(TAG, "restore key unavailable", e)
            secrets.restoreKey
        }
        return try {
            // The whole file is built before the old one is touched.
            val bytes = backupCreate(session.requireEncKey(), restoreKey, store)
            write(uri, bytes)
            markRestoreKeyWritten(secrets, restoreKey)
            prefs.recordSuccess(System.currentTimeMillis())
            BackupRunResult.Done
        } catch (e: Throwable) {
            // Includes a revoked grant or deleted document (SecurityException,
            // FileNotFoundException): the user picks the location again.
            Log.w(TAG, "backup failed", e)
            prefs.recordFailure()
            BackupRunResult.Failed
        }
    }

    /**
     * Overwrites the document in one write of the complete bytes. SAF has no rename, so
     * truncate-and-write ("wt") is the closest to atomic. There is deliberately no "w"
     * fallback: some providers don't truncate on "w", and a shorter new backup would keep
     * the old file's tail, which breaks the seal. A provider that rejects "wt" fails the
     * backup visibly instead, and the user picks another location.
     */
    private fun write(uri: Uri, bytes: ByteArray) {
        val opened: OutputStream? = context.contentResolver.openOutputStream(uri, "wt")
        val out = opened ?: error("cannot open backup location")
        out.use { stream ->
            stream.write(bytes)
            stream.flush()
            (stream as? FileOutputStream)?.let {
                try {
                    it.fd.sync()
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun displayName(uri: Uri): String? {
        val queried = try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (e: Exception) {
            null
        }
        return queried ?: uri.lastPathSegment
    }

    private fun releaseGrant(uri: Uri) {
        try {
            context.contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val TAG = "BackupService"
        private const val DEFAULT_SNOOZE_MS = 7L * 24 * 60 * 60 * 1000
        private const val RESTORE_KEY_TIMEOUT_MS = 5_000L
    }
}
