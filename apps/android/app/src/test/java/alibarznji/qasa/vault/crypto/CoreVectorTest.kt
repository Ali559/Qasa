package alibarznji.qasa.vault.crypto

import java.io.File
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Checks the Android vault crypto against the golden vectors shared with iOS and the desktop
 * core (`core/qasa-vault/tests/vectors.json`). A failure means the platforms can no longer read
 * each other's vaults: fix the code, never the vectors.
 */
class CoreVectorTest {
    private val vectors: JsonObject by lazy {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null && !File(dir, "core/qasa-vault/tests/vectors.json").isFile) dir = dir.parentFile
        requireNotNull(dir) { "core/qasa-vault/tests/vectors.json not found above ${System.getProperty("user.dir")}" }
        Json.parseToJsonElement(File(dir, "core/qasa-vault/tests/vectors.json").readText()).jsonObject
    }

    private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content
    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    @Test fun iterationCountMatches() =
        assertEquals(MasterKeyDeriver.PBKDF2_ITERATIONS, vectors.getValue("pbkdf2_iterations").jsonPrimitive.int)

    @Test fun keyDerivation() {
        for (c in vectors.getValue("kdf").jsonArray.map { it.jsonObject }) {
            val name = c.str("name")
            val root = MasterKeyDeriver.deriveRootKey(c.str("password"), hex(c.str("salt_hex")))
            assertArrayEquals("root key: $name", hex(c.str("root_key_hex")), root)
            assertArrayEquals("verifier: $name", hex(c.str("verifier_hex")), MasterKeyDeriver.passwordVerifier(root))
            val (enc, search) = MasterKeyDeriver.deriveSubkeys(root)
            assertArrayEquals("enc key: $name", hex(c.str("enc_key_hex")), enc)
            assertArrayEquals("search key: $name", hex(c.str("search_key_hex")), search)
        }
    }

    @Test fun aesGcmBlobs() {
        for (c in vectors.getValue("aead").jsonArray.map { it.jsonObject }) {
            val name = c.str("name")
            val key = hex(c.str("key_hex"))
            val blob = hex(c.str("blob_hex"))
            assertEquals("decrypt: $name", c.str("plaintext"), String(VaultCrypto.decrypt(blob, key), Charsets.UTF_8))

            // Sealing with the same nonce must give the same bytes as the core.
            val nonce = hex(c.str("nonce_hex"))
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            assertArrayEquals("encrypt: $name", blob, nonce + cipher.doFinal(c.str("plaintext").toByteArray(Charsets.UTF_8)))
        }
    }

    @Test fun searchTokensAndHashes() {
        val search = vectors.getValue("search").jsonObject
        val key = hex(search.str("key_hex"))
        for (c in search.getValue("cases").jsonArray.map { it.jsonObject }) {
            val name = c.str("name")
            val text = c.str("text")
            val tokens = c.getValue("tokens").jsonArray.map { it.jsonPrimitive.content }.toSet()
            val hashes = c.getValue("hashes").jsonArray.map { it.jsonPrimitive.content }.toSet()
            assertEquals("tokens: $name", tokens, VaultSearchIndex.tokenize(text).toSet())
            assertEquals("hashes: $name", hashes, VaultSearchIndex.tokenHashes(text, key).toSet())
        }
    }
}
