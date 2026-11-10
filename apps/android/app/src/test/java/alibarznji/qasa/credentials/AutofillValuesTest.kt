package alibarznji.qasa.credentials

import alibarznji.qasa.credentials.AutofillFieldType.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutofillValuesTest {
    private val card = AutofillSecrets(number = "4242 4242 4242 4242", securityCode = "123", holder = "Ali Barznji", expiry = "03/29")
    private val name = AutofillSecrets(firstName = "Ali", middleName = "K", lastName = "Barznji")
    private val email = AutofillSecrets(email = "ali@example.com")
    private val phone = AutofillSecrets(phoneCountry = "IQ", phoneNumber = "0750 123 4567")
    private val address = AutofillSecrets(street = "100 Salim St", line2 = "Building 4, Floor 2", city = "Sulaymaniyah", postalCode = "46001", country = "IQ")

    private fun field(id: Int, type: AutofillFieldType, signals: FieldSignals = FieldSignals()) = ClassifiedField(id, type, signals)
    private fun text(v: FieldValue?) = (v as FieldValue.Text).text

    @Test
    fun cardFieldsFillFromTheCard() {
        val fields = listOf(
            field(1, CardNumber), field(2, CardName), field(3, CardExpiry), field(4, CardSecurityCode),
        )
        val v = AutofillValues.valuesFor(fields, card)
        assertEquals("4242424242424242", text(v[1]))
        assertEquals("Ali Barznji", text(v[2]))
        assertEquals("03/29", text(v[3]))
        assertEquals("123", text(v[4]))
    }

    @Test
    fun splitExpiryAndSelects() {
        val month = field(1, CardExpiryMonth, FieldSignals(options = listOf("Month", "01", "02", "03", "04")))
        val year = field(2, CardExpiryYear, FieldSignals(options = listOf("Year", "2027", "2028", "2029")))
        val yearText = field(3, CardExpiryYear, FieldSignals(maxLength = 2))
        val monthText = field(4, CardExpiryMonth)
        val v = AutofillValues.valuesFor(listOf(month, year, yearText, monthText), card)
        assertEquals(FieldValue.ListIndex(3), v[1])
        assertEquals(FieldValue.ListIndex(3), v[2])
        assertEquals("29", text(v[3]))
        assertEquals("03", text(v[4]))
        val named = field(5, CardExpiryMonth, FieldSignals(options = listOf("January", "February", "March")))
        assertEquals(FieldValue.ListIndex(2), AutofillValues.valuesFor(listOf(named), card)[5])
        val fourDigit = field(6, CardExpiry, FieldSignals(htmlAttributes = mapOf("placeholder" to "MM/YYYY")))
        assertEquals("03/2029", text(AutofillValues.valuesFor(listOf(fourDigit), card)[6]))
    }

    @Test
    fun expiryParsing() {
        assertEquals("03" to "29", AutofillValues.expiry("3/29"))
        assertEquals("12" to "30", AutofillValues.expiry("12/2030"))
        assertNull(AutofillValues.expiry("13/29"))
        assertNull(AutofillValues.expiry("soon"))
    }

    @Test
    fun anAddressPickFillsTheWholeProfile() {
        val fields = listOf(
            field(1, FullName), field(2, Email), field(3, Phone), field(4, StreetAddress),
            field(5, AddressLine2), field(6, City), field(7, PostalCode), field(8, Organization),
        )
        val v = AutofillValues.valuesFor(fields, address, profile = listOf(name, email, phone))
        assertEquals("Ali K Barznji", text(v[1]))
        assertEquals("ali@example.com", text(v[2]))
        assertEquals("+9647501234567", text(v[3]))
        assertEquals("100 Salim St", text(v[4]))
        assertEquals("Building 4, Floor 2", text(v[5]))
        assertEquals("Sulaymaniyah", text(v[6]))
        assertEquals("46001", text(v[7]))
        // No company in the vault: the field is left alone.
        assertNull(v[8])
    }

    @Test
    fun phoneWithASeparateCountryCodeField() {
        val fields = listOf(
            field(1, PhoneCountryCode, FieldSignals(options = listOf("+1", "+44", "+964"))),
            field(2, Phone),
        )
        val v = AutofillValues.valuesFor(fields, phone)
        assertEquals(FieldValue.ListIndex(2), v[1])
        assertEquals("07501234567", text(v[2]))
    }

    @Test
    fun countrySelectByCodeOrName() {
        val byCode = field(1, Country, FieldSignals(options = listOf("US", "IQ", "DE")))
        val byName = field(2, Country, FieldSignals(options = listOf("Germany", "Iraq", "Turkey")))
        val text = field(3, Country)
        val v = AutofillValues.valuesFor(listOf(byCode, byName, text), address)
        assertEquals(FieldValue.ListIndex(1), v[1])
        assertEquals(FieldValue.ListIndex(1), v[2])
        assertEquals("Iraq", text(v[3]))
    }

    @Test
    fun loginFillsUsernameAndPasswordOnly() {
        val login = AutofillSecrets(username = "ali", password = "s3cret", totp = "otpauth://totp/x?secret=JBSWY3DPEHPK3PXP")
        val v = AutofillValues.valuesFor(listOf(field(1, Username), field(2, Password), field(3, OneTimeCode)), login)
        assertEquals("ali", text(v[1]))
        assertEquals("s3cret", text(v[2]))
        // The code is filled from the current TOTP by the caller, never from a stored value.
        assertNull(v[3])
    }

    @Test
    fun anOptionNotInTheListIsLeftAlone() {
        val f = field(1, Country, FieldSignals(options = listOf("France", "Spain")))
        assertNull(AutofillValues.valuesFor(listOf(f), address)[1])
    }
}
