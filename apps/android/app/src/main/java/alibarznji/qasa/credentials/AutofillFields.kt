package alibarznji.qasa.credentials

import android.text.InputType

/**
 * Which part of a form a field belongs to (docs/autofill-plan.md §3.1). A dataset only
 * carries the fields of one group, so the system shows logins under a password field,
 * cards under a card field and profiles under an address field.
 */
enum class AutofillGroup { Login, Card, Identity }

/** The field types of plan §3.1, named after their HTML `autocomplete` tokens. */
enum class AutofillFieldType(val group: AutofillGroup) {
    Username(AutofillGroup.Login),
    Password(AutofillGroup.Login),
    NewPassword(AutofillGroup.Login),
    OneTimeCode(AutofillGroup.Login),
    CardNumber(AutofillGroup.Card),
    CardName(AutofillGroup.Card),
    CardExpiry(AutofillGroup.Card),
    CardExpiryMonth(AutofillGroup.Card),
    CardExpiryYear(AutofillGroup.Card),
    CardSecurityCode(AutofillGroup.Card),
    FullName(AutofillGroup.Identity),
    GivenName(AutofillGroup.Identity),
    MiddleName(AutofillGroup.Identity),
    FamilyName(AutofillGroup.Identity),
    Email(AutofillGroup.Identity),
    Phone(AutofillGroup.Identity),
    PhoneCountryCode(AutofillGroup.Identity),
    StreetAddress(AutofillGroup.Identity),
    AddressLine2(AutofillGroup.Identity),
    City(AutofillGroup.Identity),
    PostalCode(AutofillGroup.Identity),
    Country(AutofillGroup.Identity),
    Organization(AutofillGroup.Identity),
}

/**
 * What a field says about itself: the platform's hints, the HTML attributes a browser
 * reports, and the developer-visible names. Plain values so classification runs (and is
 * tested) without an `AssistStructure`.
 */
data class FieldSignals(
    val autofillHints: List<String> = emptyList(),
    /** HTML attributes, keys lowercased (`type`, `name`, `id`, `autocomplete`, `label`, …). */
    val htmlAttributes: Map<String, String> = emptyMap(),
    val hint: String? = null,
    val idEntry: String? = null,
    val inputType: Int = 0,
    /** `<select>` / spinner entries, when the field is a list. */
    val options: List<String> = emptyList(),
    val maxLength: Int? = null,
)

object AutofillFieldClassifier {
    /** Android `View.AUTOFILL_HINT_*`, androidx `HintConstants` and HTML `autocomplete` tokens. */
    private val hintTable: Map<String, AutofillFieldType> = buildMap {
        fun put(type: AutofillFieldType, vararg keys: String) = keys.forEach { put(it.lowercase(), type) }
        put(AutofillFieldType.Username, "username", "newUsername", "emailOrUsername")
        put(AutofillFieldType.Password, "password", "current-password", "currentPassword")
        put(AutofillFieldType.NewPassword, "newPassword", "new-password")
        put(AutofillFieldType.OneTimeCode, "smsOTPCode", "one-time-code", "2faAppOTPCode", "emailOTPCode", "otpCode")
        put(AutofillFieldType.CardNumber, "creditCardNumber", "cc-number")
        put(AutofillFieldType.CardName, "cc-name", "creditCardHolderName")
        put(AutofillFieldType.CardExpiry, "creditCardExpirationDate", "cc-exp")
        put(AutofillFieldType.CardExpiryMonth, "creditCardExpirationMonth", "cc-exp-month")
        put(AutofillFieldType.CardExpiryYear, "creditCardExpirationYear", "cc-exp-year")
        put(AutofillFieldType.CardSecurityCode, "creditCardSecurityCode", "cc-csc")
        put(AutofillFieldType.FullName, "name", "personName")
        put(AutofillFieldType.GivenName, "given-name", "personGivenName")
        put(AutofillFieldType.MiddleName, "additional-name", "personMiddleName")
        put(AutofillFieldType.FamilyName, "family-name", "personFamilyName")
        put(AutofillFieldType.Email, "email", "emailAddress")
        put(AutofillFieldType.Phone, "phone", "tel", "phoneNumber", "phoneNumberDevice", "tel-national", "phoneNational")
        put(AutofillFieldType.PhoneCountryCode, "tel-country-code", "phoneCountryCode")
        put(AutofillFieldType.StreetAddress, "postalAddress", "street-address", "address-line1", "streetAddress")
        put(AutofillFieldType.AddressLine2, "address-line2", "extendedAddress", "aptNumber")
        put(AutofillFieldType.City, "address-level2", "addressLocality", "addressCity")
        put(AutofillFieldType.PostalCode, "postalCode", "postal-code")
        put(AutofillFieldType.Country, "country", "country-name", "addressCountry")
        put(AutofillFieldType.Organization, "organization")
    }

