package alibarznji.qasa.credentials

import alibarznji.qasa.credentials.AutofillFieldType.*
import android.text.InputType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutofillFieldClassifierTest {
    private fun html(vararg attrs: Pair<String, String>) = FieldSignals(htmlAttributes = attrs.toMap())
    private fun classify(s: FieldSignals) = AutofillFieldClassifier.classify(s)

    @Test
    fun autocompleteTokensWin() {
        assertEquals(CardNumber, classify(html("type" to "text", "autocomplete" to "cc-number", "name" to "first_name")))
        assertEquals(PostalCode, classify(html("autocomplete" to "shipping postal-code")))
        assertEquals(Username, classify(html("type" to "email", "autocomplete" to "username")))
        assertEquals(NewPassword, classify(html("type" to "password", "autocomplete" to "new-password")))
        assertEquals(OneTimeCode, classify(html("autocomplete" to "one-time-code")))
        assertEquals(GivenName, classify(html("autocomplete" to "billing given-name")))
    }

    @Test
    fun androidHints() {
        assertEquals(Password, classify(FieldSignals(autofillHints = listOf("password"))))
        assertEquals(CardSecurityCode, classify(FieldSignals(autofillHints = listOf("creditCardSecurityCode"))))
        assertEquals(Phone, classify(FieldSignals(autofillHints = listOf("phone"))))
        assertEquals(StreetAddress, classify(FieldSignals(autofillHints = listOf("postalAddress"))))
    }

    @Test
    fun passwordInputTypes() {
        val password = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        assertEquals(Password, classify(FieldSignals(inputType = password, idEntry = "field3")))
        assertEquals(NewPassword, classify(FieldSignals(inputType = password, idEntry = "confirm_password")))
        assertEquals(Password, classify(html("type" to "password", "name" to "pass")))
        val pin = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        assertEquals(CardSecurityCode, classify(FieldSignals(inputType = pin, idEntry = "cvv")))
    }

    @Test
    fun namesAndIdsInEveryCase() {
        assertEquals(GivenName, classify(html("name" to "firstName")))
        assertEquals(FamilyName, classify(html("id" to "last-name")))
        assertEquals(CardExpiryMonth, classify(html("name" to "exp_month")))
        assertEquals(CardExpiryYear, classify(html("name" to "expYear")))
        assertEquals(CardExpiry, classify(html("placeholder" to "MM / YY", "name" to "exp-date")))
        assertEquals(CardName, classify(html("name" to "cardholder-name")))
        assertEquals(CardSecurityCode, classify(html("name" to "cvc")))
        assertEquals(City, classify(html("name" to "billing_city")))
        assertEquals(Country, classify(html("name" to "country")))
        assertEquals(Organization, classify(html("label" to "Company")))
        assertEquals(AddressLine2, classify(html("name" to "address2")))
        assertEquals(StreetAddress, classify(html("name" to "address1")))
    }

    @Test
    fun usernameIsNeverAName() {
        assertEquals(Username, classify(html("name" to "username")))
        assertEquals(FullName, classify(html("name" to "name")))
        assertEquals(Email, classify(html("name" to "email_address")))
    }

    @Test
    fun shortWordsMatchWholeWordsOnly() {
        assertEquals(Phone, classify(html("name" to "tel")))
        // "hotel" is not a phone field; nothing else marks it.
        assertNull(classify(html("name" to "hotel")))
        assertNull(classify(html("name" to "search_query")))
    }

    @Test
    fun arabicAndSoraniLabels() {
        assertEquals(Password, classify(FieldSignals(hint = "كلمة المرور", inputType = InputType.TYPE_CLASS_TEXT)))
        assertEquals(Phone, classify(FieldSignals(hint = "ژمارەی مۆبایل")))
        assertEquals(City, classify(FieldSignals(hint = "المدينة")))
        assertEquals(CardNumber, classify(FieldSignals(hint = "رقم البطاقة")))
    }

    @Test
    fun keyboardTypesAsLastResort() {
        assertEquals(Email, classify(FieldSignals(inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)))
        assertEquals(Phone, classify(FieldSignals(inputType = InputType.TYPE_CLASS_PHONE)))
        assertEquals(Phone, classify(html("type" to "tel")))
        assertNull(classify(FieldSignals(inputType = InputType.TYPE_CLASS_TEXT)))
    }

    @Test
    fun buttonsAndHiddenInputsAreIgnored() {
        assertNull(classify(html("type" to "hidden", "name" to "password")))
        assertNull(classify(html("type" to "submit", "name" to "login")))
    }

    private fun form(vararg types: AutofillFieldType) =
        AutofillForm(types.mapIndexed { i, t -> ClassifiedField(i, t, FieldSignals()) })

    @Test
    fun emailBeforePasswordIsTheUsername() {
        val f = form(Email, Password)
        assertEquals(listOf(Username, Password), f.fields.map { it.type })
        assertTrue(f.hasLogin)
        assertFalse(f.hasIdentity)
    }

    @Test
    fun loneEmailIsAUsernameStep() {
        assertEquals(listOf(Username), form(Email).fields.map { it.type })
        // With an address on the form it is the contact email.
        assertEquals(listOf(Email, StreetAddress), form(Email, StreetAddress).fields.map { it.type })
    }

    @Test
    fun checkoutFormSplitsIntoGroups() {
        val f = form(Email, GivenName, FamilyName, StreetAddress, City, PostalCode, CardNumber, CardExpiry, CardSecurityCode)
        assertFalse(f.hasLogin)
        assertTrue(f.hasCard)
        assertTrue(f.hasIdentity)
        assertEquals(3, f.group(AutofillGroup.Card).size)
        assertEquals(6, f.group(AutofillGroup.Identity).size)
    }

    @Test
    fun policyMatchesThePlanTable() {
        assertTrue(AutofillPolicy.needsAuth(AutofillIndexStore.KIND_LOGIN, unlocked = false, confirmEveryFill = false))
        assertFalse(AutofillPolicy.needsAuth(AutofillIndexStore.KIND_LOGIN, unlocked = true, confirmEveryFill = false))
        assertTrue(AutofillPolicy.needsAuth(AutofillIndexStore.KIND_LOGIN, unlocked = true, confirmEveryFill = true))
        assertFalse(AutofillPolicy.needsAuth(AutofillIndexStore.KIND_ADDRESS, unlocked = true, confirmEveryFill = false))
        assertTrue(AutofillPolicy.needsAuth(AutofillIndexStore.KIND_CARD, unlocked = true, confirmEveryFill = false))
        assertTrue(AutofillPolicy.needsAuth(AutofillIndexStore.KIND_BANK, unlocked = true, confirmEveryFill = false))
        // "Search Qasa" (no item yet) on an unlocked vault opens the picker without a prompt.
        assertFalse(AutofillPolicy.needsAuth(null, unlocked = true, confirmEveryFill = false))
    }
}
