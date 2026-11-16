package alibarznji.qasa.siteicons

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** docs/site-icons.md "Cache": sealed with the vault key, keyed by host, and its TTLs. */
class SiteIconCacheTest {
    private val dir: File = Files.createTempDirectory("site-icons").toFile()
    private var now = 1_700_000_000_000L
    private val cache = SiteIconCache(dir) { now }
    private val key = ByteArray(32) { it.toByte() }
    private val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 1, 2, 3)

    @After fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test fun neverCheckedIsDue() {
        assertNull(cache.read("github.com", key))
        assertTrue(cache.isDue(null))
    }

    @Test fun iconIsRefreshedAfterThirtyDays() {
        cache.record("github.com", SiteIconStatus.Icon, png, key)
        val entry = cache.read("github.com", key)!!
        assertArrayEquals(png, entry.png)
        now += 30 * SiteIconCache.DAY_MS - 1
        assertFalse(cache.isDue(entry))
        now += 1
        assertTrue(cache.isDue(entry))
    }

    @Test fun noneIsRetriedAfterSevenDays() {
        cache.record("example.org", SiteIconStatus.None, null, key)
        val entry = cache.read("example.org", key)!!
        assertNull(entry.png)
        now += 7 * SiteIconCache.DAY_MS - 1
        assertFalse(cache.isDue(entry))
        now += 1
        assertTrue(cache.isDue(entry))
    }

    @Test fun failureIsRetriedAfterADayAndKeepsAnEarlierIcon() {
        cache.record("github.com", SiteIconStatus.Icon, png, key)
        now += 31 * SiteIconCache.DAY_MS
        cache.record("github.com", SiteIconStatus.Failure, null, key)
        val entry = cache.read("github.com", key)!!
        assertEquals(SiteIconStatus.Failure, entry.status)
        assertArrayEquals(png, entry.png)
        now += SiteIconCache.DAY_MS - 1
        assertFalse(cache.isDue(entry))
        now += 1
        assertTrue(cache.isDue(entry))
    }

    @Test fun aClockGoingBackwardsChecksAgain() {
        cache.record("github.com", SiteIconStatus.Icon, png, key)
        val entry = cache.read("github.com", key)!!
        now -= 1
        assertTrue(cache.isDue(entry))
    }

    @Test fun storedSealedAndUnreadableWithAnotherKey() {
        cache.record("github.com", SiteIconStatus.Icon, png, key)
        val files = dir.listFiles().orEmpty()
        assertEquals(1, files.size)
        // Neither the file name nor its bytes carry the host.
        assertFalse(files[0].name.contains("github"))
        assertFalse(String(files[0].readBytes(), Charsets.ISO_8859_1).contains("github.com"))
        assertNull(cache.read("github.com", ByteArray(32) { 7 }))
        assertNull(cache.read("gitlab.com", key))
    }

    @Test fun clearDeletesEverything() {
        cache.record("github.com", SiteIconStatus.Icon, png, key)
        cache.clear()
        assertNull(cache.read("github.com", key))
        assertFalse(dir.exists())
    }
}
