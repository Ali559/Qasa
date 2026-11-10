package alibarznji.qasa.backup

import alibarznji.qasa.R
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.qasa_ffi.FfiBackupException
import uniffi.qasa_ffi.FfiBackupInfo
import uniffi.qasa_ffi.FfiRestoreOutcome

class BackupPrefsTest {
    @Test fun defaultsAreOffWithNothingChosen() {
        val s = BackupPrefs(MemoryPrefs()).state.value
        assertNull(s.locationUri)
        assertNull(s.locationName)
        assertEquals(BackupFrequency.Off, s.frequency)
        assertNull(s.lastBackupMs)
        assertFalse(s.failed)
        assertNull(s.snoozedUntilMs)
    }

    @Test fun valuesSurviveANewInstance() {
        val store = MemoryPrefs()
        BackupPrefs(store).apply {
            setLocation("content://docs/backup", "Qasa backup - Pixel.qasabackup")
            setFrequency(BackupFrequency.Weekly)
            recordSuccess(1_000L)
            snoozeReminder(5_000L)
        }
        val s = BackupPrefs(store).state.value
        assertEquals("content://docs/backup", s.locationUri)
        assertEquals("Qasa backup - Pixel.qasabackup", s.locationName)
        assertEquals(BackupFrequency.Weekly, s.frequency)
        assertEquals(1_000L, s.lastBackupMs)
        assertEquals(5_000L, s.snoozedUntilMs)
    }

    @Test fun failureFlagIsClearedBySuccessOrANewLocation() {
        val prefs = BackupPrefs(MemoryPrefs())
        prefs.recordFailure()
        assertTrue(prefs.state.value.failed)
        prefs.recordSuccess(2L)
        assertFalse(prefs.state.value.failed)
        prefs.recordFailure()
        prefs.setLocation("content://new", null)
        assertFalse(prefs.state.value.failed)
        assertNull(prefs.state.value.locationName)
    }

    @Test fun lastBackupAtZeroIsStillABackup() {
        val prefs = BackupPrefs(MemoryPrefs())
        prefs.recordSuccess(0L)
        assertEquals(0L, prefs.state.value.lastBackupMs)
    }

    @Test fun clearForgetsEverything() {
        val prefs = BackupPrefs(MemoryPrefs())
        prefs.setLocation("content://x", "x")
        prefs.setFrequency(BackupFrequency.Daily)
        prefs.recordSuccess(9L)
        prefs.recordFailure()
        prefs.snoozeReminder(10L)
        prefs.clear()
        assertEquals(BackupSettings(), prefs.state.value)
    }

    @Test fun frequencyWireValuesMatchTheCore() {
        assertEquals(listOf("off", "daily", "weekly", "monthly"), BackupFrequency.entries.map { it.wire })
        BackupFrequency.entries.forEach { assertEquals(it, BackupFrequency.fromWire(it.wire)) }
        assertEquals(BackupFrequency.Off, BackupFrequency.fromWire(null))
        assertEquals(BackupFrequency.Off, BackupFrequency.fromWire("hourly"))
    }

    @Test fun everyBackupErrorHasItsMessage() {
        assertEquals(R.string.backup_error_notabackup, backupErrorMessage(FfiBackupException.NotABackup()))
        assertEquals(R.string.backup_error_newerversion, backupErrorMessage(FfiBackupException.NewerVersion()))
        assertEquals(R.string.backup_error_wrongpassword, backupErrorMessage(FfiBackupException.WrongPassword()))
        assertEquals(R.string.backup_error_differentvault, backupErrorMessage(FfiBackupException.DifferentVault()))
        assertEquals(R.string.backup_error_novault, backupErrorMessage(FfiBackupException.NoVault()))
        assertEquals(R.string.backup_error_unreadable, backupErrorMessage(FfiBackupException.Store("disk")))
        assertEquals(R.string.backup_error_unreadable, backupErrorMessage(java.io.IOException()))
    }

    private fun outcome(written: UInt) = FfiRestoreOutcome(
        adopted = false,
        written = written,
        itemsInBackup = 3u,
        info = FfiBackupInfo(createdAtMs = 1, deviceName = "Pixel", platform = "android"),
        restoreKey = null,
    )

    @Test fun mergeResultMessages() {
        assertEquals(RestoreMessage(R.string.backup_restore_uptodate), mergedMessage(outcome(0u)))
        assertEquals(RestoreMessage(R.string.backup_restore_mergedfmt, 4), mergedMessage(outcome(4u)))
    }
}

/** In-memory [SharedPreferences] for the JVM tests. */
private class MemoryPrefs : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()
    override fun getString(key: String, defValue: String?) = values[key] as String? ?: defValue
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? = defValues
    override fun getInt(key: String, defValue: Int) = values[key] as Int? ?: defValue
    override fun getLong(key: String, defValue: Long) = values[key] as Long? ?: defValue
    override fun getFloat(key: String, defValue: Float) = values[key] as Float? ?: defValue
    override fun getBoolean(key: String, defValue: Boolean) = values[key] as Boolean? ?: defValue
    override fun contains(key: String) = values.containsKey(key)
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val removed = mutableSetOf<String>()
        private var clear = false

        override fun putString(key: String, value: String?) = apply { pending[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?) = apply { pending[key] = values }
        override fun putInt(key: String, value: Int) = apply { pending[key] = value }
        override fun putLong(key: String, value: Long) = apply { pending[key] = value }
        override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
        override fun remove(key: String) = apply { removed += key }
        override fun clear() = apply { clear = true }
        override fun commit(): Boolean {
            if (clear) values.clear()
            removed.forEach(values::remove)
            pending.forEach { (k, v) -> if (v == null) values.remove(k) else values[k] = v }
            return true
        }
        override fun apply() {
            commit()
        }
    }
}
