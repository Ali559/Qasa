package alibarznji.qasa.credentials

/**
 * What a fill request offers on a login, sign-up or change-password form (design rule 6c),
 * as plain ids so the decision is unit tested without the framework.
 */
data class LoginOffers<T>(
    /** The fields saved logins and "Search Qasa" fill: username, current password, one-time code. */
    val savedLoginIds: List<T>,
    /** The new-password and confirm fields "Use a strong password" fills, in screen order. */
    val strongPasswordIds: List<T>,
)

/** The fields a [android.service.autofill.SaveInfo] watches so Android offers to save. */
data class SavePlan<T>(
    /** The password the save reads: the new password on a sign-up or change form. */
    val passwordId: T,
    val usernameId: T?,
    /** Watched too, so a change typed into any of them counts as "the user changed something". */
    val optionalIds: List<T>,
)

object AutofillOffers {
    /**
     * Saved logins never go on a new-password field (rule 6c): such a field only gets the
     * strong password. The rest of the login group (a sign-up form's username, a change
     * form's current password) keeps its saved logins.
     */
    fun <T> login(form: AutofillForm<T>): LoginOffers<T> {
        val strong = form.fields.filter { it.type == AutofillFieldType.NewPassword }.map { it.id }
        val saved = if (form.hasLogin) {
            form.group(AutofillGroup.Login).filter { it.type != AutofillFieldType.NewPassword }.map { it.id }
        } else {
            emptyList()
        }
        return LoginOffers(savedLoginIds = saved, strongPasswordIds = strong)
    }

    /** The strong-password pick: the same [password] in every new-password field (new and confirm). */
    fun <T> strongPasswordValues(form: AutofillForm<T>, password: String): Map<T, String> =
        form.fields.filter { it.type == AutofillFieldType.NewPassword }.associate { it.id to password }

    /** What to watch for a save, or null when the form has no password to save. */
    fun <T> save(form: AutofillForm<T>): SavePlan<T>? {
        val password = form.passwordField ?: return null
        val username = form.group(AutofillGroup.Login).firstOrNull { it.type == AutofillFieldType.Username }
        // On a change form the current password is watched as well: typing it is a change,
        // so the save still shows when the new password came from our own suggestion.
        val current = form.fields.filter { it.type == AutofillFieldType.Password && it.id != password.id }
        return SavePlan(
            passwordId = password.id,
            usernameId = username?.id,
            optionalIds = listOfNotNull(username?.id) + current.map { it.id },
        )
    }
}
