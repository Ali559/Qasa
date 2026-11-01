package alibarznji.qasa.screens.breach

import alibarznji.qasa.vault.VaultPayload

/** A vault Login the Rotate Password sheet can update (rotate_spec step 2). */
data class RotateCandidate(val id: String, val name: String, val subtitle: String)

/**
 * The Rotate Password matching rule shared with iOS: only Login items whose `email` OR `username`
 * equals the breached email after trimming, ignoring case. Other kinds (e.g. a PersonalEmail
 * holding the same address) have no password to rotate. Sorted by item name.
 */
fun matchRotateCandidates(email: String, items: List<Pair<String, VaultPayload>>): List<RotateCandidate> {
    val target = email.trim()
    if (target.isEmpty()) return emptyList()
    fun matches(value: String) = value.trim().equals(target, ignoreCase = true)
    return items
        .mapNotNull { (id, payload) ->
            val login = payload as? VaultPayload.Login ?: return@mapNotNull null
            if (!matches(login.email) && !matches(login.username)) return@mapNotNull null
            RotateCandidate(id = id, name = login.displayName(), subtitle = login.subtitle())
        }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
}
