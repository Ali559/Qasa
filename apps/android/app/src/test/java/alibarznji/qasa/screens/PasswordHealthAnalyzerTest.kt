package alibarznji.qasa.screens

import alibarznji.qasa.vault.VaultItemKind
import alibarznji.qasa.vault.VaultListRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Shared vectors with iOS `PasswordHealthAnalyzer`: the same vault must score the same
 * on both apps, so these expectations are derived from the Swift rules, not Android's.
 */
class PasswordHealthAnalyzerTest {

    private fun row(id: String) = VaultListRow(
        id = id,
        kind = VaultItemKind.Login,
        displayName = id,
        subtitle = "",
        collectionId = null,
        updatedAt = 0,
        createdAt = 0,
        lastUsedAt = null,
    )

    @Test
    fun weakRulesMatchIos() {
        // < 10 characters
        assertTrue(PasswordHealthAnalyzer.isWeak("Ab1!xyz9"))
        // common password
        assertTrue(PasswordHealthAnalyzer.isWeak("password1"))
        // one character class only, even when long
        assertTrue(PasswordHealthAnalyzer.isWeak("abcdefghijklmnop"))
        assertTrue(PasswordHealthAnalyzer.isWeak("12345678901234"))
        // 10–11 characters with only two classes
        assertTrue(PasswordHealthAnalyzer.isWeak("abcdefgh12"))
        // 10–11 characters with all three classes
        assertFalse(PasswordHealthAnalyzer.isWeak("abcdefg1!x"))
        // 12+ characters with two classes
        assertFalse(PasswordHealthAnalyzer.isWeak("abcdefghij12"))
        // Android used to call this strong (>= 8, not common, not all digits)
        assertTrue(PasswordHealthAnalyzer.isWeak("sunshine99"))
    }

    @Test
    fun scoreMatchesIosForSharedVault() {
        val logins = listOf(
            row("a") to "abcdefghij12",   // strong, reused
            row("b") to "abcdefghij12",   // strong, reused
            row("c") to "short",          // weak
            row("d") to "Zq9!vT4#mL2p",   // strong
            row("e") to "Pw7\$kD3@nX8r",  // strong
        )
        val report = PasswordHealthAnalyzer.analyze(logins)
        assertEquals(5, report.totalPasswords)
        assertEquals(1, report.weakCount)
        assertEquals(2, report.reusedCount)
        // 100 - 12 (weak) - 20 (2 reused) - Int((1 - 4/5) * 20). In IEEE doubles
        // (1 - 0.8) * 20 is 3.9999…, which truncates to 3 here and in Swift's Int(), so 65.
        assertEquals(65, report.score)
    }
}
