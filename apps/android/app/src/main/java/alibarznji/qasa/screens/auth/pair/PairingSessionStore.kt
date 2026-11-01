package alibarznji.qasa.screens.auth.pair

import kotlin.random.Random

/**
 * Port of iOS `PairingSessionStore` (`PairExistingVaultView.swift:259-278`): an in-memory
 * map of 6-digit codes to account ids, each valid for 10 minutes. Settings → Linked
 * Devices → Show pairing code publishes into it.
 *
 * Like iOS, nothing is sent to the server, so a code only resolves inside the process
 * that published it; across devices the account id field is what actually links.
 */
class PairingSessionStore(
    private val now: () -> Long = System::currentTimeMillis,
    private val random: Random = Random.Default,
) {
    private val codeToAccount = mutableMapOf<String, Pair<String, Long>>()

    @Synchronized
    fun publish(accountId: String): String {
        val code = "%06d".format(random.nextInt(0, 1_000_000))
        codeToAccount[code] = accountId.lowercase() to now() + TTL_MS
        return code
    }

    @Synchronized
    fun resolve(code: String): String? {
        val entry = codeToAccount[code] ?: return null
        return entry.first.takeIf { entry.second > now() }
    }

    @Synchronized
    fun currentPublishedCode(accountId: String): String? {
        val id = accountId.lowercase()
        return codeToAccount.entries
            .firstOrNull { it.value.first == id && it.value.second > now() }
            ?.key
    }

    companion object {
        private const val TTL_MS = 600_000L

        /** `PairingSessionStore.shared`. */
        val shared = PairingSessionStore()
    }
}
