package alibarznji.qasa.license

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tokens are signed with a throwaway Ed25519 key so the vectors never expire; the
 * signature is over the exact base64url signing input, so the token strings are fixed.
 */
class LicenseTokenVerifierTest {
    private class FakeCache(initial: String? = null) : LicenseKeyCache {
        var value: String? = initial
        override fun read(): String? = value
        override fun write(base64: String) {
            value = base64
        }

        override fun clear() {
            value = null
        }
    }

    private fun verifier(cache: LicenseKeyCache = FakeCache(), pinned: String? = null) =
        LicenseTokenVerifier(cache, pinned)

    private fun cachedVerifier() = verifier(cache = FakeCache(KEY_BASE64))

    private fun pinnedVerifier() = verifier(cache = FakeCache(), pinned = KEY_BASE64)

    @Test
    fun parsesVerifiedPremiumToken() {
        val parsed = cachedVerifier().parse(TOKEN_PREMIUM)
        assertEquals("acct-1", parsed?.accountId)
        assertEquals("dev-1", parsed?.deviceId)
        assertEquals("premium", parsed?.tier)
        assertEquals(5, parsed?.deviceLimit)
        assertEquals(listOf("multi_device", "breach", "vpn"), parsed?.entitlements)
        assertTrue(parsed?.isPremium == true)
    }

    @Test
    fun freeTierTokenIsNotPremium() {
        assertFalse(cachedVerifier().parse(TOKEN_FREE)?.isPremium == true)
    }

    @Test
    fun failsClosedWithoutAKey() {
        assertNull(verifier().parse(TOKEN_PREMIUM))
    }

    @Test
    fun pinnedKeyVerifiesWithoutCache() {
        assertEquals(5, pinnedVerifier().parse(TOKEN_PREMIUM)?.deviceLimit)
    }

    @Test
    fun pinnedKeyWinsOverCachedKey() {
        val cache = FakeCache(base64Of(ByteArray(32) { 0x7F }))
        val pinned = verifier(cache = cache, pinned = KEY_BASE64)
        // The poisoned cache must be ignored in favour of the pinned key.
        assertEquals(5, pinned.parse(TOKEN_PREMIUM)?.deviceLimit)
        // The fetch path must not be able to replace a pinned key.
        pinned.cacheVerifyingKey(base64Of(ByteArray(32) { 0x7F }))
        assertEquals(base64Of(ByteArray(32) { 0x7F }), cache.value)
        assertEquals(5, pinned.parse(TOKEN_PREMIUM)?.deviceLimit)
    }

    @Test
    fun rejectsExpiredToken() {
        assertNull(cachedVerifier().parse(TOKEN_EXPIRED))
    }

    @Test
    fun rejectsAlgNoneDowngrade() {
        assertNull(cachedVerifier().parse(TOKEN_ALG_NONE))
    }

    @Test
    fun rejectsNonEdDsaAlgorithm() {
        assertNull(cachedVerifier().parse(TOKEN_ALG_RS256))
    }

    @Test
    fun rejectsTamperedPayload() {
        assertNull(cachedVerifier().parse(TOKEN_TAMPERED))
    }

    @Test
    fun rejectsTokenSignedByAnotherKey() {
        val cache = FakeCache(base64Of(ByteArray(32) { (it + 1).toByte() }))
        assertNull(verifier(cache = cache).parse(TOKEN_PREMIUM))
    }

    @Test
    fun clampsDeviceLimitToAtLeastOne() {
        assertEquals(1, cachedVerifier().parse(TOKEN_ZERO_LIMIT)?.deviceLimit)
    }

    @Test
    fun rejectsMalformedTokens() {
        val v = cachedVerifier()
        assertNull(v.parse(""))
        assertNull(v.parse("not-a-jwt"))
        assertNull(v.parse("a.b"))
        assertNull(v.parse("a.b.c.d"))
        assertNull(v.parse("." + TOKEN_PREMIUM.substringAfter('.')))
        assertNull(v.parse("e30.e30."))
    }

    @Test
    fun cacheIgnoresUndecodableKey() {
        val cache = FakeCache()
        val v = verifier(cache = cache)
        v.cacheVerifyingKey("!!!not base64!!!")
        assertNull(cache.value)
        v.cacheVerifyingKey(KEY_BASE64)
        assertEquals(KEY_BASE64, cache.value)
        v.clearCachedVerifyingKey()
        assertNull(cache.value)
    }

    private fun base64Of(bytes: ByteArray): String = java.util.Base64.getEncoder().encodeToString(bytes)

