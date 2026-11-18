package alibarznji.qasa.credentials

import alibarznji.qasa.vault.crypto.PasswordRules
import alibarznji.qasa.vault.crypto.PasswordSpec

/**
 * What a fill request offers on a login, sign-up or change-password form (design rule 6c),
 * as plain ids so the decision is unit tested without the framework.
 */
data class LoginOffers<T>(
    /** The fields saved logins and "Search Qasa" fill: username, current password, one-time code. */
    val savedLoginIds: List<T>,
    /** The new-password and confirm fields "Use a strong password" fills, in screen order. */
    val strongPasswordIds: List<T>,
    /**
     * R7: the login password R1 couldn't call new (it may still be a sign-up nobody could
     * tell). It gets "Use a strong password" after its saved logins, filled into this one
     * field only (no confirm is known). Empty when the form has a new-password field.
     */
    val fallbackStrongIds: List<T> = emptyList(),
)

/** One row of a login fill response, in the order the system shows them (R7, R8). */
sealed interface LoginRow<out E> {
    /** "Use a strong password"; [fallback] is R7's row on a login password. */
    data class StrongPassword(val fallback: Boolean) : LoginRow<Nothing>

    /** A saved login; [recent] is R8's "the login you just picked", filled without unlocking again. */
    data class Saved<E>(val entry: E, val recent: Boolean = false) : LoginRow<E>

    /** "Search Qasa", always last. */
    data object Search : LoginRow<Nothing>
}

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
    fun <T> login(form: AutofillForm<T>, focused: T? = null): LoginOffers<T> {
        val strong = form.fields.filter { it.type == AutofillFieldType.NewPassword }.map { it.id }
        val saved = if (form.hasLogin) {
            form.group(AutofillGroup.Login).filter { it.type != AutofillFieldType.NewPassword }.map { it.id }
        } else {
            emptyList()
        }
        // R7: one login password (the focused one, else the first); never a change form's
        // current password, which sits next to a new one.
        val passwords = form.fields.filter { it.type == AutofillFieldType.Password }
        val fallback = if (strong.isEmpty()) {
            listOfNotNull(passwords.firstOrNull { it.id == focused } ?: passwords.firstOrNull()).map { it.id }
        } else {
            emptyList()
        }
        return LoginOffers(savedLoginIds = saved, strongPasswordIds = strong, fallbackStrongIds = fallback)
    }

    /**
     * R7 and R8: the login rows in order. [matches] are the saved logins for this caller,
     * best first; [recent] is R8's login just picked for a field it didn't fill (first, once).
     * - a new field: "Use a strong password", then the rest of the form's rows;
     * - a login password with no match: "Use a strong password", then "Search Qasa";
     * - with matches: the logins, then "Use a strong password", then "Search Qasa".
     */
    fun <T, E> loginRows(offers: LoginOffers<T>, matches: List<E>, recent: E? = null, limit: Int = 5): List<LoginRow<E>> {
        val rows = mutableListOf<LoginRow<E>>()
        if (offers.strongPasswordIds.isNotEmpty()) rows += LoginRow.StrongPassword(fallback = false)
        if (offers.savedLoginIds.isEmpty()) return rows
        if (recent != null) rows += LoginRow.Saved(recent, recent = true)
        matches.filter { it != recent }.take(if (recent != null) limit - 1 else limit).forEach { rows += LoginRow.Saved(it) }
        if (offers.fallbackStrongIds.isNotEmpty()) rows += LoginRow.StrongPassword(fallback = true)
        rows += LoginRow.Search
        return rows
    }

    /** The strong-password pick: the same [password] in every new-password field (new and confirm). */
    fun <T> strongPasswordValues(form: AutofillForm<T>, password: String): Map<T, String> =
        form.fields.filter { it.type == AutofillFieldType.NewPassword }.associate { it.id to password }

    /** R7's pick: the [password] in [targets] only, when they are still password fields of [form]. */
    fun <T> fallbackStrongPasswordValues(form: AutofillForm<T>, targets: List<T>, password: String): Map<T, String> =
        form.fields.filter { it.id in targets && it.type == AutofillFieldType.Password }.associate { it.id to password }

    /**
     * R2: the strong password fits every new field: their smallest `maxlength`, largest
     * `minlength`, the site's `passwordrules` (first new field that has them), digits only
     * on a number keyboard.
     */
    fun <T> strongPasswordSpec(form: AutofillForm<T>): PasswordSpec =
        strongPasswordSpec(form.fields.filter { it.type == AutofillFieldType.NewPassword }.map { it.signals })

    /** R2 for any fields: R7's fallback fits the one login password it fills. */
    fun strongPasswordSpec(fields: List<FieldSignals>): PasswordSpec {
        val rules = fields.firstNotNullOfOrNull { PasswordRules.parse(it.htmlAttributes["passwordrules"]) }
        return PasswordSpec.forFields(
            maxLengths = fields.map { it.maxLength },
            minLengths = fields.map { it.htmlAttributes["minlength"]?.trim()?.toIntOrNull() },
            rules = rules,
            numeric = fields.isNotEmpty() && fields.all { AutofillFieldClassifier.isNumericField(it) },
        )
    }

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
