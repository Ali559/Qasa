package alibarznji.qasa.license

import java.math.BigInteger
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** RFC 8032 §7.1 test vectors. */
class Ed25519Test {
    private fun hex(value: String): ByteArray =
        ByteArray(value.length / 2) { value.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    @Test
    fun acceptsValidEmptyMessageSignature() {
        assertTrue(
            Ed25519.verify(
                publicKey = hex("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a"),
                message = ByteArray(0),
                signature = hex(
                    "e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e065224901555fb882159" +
                        "0a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b",
                ),
            ),
        )
    }

    @Test
    fun acceptsValidOneByteMessageSignature() {
        assertTrue(
            Ed25519.verify(
                publicKey = hex("3d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c"),
                message = hex("72"),
                signature = hex(
                    "92a009a9f0d4cab8720e820b5f642540a2b27b5416503f8fb3762223ebdb69da085ac1e" +
                        "43e15996e458f3613d0f11d8c387b2eaeb4302aeeb00d291612bb0c00",
                ),
            ),
        )
    }

    @Test
    fun rejectsTamperedMessage() {
        assertFalse(
            Ed25519.verify(
                publicKey = hex("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a"),
                message = hex("01"),
                signature = hex(
                    "e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e065224901555fb882159" +
                        "0a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b",
                ),
            ),
        )
    }

    @Test
    fun rejectsTamperedSignature() {
        val signature = hex(
            "e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e065224901555fb882159" +
                "0a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b",
        )
        signature[0] = (signature[0] + 1).toByte()
        assertFalse(
            Ed25519.verify(
                publicKey = hex("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a"),
                message = ByteArray(0),
                signature = signature,
            ),
        )
    }

    @Test
    fun rejectsWrongPublicKey() {
        assertFalse(
            Ed25519.verify(
                publicKey = hex("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a"),
                message = hex("72"),
                signature = hex(
                    "92a009a9f0d4cab8720e820b5f642540a2b27b5416503f8fb3762223ebdb69da085ac1e" +
                        "43e15996e458f3613d0f11d8c387b2eaeb4302aeeb00d291612bb0c00",
                ),
            ),
        )
    }

    /** S must be canonical: S + L is a second, valid-looking encoding of the same signature. */
    @Test
    fun rejectsNonCanonicalScalar() {
        val signature = hex(
            "e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e065224901555fb882159" +
                "0a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b",
        )
        val l = BigInteger("1000000000000000000000000000000014def9dea2f79cd65812631a5cf5d3ed", 16)
        val s = BigInteger(1, signature.copyOfRange(32, 64).reversedArray())
        val malleable = s.add(l).toByteArray().let { it.reversedArray() }
        malleable.copyInto(signature, 32)
        assertFalse(
            Ed25519.verify(
                publicKey = hex("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a"),
                message = ByteArray(0),
                signature = signature,
            ),
        )
    }

    @Test
    fun rejectsMalformedInput() {
        val key = hex("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a")
        assertFalse(Ed25519.verify(ByteArray(31), ByteArray(0), ByteArray(64)))
        assertFalse(Ed25519.verify(key, ByteArray(0), ByteArray(63)))
        assertFalse(Ed25519.verify(ByteArray(32), ByteArray(0), ByteArray(64)))
    }
}
