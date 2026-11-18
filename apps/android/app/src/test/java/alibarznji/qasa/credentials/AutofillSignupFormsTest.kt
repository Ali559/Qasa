package alibarznji.qasa.credentials

import alibarznji.qasa.vault.crypto.PasswordGenerator
import alibarznji.qasa.vault.crypto.PasswordRules
import android.text.InputType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The forms of docs/autofill-test-pages/signup.html as Chrome reports them (HTML attributes),
 * and rules R1/R2 of docs/autofill-hardening.md: which fields get "Use a strong password",
 * which get saved logins, and what the generated password looks like.
 */
class AutofillSignupFormsTest {
    private fun input(vararg attrs: Pair<String, String>, maxLength: Int? = null) =
        FieldSignals(htmlAttributes = attrs.toMap(), maxLength = maxLength)

    /** Classified like [AutofillStructureParser]: typed fields in order, the rest as context. */
    private fun form(vararg fields: FieldSignals): AutofillForm<Int> = formWithButtons(emptyList(), *fields)

    /** [buttons]: the screen's native button text, as the parser collects it (R1 rule 6). */
    private fun formWithButtons(buttons: List<String>, vararg fields: FieldSignals): AutofillForm<Int> {
        val typed = mutableListOf<ClassifiedField<Int>>()
        val others = mutableListOf<FieldSignals>()
        fields.forEachIndexed { i, s ->
            val type = AutofillFieldClassifier.classify(s)
            if (type != null) typed += ClassifiedField(i, type, s) else others += s
        }
        return AutofillForm(typed, others, buttons)
    }

    /** A Flutter field outside an AutofillGroup: only its placeholder, no type, id or hints. */
    private fun flutter(hintText: String) = FieldSignals(hint = hintText)

    private fun assertOffers(form: AutofillForm<Int>, strong: List<Int>, saved: List<Int>) {
        val offers = AutofillOffers.login(form)
        assertEquals("strong-password fields", strong, offers.strongPasswordIds)
        assertEquals("saved-login fields", saved, offers.savedLoginIds)
    }

    private fun assertStrong(form: AutofillForm<Int>, length: Int) {
        repeat(20) {
            val password = PasswordGenerator.generate(AutofillOffers.strongPasswordSpec(form))
            assertEquals(length, password.length)
            assertTrue(password.any { it.isUpperCase() } && password.any { it.isLowerCase() } && password.any { it.isDigit() })
            assertTrue(password.any { !it.isLetterOrDigit() })
        }
    }

    @Test
    fun formA_newPasswordOnBoth() {
        val f = form(
            input("type" to "email", "name" to "email", "autocomplete" to "email"),
            input("type" to "password", "name" to "password", "autocomplete" to "new-password"),
            input("type" to "password", "name" to "password_confirmation", "autocomplete" to "new-password"),
        )
        assertOffers(f, strong = listOf(1, 2), saved = listOf(0))
        assertStrong(f, 16)
    }

    @Test
    fun formB_confirmWithoutHintIsNew() {
        val f = form(
            input("type" to "email", "name" to "email"),
            input("type" to "password", "name" to "password", "autocomplete" to "new-password"),
            input("type" to "password", "name" to "password_repeat"),
        )
        // The reproduced bug: the confirm field was a current password with saved logins.
        assertOffers(f, strong = listOf(1, 2), saved = listOf(0))
        assertEquals(mapOf(1 to "x", 2 to "x"), AutofillOffers.strongPasswordValues(f, "x"))
    }

    @Test
    fun formB_confirmWithoutHintOrRepeatWordIsStillTheConfirm() {
        val f = form(
            input("type" to "email", "name" to "email"),
            input("type" to "password", "name" to "password", "autocomplete" to "new-password"),
            input("type" to "password", "name" to "field3"),
        )
        assertOffers(f, strong = listOf(1, 2), saved = listOf(0))
    }

    @Test
    fun formC_twoPlainPasswordFields() {
        val f = form(
            input("type" to "text", "name" to "user"),
            input("type" to "password", "id" to "pw1", "name" to "pw1"),
            input("type" to "password", "id" to "pw2", "name" to "pw2"),
        )
        assertOffers(f, strong = listOf(1, 2), saved = listOf(0))
    }