    /**
     * Name/id/label words, checked in this order (the first hit wins), so "username" is
     * never read as a name and "card name" never as a person's name. Latin keys are
     * matched against the text with separators removed; Arabic and Sorani keys against
     * the raw text. [short] keys must be a whole word (`tel` is not in `hotel`).
     */
    private class Rule(val type: AutofillFieldType, val keys: List<String>, val short: List<String> = emptyList())

    private val rules = listOf(
        Rule(AutofillFieldType.CardSecurityCode, listOf("securitycode", "cardcode", "cardverification", "رمز الأمان", "کۆدی ئاسایش"), listOf("cvc", "cvv", "csc", "cvn", "cvv2", "cid")),
        Rule(AutofillFieldType.CardExpiryMonth, listOf("expmonth", "expirymonth", "expirationmonth", "ccmonth", "cardmonth", "expirationdatemonth", "expmm")),
        Rule(AutofillFieldType.CardExpiryYear, listOf("expyear", "expiryyear", "expirationyear", "ccyear", "cardyear", "expirationdateyear", "expyy")),
        Rule(AutofillFieldType.CardExpiry, listOf("expiry", "expiration", "expdate", "ccexp", "mmyy", "validthru", "تاريخ الانتهاء", "بەسەرچوون")),
        Rule(AutofillFieldType.CardName, listOf("cardholder", "nameoncard", "ccname", "cardname", "holdername", "accountholder", "اسم حامل البطاقة", "ناوی سەر کارت")),
        Rule(AutofillFieldType.CardNumber, listOf("cardnumber", "ccnumber", "ccnum", "cardnum", "creditcard", "debitcard", "رقم البطاقة", "ژمارەی کارت")),
        Rule(AutofillFieldType.OneTimeCode, listOf("onetimecode", "onetimepassword", "verificationcode", "authcode", "smscode", "securitykey", "totp", "رمز التحقق", "کۆدی پشتڕاستکردنەوە"), listOf("otp", "2fa", "mfa", "pin")),
        Rule(AutofillFieldType.NewPassword, listOf("newpassword", "confirmpassword", "passwordconfirm", "repeatpassword", "retypepassword", "createpassword", "password2", "كلمة المرور الجديدة", "تأكيد كلمة المرور")),
        Rule(AutofillFieldType.Password, listOf("password", "passwd", "passcode", "كلمة المرور", "كلمة السر", "وشەی نهێنی", "تێپەڕەوشە"), listOf("pwd", "pass", "pw")),
        Rule(AutofillFieldType.Username, listOf("username", "userid", "loginid", "login", "identifier", "accountname", "اسم المستخدم", "ناوی بەکارهێنەر"), listOf("user")),
        Rule(AutofillFieldType.Email, listOf("email", "e-mail", "البريد", "ئیمەیڵ", "ایمێڵ"), listOf("mail")),
        Rule(AutofillFieldType.PhoneCountryCode, listOf("countrycode", "dialcode", "phonecode", "callingcode", "phoneprefix", "رمز الدولة")),
        Rule(AutofillFieldType.Phone, listOf("phone", "mobile", "msisdn", "telephone", "هاتف", "جوال", "موبايل", "مۆبایل", "تەلەفۆن", "ژمارەی مۆبایل"), listOf("tel", "cell")),
        Rule(AutofillFieldType.GivenName, listOf("firstname", "givenname", "forename", "الاسم الأول", "ناوی یەکەم"), listOf("fname", "first")),
        Rule(AutofillFieldType.MiddleName, listOf("middlename", "اسم الأب", "ناوی باوک"), listOf("mname", "middle")),
        Rule(AutofillFieldType.FamilyName, listOf("lastname", "surname", "familyname", "اسم العائلة", "ناوی خێزان", "نازناو"), listOf("lname", "last")),
        Rule(AutofillFieldType.AddressLine2, listOf("address2", "addressline2", "apartment", "suite", "building", "الشقة", "شوقە"), listOf("apt", "unit", "line2")),
        Rule(AutofillFieldType.PostalCode, listOf("zipcode", "postal", "postcode", "الرمز البريدي", "کۆدی پۆست"), listOf("zip", "plz")),
        Rule(AutofillFieldType.City, listOf("city", "town", "locality", "المدينة", "شار"), listOf()),
        Rule(AutofillFieldType.Country, listOf("country", "الدولة", "البلد", "وڵات")),
        Rule(AutofillFieldType.StreetAddress, listOf("address1", "addressline1", "streetaddress", "street", "address", "العنوان", "ناونیشان")),
        Rule(AutofillFieldType.Organization, listOf("company", "organization", "organisation", "employer", "الشركة", "کۆمپانیا")),
        Rule(AutofillFieldType.FullName, listOf("fullname", "yourname", "الاسم الكامل", "ناوی تەواو"), listOf("name", "الاسم", "ناو")),
    )

