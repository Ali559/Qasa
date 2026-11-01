package alibarznji.qasa.screens.vault.list

import alibarznji.qasa.R
import alibarznji.qasa.credentials.AutofillIndexEntry
import alibarznji.qasa.vault.VaultItemKind
import alibarznji.qasa.vault.VaultListRow
import androidx.annotation.StringRes

/** Port of iOS `AutofillContextHint` (`SharedAutofill/AutofillContextMatcher.swift:3`). */
internal enum class AutofillContextHint { Login, PaymentCard, Bank, Personal, Unknown }

/** Port of iOS `SuggestedContext` (`Services/SuggestedContext.swift`). */
internal enum class SuggestedContext(
    @StringRes val titleRes: Int?,
    val autofillHint: AutofillContextHint,
    val serviceIdentifiers: List<String>,
) {
    None(null, AutofillContextHint.Unknown, emptyList()),
    Checkout(R.string.vault_suggestedcheckout, AutofillContextHint.PaymentCard, emptyList()),
    GoogleSignIn(R.string.vault_suggestedgoogle, AutofillContextHint.Login, listOf("accounts.google.com", "google.com")),
}

internal interface SuggestedContextProviding {
    val context: SuggestedContext
}

/** iOS `MockSuggestedContextProvider`: the list always suggests for checkout today. */
internal class MockSuggestedContextProvider : SuggestedContextProviding {
    override val context: SuggestedContext = SuggestedContext.Checkout
}

/** Port of iOS `VaultSuggestionEngine` (`Services/SuggestedContext.swift:46`). */
internal object VaultSuggestionEngine {
    fun suggest(
        rows: List<VaultListRow>,
        context: SuggestedContext,
        index: List<AutofillIndexEntry>,
    ): List<VaultListRow> {
        if (context == SuggestedContext.None) return emptyList()

        // Prefer ranking via the shared Autofill matcher when an index exists.
        if (index.isNotEmpty()) {
            val ranked = AutofillContextMatcher.candidates(index, context.serviceIdentifiers, context.autofillHint)
            val byId = rows.associateBy { it.id }
            val matched = ranked.mapNotNull { byId[it.id] }
            if (matched.isNotEmpty()) return matched
        }

        return when (context) {
            SuggestedContext.None -> emptyList()
            SuggestedContext.Checkout -> rows.filter { it.kind == VaultItemKind.Payment }
            SuggestedContext.GoogleSignIn -> rows.filter { row ->
                if (row.kind != VaultItemKind.Login) return@filter false
                val hay = (row.subtitle + row.displayName).lowercase()
                hay.contains("google") || hay.contains("gmail") || hay.contains("accounts.google")
            }
        }
    }
}

/**
 * Port of iOS `AutofillContextMatcher.candidates` — only the ranking the list needs
 * (the autofill services do their own matching).
 */
internal object AutofillContextMatcher {
    private data class Scored(val entry: AutofillIndexEntry, val score: Int)

    fun candidates(
        entries: List<AutofillIndexEntry>,
        serviceIdentifiers: List<String>,
        hint: AutofillContextHint,
    ): List<AutofillIndexEntry> {
        val hosts = serviceIdentifiers.mapNotNull { normalizeHost(it) }
        var scored = entries.mapNotNull { entry ->
            val score = scoreEntry(entry, hosts, hint)
            if (score <= 0 && hint != AutofillContextHint.Unknown && hosts.isNotEmpty()) return@mapNotNull null
            val finalScore = if (score > 0) score else baseScore(entry.kind, hint)
            if (finalScore > 0) Scored(entry, finalScore) else null
        }
        if (scored.isEmpty()) {
            // Fallback: show type-appropriate items even without a host match.
            scored = entries.mapNotNull { entry ->
                val s = baseScore(entry.kind, hint)
                if (s <= 0 && hint != AutofillContextHint.Unknown) null else Scored(entry, maxOf(s, 1))
            }
        }
        return scored
            .sortedWith(compareByDescending<Scored> { it.score }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.entry.title })
            .map { it.entry }
    }

    fun normalizeHost(raw: String): String? {
        var s = raw.trim().lowercase()
        if (s.isEmpty()) return null
        if (s.startsWith("http://") || s.startsWith("https://")) {
            runCatching { java.net.URI(s).host }.getOrNull()?.let { s = it }
        }
        if (s.startsWith("www.")) s = s.drop(4)
        return s.ifEmpty { null }
    }

    private fun registrableDomain(host: String): String {
        val parts = host.split(".")
        return if (parts.size < 2) host else parts.takeLast(2).joinToString(".")
    }

    private fun scoreEntry(entry: AutofillIndexEntry, hosts: List<String>, hint: AutofillContextHint): Int {
        var score = baseScore(entry.kind, hint)
        val entryHost = entry.websiteHost
        if (entry.kind != "login" || entryHost == null || hosts.isEmpty()) return score
        val normalized = normalizeHost(entryHost) ?: entryHost
        for (host in hosts) {
            score += when {
                normalized == host -> 100
                normalized.endsWith(".$host") || host.endsWith(".$normalized") -> 80
                registrableDomain(normalized) == registrableDomain(host) -> 60
                normalized.contains(host) || host.contains(normalized) -> 30
                (entry.title + " " + entry.subtitle).lowercase()
                    .let { it.contains(registrableDomain(host)) || it.contains(host) } -> 20
                else -> 0
            }
        }
        return score
    }

    private fun baseScore(kind: String, hint: AutofillContextHint): Int = when (hint) {
        AutofillContextHint.Login -> if (kind == "login") 10 else 0
        AutofillContextHint.PaymentCard -> when (kind) { "card" -> 10; "bankAccount" -> 3; else -> 0 }
        AutofillContextHint.Bank -> when (kind) { "bankAccount" -> 10; "card" -> 3; else -> 0 }
        AutofillContextHint.Personal -> 0
        AutofillContextHint.Unknown -> 5
    }
}
