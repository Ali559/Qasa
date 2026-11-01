package alibarznji.qasa.screens.auth.pair

import alibarznji.qasa.R
import alibarznji.qasa.network.DeviceLinkCheckResponse
import androidx.annotation.StringRes

/** `PairExistingVaultView.Step` (`PairExistingVaultView.swift:18`). */
enum class PairStep { Intro, EnterCode, NeedsPremium, Success }

/**
 * Where system back goes from each step; `null` means Cancel. iOS shows this flow as a
 * full-screen cover with only a Cancel button, so back never skips past the intro. From
 * Premium required we step back to the code field so another account can be tried, and
 * from Success there is nothing to undo (the account id is already adopted), so it
 * leaves like Cancel does on iOS.
 */
fun PairStep.previous(): PairStep? = when (this) {
    PairStep.Intro -> null
    PairStep.EnterCode -> PairStep.Intro
    PairStep.NeedsPremium -> PairStep.EnterCode
    PairStep.Success -> null
}

/** Result of the server eligibility probe, mapped to what the flow shows. */
sealed interface LinkOutcome {
    data object Linked : LinkOutcome
    data object NeedsPremium : LinkOutcome
    data class Error(@StringRes val message: Int) : LinkOutcome
}

private val CanonicalUuid =
    Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

/**
 * `UUID(uuidString:)` only accepts the canonical 8-4-4-4-12 form. `java.util.UUID.fromString`
 * also accepts short groups like "1-2-3-4-5", which iOS would reject, so match the form
 * explicitly. Returns the lowercase id iOS stores (`VaultKeychainStore.accountId`).
 */
fun parseAccountUuid(text: String): String? {
    val trimmed = text.trim()
    return if (CanonicalUuid.matches(trimmed)) trimmed.lowercase() else null
}

/**
 * `applyPairing` (`PairExistingVaultView.swift:224-231`): a pasted account id wins over the
 * code; otherwise the code is looked up in the pairing session store.
 */
fun resolvePairTarget(accountIdText: String, code: String, store: PairingSessionStore): String? =
    parseAccountUuid(accountIdText) ?: store.resolve(code.trim())

/**
 * `PairExistingVaultView.swift:241-249`. The server's `error` is a code, never display
 * text: Premium required when the code says so or the tier is not premium, otherwise the
 * device-limit message.
 */
fun classifyLinkCheck(check: DeviceLinkCheckResponse): LinkOutcome = when {
    check.eligible -> LinkOutcome.Linked
    check.error == "premium_required" || !check.tier.equals("premium", ignoreCase = true) ->
        LinkOutcome.NeedsPremium
    else -> LinkOutcome.Error(R.string.pair_limitexceeded)
}
