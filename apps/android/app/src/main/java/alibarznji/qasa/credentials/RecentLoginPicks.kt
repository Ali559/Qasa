package alibarznji.qasa.credentials

/**
 * R8 (docs/autofill-hardening.md): the login just picked, so an app that reports one field
 * at a time (Flutter or React Native without an autofill group) gets its password from the
 * same login on its next request, first and without unlocking again.
 *
 * Only the item's id, never its values: they are read from the vault when the dataset is
 * built, and only while it is unlocked.
 */
data class RecentLoginPick(
    val itemId: String,
    /** [RecentLoginPicks.callerKey]: the app's package, or the browser's site. */
    val callerKey: String,
    /** The field types that pick filled (username, password, one-time code). */
    val filled: Set<AutofillFieldType>,
    val pickedAt: Long,
)

/** The rules, with the clock passed in so they are unit tested. */
class RecentLoginPickCache(private val ttlMillis: Long = TTL_MILLIS) {
    private var last: RecentLoginPick? = null

    @Synchronized
    fun record(itemId: String, caller: AutofillCaller, filled: Set<AutofillFieldType>, now: Long) {
        if (filled.isEmpty()) return
        val key = RecentLoginPicks.callerKey(caller)
        val previous = live(now)?.takeIf { it.itemId == itemId && it.callerKey == key }
        last = RecentLoginPick(itemId, key, filled + previous?.filled.orEmpty(), now)
    }

    /**
     * A pick of the cached login reported later by the fill event history (R8's own row,
     * which has no authentication): adds what it filled, never another item or caller.
     */
    @Synchronized
    fun recordAgain(itemId: String, filled: Set<AutofillFieldType>, now: Long) {
        val current = live(now)?.takeIf { it.itemId == itemId } ?: return
        // Not a new pick time: the history is read on every request, and the 2 minutes run
        // from the pick the user made through the unlock.
        last = current.copy(filled = current.filled + filled)
    }

    /**
     * The login to put first for [caller], whose [focusedType] field asks: the cached pick,
     * when it is the same caller, under [ttlMillis] old, and didn't fill a field of that type.
     */
    @Synchronized
    fun lookup(caller: AutofillCaller, focusedType: AutofillFieldType?, now: Long): String? {
        val pick = live(now) ?: return null
        if (focusedType == null || focusedType.group != AutofillGroup.Login || focusedType == AutofillFieldType.NewPassword) return null
        if (pick.callerKey != RecentLoginPicks.callerKey(caller)) return null
        return pick.itemId.takeIf { focusedType !in pick.filled }
    }

    @Synchronized
    fun current(now: Long): RecentLoginPick? = live(now)

    @Synchronized
    fun clear() {
        last = null
    }

    private fun live(now: Long): RecentLoginPick? {
        val pick = last ?: return null
        if (now - pick.pickedAt !in 0 until ttlMillis) {
            last = null
            return null
        }
        return pick
    }

    companion object {
        const val TTL_MILLIS = 2L * 60 * 1000
    }
}

/** The process-wide cache (in memory only; [clear]ed by `VaultSession.lock`). */
object RecentLoginPicks {
    /** Dataset id prefix of R8's own row, so the fill event history reports its pick. */
    const val DATASET_PREFIX = "qasa-recent-"

    val cache = RecentLoginPickCache()

    fun clear() = cache.clear()

    /** The app's package, or the site for a browser (one browser's tab is one site). */
    fun callerKey(caller: AutofillCaller): String = when (caller) {
        is AutofillCaller.App -> "app:" + caller.packageName
        is AutofillCaller.Web -> "web:" + caller.host
    }

    /** R8's dataset id: the item and the field types the row fills (no values). */
    fun datasetId(itemId: String, filled: Set<AutofillFieldType>): String =
        DATASET_PREFIX + filled.joinToString(",") { it.name } + ":" + itemId

    /** The item and field types of a [datasetId], or null for any other dataset. */
    fun parseDatasetId(id: String): Pair<String, Set<AutofillFieldType>>? {
        if (!id.startsWith(DATASET_PREFIX)) return null
        val rest = id.removePrefix(DATASET_PREFIX)
        val types = rest.substringBefore(':', "")
        val item = rest.substringAfter(':', "").ifEmpty { return null }
        val filled = types.split(',').mapNotNull { name -> AutofillFieldType.entries.firstOrNull { it.name == name } }.toSet()
        return item to filled
    }
}