    private companion object {
        const val KEY_BASE64 = "y+Ly4jVTTR02FQ1TMWgn7k5KqkzrbBj8VSwM5JMT8qs="

        const val TOKEN_PREMIUM = "eyJhbGciOiJFZERTQSIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJhY2N0LTEiLCJkaWQiOiJkZXYtMSIsImp0aSI6Imp0aS0xIiwidGllciI6InByZW1pdW0iLCJlbnRpdGxlbWVudHMiOlsibXVsdGlfZGV2aWNlIiwiYnJlYWNoIiwidnBuIl0sImRldmljZV9saW1pdCI6NSwiaWF0IjoxNzAwMDAwMDAwLCJleHAiOjQxMDI0NDQ4MDB9.TnvPYbYIq3lDmAIrg5hXVfGmCANYasvK3MYuA_sn4s_oKQas4j2twQLM3V1Xv6Y8-c2e9gQ4WHfF6P2ZxIH0AA"
        const val TOKEN_FREE = "eyJhbGciOiJFZERTQSIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJhY2N0LTEiLCJkaWQiOiJkZXYtMSIsImp0aSI6Imp0aS0xIiwidGllciI6ImZyZWUiLCJlbnRpdGxlbWVudHMiOlsibXVsdGlfZGV2aWNlIiwiYnJlYWNoIiwidnBuIl0sImRldmljZV9saW1pdCI6MSwiaWF0IjoxNzAwMDAwMDAwLCJleHAiOjQxMDI0NDQ4MDB9.wmnu7AzP3fi9Nx9WObhQ5dmUK7TVEc0XjyQRFlcYC6dKmtXvYzDOZ0l6AgZ69Ue96pWsHnbQayhqRkaruS54Bg"
        const val TOKEN_EXPIRED = "eyJhbGciOiJFZERTQSIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJhY2N0LTEiLCJkaWQiOiJkZXYtMSIsImp0aSI6Imp0aS0xIiwidGllciI6InByZW1pdW0iLCJlbnRpdGxlbWVudHMiOlsibXVsdGlfZGV2aWNlIiwiYnJlYWNoIiwidnBuIl0sImRldmljZV9saW1pdCI6NSwiaWF0IjoxNzAwMDAwMDAwLCJleHAiOjE2MDAwMDAwMDB9.1G4kra___h0fFbBuyJeGWkOA9aiScy8ezErSZ94MWBWS-NlfbzU8WBGhx30WA7tlxrMui-vUhACqAF5oZhv4BQ"
        const val TOKEN_ZERO_LIMIT = "eyJhbGciOiJFZERTQSIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJhY2N0LTEiLCJkaWQiOiJkZXYtMSIsImp0aSI6Imp0aS0xIiwidGllciI6InByZW1pdW0iLCJlbnRpdGxlbWVudHMiOlsibXVsdGlfZGV2aWNlIiwiYnJlYWNoIiwidnBuIl0sImRldmljZV9saW1pdCI6MCwiaWF0IjoxNzAwMDAwMDAwLCJleHAiOjQxMDI0NDQ4MDB9.8fj2_3ORr1qJD4DScyxcRfEDwFxw1iFP6UPSFUrG6rvORFAn8uQpdwgTVcB6DTGztyy1sq9LcyIKmCfIqyryBA"
        const val TOKEN_ALG_NONE = "eyJhbGciOiJub25lIn0.eyJzdWIiOiJhY2N0LTEiLCJkaWQiOiJkZXYtMSIsImp0aSI6Imp0aS0xIiwidGllciI6InByZW1pdW0iLCJlbnRpdGxlbWVudHMiOlsibXVsdGlfZGV2aWNlIiwiYnJlYWNoIiwidnBuIl0sImRldmljZV9saW1pdCI6NSwiaWF0IjoxNzAwMDAwMDAwLCJleHAiOjQxMDI0NDQ4MDB9."
        const val TOKEN_ALG_RS256 = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJhY2N0LTEiLCJkaWQiOiJkZXYtMSIsImp0aSI6Imp0aS0xIiwidGllciI6InByZW1pdW0iLCJlbnRpdGxlbWVudHMiOlsibXVsdGlfZGV2aWNlIiwiYnJlYWNoIiwidnBuIl0sImRldmljZV9saW1pdCI6NSwiaWF0IjoxNzAwMDAwMDAwLCJleHAiOjQxMDI0NDQ4MDB9."
        const val TOKEN_TAMPERED = "eyJhbGciOiJFZERTQSIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJhY2N0LTEiLCJkaWQiOiJkZXYtMSIsImp0aSI6Imp0aS0xIiwidGllciI6ImZyZWUiLCJlbnRpdGxlbWVudHMiOlsibXVsdGlfZGV2aWNlIiwiYnJlYWNoIiwidnBuIl0sImRldmljZV9saW1pdCI6NSwiaWF0IjoxNzAwMDAwMDAwLCJleHAiOjQxMDI0NDQ4MDB9.TnvPYbYIq3lDmAIrg5hXVfGmCANYasvK3MYuA_sn4s_oKQas4j2twQLM3V1Xv6Y8-c2e9gQ4WHfF6P2ZxIH0AA"
    }
}
