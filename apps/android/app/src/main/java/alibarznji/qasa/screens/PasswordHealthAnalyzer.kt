package alibarznji.qasa.screens

import alibarznji.qasa.vault.VaultListRow

data class PasswordHealthReport(
    val totalPasswords: Int,
    val reusedCount: Int,
    val weakCount: Int,
    val score: Int,
    val reusedItems: List<VaultListRow>,
    val weakItems: List<VaultListRow>,
    /** Logins whose password is in Pwned Passwords, most-seen first. */
    val breachedItems: List<VaultListRow> = emptyList(),
    /** Pwned Passwords count per row id, for [breachedItems]. */
    val breachCounts: Map<String, Int> = emptyMap(),
    /** The breach check ran and failed (usually offline). Breached stays 0 then. */
    val breachCheckFailed: Boolean = false,
    /**
     * Free tier: the breach check is Premium, so it never ran. Breached stays 0, the score
     * has no breach deduction, and the UI shows the locked state instead of a count.
     */
    val breachLocked: Boolean = false,
) {
    val breachedCount: Int get() = breachedItems.size

    companion object {
        val empty = PasswordHealthReport(0, 0, 0, 100, emptyList(), emptyList())
    }
}

object PasswordHealthAnalyzer {
    private val common = setOf(
        "password", "password1", "123456", "12345678", "qwerty", "abc123",
        "letmein", "admin", "welcome", "iloveyou", "monkey", "dragon",
    )

    /**
     * Synchronous, like iOS. [breachCounts] maps a password to its Pwned Passwords count;
     * null means not checked yet. Callers run [alibarznji.qasa.breach.PwnedPasswordsService]
     * themselves and re-run this with the result.
     */
    fun analyze(
        logins: List<Pair<VaultListRow, String>>,
        breachCounts: Map<String, Int>? = null,
    ): PasswordHealthReport {
        if (logins.isEmpty()) return PasswordHealthReport.empty
        val frequency = logins.groupingBy { it.second }.eachCount()
        val reusedItems = logins.filter { (frequency[it.second] ?: 0) > 1 }.map { it.first }
            .sortedBy { it.displayName.lowercase() }
        val weakItems = logins.filter { isWeak(it.second) }.map { it.first }
            .sortedBy { it.displayName.lowercase() }
        val rowCounts = buildMap {
            logins.forEach { (row, password) -> breachCounts?.get(password)?.let { put(row.id, it) } }
        }
        val breachedItems = logins.map { it.first }.filter { it.id in rowCounts }
            .sortedByDescending { rowCounts[it.id] ?: 0 }
        val reusedCount = reusedItems.map { it.id }.toSet().size
        val weakCount = weakItems.map { it.id }.toSet().size
        var score = 100
        score -= weakCount * 12
        score -= reusedCount * 10
        score -= breachedItems.size * 15
        if (logins.size >= 5) {
            val uniqueRatio = logins.map { it.second }.toSet().size.toDouble() / logins.size
            score -= ((1 - uniqueRatio) * 20).toInt()
        }
        score = score.coerceIn(0, 100)
        return PasswordHealthReport(
            logins.size, reusedCount, weakCount, score, reusedItems, weakItems,
            breachedItems = breachedItems,
            breachCounts = rowCounts,
        )
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
