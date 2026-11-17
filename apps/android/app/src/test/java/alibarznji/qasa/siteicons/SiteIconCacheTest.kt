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

    @Test fun anUnshapedEntryIsShapedOnReadAndWrittenBack() {
        val shapedPng = byteArrayOf(9, 9, 9)
        var calls = 0
        val shaping = SiteIconCache(dir, reshape = { calls++; shapedPng }) { now }
        shaping.writeUnshapedForTest(SiteIconEntry("github.com", SiteIconStatus.Icon, now - 5, png, shape = 0), key)
        val first = shaping.read("github.com", key)!!
        assertArrayEquals(shapedPng, first.png)
        assertEquals(SiteIconCache.SHAPE_VERSION, first.shape)
        // Same status and time: nothing is fetched again for this.
        assertEquals(SiteIconStatus.Icon, first.status)
        assertEquals(now - 5, first.checkedAt)
        assertFalse(shaping.isDue(first))
        // Written back: the next read doesn't shape it again.
        val second = shaping.read("github.com", key)!!
        assertArrayEquals(shapedPng, second.png)
        assertEquals(1, calls)
    }

    @Test fun anEntryThatCantBeShapedIsKeptAndTriedAgain() {
        var calls = 0
        val failing = SiteIconCache(dir, reshape = { calls++; null }) { now }
        failing.writeUnshapedForTest(SiteIconEntry("github.com", SiteIconStatus.Icon, now, png, shape = 0), key)
        assertArrayEquals(png, failing.read("github.com", key)!!.png)
        assertArrayEquals(png, failing.read("github.com", key)!!.png)
        assertEquals(2, calls)
    }

    @Test fun newEntriesAreRecordedShaped() {
        var calls = 0
        val shaping = SiteIconCache(dir, reshape = { calls++; it }) { now }
        shaping.record("github.com", SiteIconStatus.Icon, png, key)
        assertEquals(SiteIconCache.SHAPE_VERSION, shaping.read("github.com", key)!!.shape)
        assertEquals(0, calls)
    }
}
