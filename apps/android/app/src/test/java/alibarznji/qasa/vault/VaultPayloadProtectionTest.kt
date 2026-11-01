package alibarznji.qasa.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `isProtectedNote` decides whether a note is gated, so it must be true for exactly one
 * shape: a secure note with `protectItem` set.
 */
class VaultPayloadProtectionTest {

    @Test
    fun `protected note is gated`() {
        val note = VaultPayload.SecureNote(title = "Wifi", content = "secret", protectItem = true)
        assertTrue(note.isProtectedNote)
    }

    @Test
    fun `plain note is not gated`() {
        val note = VaultPayload.SecureNote(title = "Wifi", content = "secret", protectItem = false)
        assertFalse(note.isProtectedNote)
    }

    @Test
    fun `no other payload type is a protected note`() {
        assertFalse(VaultPayload.Login(password = "x").isProtectedNote)
        assertFalse(VaultPayload.Card(number = "4111").isProtectedNote)
        assertFalse(VaultPayload.BankAccount(accountNumber = "1234").isProtectedNote)
        assertFalse(VaultPayload.Name(firstName = "A").isProtectedNote)
    }

    @Test
    fun `a protected note never leaks its content in the subtitle`() {
        // The list row renders the subtitle, so this is the value that has to stay opaque.
        val note = VaultPayload.SecureNote(title = "Wifi", content = "hunter2", protectItem = true)
        assertEquals("Protected", note.subtitle())
    }
}
