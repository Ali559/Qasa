package alibarznji.qasa.vault.crypto

import alibarznji.qasa.vault.VaultPayload

object VaultSearchIndex {
    private const val MIN_NGRAM = 2
    private const val MAX_NGRAM = 3

    fun tokenize(text: String): List<String> {
        val lowered = text.lowercase()
        val tokens = linkedSetOf<String>()
        val words = Regex("[^a-z0-9]+").split(lowered).filter { it.isNotEmpty() }
        for (word in words) {
            tokens.add(word)
            for (length in MIN_NGRAM..MAX_NGRAM) {
                if (word.length < length) continue
                var start = 0
                while (start + length <= word.length) {
                    tokens.add(word.substring(start, start + length))
                    start++
                }
            }
        }
        return tokens.sorted()
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
