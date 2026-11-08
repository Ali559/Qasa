package alibarznji.qasa.vault.crypto

import alibarznji.qasa.vault.VaultPayload
import java.text.BreakIterator

object VaultSearchIndex {
    private const val MIN_NGRAM = 2
    private const val MAX_NGRAM = 3

    /**
     * Matches iOS `VaultSearchIndex.tokenize` and the desktop core (`core/qasa-vault`): split on
     * anything outside Unicode letters, marks and numbers, and count n-gram characters as
     * grapheme clusters, so Arabic and Kurdish are searchable.
     */
    fun tokenize(text: String): List<String> {
        val lowered = text.lowercase()
        val tokens = sortedSetOf<String>()
        for (word in words(lowered)) {
            tokens.add(word)
            val graphemes = graphemes(word)
            for (length in MIN_NGRAM..MAX_NGRAM) {
                for (start in 0..graphemes.size - length) {
                    tokens.add(graphemes.subList(start, start + length).joinToString(""))
                }
            }
        }
        return tokens.toList()
    }

    private fun isAlphanumeric(codePoint: Int): Boolean = when (Character.getType(codePoint).toByte()) {
        Character.UPPERCASE_LETTER, Character.LOWERCASE_LETTER, Character.TITLECASE_LETTER,
        Character.MODIFIER_LETTER, Character.OTHER_LETTER,
        Character.NON_SPACING_MARK, Character.COMBINING_SPACING_MARK, Character.ENCLOSING_MARK,
        Character.DECIMAL_DIGIT_NUMBER, Character.LETTER_NUMBER, Character.OTHER_NUMBER -> true
        else -> false
    }

    private fun words(text: String): List<String> {
        val words = mutableListOf<String>()
        val current = StringBuilder()
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (isAlphanumeric(cp)) {
                current.appendCodePoint(cp)
            } else if (current.isNotEmpty()) {
                words.add(current.toString())
                current.clear()
            }
            i += Character.charCount(cp)
        }
        if (current.isNotEmpty()) words.add(current.toString())
        return words
    }

    private fun graphemes(word: String): List<String> {
        val it = BreakIterator.getCharacterInstance()
        it.setText(word)
        val out = mutableListOf<String>()
        var start = it.first()
        var end = it.next()
        while (end != BreakIterator.DONE) {
            out.add(word.substring(start, end))
            start = end
            end = it.next()
        }
        return out
    }

    fun tokenHashes(text: String, searchKey: ByteArray): List<String> =
        tokenize(text).map { hashToken(it, searchKey) }

    fun hashToken(token: String, searchKey: ByteArray): String {
        val mac = MasterKeyDeriver.hmacSha256(searchKey, token.toByteArray(Charsets.UTF_8))
        return mac.joinToString("") { "%02x".format(it) }
    }

    fun searchableStrings(payload: VaultPayload): List<String> = when (payload) {
        is VaultPayload.Login -> listOf(payload.itemName, payload.email, payload.username, payload.website, payload.note)
        is VaultPayload.SecureNote -> listOf(payload.title, payload.content)
        is VaultPayload.Card -> listOf(payload.itemName, payload.accountHolder, payload.note)
        is VaultPayload.BankAccount -> listOf(payload.itemName, payload.accountHolder, payload.bankName)
        is VaultPayload.Name -> listOf(payload.firstName, payload.middleName, payload.lastName, payload.defaultUsername)
        is VaultPayload.PersonalEmail -> listOf(payload.itemName, payload.email)
        is VaultPayload.Phone -> listOf(payload.itemName, payload.number)
        is VaultPayload.Address -> listOf(payload.itemName, payload.address, payload.city, payload.recipient)
        is VaultPayload.Company -> listOf(payload.companyName, payload.jobTitle)
        is VaultPayload.Website -> listOf(payload.itemName, payload.websiteURL)
        is VaultPayload.IdCard -> listOf(payload.name, payload.number)
        is VaultPayload.Passport -> listOf(payload.name, payload.number)
        is VaultPayload.DriverLicense -> listOf(payload.name, payload.number)
    }
}