    private val ignoredHtmlTypes = setOf("hidden", "submit", "button", "reset", "checkbox", "radio", "file", "image", "search", "range", "color")

    /** The field's type, or null when nothing marks it as fillable. */
    fun classify(s: FieldSignals): AutofillFieldType? {
        val html = s.htmlAttributes
        val htmlType = html["type"]?.lowercase()
        if (htmlType in ignoredHtmlTypes) return null
        // 1. Explicit hints.
        val declared = s.autofillHints + listOfNotNull(html["autocomplete"], html["x-autocompletetype"], html["ua-autofill-hints"])
        for (raw in declared) {
            for (token in raw.lowercase().split(' ', ',')) {
                if (token == "off" || token == "on" || token.isBlank()) continue
                hintTable[token]?.let { return it }
            }
        }
        // 2. The input's type.
        val password = isPasswordInput(s.inputType) || htmlType == "password"
        val text = describe(s)
        if (password) {
            return when (match(text)) {
                AutofillFieldType.NewPassword -> AutofillFieldType.NewPassword
                AutofillFieldType.OneTimeCode -> AutofillFieldType.OneTimeCode
                AutofillFieldType.CardSecurityCode -> AutofillFieldType.CardSecurityCode
                else -> AutofillFieldType.Password
            }
        }
        // 3. Names, ids and labels.
        match(text)?.let { return it }
        // 4. What the keyboard was asked for.
        return when {
            htmlType == "email" || isEmailInput(s.inputType) -> AutofillFieldType.Email
            htmlType == "tel" || s.inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_PHONE -> AutofillFieldType.Phone
            textVariation(s.inputType) == InputType.TYPE_TEXT_VARIATION_PERSON_NAME -> AutofillFieldType.FullName
            textVariation(s.inputType) == InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS -> AutofillFieldType.StreetAddress
            else -> null
        }
    }

    private fun describe(s: FieldSignals): String {
        val html = s.htmlAttributes
        return listOfNotNull(
            html["name"], html["id"], html["label"], html["placeholder"], html["aria-label"], html["title"],
            s.hint, s.idEntry,
        ).joinToString(" ").lowercase()
    }

