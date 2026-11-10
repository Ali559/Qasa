package alibarznji.qasa.vault


import alibarznji.qasa.backup.RestoreKeySecrets
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VaultSecureStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : RestoreKeySecrets {
    private val prefs: SharedPreferences

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        prefs = EncryptedSharedPreferences.create(
            context,
            "io.kaso.qasa.vault",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        // One-time migration: earlier builds stored the master password as a plain
        // `EncryptedSharedPreferences` string. Nothing reads it any more, so leaving it
        // on disk would keep the very secret this cache exists to protect.
        if (prefs.contains(KEY_BIO_PASSWORD)) {
            prefs.edit().remove(KEY_BIO_PASSWORD).apply()
        }
    }

    var vaultExists: Boolean
        get() = prefs.getBoolean(KEY_VAULT_EXISTS, false)
        set(value) = prefs.edit().putBoolean(KEY_VAULT_EXISTS, value).apply()

    var kdfSalt: ByteArray?
        get() = prefs.getString(KEY_SALT, null)?.let { Base64.decode(it, Base64.DEFAULT) }
        set(value) {
            prefs.edit().putString(KEY_SALT, value?.let { Base64.encodeToString(it, Base64.DEFAULT) }).apply()
        }

    var passwordVerifier: ByteArray?
        get() = prefs.getString(KEY_VERIFIER, null)?.let { Base64.decode(it, Base64.DEFAULT) }
        set(value) {
            prefs.edit().putString(KEY_VERIFIER, value?.let { Base64.encodeToString(it, Base64.DEFAULT) }).apply()
        }

    /**
     * The KDF parameters version (iOS `VaultKeychainStore.kdfVersion`). Vaults made on
     * Android predate the field and are version 1; a vault adopted over Wi‑Fi records
     * the version it arrived with.
     */
    var kdfVersion: Int
        get() = prefs.getInt(KEY_KDF_VERSION, 1)
        set(value) = prefs.edit().putInt(KEY_KDF_VERSION, value).apply()

    /**
     * Rows arrived over Wi‑Fi while their search tokens could not be built (that needs the
     * search key). The next unlock rebuilds them.
     */
    var searchReindexPending: Boolean
        get() = prefs.getBoolean(KEY_REINDEX, false)
        set(value) = prefs.edit().putBoolean(KEY_REINDEX, value).apply()

    var accountId: String?
        get() = prefs.getString(KEY_ACCOUNT_ID, null)
        set(value) = prefs.edit().putString(KEY_ACCOUNT_ID, value).apply()

    var deviceFingerprint: String?
        get() = prefs.getString(KEY_DEVICE_FP, null)
        set(value) = prefs.edit().putString(KEY_DEVICE_FP, value).apply()

    var licenseToken: String?
        get() = prefs.getString(KEY_LICENSE, null)
        set(value) = prefs.edit().putString(KEY_LICENSE, value).apply()

    /**
     * Base64 Ed25519 verifying key for license tokens. Deliberately kept out of the
     * build-pinned path: a build that pins a key never reads or writes this.
     */
    var licenseVerifyingKey: String?
        get() = prefs.getString(KEY_LICENSE_KEY, null)
        set(value) = prefs.edit().putString(KEY_LICENSE_KEY, value).apply()

    // ---- Backup restore key (see `backup/RestoreKeyRotation.kt`) ---------------------
    //
    // Raw 32-byte secrets: they let a device holding a backup replace this one, so they
    // live only here, next to the license token, and never in plain prefs or logs.

    /** This device's current restore key; only set once the server confirmed its hash. */
    override var restoreKey: ByteArray?
        get() = prefs.getString(KEY_RESTORE_KEY, null)?.let { Base64.decode(it, Base64.NO_WRAP) }
        set(value) = putBytes(KEY_RESTORE_KEY, value)

    /** Whether a backup file holding [restoreKey] was written successfully. */
    override var restoreKeyWritten: Boolean
        get() = prefs.getBoolean(KEY_RESTORE_KEY_WRITTEN, false)
        set(value) = prefs.edit().putBoolean(KEY_RESTORE_KEY_WRITTEN, value).apply()

    /** The key from a backup this device restored, until a registration uses it. */
    override var pendingRestoreKey: ByteArray?
        get() = prefs.getString(KEY_PENDING_RESTORE_KEY, null)?.let { Base64.decode(it, Base64.NO_WRAP) }
        set(value) = putBytes(KEY_PENDING_RESTORE_KEY, value)

    /** This device lost its slot: the server already forgot the hashes of its key. */
    fun clearOwnRestoreKey() {
        prefs.edit().remove(KEY_RESTORE_KEY).remove(KEY_RESTORE_KEY_WRITTEN).apply()
    }

    private fun putBytes(key: String, value: ByteArray?) {
        val edit = prefs.edit()
        if (value == null) edit.remove(key) else edit.putString(key, Base64.encodeToString(value, Base64.NO_WRAP))
        edit.apply()
    }

    var biometricsEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRICS, false)
        set(value) = prefs.edit().putBoolean(KEY_BIOMETRICS, value).apply()

    /**
     * Epoch millis of the last create, unlock, or password change done with the master
     * password (see [BiometricPolicy]). Biometric unlocks never write it. Null when none was
     * recorded, which the policy treats as due. Kept next to the biometric flag so a vault
     * reset clears both together.
     */
    var lastMasterPasswordUnlock: Long?
        get() = if (prefs.contains(KEY_LAST_MASTER_UNLOCK)) prefs.getLong(KEY_LAST_MASTER_UNLOCK, 0L) else null
        set(value) {
            val edit = prefs.edit()
            if (value == null) edit.remove(KEY_LAST_MASTER_UNLOCK) else edit.putLong(KEY_LAST_MASTER_UNLOCK, value)
            edit.apply()
        }

    // ---- Hardware-gated master-password cache -------------------------------------
    //
    // The master password used to sit in `EncryptedSharedPreferences` as a plaintext
    // string: anyone with the device unlocked, or with ADB/root access, read it straight
    // out. It is now AES-GCM ciphertext whose key requires a fresh
    // `BIOMETRIC_STRONG | DEVICE_CREDENTIAL` authorization for *every* use (0-second
    // validity), so the bytes are unreachable without the system prompt — and the
    // Cipher that unlocks them only exists once that prompt has succeeded.

    /** True when a master password is currently cached behind the gate. */
    val biometricSecretStored: Boolean
        get() = prefs.contains(KEY_BIO_SECRET) && prefs.contains(KEY_BIO_IV)

    /**
     * False when this device cannot gate a key at all (no secure lock screen, no
     * enrolled authenticator). Callers must fail closed and offer the master password.
     */
    val biometricGateAvailable: Boolean
        get() = gateKey() != null

    /** A cipher for `BiometricPrompt.CryptoObject`, so the write needs authorization too. */
    fun biometricEncryptCipher(): Cipher? {
        fun make(): Cipher? = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, gateKey() ?: return null)
        }
        return try {
            make()
        } catch (_: KeyPermanentlyInvalidatedException) {
            // Nothing sealed with the old key can be opened any more: start over with a new one.
            clearBiometricPassword()
            dropGateKey()
            runCatching { make() }.getOrNull()
        } catch (_: Exception) {
            null
        }
    }

    /** Encrypts the master password with a cipher the prompt has already authorized. */
    fun saveBiometricSecret(cipher: Cipher, password: String): Boolean = try {
        val blob = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(KEY_BIO_IV, Base64.encodeToString(cipher.iv, Base64.DEFAULT))
            .putString(KEY_BIO_SECRET, Base64.encodeToString(blob, Base64.DEFAULT))
            .apply()
        true
    } catch (_: Exception) {
        // Throws UserNotAuthenticatedException when the cipher was never authorized.
        false
    }

    /** The decrypt cipher for the unlock prompt, or null when nothing is cached. */
    fun biometricDecryptCipher(): Cipher? {
        if (!biometricSecretStored) return null
        val iv = prefs.getString(KEY_BIO_IV, null)?.let { Base64.decode(it, Base64.DEFAULT) }
            ?: return null
        return try {
            Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, gateKey() ?: return null, GCMParameterSpec(128, iv))
            }
        } catch (_: KeyPermanentlyInvalidatedException) {
            // Screen lock removed or reset: the sealed password is gone for good. The next
            // password unlock seals it again under a new key.
            clearBiometricPassword()
            dropGateKey()
            null
        } catch (_: Exception) {
            clearBiometricPassword()
            null
        }
    }

    /** Decrypts the master password using a cipher the prompt has already authorized. */
    fun loadBiometricSecret(cipher: Cipher): String? = try {
        val blob = prefs.getString(KEY_BIO_SECRET, null)?.let { Base64.decode(it, Base64.DEFAULT) }
            ?: return null
        String(cipher.doFinal(blob), Charsets.UTF_8)
    } catch (_: Exception) {
        null
    }

    fun clearBiometricPassword() {
        prefs.edit()
            .remove(KEY_BIO_PASSWORD)
            .remove(KEY_BIO_SECRET)
            .remove(KEY_BIO_IV)
            .apply()
    }

    /** Everything, the restore key secrets included. */
    fun resetVaultSecrets() {
        prefs.edit().clear().apply()
    }

    private var cachedGateKey: SecretKey? = null

    /**
     * The key that seals the master password. It must survive app restarts, so an existing
     * one is reused: generating under the same alias replaced it on every process start,
     * and after a restart the sealed password no longer decrypted, was dropped, and
     * fingerprint unlock silently stopped working.
     */
    @Synchronized
    private fun gateKey(): SecretKey? {
        cachedGateKey?.let { return it }
        val key = existingGateKey() ?: generateGateKey()
        cachedGateKey = key
        return key
    }

    /** Android invalidated the key (screen lock removed): forget it so a new one is made. */
    @Synchronized
    private fun dropGateKey() {
        cachedGateKey = null
        try {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(GATE_KEY_ALIAS)
        } catch (_: Exception) {
        }
    }

    private fun generateGateKey(): SecretKey? {
        return try {
            val generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE,
            )
            val spec = KeyGenParameterSpec.Builder(
                GATE_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true)
                // Keep the key usable when a device credential is enrolled, not only
                // while a fingerprint stays on the sensor.
                .setInvalidatedByBiometricEnrollment(false)
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        setUserAuthenticationParameters(
                            0,
                            KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
                        )
                    } else {
                        // -1 is the only way to express "authorize every single use"
                        // before API 30, which is what the CryptoObject flow requires.
                        @Suppress("DEPRECATION")
                        setUserAuthenticationValidityDurationSeconds(-1)
                    }
                }
                .build()
            generator.init(spec)
            generator.generateKey()
            val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            store.getKey(GATE_KEY_ALIAS, null) as? SecretKey
        } catch (_: Exception) {
            // No secure lock screen, or the key was invalidated. Biometric unlock is then
            // unavailable and the master password is the only way in.
            null
        }
    }

    private fun existingGateKey(): SecretKey? = try {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        store.getKey(GATE_KEY_ALIAS, null) as? SecretKey
    } catch (_: Exception) {
        // Unreadable (e.g. invalidated when the screen lock was removed): make a new one.
        null
    }

    companion object {
        private const val KEY_VAULT_EXISTS = "vaultExists"
        private const val KEY_SALT = "kdfSalt"
        private const val KEY_KDF_VERSION = "kdfVersion"
        private const val KEY_REINDEX = "searchReindexPending"
        private const val KEY_VERIFIER = "passwordVerifier"
        private const val KEY_ACCOUNT_ID = "accountId"
        private const val KEY_DEVICE_FP = "deviceFingerprint"
        private const val KEY_LICENSE = "licenseToken"
        private const val KEY_LICENSE_KEY = "licenseVerifyingKey"
        private const val KEY_BIOMETRICS = "biometricsEnabled"
        private const val KEY_LAST_MASTER_UNLOCK = "lastMasterPasswordUnlock"
        private const val KEY_RESTORE_KEY = "restoreKey"
        private const val KEY_RESTORE_KEY_WRITTEN = "restoreKeyWritten"
        private const val KEY_PENDING_RESTORE_KEY = "pendingRestoreKey"
        private const val KEY_BIO_PASSWORD = "biometricPassword"
        private const val KEY_BIO_SECRET = "biometricSecret"
        private const val KEY_BIO_IV = "biometricSecretIv"
        private const val GATE_KEY_ALIAS = "qasa_biometric_gate_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
