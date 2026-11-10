package alibarznji.qasa.backup

import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

/**
 * The backup restore key secrets (`VaultSecureStore` in the app, a fake in tests).
 *
 * Each backup carries a secret restore key whose SHA-256 this device sent to the server;
 * a device that restores the backup presents the key and may take this device's slot.
 */
interface RestoreKeySecrets {
    /** This device's current key (32 bytes); set only once the server confirmed its hash. */
    var restoreKey: ByteArray?

    /** Whether a backup file holding [restoreKey] was written successfully. */
    var restoreKeyWritten: Boolean

    /** The key from a backup this device restored, until registration uses it. */
    var pendingRestoreKey: ByteArray?
}

/** Body of `PUT /v1/devices/me/restore-key`. */
@Serializable
data class RestoreKeyHashRequest(val key_hash: String)

/** What a rotation needs beyond the stored secrets. */
interface RestoreKeyRotationPorts {
    /** Whether this device holds a license token (the PUT is license-authenticated). */
    fun hasLicense(): Boolean

    /** `backupNewRestoreKey()`: 32 random bytes. */
    fun newKey(): ByteArray

    /** `backupRestoreKeyHash(key)`: SHA-256. */
    fun hash(key: ByteArray): ByteArray

    /** Sends the base64 hash; throws on anything but 2xx. Must use a short timeout. */
    suspend fun putHash(request: RestoreKeyHashRequest)

    fun log(message: String, error: Throwable)
}

internal fun restoreKeyBase64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

/**
 * Runs before every backup (manual and automatic) and returns the key the file should
 * carry, or null for none. Never throws for a network failure: the backup goes ahead.
 *
 * A new key is made when there is none yet, or when a file holding the current one was
 * already written. The server keeps the current and the previous hash, and the file on
 * disk always holds one of them: the old key stays until the server confirmed the new one,
 * and is only replaced again after a file holding the new one exists.
 */
suspend fun prepareRestoreKey(secrets: RestoreKeySecrets, ports: RestoreKeyRotationPorts): ByteArray? {
    val current = secrets.restoreKey
    if (ports.hasLicense() && (current == null || secrets.restoreKeyWritten)) {
        try {
            val fresh = ports.newKey()
            ports.putHash(RestoreKeyHashRequest(restoreKeyBase64(ports.hash(fresh))))
            secrets.restoreKey = fresh
            secrets.restoreKeyWritten = false
            return fresh
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Offline, 4xx, no license on the server: keep the previous state.
            ports.log("restore key rotation failed", e)
        }
    }
    return current
}

/** After the backup file holding [used] was written successfully. */
fun markRestoreKeyWritten(secrets: RestoreKeySecrets, used: ByteArray?) {
    val current = secrets.restoreKey ?: return
    if (used != null && used.contentEquals(current)) secrets.restoreKeyWritten = true
}

/**
 * Local vault reset, Clear Local Data and account deletion. The secure store reset clears
 * these too; this keeps them gone should that reset ever become selective.
 */
fun clearRestoreKeys(secrets: RestoreKeySecrets) {
    secrets.restoreKey = null
    secrets.restoreKeyWritten = false
    secrets.pendingRestoreKey = null
}