    @Test
    fun formD_onePasswordWithNamesIsSignUp() {
        val f = form(
            input("name" to "first_name", "autocomplete" to "given-name"),
            input("name" to "last_name", "autocomplete" to "family-name"),
            input("type" to "email", "name" to "email", "autocomplete" to "email"),
            input("type" to "password", "name" to "password"),
        )
        assertOffers(f, strong = listOf(3), saved = listOf(2))
        assertStrong(f, 16)
    }

    @Test
    fun onePasswordWithBirthdayIsSignUp() {
        val f = form(
            input("type" to "email", "name" to "email"),
            input("type" to "date", "name" to "dob"),
            input("type" to "password", "name" to "password"),
        )
        assertOffers(f, strong = listOf(2), saved = listOf(0))
    }

    @Test
    fun onePasswordThatSaysSignUpIsNew() {
        val f = form(
            input("type" to "email", "name" to "email"),
            input("type" to "password", "name" to "signup_password"),
        )
        assertOffers(f, strong = listOf(1), saved = listOf(0))
    }

    @Test
    fun formE_changePassword() {
        val f = form(
            input("type" to "password", "name" to "current", "autocomplete" to "current-password"),
            input("type" to "password", "name" to "new", "autocomplete" to "new-password"),
            input("type" to "password", "name" to "confirm"),
        )
        assertOffers(f, strong = listOf(1, 2), saved = listOf(0))
        assertEquals(1, AutofillOffers.save(f)!!.passwordId)
    }

    @Test
    fun threeUnmarkedPasswordsAreCurrentNewNew() {
        val f = form(
            input("type" to "password", "name" to "a"),
            input("type" to "password", "name" to "b"),
            input("type" to "password", "name" to "c"),
        )
        assertOffers(f, strong = listOf(1, 2), saved = listOf(0))
    }

    @Test
    fun formF_maxLength12() {
        val f = form(
            input("type" to "email", "name" to "email"),
            input("type" to "password", "name" to "password", "autocomplete" to "new-password", "maxlength" to "12", maxLength = 12),
            input("type" to "password", "name" to "confirm_password", "maxlength" to "12", maxLength = 12),
        )
        assertOffers(f, strong = listOf(1, 2), saved = listOf(0))
        assertStrong(f, 12)
    }

    @Test
    fun formG_kurdishLabels() {
        val f = form(
            input("type" to "email", "name" to "f1", "aria-label" to "ئیمەیڵ"),
            input("type" to "password", "name" to "f2", "aria-label" to "وشەی نهێنی نوێ"),
            input("type" to "password", "name" to "f3", "aria-label" to "دووبارەکردنەوەی وشەی نهێنی"),
        )
        assertOffers(f, strong = listOf(1, 2), saved = listOf(0))
    }

    @Test
    fun formH_controlledInputsClassifyLikeA() {
        val f = form(
            input("type" to "email", "name" to "email", "data-controlled" to ""),
            input("type" to "password", "name" to "password", "autocomplete" to "new-password", "data-controlled" to ""),
            input("type" to "password", "name" to "confirmPassword", "autocomplete" to "new-password", "data-controlled" to ""),
        )
        assertOffers(f, strong = listOf(1, 2), saved = listOf(0))
    }

    @Test
    fun plainLoginStaysALogin() {
        val f = form(
            input("type" to "email", "name" to "email"),
            input("type" to "password", "name" to "password"),
        )
        assertOffers(f, strong = emptyList(), saved = listOf(0, 1))
        assertEquals(listOf(AutofillFieldType.Username, AutofillFieldType.Password), f.fields.map { it.type })
    }

    @Test
    fun nativeLoginStaysALogin() {
        val password = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        val f = form(
            FieldSignals(autofillHints = listOf("username"), idEntry = "username"),
            FieldSignals(autofillHints = listOf("password"), idEntry = "password", inputType = password),
        )
        assertOffers(f, strong = emptyList(), saved = listOf(0, 1))
    }

    @Test
    fun phoneLoginStaysALogin() {
        // The phone is the username here, not sign-up context.
        val f = form(
            input("type" to "tel", "name" to "phone"),
            input("type" to "password", "name" to "password"),
        )
        assertOffers(f, strong = emptyList(), saved = listOf(0, 1))
    }

