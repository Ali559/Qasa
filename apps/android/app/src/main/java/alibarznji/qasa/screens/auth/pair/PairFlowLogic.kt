package alibarznji.qasa.screens.auth.pair

import alibarznji.qasa.R
import alibarznji.qasa.network.DeviceLinkCheckResponse
import androidx.annotation.StringRes

/**
 * The Link Device flow. [Intro] is the main path for a new device: join the other device
 * over Wi‑Fi, which copies its vault and account here ([Adopted]). [AccountId] is the
 * advanced path that only adopts a pasted account id ([NeedsPremium] / [Success]).
 */
enum class PairStep { Intro, AccountId, NeedsPremium, Success, Adopted }

/**
 * Where system back goes from each step; `null` means Cancel. From Premium required we
 * step back to the account field so another account can be tried; from Success and
 * Adopted there is nothing to undo, so back leaves like Cancel.
 */
fun PairStep.previous(): PairStep? = when (this) {
    PairStep.Intro -> null
    PairStep.AccountId -> PairStep.Intro
    PairStep.NeedsPremium -> PairStep.AccountId
    PairStep.Success -> null
    PairStep.Adopted -> null
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
