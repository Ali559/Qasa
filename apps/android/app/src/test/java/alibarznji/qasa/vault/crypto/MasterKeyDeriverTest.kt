package alibarznji.qasa.vault.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MasterKeyDeriverTest {
    private val password = "test-password-123"
    private val salt = ByteArray(32) // zeros

    // Golden vectors from Python hashlib matching CryptoKit empty-salt HKDF
    private val expectedRoot =
        "c8b67315e126e491385a4a9ae50d7e0a3848ccebe22a0e902588e5cbb722a70e"
    private val expectedVerifier =
        "78aad06c9ae8276f705f3457393c8c79d0d2e216d1144fd62740fb0ac347bd80"
    private val expectedEnc =
        "ea7d705e8d53a88d592f155543a7d35d7f631fd0f58570257e0b8f303cdffcd9"
    private val expectedSearch =
        "6b8f7a8ec516e8835d5b55573a86d74855a439326c319b2bc480a04fca3c7c27"

    @Test
    fun deriveRootKey_matchesGolden() {
        val root = MasterKeyDeriver.deriveRootKey(password, salt)
        assertEquals(expectedRoot, root.toHex())
    }

    @Test
    fun passwordVerifier_matchesGolden() {
        val root = MasterKeyDeriver.deriveRootKey(password, salt)
        assertEquals(expectedVerifier, MasterKeyDeriver.passwordVerifier(root).toHex())
    }

    @Test
    fun deriveSubkeys_matchesGolden() {
        val root = MasterKeyDeriver.deriveRootKey(password, salt)
        val (enc, search) = MasterKeyDeriver.deriveSubkeys(root)
        assertEquals(expectedEnc, enc.toHex())
        assertEquals(expectedSearch, search.toHex())
    }

    @Test
    fun aesGcm_roundTrip() {
        val root = MasterKeyDeriver.deriveRootKey(password, salt)
        val (enc, _) = MasterKeyDeriver.deriveSubkeys(root)
        val plain = "hello".toByteArray(Charsets.UTF_8)
        val blob = VaultCrypto.encrypt(plain, enc)
        assertTrue(blob.size > 12 + 16)
        assertArrayEquals(plain, VaultCrypto.decrypt(blob, enc))
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
