package alibarznji.qasa.credentials

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The same table as the desktop bridge (`matcher.rs`) and iOS `AutofillContextMatcher`. */
class AutofillMatcherTest {
    private fun web(host: String) = AutofillCaller.Web(host, "com.android.chrome")

    @Test
    fun hostScoresMatchTheDesktopTable() {
        assertEquals(100, AutofillMatcher.score("github.com", web("github.com")))
        assertEquals(100, AutofillMatcher.score("https://www.GitHub.com/login", web("github.com")))
        assertEquals(80, AutofillMatcher.score("github.com", web("gist.github.com")))
        assertEquals(60, AutofillMatcher.score("accounts.google.com", web("mail.google.com")))
        assertEquals(30, AutofillMatcher.score("google.com", web("google.com.evil.io")))
        assertEquals(0, AutofillMatcher.score("github.com", web("gitlab.com")))
    }

    @Test
    fun publicSuffixesSeparateSites() {
        assertEquals("example.co.uk", AutofillMatcher.registrableDomain("a.example.co.uk"))
        assertTrue(AutofillMatcher.score("alice.github.io", web("mallory.github.io")) < AutofillMatcher.FILL_THRESHOLD)
        assertTrue(AutofillMatcher.score("shop.co.uk", web("evil.co.uk")) < AutofillMatcher.FILL_THRESHOLD)
        assertTrue(AutofillMatcher.score("http://10.0.0.1", web("127.0.0.1")) < AutofillMatcher.FILL_THRESHOLD)
    }

    @Test
    fun onlyTrustedBrowsersReportTheirDomain() {
        assertEquals(AutofillCaller.Web("paypal.com", "com.android.chrome"), AutofillMatcher.caller("com.android.chrome", "www.paypal.com"))
        // Any app can load paypal.com in a WebView: its package decides.
        assertEquals(AutofillCaller.App("com.evil.wallet"), AutofillMatcher.caller("com.evil.wallet", "paypal.com"))
    }

    @Test
    fun appsMatchBySiteName() {
        val app = AutofillCaller.App("com.instagram.android")
        assertEquals(60, AutofillMatcher.score("https://instagram.com", app))
        assertEquals(0, AutofillMatcher.score("https://facebook.com", app))
        assertEquals(100, AutofillMatcher.score("androidapp://com.instagram.android", app))
        assertEquals(0, AutofillMatcher.score("androidapp://com.instagram.lite", app))
        // Generic labels and very short names never match.
        assertEquals(0, AutofillMatcher.score("https://android.com", AutofillCaller.App("com.example.android")))
        assertEquals(0, AutofillMatcher.score("https://x.com", AutofillCaller.App("com.x.client")))
    }

    @Test
    fun rankDropsWeakMatchesAndSortsBestFirst() {
        val items = listOf("google.com.evil.io", "mail.google.com", "google.com", "gitlab.com")
        assertEquals(listOf("google.com", "mail.google.com"), AutofillMatcher.rank(items, web("google.com")) { it })
        assertEquals(emptyList<String>(), AutofillMatcher.rank(listOf("", "  "), web("google.com")) { it })
    }
}