    private fun match(text: String): AutofillFieldType? {
        if (text.isBlank()) return null
        // camelCase / snake_case / kebab-case all collapse to one run: "first_name" → "firstname".
        val compact = text.filter { it.isLetterOrDigit() || it == ' ' }.replace(" ", "")
        val words = text.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }.toSet()
        for (rule in rules) {
            if (rule.keys.any { key -> if (key.any { it.code > 0x7f }) text.contains(key) else compact.contains(key) }) return rule.type
            if (rule.short.any { it in words }) return rule.type
        }
        return null
    }

    private fun textVariation(inputType: Int): Int =
        if (inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT) inputType and InputType.TYPE_MASK_VARIATION else -1

    private fun isPasswordInput(inputType: Int): Boolean {
        val klass = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return (klass == InputType.TYPE_CLASS_TEXT && variation in setOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
        )) || (klass == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
    }

    private fun isEmailInput(inputType: Int): Boolean {
        val variation = textVariation(inputType)
        return variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS || variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
    }
}

/** One field of the screen, classified. [T] is the platform id (`AutofillId` in the service). */
data class ClassifiedField<T>(val id: T, val type: AutofillFieldType, val signals: FieldSignals)

/** The screen's fields, split into the groups a dataset can fill. */
class AutofillForm<T>(fields: List<ClassifiedField<T>>) {
    val fields: List<ClassifiedField<T>> = resolveUsernames(fields)

    fun group(group: AutofillGroup): List<ClassifiedField<T>> = when (group) {
        // A username step (Google's "Email or phone" page) is a login form without a password.
        AutofillGroup.Login -> fields.filter { it.type.group == AutofillGroup.Login }
        else -> fields.filter { it.type.group == group }
    }

    val hasLogin: Boolean get() = fields.any { it.type == AutofillFieldType.Password || it.type == AutofillFieldType.Username || it.type == AutofillFieldType.OneTimeCode }
    val hasCard: Boolean get() = group(AutofillGroup.Card).any { it.type == AutofillFieldType.CardNumber }
    val hasIdentity: Boolean get() = group(AutofillGroup.Identity).isNotEmpty()

    /** A sign-up or change-password form: it gets "Use a strong password" (design rule 6c). */
    val hasNewPassword: Boolean get() = fields.any { it.type == AutofillFieldType.NewPassword }

    /**
     * The password a save reads: the new password on a sign-up or change form (that is the
     * one the account has after submit), else the password.
     */
    val passwordField: ClassifiedField<T>? get() =
        fields.firstOrNull { it.type == AutofillFieldType.NewPassword } ?: fields.firstOrNull { it.type == AutofillFieldType.Password }

    companion object {
        /**
         * Login forms often ask for "email" or "phone" as the username. With a password on
         * the screen and no username, the email/phone field before it is the username; with
         * no password and that field alone, it is a username step.
         */
        private fun <T> resolveUsernames(fields: List<ClassifiedField<T>>): List<ClassifiedField<T>> {
            if (fields.any { it.type == AutofillFieldType.Username }) return fields
            val passwordAt = fields.indexOfFirst { it.type == AutofillFieldType.Password || it.type == AutofillFieldType.NewPassword }
            val identityKinds = fields.filter { it.type.group == AutofillGroup.Identity }
            val candidate = when {
                passwordAt > 0 -> fields.subList(0, passwordAt).lastOrNull { it.type == AutofillFieldType.Email || it.type == AutofillFieldType.Phone }
                passwordAt < 0 && identityKinds.size == 1 && fields.none { it.type.group == AutofillGroup.Card } ->
                    identityKinds.single().takeIf { it.type == AutofillFieldType.Email || it.type == AutofillFieldType.Phone }
                else -> null
            } ?: return fields
            return fields.map { if (it === candidate) it.copy(type = AutofillFieldType.Username) else it }
        }
    }
}

/** When a pick needs biometrics or the master password first (docs/autofill-plan.md §1.3). */
object AutofillPolicy {
    /** Payment and ID kinds ask on every fill, whatever the setting says. */
    private val alwaysConfirm = setOf(AutofillIndexStore.KIND_CARD, AutofillIndexStore.KIND_BANK)

    fun needsAuth(kind: String?, unlocked: Boolean, confirmEveryFill: Boolean): Boolean =
        !unlocked || confirmEveryFill || kind in alwaysConfirm
}
