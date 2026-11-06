package alibarznji.qasa.vault.totp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** RFC 6238 Appendix B vectors plus the shared (iOS + Android) input rules. */
class TotpTest {

    private val times = longArrayOf(59, 1111111109, 1111111111, 1234567890, 2000000000, 20000000000)

    private fun rfc(algorithm: Totp.Algorithm, ascii: String) =
        Totp.Params(ascii.toByteArray(Charsets.US_ASCII), "", algorithm, digits = 8, period = 30)

    private fun assertVectors(params: Totp.Params, expected: List<String>) {
        assertEquals(expected, times.map { Totp.code(params, it) })
    }

    @Test fun rfc6238Sha1() = assertVectors(
        rfc(Totp.Algorithm.SHA1, "12345678901234567890"),
        listOf("94287082", "07081804", "14050471", "89005924", "69279037", "65353130"),
    )

    @Test fun rfc6238Sha256() = assertVectors(
        rfc(Totp.Algorithm.SHA256, "12345678901234567890123456789012"),
        listOf("46119246", "68084774", "67062674", "91819424", "90698825", "77737706"),
    )

    @Test fun rfc6238Sha512() = assertVectors(
        rfc(Totp.Algorithm.SHA512, "1234567890123456789012345678901234567890123456789012345678901234"),
        listOf("90693936", "25091201", "99943326", "93441116", "38618901", "47863826"),
    )

    @Test fun bareSecretIsCleanedAndWrapped() {
        // "12345678901234567890" in base32, lowercase, spaced, dashed and padded.
        val uri = Totp.normalize("gezd gnbv-gy3t qojq gezd gnbv gy3t qojq====")
        assertEquals("otpauth://totp/Qasa?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", uri)
        val params = Totp.parse(uri!!)!!
        assertEquals(Totp.Algorithm.SHA1, params.algorithm)
        assertEquals(6, params.digits)
        assertEquals(30, params.period)
        // Same secret, so the 6-digit code is the low 6 digits of the RFC SHA1 vector.
        assertEquals("287082", Totp.code(params, 59))
    }

    @Test fun fullUriKeepsLabelAndParameters() {
        val raw = "OTPAUTH://TOTP/GitHub:ali%40example.com?secret=gezd-gnbv gy3t qojq gezd gnbv gy3t qojq&" +
            "issuer=GitHub&algorithm=SHA256&digits=8&period=60"
        val uri = Totp.normalize(raw)
        assertEquals(
            "otpauth://totp/GitHub:ali%40example.com?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&" +
                "issuer=GitHub&algorithm=SHA256&digits=8&period=60",
            uri,
        )
        val params = Totp.parse(uri!!)!!
        assertEquals(Totp.Algorithm.SHA256, params.algorithm)
        assertEquals(8, params.digits)
        assertEquals(60, params.period)
        assertEquals("GitHub", params.issuer)
        assertEquals("GitHub:ali@example.com", params.label)
        assertEquals("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", params.secretBase32)
    }

    @Test fun hotpIsRejected() {
        assertNull(Totp.normalize("otpauth://hotp/X?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&counter=1"))
    }

    @Test fun badBase32CharacterIsRejected() {
        // 0, 1, 8 and 9 are not base32.
        assertNull(Totp.normalize("GEZDGNBVGY3TQOJ0GEZDGNBVGY3TQOJQ"))
        assertNull(Totp.normalize("otpauth://totp/X?secret=GEZDGNBVGY3TQOJ1"))
    }

    @Test fun tooShortSecretIsRejected() {
        // 9 bytes is one short; 16 characters (10 bytes) is the minimum.
        assertNull(Totp.normalize("GEZDGNBVGY3TQOJ"))
        assertNull(Totp.normalize("otpauth://totp/X?secret=GEZDGNBVGY3TQOJ"))
        assertNotNull(Totp.normalize("GEZDGNBVGY3TQOJQ"))
    }

    @Test fun outOfRangeParametersAreRejected() {
        val base = "otpauth://totp/X?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
        assertNotNull(Totp.normalize(base))
        assertNull(Totp.normalize("$base&digits=9"))
        assertNull(Totp.normalize("$base&digits=5"))
        assertNull(Totp.normalize("$base&period=10"))
        assertNull(Totp.normalize("$base&period=121"))
        assertNull(Totp.normalize("$base&algorithm=MD5"))
        assertNull(Totp.normalize("otpauth://totp/X?issuer=NoSecret"))
    }

    @Test fun nonOtpauthQrIsRejected() {
        val qr = "https://example.com/setup?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
        assertFalse(Totp.isOtpauthUri(qr))
        assertNull(Totp.normalize(qr, requireUri = true))
        assertNull(Totp.normalize(qr))
        // A bare key is fine to type, but a scanned QR code must be an otpauth URI.
        assertNull(Totp.normalize("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", requireUri = true))
        assertTrue(Totp.isOtpauthUri("otpauth://totp/X?secret=A"))
    }

    @Test fun countdownAndGrouping() {
        assertEquals(1, Totp.secondsRemaining(30, 59))
        assertEquals(30, Totp.secondsRemaining(30, 60))
        assertEquals("123 456", Totp.grouped("123456"))
        assertEquals("1234 5678", Totp.grouped("12345678"))
    }
}
