package alibarznji.qasa.credentials

/**
 * R4's two-screen sign-ups and logins (docs/autofill-hardening.md, Android 12+, apps only):
 * a username screen delays its save (`SaveInfo.FLAG_DELAY_SAVE`) and keeps the username
 * field's id in the response's client state; the password screen's request reads it back
 * and saves both as one login. Pure, over plain ids, so it is unit tested; the service puts
 * the id into and out of the `Bundle`.
 */
object TwoScreenSave {
    /** Client-state keys (the field's `AutofillId` and the app it belongs to). */
    const val KEY_USERNAME_ID = "qasa.delayedSave.usernameId"
    const val KEY_PACKAGE = "qasa.delayedSave.package"

    /** What the first screen's delayed save watches. */
    data class Delayed<T>(val usernameId: T, val isEmail: Boolean)

    /** Only native apps: a browser's pages are one session per page, and its fields are web fields. */
    fun applies(caller: AutofillCaller, sdkInt: Int, compatMode: Boolean): Boolean =
        sdkInt >= 31 && !compatMode && caller is AutofillCaller.App && caller.packageName !in AutofillMatcher.trustedBrowsers

    /** A screen with a username or email and no password: its save waits for the next screen. */
    fun <T> delayedOf(form: AutofillForm<T>): Delayed<T>? {
        if (form.fields.any { it.type == AutofillFieldType.Password || it.type == AutofillFieldType.NewPassword }) return null
        val username = form.fields.firstOrNull { it.type == AutofillFieldType.Username }
            ?: form.fields.singleOrNull { it.type == AutofillFieldType.Email }
            ?: return null
        val isEmail = username.type == AutofillFieldType.Email || AutofillFieldClassifier.classify(username.signals) == AutofillFieldType.Email
        return Delayed(username.id, isEmail)
    }

    /**
     * The password screen's save plan with the earlier screen's username: when this screen
     * has a password but no username of its own, [previousUsername] (from the client state,
     * same app) becomes the plan's username; both are then required ids.
     */
    fun <T> plan(form: AutofillForm<T>, previousUsername: T?): SavePlan<T>? {
        val plan = AutofillOffers.save(form) ?: return null
        if (plan.usernameId != null || previousUsername == null) return plan
        return plan.copy(usernameId = previousUsername)
    }

    /** The username a save reads: this screen's, else the earlier screen's value for [previousUsername]. */
    fun <T> username(current: String?, earlierValues: List<Map<T, String>>, previousUsername: T?, earlierUsernames: List<String?>): String {
        current?.takeIf { it.isNotEmpty() }?.let { return it }
        if (previousUsername != null) {
            earlierValues.asReversed().firstNotNullOfOrNull { it[previousUsername]?.takeIf { v -> v.isNotEmpty() } }?.let { return it }
        }
        return earlierUsernames.asReversed().firstOrNull { !it.isNullOrEmpty() }.orEmpty()
    }
}
