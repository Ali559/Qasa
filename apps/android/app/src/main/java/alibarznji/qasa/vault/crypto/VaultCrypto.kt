package alibarznji.qasa.vault.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Matches iOS VaultCrypto: AES-256-GCM combined nonce(12)||ciphertext||tag(16). */
object VaultCrypto {
    private const val NONCE_LENGTH = 12
    private const val TAG_LENGTH_BITS = 128
    private const val TAG_LENGTH = 16

    fun encrypt(plaintext: ByteArray, key: ByteArray): ByteArray {
        val nonce = ByteArray(NONCE_LENGTH)
        SecureRandom().nextBytes(nonce)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_LENGTH_BITS, nonce))
        val cipherAndTag = cipher.doFinal(plaintext)
        return nonce + cipherAndTag
    }

    fun decrypt(blob: ByteArray, key: ByteArray): ByteArray {
        require(blob.size >= NONCE_LENGTH + TAG_LENGTH) { "Invalid ciphertext" }
        val nonce = blob.copyOfRange(0, NONCE_LENGTH)
        val cipherAndTag = blob.copyOfRange(NONCE_LENGTH, blob.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_LENGTH_BITS, nonce))
        return cipher.doFinal(cipherAndTag)
    }
}

sealed class VaultCryptoException(message: String) : Exception(message) {
    class EncryptionFailed : VaultCryptoException("Encryption failed")
    class InvalidCiphertext : VaultCryptoException("Invalid ciphertext")
    class VaultLocked : VaultCryptoException("Vault is locked")
    class ItemNotFound : VaultCryptoException("Item not found")
    class WrongPassword : VaultCryptoException("Wrong master password")
    class WeakPassword : VaultCryptoException("Password must be at least 8 characters")
}
