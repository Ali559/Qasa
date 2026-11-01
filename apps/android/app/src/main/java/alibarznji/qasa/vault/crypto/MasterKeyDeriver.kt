package alibarznji.qasa.vault.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Matches iOS [MasterKeyDeriver]: PBKDF2-HMAC-SHA256 @ 310_000,
 * HKDF-SHA256 with empty salt (= 32 zero bytes extract key), infos qasa-enc / qasa-search.
 */
object MasterKeyDeriver {
    const val SALT_LENGTH = 32
    const val ROOT_KEY_LENGTH = 32
    const val PBKDF2_ITERATIONS = 310_000

    fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH)
        SecureRandom().nextBytes(salt)
        return salt
    }

    fun deriveRootKey(password: String, salt: ByteArray): ByteArray {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, ROOT_KEY_LENGTH * 8)
        return factory.generateSecret(spec).encoded
    }

    fun passwordVerifier(rootKey: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(rootKey)

    fun deriveSubkeys(rootKey: ByteArray): Pair<ByteArray, ByteArray> {
        val enc = hkdfSha256(ikm = rootKey, salt = ByteArray(0), info = "qasa-enc".toByteArray(), length = 32)
        val search = hkdfSha256(ikm = rootKey, salt = ByteArray(0), info = "qasa-search".toByteArray(), length = 32)
        return enc to search
    }

    /**
     * RFC 5869 HKDF-SHA256. Empty salt uses HashLen zero bytes (CryptoKit / RFC).
     */
    fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val hashLen = 32
        val actualSalt = if (salt.isEmpty()) ByteArray(hashLen) else salt
        val prk = hmacSha256(actualSalt, ikm)
        val n = (length + hashLen - 1) / hashLen
        val okm = ByteArray(n * hashLen)
        var t = ByteArray(0)
        var offset = 0
        for (i in 1..n) {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            mac.update(t)
            mac.update(info)
            mac.update(byteArrayOf(i.toByte()))
            t = mac.doFinal()
            System.arraycopy(t, 0, okm, offset, hashLen)
            offset += hashLen
        }
        return okm.copyOf(length)
    }

    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }
}