    @Test
    fun googleStylePasswdAndPasswdAgain() {
        val f = form(
            input("type" to "password", "name" to "Passwd", "aria-label" to "Password"),
            input("type" to "password", "name" to "PasswdAgain", "aria-label" to "Confirm"),
        )
        assertOffers(f, strong = listOf(0, 1), saved = emptyList())
    }

    @Test
    fun arabicConfirm() {
        val f = form(
            input("type" to "email", "name" to "e"),
            input("type" to "password", "name" to "p1", "aria-label" to "كلمة المرور"),
            input("type" to "password", "name" to "p2", "aria-label" to "تأكيد كلمة المرور"),
        )
        assertOffers(f, strong = listOf(1, 2), saved = listOf(0))
    }

    @Test
    fun passwordRulesAndMinLengthShapeTheGeneratedPassword() {
        val f = form(
            input("type" to "email", "name" to "email"),
            input(
                "type" to "password", "name" to "password", "autocomplete" to "new-password",
                "passwordrules" to "required: lower; required: digit; max-consecutive: 1; minlength: 20",
            ),
            input("type" to "password", "name" to "password_repeat"),
        )
        val spec = AutofillOffers.strongPasswordSpec(f)
        repeat(50) {
            val password = PasswordGenerator.generate(spec)
            assertEquals(20, password.length)
            assertTrue(password, password.all { it.isLowerCase() || it.isDigit() })
            assertTrue(password.any { it.isDigit() } && password.any { it.isLowerCase() })
            assertEquals(1, PasswordGenerator.longestRun(password.toCharArray()))
        }
        val minOnly = form(input("type" to "password", "autocomplete" to "new-password", "minlength" to "24"))
        assertEquals(24, AutofillOffers.strongPasswordSpec(minOnly).length)
        // The rules are the site's; an empty attribute is no rules.
        assertEquals(null, PasswordRules.parse(""))
    }

    @Test
    fun numericShortFieldGetsDigits() {
        val pin = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        val f = form(
            FieldSignals(autofillHints = listOf("newPassword"), inputType = pin, maxLength = 6),
            FieldSignals(autofillHints = listOf("newPassword"), inputType = pin, maxLength = 6),
        )
        val password = PasswordGenerator.generate(AutofillOffers.strongPasswordSpec(f))
        assertEquals(6, password.length)
        assertTrue(password.all { it.isDigit() })
    }
    // Phase 12: forms I–L (docs/autofill-hardening.md R1 rule 6, "Detecting a password field").

    @Test
    fun formI_fullNameUsernameEmailOnePasswordIsSignUp() {
        val f = formWithButtons(
            listOf("Continue"),
            input("name" to "fullName", "placeholder" to "Full name"),
            input("name" to "username", "placeholder" to "Username"),
            input("type" to "email", "name" to "email", "placeholder" to "Email"),
            input("type" to "password", "name" to "password", "placeholder" to "Password"),
        )
        assertEquals(AutofillFieldType.FullName, f.fields[0].type)
        assertOffers(f, strong = listOf(3), saved = listOf(1))
        assertStrong(f, 16)
    }

    @Test
    fun formI_usernameAndEmailAloneMakeTheOnePasswordNew() {
        val f = form(
            input("type" to "text", "name" to "username"),
            input("type" to "email", "name" to "email"),
            input("type" to "password", "name" to "password"),
        )
        assertOffers(f, strong = listOf(2), saved = listOf(0))
    }

    @Test
    fun formI_flutterAllFieldsReportedByPlaceholder() {
        // The Dozarawa shape inside an AutofillGroup: every field, placeholders only.
        val f = form(flutter("Full name"), flutter("Username"), flutter("Email"), flutter("Password"))
        assertEquals(
            listOf(AutofillFieldType.FullName, AutofillFieldType.Username, AutofillFieldType.Email, AutofillFieldType.NewPassword),
            f.fields.map { it.type },
        )
        assertOffers(f, strong = listOf(3), saved = listOf(1))
    }

