package alibarznji.qasa.screens.breach

import alibarznji.qasa.vault.VaultPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** rotate_spec step 2: Login items whose email OR username equals the card email (trim, any case). */
class RotatePasswordLogicTest {
    private fun login(name: String, email: String = "", username: String = "") =
        VaultPayload.Login(itemName = name, email = email, username = username, password = "old")

    @Test fun matchesEmailOrUsernameTrimmedAndCaseInsensitive() {
        val items = listOf(
            "1" to login("Zeta", email = "  Ali@Example.com "),
            "2" to login("alpha", username = "ali@example.COM"),
            "3" to login("Other", email = "someone@example.com", username = "ali"),
        )
        val result = matchRotateCandidates(" ALI@example.com\n", items)
        assertEquals(listOf("2", "1"), result.map { it.id })
        assertEquals("alpha", result.first().name)
        assertEquals("ali@example.COM", result.first().subtitle)
    }

    @Test fun ignoresNonLoginItemsEvenWithTheSameEmail() {
        val items = listOf(
            "p" to VaultPayload.PersonalEmail(email = "ali@example.com"),
            "n" to VaultPayload.SecureNote(title = "ali@example.com", content = "ali@example.com"),
        )
        assertTrue(matchRotateCandidates("ali@example.com", items).isEmpty())
    }

    @Test fun blankEmailMatchesNothing() {
        val items = listOf("1" to login("Blank"))
        assertTrue(matchRotateCandidates("   ", items).isEmpty())
    }

    @Test fun untitledLoginUsesDisplayName() {
        val result = matchRotateCandidates("a@b.co", listOf("1" to login("", email = "a@b.co")))
        assertEquals("Untitled", result.single().name)
    }
}
