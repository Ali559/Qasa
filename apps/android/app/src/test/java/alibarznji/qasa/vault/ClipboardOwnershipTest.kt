package alibarznji.qasa.vault

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wipe must never destroy a clip the user replaced after our copy. iOS gets this from
 * `UIPasteboard.changeCount`; Android has no change count, so identity of the value is
 * the whole ownership check.
 */
class ClipboardOwnershipTest {

    @Test
    fun `wipes while the copied value is still the clip`() {
        assertTrue(ClipboardOwnership.shouldWipe("4111 1111", "4111 1111"))
    }

    @Test
    fun `refuses to wipe a value the user replaced`() {
        assertFalse(ClipboardOwnership.shouldWipe("4111 1111", "my own note"))
    }

    @Test
    fun `refuses to wipe an empty clip`() {
        assertFalse(ClipboardOwnership.shouldWipe("4111 1111", ""))
    }

    @Test
    fun `refuses to wipe when ownership was never taken`() {
        assertFalse(ClipboardOwnership.shouldWipe(null, "4111 1111"))
    }

    @Test
    fun `an unreadable clipboard is never treated as ours`() {
        // Android 10+ denies clipboard reads to a backgrounded app; a null read means
        // "unknown", and the caller must retry rather than wipe.
        assertFalse(ClipboardOwnership.stillOwns("4111 1111", null))
    }

    @Test
    fun `identical text in a different clip still counts as ours`() {
        // The value is the only ownership signal Android exposes, so this is the honest
        // limit of the check rather than a bug.
        assertTrue(ClipboardOwnership.stillOwns("same", "same"))
    }
}