    @Test
    fun formJ_createAccountButtonMakesTheOnePasswordNew() {
        val f = formWithButtons(
            listOf("Create account"),
            input("type" to "email", "name" to "email"),
            input("type" to "password", "name" to "password"),
        )
        assertOffers(f, strong = listOf(1), saved = listOf(0))
        // Other sign-up buttons, in each language.
        for (button in listOf("Sign up", "SIGN UP", "Register", "Join", "إنشاء حساب", "حساب جديد", "التسجيل", "تۆمارکردن", "خۆتۆمارکردن", "دروستکردنی هەژمار")) {
            val g = formWithButtons(listOf(button), input("type" to "email", "name" to "email"), input("type" to "password", "name" to "password"))
            assertOffers(g, strong = listOf(1), saved = listOf(0))
        }
    }

    @Test
    fun formJ_neutralButtonsCountForNothing() {
        for (button in listOf("Continue", "Next", "Submit", "متابعة", "بەردەوامبە")) {
            val f = formWithButtons(listOf(button), input("type" to "email", "name" to "email"), input("type" to "password", "name" to "password"))
            assertOffers(f, strong = emptyList(), saved = listOf(0, 1))
        }
    }

    @Test
    fun formK_arabicLoginStaysALogin() {
        val f = formWithButtons(
            listOf("تسجيل الدخول"),
            input("type" to "email", "name" to "email", "aria-label" to "البريد الإلكتروني"),
            input("type" to "password", "name" to "password", "aria-label" to "كلمة المرور"),
        )
        assertOffers(f, strong = emptyList(), saved = listOf(0, 1))
        assertEquals(listOf(AutofillFieldType.Username, AutofillFieldType.Password), f.fields.map { it.type })
        // R7: the login password still gets the strong password, after its logins.
        assertEquals(listOf(1), AutofillOffers.login(f, focused = 1).fallbackStrongIds)
    }

    @Test
    fun formK_loginButtonWinsOverASignUpLink() {
        for (buttons in listOf(listOf("تسجيل الدخول", "إنشاء حساب"), listOf("Log in", "Don't have an account? Sign up"), listOf("Sign in", "Register"))) {
            val f = formWithButtons(buttons, input("type" to "email", "name" to "email"), input("type" to "password", "name" to "password"))
            assertOffers(f, strong = emptyList(), saved = listOf(0, 1))
        }
    }

    @Test
    fun bareTasjeelInAFieldLabelIsNotSignUp() {
        // تسجيل الدخول is "log in": a login password labelled with it stays a login.
        val f = form(
            input("type" to "email", "name" to "email"),
            input("type" to "password", "name" to "p", "aria-label" to "كلمة المرور - تسجيل الدخول"),
        )
        assertOffers(f, strong = emptyList(), saved = listOf(0, 1))
        val g = form(
            input("type" to "email", "name" to "email"),
            input("type" to "password", "name" to "p", "aria-label" to "كلمة المرور - التسجيل"),
        )
        assertOffers(g, strong = listOf(1), saved = listOf(0))
    }

    @Test
    fun formL_flutterPasswordReportedAloneIsAPassword() {
        for (hintText in listOf("Password", "كلمة المرور", "وشەی نهێنی")) {
            val signals = FieldSignals(autofillHints = emptyList(), hint = hintText, inputType = 0, idEntry = null)
            assertTrue(hintText, AutofillFieldClassifier.isPasswordField(signals))
            val f = form(signals)
            // Alone, R1 can't call it new: a login password, with R7's strong password.
            assertEquals(listOf(AutofillFieldType.Password), f.fields.map { it.type })
            val offers = AutofillOffers.login(f, focused = 0)
            assertEquals(listOf(0), offers.savedLoginIds)
            assertEquals(listOf(0), offers.fallbackStrongIds)
            // No saved login: "Use a strong password" first, then "Search Qasa".
            assertEquals(
                listOf(LoginRow.StrongPassword(fallback = true), LoginRow.Search),
                AutofillOffers.loginRows(offers, emptyList<String>()),
            )
            assertEquals(mapOf(0 to "x"), AutofillOffers.fallbackStrongPasswordValues(f, listOf(0), "x"))
        }
    }

    @Test
    fun aTypedTextFieldNamedPasswordHintIsNotAPasswordField() {
        val text = InputType.TYPE_CLASS_TEXT
        assertTrue(!AutofillFieldClassifier.isPasswordField(FieldSignals(hint = "Password hint", inputType = text)))
        assertTrue(!AutofillFieldClassifier.isPasswordField(input("type" to "text", "name" to "password_hint")))
    }
}
