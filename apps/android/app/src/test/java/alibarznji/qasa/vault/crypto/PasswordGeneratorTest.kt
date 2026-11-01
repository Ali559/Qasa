package alibarznji.qasa.vault.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PasswordGeneratorTest {

    @Test
    fun honoursRequestedLength() {
        // Lower bound is the number of enabled classes, since each one is guaranteed a slot.
        for (len in listOf(3, 4, 8, 16, 64, 128, 256)) {
            val pw = PasswordGenerator.generate(length = len, symbols = false)
            assertEquals("length $len", len, pw.length)
        }
        val single = PasswordGenerator.generate(
            length = 1, uppercase = false, lowercase = false, digits = true, symbols = false,
        )
        assertEquals(1, single.length)
    }

    @Test
    fun guaranteesOneCharacterFromEveryEnabledClass() {
        // Repeat so a missing-class bug cannot hide behind luck.
        repeat(300) {
            val pw = PasswordGenerator.generate(length = 16)
            assertTrue(pw.any { it in 'A'..'Z' })
            assertTrue(pw.any { it in 'a'..'z' })
            assertTrue(pw.any { it in '0'..'9' })
            assertTrue(pw.any { it in "!@#$%^&*()_+-=" })
        }
    }

    @Test
    fun excludesDisabledClasses() {
        val digitsOnly = PasswordGenerator.generate(
            length = 32, uppercase = false, lowercase = false, digits = true, symbols = false,
        )
        assertTrue(digitsOnly.all { it in '0'..'9' })

        val symbolsOnly = PasswordGenerator.generate(
            length = 32, uppercase = false, lowercase = false, digits = false, symbols = true,
        )
        assertTrue(symbolsOnly.all { it in "!@#$%^&*()_+-=" })
    }

    @Test
    fun coversEveryBytePosition() {
        // 4 forced class characters, so with length 8 every position is chance-sampled
        // from the full pool. Guards against a Fisher-Yates that leaves the tail untouched.
        val seen = mutableSetOf<Char>()
        repeat(400) { seen += PasswordGenerator.generate(length = 8).toSet() }
        val all = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()_+-="
        val missing = all.filter { it !in seen }
        assertTrue("positions never varied across: $missing", missing.isEmpty())
    }

    @Test
    fun shufflesForcedCharactersOutOfThePrefix() {
        // The class characters are generated into slots 0..3 first, so without a shuffle
        // only those four would ever reach the start. Every pool character should show up
        // in the first four slots at least once across many runs.
        val all = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()_+-="
        val prefixChars = mutableSetOf<Char>()
        repeat(400) {
            prefixChars += PasswordGenerator.generate(length = 32).substring(0, 4).toSet()
        }
        val neverMoved = all.filter { it !in prefixChars }
        assertTrue("never appeared in the first 4 slots: $neverMoved", neverMoved.isEmpty())
    }

    @Test
    fun doesNotRepeat() {
        val seen = HashSet<String>()
        repeat(200) { assertTrue("collision", seen.add(PasswordGenerator.generate(length = 24))) }
    }

    @Test
    fun rejectsInvalidConfigurations() {
        assertThrows { PasswordGenerator.generate(length = 0) }
        assertThrows { PasswordGenerator.generate(length = PasswordGenerator.MAX_LENGTH + 1) }
        assertThrows {
            PasswordGenerator.generate(length = 8, uppercase = false, lowercase = false, digits = false, symbols = false)
        }
        // 4 classes enabled but only 3 characters requested.
        assertThrows { PasswordGenerator.generate(length = 3) }
    }

    @Test
    fun generateOrNullSwallowsInvalidInput() {
        assertNull(
            PasswordGenerator.generateOrNull(
                length = 4, uppercase = false, lowercase = false, digits = false, symbols = false,
            )
        )
        assertEquals(20, PasswordGenerator.generateOrNull(length = 20)!!.length)
    }

    @Test
    fun producesDifferentOutputForDifferentClassSelections() {
        assertNotEquals(
            PasswordGenerator.generate(length = 32, symbols = false),
            PasswordGenerator.generate(length = 32, symbols = true),
        )
    }

    @Test
    fun minimumLengthWithAllClassesSucceeds() {
        assertEquals(4, PasswordGenerator.generate(length = 4).length)
    }

    private fun assertThrows(block: () -> Unit) {
        try {
            block()
            fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
