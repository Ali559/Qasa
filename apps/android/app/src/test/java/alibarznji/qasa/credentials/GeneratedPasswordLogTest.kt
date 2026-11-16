package alibarznji.qasa.credentials

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R4: a picked strong password is kept until saved, offered once, or 24 hours old. */
class GeneratedPasswordLogTest {
    private val hour = 60L * 60 * 1000

    private fun entry(n: Int, at: Long, picked: Boolean = false) =
        GeneratedPassword("${GeneratedPasswordLog.DATASET_PREFIX}$n", "example.com", "example.com", "pw$n", at, picked)

    @Test
    fun offersAreCappedAndExpire() {
        var log = emptyList<GeneratedPassword>()
        for (i in 1..20) log = GeneratedPasswordLog.offered(log, entry(i, i.toLong()), now = 20)
        assertEquals(GeneratedPasswordLog.MAX_OFFERED, log.size)
        assertEquals("pw20", log.last().password)
        assertTrue(GeneratedPasswordLog.prune(log, now = 25 * hour).isEmpty())
    }

    @Test
    fun aPickByIdOrValueIsKept() {
        val log = listOf(entry(1, 0), entry(2, 0), entry(3, 0))
        val picked = GeneratedPasswordLog.picked(log, listOf("${GeneratedPasswordLog.DATASET_PREFIX}2"), password = "pw3")
        assertEquals(listOf(false, true, true), picked.map { it.picked })
        // Later offers don't push a picked one out.
        var next = picked
        for (i in 10..30) next = GeneratedPasswordLog.offered(next, entry(i, 1), now = 2)
        assertTrue(next.any { it.password == "pw2" && it.picked })
    }

    @Test
    fun unlockOffersTheNewestUnsavedPickOnce() {
        val log = listOf(entry(1, 0, picked = true), entry(2, 1, picked = true), entry(3, 2))
        val (due, rest) = GeneratedPasswordLog.dueOnUnlock(log, savedPasswords = setOf("pw2"), now = hour)
        // pw2 is saved (dropped); pw1 is the pick no login holds.
        assertEquals("pw1", due!!.password)
        assertEquals(listOf("pw3"), rest.map { it.password })
        val (again, _) = GeneratedPasswordLog.dueOnUnlock(rest, emptySet(), now = hour)
        assertNull(again)
    }

    @Test
    fun expiredPicksAreNotOffered() {
        val (due, rest) = GeneratedPasswordLog.dueOnUnlock(listOf(entry(1, 0, picked = true)), emptySet(), now = 25 * hour)
        assertNull(due)
        assertTrue(rest.isEmpty())
    }
}
