package alibarznji.qasa.vault

import alibarznji.qasa.vault.crypto.MasterKeyDeriver
import alibarznji.qasa.vault.crypto.VaultCryptoException
import java.util.UUID
import javax.crypto.Cipher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class VaultSession @Inject constructor(
    private val store: VaultSecureStore,
    private val clipboard: EphemeralClipboard,
) {
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    /**
     * Protected notes the user has already authenticated for, per unlock. Cleared by
     * [lock], so a locked vault re-prompts rather than trusting an earlier decision.
     */
    private val _revealedProtectedNotes = MutableStateFlow<Set<String>>(emptySet())
    val revealedProtectedNotes: StateFlow<Set<String>> = _revealedProtectedNotes.asStateFlow()

    private var encKey: ByteArray? = null
    private var searchKey: ByteArray? = null

    val vaultExists: Boolean get() = store.vaultExists
    val isUnlocked: Boolean get() = _unlocked.value

    fun isProtectedNoteRevealed(itemId: String): Boolean =
        itemId in _revealedProtectedNotes.value

    fun markProtectedNoteRevealed(itemId: String) {
        _revealedProtectedNotes.value = _revealedProtectedNotes.value + itemId
    }

    /**
     * Creates the vault. When [biometricsEnabled] is set, [sealedCipher] must be a cipher
     * a `BiometricPrompt` has already authorized, otherwise biometrics stay off: a master
     * password is never cached without the hardware vouching for the write.
     */
    fun createVault(
        password: String,
        biometricsEnabled: Boolean,
        sealedCipher: Cipher? = null,
    ) {
        val salt = MasterKeyDeriver.generateSalt()
        val root = MasterKeyDeriver.deriveRootKey(password, salt)
        val verifier = MasterKeyDeriver.passwordVerifier(root)
        val (enc, search) = MasterKeyDeriver.deriveSubkeys(root)

        if (store.deviceFingerprint == null) {
            store.deviceFingerprint = UUID.randomUUID().toString().lowercase()
        }
        if (store.accountId == null) {
            store.accountId = UUID.randomUUID().toString().lowercase()
        }

        // Whatever was cached belonged to a different password: destroy it first.
        store.clearBiometricPassword()
        val sealed = sealedCipher != null && captureBiometricSecret(sealedCipher, password)

        store.kdfSalt = salt
        store.passwordVerifier = verifier
        store.vaultExists = true
        store.biometricsEnabled = biometricsEnabled && sealed
        encKey = enc
        searchKey = search
        _unlocked.value = true
    }

    fun unlock(password: String): Boolean {
        val salt = store.kdfSalt ?: return false
        val expected = store.passwordVerifier ?: return false
        if (!store.vaultExists) return false
        val root = MasterKeyDeriver.deriveRootKey(password, salt)
        if (!MasterKeyDeriver.passwordVerifier(root).contentEquals(expected)) return false
        val (enc, search) = MasterKeyDeriver.deriveSubkeys(root)
        encKey = enc
        searchKey = search
        _unlocked.value = true
        return true
    }

    /**
     * Caches the master password for biometric unlock, using a cipher that a
     * `BiometricPrompt` has already authorized. There is no other writer: the bytes
     * cannot be encrypted into the gate without the hardware saying yes.
     */
    /**
     * Unlock with the password exactly as typed, then trimmed. Vaults are created with the
     * raw password but a changed password is stored trimmed (both apps), so trimming only as
     * a fallback can never lock out a password with deliberate edge spaces. Returns the
     * variant that worked, so biometrics seal the right secret; null when neither does.
     */
    fun unlockMatching(typed: String): String? {
        if (unlock(typed)) return typed
        val trimmed = typed.trim()
        return if (trimmed != typed && trimmed.isNotEmpty() && unlock(trimmed)) trimmed else null
    }

    fun captureBiometricSecret(cipher: Cipher, password: String): Boolean {
        if (!store.biometricGateAvailable) return false
        return store.saveBiometricSecret(cipher, password)
    }

    /** True when the master password should be sealed behind the gate on this unlock. */
    val needsBiometricCapture: Boolean
        get() = store.biometricsEnabled && store.biometricGateAvailable && !store.biometricSecretStored

    /** ENCRYPT cipher to hand `BiometricPrompt` when sealing the master password. */
    fun biometricCaptureCipher(): Cipher? =
        if (store.biometricGateAvailable) store.biometricEncryptCipher() else null

    /** True when a gated secret is present and this device can open the gate. */
    fun biometricUnlockAvailable(): Boolean =
        store.biometricsEnabled && store.biometricGateAvailable && store.biometricSecretStored

    /** The user's preference, independent of whether a secret is currently sealed. */
    val biometricsEnabled: Boolean
        get() = store.biometricsEnabled

    fun setBiometricsEnabled(enabled: Boolean) {
        store.biometricsEnabled = enabled
        if (!enabled) store.clearBiometricPassword()
    }

    /** The cipher to hand to `BiometricPrompt` for the unlock, or null when unavailable. */
    fun biometricUnlockCipher(): Cipher? =
        if (biometricUnlockAvailable()) store.biometricDecryptCipher() else null

    /**
     * Unlocks using the password sealed behind the gate, decrypting it with a cipher the
     * `BiometricPrompt` has already authorized.
     *
     * The password never crosses this boundary unprompted: there is no overload that
     * reads the cache without a cipher the platform issued post-authentication.
     */
    fun unlockWithBiometricCipher(cipher: Cipher): Boolean {
        val password = store.loadBiometricSecret(cipher) ?: run {
            // The gate opened but the secret did not decrypt. Drop it so a stale or
            // tampered value cannot be replayed on the next attempt.
            store.clearBiometricPassword()
            return false
        }
        val ok = unlock(password)
        if (!ok) store.clearBiometricPassword()
        return ok
    }

    fun lock() {
        // Overwrite the key bytes before dropping the references. Relying on GC alone
        // leaves the master key in the heap for an unbounded time on a device that just
        // backgrounded, where a memory dump would recover it.
        encKey?.fill(0)
        searchKey?.fill(0)
        encKey = null
        searchKey = null
        // An earlier "yes, show this note" must not outlive the unlock that produced it.
        _revealedProtectedNotes.value = emptySet()
        // A copied card number left in the clipboard is the same secret, so it does not
        // outlive the lock either.
        clipboard.clearIfOwned()
        _unlocked.value = false
    }

    fun requireEncKey(): ByteArray =
        encKey?.takeIf { _unlocked.value } ?: throw VaultCryptoException.VaultLocked()

    fun requireSearchKey(): ByteArray =
        searchKey?.takeIf { _unlocked.value } ?: throw VaultCryptoException.VaultLocked()

    fun wipeVault() {
        lock()
        store.resetVaultSecrets()
    }

    fun derivePasswordChange(current: String, newPassword: String): PasswordChangeMaterial {
        val salt = store.kdfSalt ?: throw VaultCryptoException.VaultLocked()
        val expected = store.passwordVerifier ?: throw VaultCryptoException.VaultLocked()
        val oldEnc = encKey ?: throw VaultCryptoException.VaultLocked()
        if (!_unlocked.value) throw VaultCryptoException.VaultLocked()

        val currentRoot = MasterKeyDeriver.deriveRootKey(current, salt)
        if (!MasterKeyDeriver.passwordVerifier(currentRoot).contentEquals(expected)) {
            throw VaultCryptoException.WrongPassword()
        }
        val trimmed = newPassword.trim()
        if (trimmed.length < 8) throw VaultCryptoException.WeakPassword()

        val newSalt = MasterKeyDeriver.generateSalt()
        val newRoot = MasterKeyDeriver.deriveRootKey(trimmed, newSalt)
        val newVerifier = MasterKeyDeriver.passwordVerifier(newRoot)
        val (newEnc, newSearch) = MasterKeyDeriver.deriveSubkeys(newRoot)
        return PasswordChangeMaterial(oldEnc, newSalt, newVerifier, trimmed, newEnc, newSearch)
    }

    fun commitPasswordChange(material: PasswordChangeMaterial) {
        store.kdfSalt = material.newSalt
        store.passwordVerifier = material.newVerifier
        encKey = material.newEnc
        searchKey = material.newSearch
        // The sealed secret held the old password, so it can no longer open the vault.
        // It is destroyed here and re-captured from the new password behind a fresh
        // prompt, rather than silently left to fail on the next unlock.
        store.clearBiometricPassword()
    }

    data class PasswordChangeMaterial(
        val oldEnc: ByteArray,
        val newSalt: ByteArray,
        val newVerifier: ByteArray,
        val newPassword: String,
        val newEnc: ByteArray,
        val newSearch: ByteArray,
    )
}
