package alibarznji.qasa.screens

import alibarznji.qasa.vault.VaultListRow

data class PasswordHealthReport(
    val totalPasswords: Int,
    val reusedCount: Int,
    val weakCount: Int,
    val score: Int,
    val reusedItems: List<VaultListRow>,
    val weakItems: List<VaultListRow>,
) {
    companion object {
        val empty = PasswordHealthReport(0, 0, 0, 100, emptyList(), emptyList())
    }
}

object PasswordHealthAnalyzer {
    private val common = setOf(
        "password", "password1", "123456", "12345678", "qwerty", "abc123",
        "letmein", "admin", "welcome", "iloveyou", "monkey", "dragon",
    )

    fun analyze(logins: List<Pair<VaultListRow, String>>): PasswordHealthReport {
        if (logins.isEmpty()) return PasswordHealthReport.empty
        val frequency = logins.groupingBy { it.second }.eachCount()
        val reusedItems = logins.filter { (frequency[it.second] ?: 0) > 1 }.map { it.first }
            .sortedBy { it.displayName.lowercase() }
        val weakItems = logins.filter { isWeak(it.second) }.map { it.first }
            .sortedBy { it.displayName.lowercase() }
        val reusedCount = reusedItems.map { it.id }.toSet().size
        val weakCount = weakItems.map { it.id }.toSet().size
        var score = 100
        score -= weakCount * 12
        score -= reusedCount * 10
        if (logins.size >= 5) {
            val uniqueRatio = logins.map { it.second }.toSet().size.toDouble() / logins.size
            score -= ((1 - uniqueRatio) * 20).toInt()
        }
        score = score.coerceIn(0, 100)
        return PasswordHealthReport(logins.size, reusedCount, weakCount, score, reusedItems, weakItems)
    }

    /**
     * Mirrors iOS `PasswordHealthAnalyzer.isWeak` exactly, so one vault gets the same
     * score and weak count on both apps: under 10 characters, a common password, fewer
     * than two character classes, or under 12 characters without all three classes.
     */
    internal fun isWeak(password: String): Boolean {
        val length = password.codePointCount(0, password.length)
        if (length < 10) return true
        if (password.lowercase() in common) return true
        val hasLetter = password.any { it.isLetter() }
        val hasNumber = password.any { it.isDigit() }
        val hasSymbol = password.any { !it.isLetterOrDigit() }
        val classes = listOf(hasLetter, hasNumber, hasSymbol).count { it }
        if (classes < 2) return true
        if (length < 12 && classes < 3) return true
        return false
    }
}
