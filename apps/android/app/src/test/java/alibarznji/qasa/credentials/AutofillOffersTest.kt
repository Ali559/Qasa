package alibarznji.qasa.credentials

import alibarznji.qasa.credentials.AutofillFieldType.*
import alibarznji.qasa.vault.crypto.PasswordGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Design rule 6c: a sign-up or change-password form gets "Use a strong password". */
class AutofillOffersTest {
    private fun form(vararg types: AutofillFieldType) =
        AutofillForm(types.mapIndexed { i, t -> ClassifiedField(i, t, FieldSignals()) })

    @Test
    fun signUpFormOffersOnlyTheStrongPasswordOnItsPasswordFields() {
        val signUp = form(Email, NewPassword, NewPassword) // email, password, confirm
        val offers = AutofillOffers.login(signUp)
        assertEquals(listOf(1, 2), offers.strongPasswordIds)
        // The email before the password is the username: it keeps saved logins, the password fields do not.
        assertEquals(listOf(0), offers.savedLoginIds)
        assertTrue(signUp.hasNewPassword)
    }

    @Test
    fun newAndConfirmGetTheSameValue() {
        val values = AutofillOffers.strongPasswordValues(form(Username, NewPassword, NewPassword), "Gen3rated!pass")
        assertEquals(mapOf(1 to "Gen3rated!pass", 2 to "Gen3rated!pass"), values)
    }

    @Test
    fun changeFormKeepsSavedLoginsOnTheCurrentPassword() {
        val change = form(Password, NewPassword, NewPassword)
        val offers = AutofillOffers.login(change)
        assertEquals(listOf(0), offers.savedLoginIds)
        assertEquals(listOf(1, 2), offers.strongPasswordIds)
        assertEquals(mapOf(1 to "x", 2 to "x"), AutofillOffers.strongPasswordValues(change, "x"))
    }

    @Test
    fun newPasswordOnlyFormGetsNoSavedLogins() {
        val reset = form(NewPassword, NewPassword)
        assertFalse(reset.hasLogin)
        val offers = AutofillOffers.login(reset)
        assertEquals(emptyList<Int>(), offers.savedLoginIds)
        assertEquals(listOf(0, 1), offers.strongPasswordIds)
    }

    @Test
    fun loginFormHasNoStrongPassword() {
        val login = form(Username, Password, OneTimeCode)
        val offers = AutofillOffers.login(login)
        assertEquals(listOf(0, 1, 2), offers.savedLoginIds)
        assertEquals(emptyList<Int>(), offers.strongPasswordIds)
        assertFalse(login.hasNewPassword)
    }

    @Test
    fun signUpSaveReadsTheNewPasswordAndUsername() {
        val plan = AutofillOffers.save(form(Email, NewPassword, NewPassword))!!
        assertEquals(1, plan.passwordId)
        assertEquals(0, plan.usernameId)
        // The typed username is watched: the strong-password dataset never carries it, so
        // the framework sees a change and no dataset match, and offers to save.
        assertEquals(listOf(0), plan.optionalIds)
    }

    @Test
    fun changeSaveReadsTheNewPasswordAndWatchesTheCurrentOne() {
        val plan = AutofillOffers.save(form(Username, Password, NewPassword, NewPassword))!!
        assertEquals(2, plan.passwordId)
        assertEquals(0, plan.usernameId)
        assertEquals(listOf(0, 1), plan.optionalIds)
    }

    @Test
    fun loginSaveIsUnchanged() {
        val plan = AutofillOffers.save(form(Username, Password))!!
        assertEquals(1, plan.passwordId)
        assertEquals(0, plan.usernameId)
        assertEquals(listOf(0), plan.optionalIds)
        assertNull(AutofillOffers.save(form(Username)))
    }

    @Test
    fun defaultGeneratorMatchesTheGeneratorScreen() {
        val password = PasswordGenerator.generateDefault()
        assertEquals(PasswordGenerator.DEFAULT_LENGTH, password.length)
        assertTrue(password.any { it.isUpperCase() } && password.any { it.isLowerCase() } && password.any { it.isDigit() })
        assertTrue(password.any { !it.isLetterOrDigit() })
    }
}
