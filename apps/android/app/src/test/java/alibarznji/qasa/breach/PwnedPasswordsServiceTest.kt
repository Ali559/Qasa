package alibarznji.qasa.breach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PwnedPasswordsServiceTest {

    @Test
    fun sha1IsUppercaseHex() {
        // Well-known HIBP example: SHA-1("password").
        assertEquals("5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8", PwnedPasswordsService.sha1Hex("password"))
    }

    @Test
    fun groupsByFiveCharPrefixAndDedupes() {
        val groups = PwnedPasswordsService.groupByPrefix(listOf("password", "password", "hunter2"))
        val entries = groups.getValue("5BAA6")
        assertEquals(listOf("password" to "1E4C9B93F3F0682250B6CF8331B7EE68FD8"), entries)
        assertEquals(2, groups.size)
        groups.values.flatten().forEach { (_, suffix) -> assertEquals(35, suffix.length) }
    }

    @Test
    fun parsesRangeAndSkipsPaddingRows() {
        val body = "1E4C9B93F3F0682250B6CF8331B7EE68FD8:9659365\r\n" +
            "0018A45C4D1DEF81644B54AB7F969B88D65:0\r\n" +
            "garbage line\r\n" +
            "00D4F6E8FA6EECAD2A3AA415EEC418D38EC:2\r\n"
        val range = PwnedPasswordsService.parseRange(body)
        assertEquals(9_659_365, range["1E4C9B93F3F0682250B6CF8331B7EE68FD8"])
        assertEquals(0, range["0018A45C4D1DEF81644B54AB7F969B88D65"])
        assertEquals(3, range.size)

        val matched = PwnedPasswordsService.matchRange(
            range,
            listOf(
                "password" to "1E4C9B93F3F0682250B6CF8331B7EE68FD8",
                "padded" to "0018A45C4D1DEF81644B54AB7F969B88D65",
                "unseen" to "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF",
            ),
        )
        assertEquals(mapOf("password" to 9_659_365), matched)
    }

    @Test
    fun emptyBodyMatchesNothing() {
        assertTrue(PwnedPasswordsService.matchRange(PwnedPasswordsService.parseRange(""), listOf("a" to "B")).isEmpty())
    }
}
