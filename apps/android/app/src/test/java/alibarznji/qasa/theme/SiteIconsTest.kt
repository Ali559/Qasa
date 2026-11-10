package alibarznji.qasa.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Same cases as iOS `SiteIconTests` and the extension's `siteIcon.test.ts`. */
class SiteIconsTest {
    @Test
    fun matchesByRegistrableDomain() {
        assertEquals("google", SiteIcons.idFor("https://accounts.google.com/signin"))
        assertEquals("google", SiteIcons.idFor("gmail.com"))
        assertEquals("google", SiteIcons.idFor("mail.google.co.uk"))
        assertEquals("youtube", SiteIcons.idFor("https://www.YouTube.com/"))
        assertEquals("x", SiteIcons.idFor("twitter.com"))
        assertEquals("apple", SiteIcons.idFor("https://www.icloud.com"))
        assertEquals("github", SiteIcons.idFor("github.com"))
    }

    @Test
    fun unknownAndLookAlikeSitesKeepTheLetterTile() {
        assertNull(SiteIcons.idFor("example.com"))
        assertNull(SiteIcons.idFor(""))
        assertNull(SiteIcons.idFor("google.com.evil.io"))
        assertNull(SiteIcons.idFor("notgoogle.com"))
        assertNull(SiteIcons.idFor("alice.github.io"))
        assertNull(SiteIcons.idFor("androidapp://com.instagram.android"))
        assertNull(SiteIcons.idFor("http://10.0.0.1"))
    }

    @Test
    fun everyDomainPointsAtAnIcon() {
        assertTrue(SiteIconData.domains.values.all { it in SiteIconData.paths })
        assertTrue(SiteIconData.paths.size >= 50)
    }
}
