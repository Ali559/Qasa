package alibarznji.qasa.credentials

import alibarznji.qasa.vault.Country

/** A value for one field: text, or the index of a list entry (`<select>`, spinner). */
sealed interface FieldValue {
    data class Text(val text: String) : FieldValue
    data class ListIndex(val index: Int) : FieldValue
}

/**
 * The text each field type takes from decrypted index secrets (docs/autofill-plan.md §3.1).
 * Pure, so the mapping is unit tested; the service turns the result into `AutofillValue`s.
 */
object AutofillValues {
    /**
     * Values for the fields of one group. A login or card pick reads [primary] only; an
     * identity pick reads [primary] first and fills what it lacks from [profile], the first
     * item of each other identity kind (a name, email and phone with an address).
     */
    fun <T> valuesFor(
        fields: List<ClassifiedField<T>>,
        primary: AutofillSecrets,
        profile: List<AutofillSecrets> = emptyList(),
    ): Map<T, FieldValue> {
        val sources = listOf(primary) + profile
        val hasCountryCodeField = fields.any { it.type == AutofillFieldType.PhoneCountryCode }
        val out = LinkedHashMap<T, FieldValue>()
        for (field in fields) {
            val candidates = sources.asSequence()
                .mapNotNull { text(field.type, it, field.signals, hasCountryCodeField) }
                .firstOrNull() ?: continue
            val value = if (field.signals.options.isNotEmpty()) {
                optionIndex(field.signals.options, candidates)?.let { FieldValue.ListIndex(it) }
            } else {
                FieldValue.Text(candidates.first())
            }
            if (value != null) out[field.id] = value
        }
        return out
    }

    /**
     * The text for [type], best first (a list field picks the first that is one of its
     * options), or null when [s] has nothing for it.
     */
    fun text(type: AutofillFieldType, s: AutofillSecrets, signals: FieldSignals = FieldSignals(), separateCountryCode: Boolean = false): List<String>? {
        fun one(v: String?) = v?.trim()?.takeIf { it.isNotEmpty() }?.let { listOf(it) }
        return when (type) {
            AutofillFieldType.Username -> one(s.username) ?: one(s.email)
            AutofillFieldType.Password, AutofillFieldType.NewPassword -> one(s.password)
            AutofillFieldType.OneTimeCode -> null // filled with the current code, not a stored value
            AutofillFieldType.CardNumber -> one(s.number?.filter { it.isDigit() })
            AutofillFieldType.CardName -> one(s.holder)
            AutofillFieldType.CardSecurityCode -> one(s.securityCode)
            AutofillFieldType.CardExpiry -> expiry(s.expiry)?.let { (mm, yy) ->
                val fourDigitYear = (signals.maxLength ?: 0) >= 7 ||
                    signals.htmlAttributes.values.any { it.contains("yyyy", ignoreCase = true) }
                listOf(if (fourDigitYear) "$mm/20$yy" else "$mm/$yy")
            }
            AutofillFieldType.CardExpiryMonth -> expiry(s.expiry)?.let { (mm, _) ->
                listOf(mm, mm.trimStart('0'), monthName(mm.toInt()), monthName(mm.toInt()).take(3))
            }
            AutofillFieldType.CardExpiryYear -> expiry(s.expiry)?.let { (_, yy) ->
                val twoDigit = signals.maxLength == 2
                if (twoDigit) listOf(yy, "20$yy") else listOf("20$yy", yy)
            }
            AutofillFieldType.FullName -> one(listOfNotNull(s.firstName, s.middleName, s.lastName).joinToString(" "))
                ?: one(s.holder)
            AutofillFieldType.GivenName -> one(s.firstName)
            AutofillFieldType.MiddleName -> one(s.middleName)
            AutofillFieldType.FamilyName -> one(s.lastName)
            AutofillFieldType.Email -> one(s.email)
            AutofillFieldType.Phone -> s.phoneNumber?.trim()?.takeIf { it.isNotEmpty() }?.let { number ->
                val national = number.filter { it.isDigit() }
                val dial = s.phoneCountry?.let(Country::dialCode)
                when {
                    number.startsWith("+") -> listOf(number)
                    separateCountryCode || dial == null -> listOf(national)
                    else -> listOf(dial + national.trimStart('0'), national)
                }
            }
            AutofillFieldType.PhoneCountryCode -> s.phoneCountry?.let { code ->
                val dial = Country.dialCode(code)
                listOf(dial, dial.removePrefix("+"), code, Country.name(code))
            }
            AutofillFieldType.StreetAddress -> one(s.street)
            AutofillFieldType.AddressLine2 -> one(s.line2)
            AutofillFieldType.City -> one(s.city)
            AutofillFieldType.PostalCode -> one(s.postalCode)
            AutofillFieldType.Country -> s.country?.let { code ->
                val name = Country.name(code)
                if (signals.options.isEmpty()) listOf(name) else listOf(code, name)
            }
            AutofillFieldType.Organization -> one(s.organization)
        }
    }

    /** `MM/YY` → ("MM", "YY"); null for anything else. */
    fun expiry(raw: String?): Pair<String, String>? {
        val m = Regex("""^\s*(\d{1,2})\s*/\s*(\d{2}|\d{4})\s*$""").find(raw ?: return null) ?: return null
        val month = m.groupValues[1].toInt()
        if (month !in 1..12) return null
        return month.toString().padStart(2, '0') to m.groupValues[2].takeLast(2)
    }

    /**
     * The list entry matching one of [candidates], compared case-insensitively. A numeric
     * candidate also matches an entry with the same number ("03" and "3", "2027" and "27").
     */
    fun optionIndex(options: List<String>, candidates: List<String>): Int? {
        val normalized = options.map { it.trim().lowercase() }
        for (candidate in candidates) {
            val c = candidate.trim().lowercase()
            normalized.indexOf(c).takeIf { it >= 0 }?.let { return it }
            c.toIntOrNull()?.let { n ->
                normalized.indexOfFirst { it.toIntOrNull() == n }.takeIf { it >= 0 }?.let { return it }
            }
        }
        // "03 - March", "IQ - Iraq": an entry that starts with a candidate followed by a separator.
        for (candidate in candidates) {
            val c = candidate.trim().lowercase()
            if (c.length < 2) continue
            normalized.indexOfFirst { it.startsWith("$c ") || it.startsWith("$c-") || it.endsWith(" $c") }
                .takeIf { it >= 0 }?.let { return it }
        }
        return null
    }

    private fun monthName(month: Int): String = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )[month - 1]
}
