package alibarznji.qasa.credentials

/**
 * Rule R1 of docs/autofill-hardening.md: which password fields of one screen are new
 * (sign-up / change form: "Use a strong password", never saved logins) and which is the
 * current password. Runs after per-field classification, over the password-type fields in
 * screen order, because a confirm field named `password_repeat` or `pw2` says nothing on
 * its own; its place after a new-password field does.
 *
 * Pure: plain [FieldSignals] in, roles out.
 */
object PasswordFieldRoles {
    enum class Marker { Current, New, None }

    /** Latin words that mark a current password (whole words, camelCase split). */
    private val currentWords = setOf("current", "old", "existing")
    private val currentLong = listOf("currentpassword", "oldpassword", "existingpassword")
    private val currentNonLatin = listOf("الحالية", "الحالي", "القديمة", "القديم", "ئێستا", "ئێستایی")
    private val currentNonLatinWords = setOf("کۆن", "کۆنە")

    /** Latin words that mark a new password or its confirm. */
    private val newWords = setOf(
        "new", "create", "choose", "set", "confirm", "confirmation", "repeat", "again", "retype",
        "reenter", "verify", "verification", "conf", "re",
    )
    private val newLong = listOf(
        "newpassword", "confirmpassword", "passwordconfirm", "repeatpassword", "passwordrepeat",
        "retypepassword", "reenter", "createpassword", "choosepassword", "verifypassword",
        "passwordagain", "passwdagain", "passwordverify", "passwordverification",
    )
    private val newNonLatin = listOf("جديدة", "الجديدة", "جديد", "تأكيد", "إعادة", "اعادة", "دووبارە", "دڵنیاکردنەوە", "پشتڕاستکردنەوە")
    private val newNonLatinWords = setOf("أعد", "اعد", "نوێ", "نوی")

    /** A password word with a `2` suffix: `pw2`, `pwd2`, `password2`, `passwd_2`. */
    private val secondPassword = Regex("^(pw|pwd|pass|passwd|password|passcode|pword)2$")

    /**
     * Words on the field itself that say "this is a sign-up" (rule 6). Not the bare Arabic
     * تسجيل: تسجيل الدخول is "log in" (Phase 12); التسجيل ("registration") is.
     */
    private val signupWords = setOf("signup", "register", "registration", "create", "new", "join", "التسجيل", "تۆمار", "دروستکردن", "خۆتۆمارکردن")
    private val signupNonLatin = listOf("إنشاء حساب", "انشاء حساب", "حساب جديد", "التسجيل", "تۆمارکردن", "خۆتۆمارکردن", "دروستکردن")

    /**
     * Rule 6's button text (Phase 12): native `Button` nodes of the screen. Latin phrases are
     * matched with spaces and punctuation removed ("Sign up" → `signup`), whole words for
     * `join`. "Continue", "Next" and "Submit" are in neither list: they count for nothing.
     */
    private val signupButtonLatin = listOf("signup", "register", "createaccount", "createanaccount", "createyouraccount", "createnewaccount")
    private val signupButtonWords = setOf("join")
    private val signupButtonNonLatin = listOf(
        "إنشاء حساب", "انشاء حساب", "حساب جديد", "التسجيل",
        "تۆمارکردن", "خۆتۆمارکردن", "دروستکردنی هەژمار",
    )

    /** Log-in buttons: a screen with one is a login, whatever its "Sign up" link says. */
    private val loginButtonLatin = listOf("login", "signin", "logon")
    private val loginButtonNonLatin = listOf("تسجيل الدخول", "دخول", "چوونەژوورەوە", "چوونە ژوورەوە")

    /** Hints and names of a date of birth (no field type exists for it). */
    private val birthdayHints = setOf("birthdatefull", "birthdateday", "birthdatemonth", "birthdateyear", "bday", "bday-day", "bday-month", "bday-year")
    private val birthdayLong = listOf("birthday", "birthdate", "dateofbirth", "تاريخ الميلاد", "بەرواری لەدایکبوون", "لەدایکبوون")
    private val birthdayWords = setOf("dob", "bday", "birth")

    /** The field's own words, lowercased: name, id, label, placeholder, aria-label, title, hint. */
    fun text(s: FieldSignals): String {
        val html = s.htmlAttributes
        return listOfNotNull(
            html["name"], html["id"], html["label"], html["placeholder"], html["aria-label"], html["title"],
            s.hint, s.idEntry,
        ).joinToString(" ")
    }

    /** Words split on separators, camelCase and letter/digit runs stay together (`pw2`). */
    private fun words(raw: String): Set<String> =
        raw.replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.isNotEmpty() }
            .toSet()

    private fun compact(raw: String): String = raw.lowercase().filter { it.isLetterOrDigit() }

    private fun declaredTokens(s: FieldSignals): List<String> {
        val html = s.htmlAttributes
        return (s.autofillHints + listOfNotNull(html["autocomplete"], html["x-autocompletetype"], html["ua-autofill-hints"]))
            .flatMap { it.lowercase().split(' ', ',') }
            .filter { it.isNotBlank() }
    }

    /** What the field says about itself: explicit hints first, then its words. */
    fun marker(s: FieldSignals): Marker {
        val tokens = declaredTokens(s)
        if (tokens.any { it == "new-password" || it == "newpassword" }) return Marker.New
        if (tokens.any { it == "current-password" || it == "currentpassword" }) return Marker.Current
        val raw = text(s)
        if (raw.isBlank()) return Marker.None
        val lower = raw.lowercase()
        val ws = words(raw)
        val joined = compact(raw)
        val current = ws.any { it in currentWords } || currentLong.any { joined.contains(it) } ||
            currentNonLatin.any { lower.contains(it) } || ws.any { it in currentNonLatinWords }
        if (current) return Marker.Current
        val new = ws.any { it in newWords || secondPassword.matches(it) } || newLong.any { joined.contains(it) } ||
            newNonLatin.any { lower.contains(it) } || ws.any { it in newNonLatinWords }
        return if (new) Marker.New else Marker.None
    }

    /** Whether the field's own words say sign-up / register / create. */
    fun saysSignup(s: FieldSignals): Boolean {
        val raw = text(s)
        val ws = words(raw)
        val joined = compact(raw)
        return ws.any { it in signupWords } || joined.contains("signup") || joined.contains("register") ||
            signupNonLatin.any { raw.lowercase().contains(it) }
    }

    /** Whether one button's text says sign up / register / create account / join. */
    fun buttonSaysSignup(text: String): Boolean {
        val lower = text.lowercase()
        val joined = compact(text)
        return signupButtonLatin.any { joined.contains(it) } || words(text).any { it in signupButtonWords } ||
            signupButtonNonLatin.any { lower.contains(it) }
    }

    /** Whether one button's text says log in / sign in (Arabic تسجيل الدخول, Kurdish چوونەژوورەوە). */
    fun buttonSaysLogin(text: String): Boolean {
        val lower = text.lowercase()
        val joined = compact(text)
        return loginButtonLatin.any { joined.contains(it) } || loginButtonNonLatin.any { lower.contains(it) }
    }

    /**
     * Rule 6 from the screen's buttons: some button says sign up and none says log in. A login
     * screen often carries a "Sign up" link next to its "Log in" button; Android can't tell
     * which one submits, so the log-in button wins (the password then gets R7's fallback).
     */
    fun buttonsSaySignup(buttons: List<String>): Boolean =
        buttons.any(::buttonSaysSignup) && buttons.none(::buttonSaysLogin)

    /** A date-of-birth field, among the fields the classifier left untyped. */
    fun isBirthday(s: FieldSignals): Boolean {
        if (declaredTokens(s).any { it in birthdayHints }) return true
        val raw = text(s)
        if (raw.isBlank()) return false
        val lower = raw.lowercase()
        return birthdayLong.any { compact(raw).contains(it) || lower.contains(it) } || words(raw).any { it in birthdayWords }
    }

    /**
     * The new / current decision for [passwords] (the screen's password-type fields in
     * order). [signupContext] is rule 6's "the form also asks for a name, a phone or a date
     * of birth". Returns true for every field that is new.
     */
    fun resolve(passwords: List<FieldSignals>, signupContext: Boolean): List<Boolean> {
        val marks = passwords.map { marker(it) }
        if (marks.isEmpty()) return emptyList()
        val out = arrayOfNulls<Boolean>(marks.size)
        marks.forEachIndexed { i, m ->
            when (m) {
                Marker.Current -> out[i] = false
                Marker.New -> out[i] = true
                Marker.None -> Unit
            }
        }
        // 3. The field right after a new one, if not current, is its confirm.
        for (i in 1 until out.size) if (out[i] == null && out[i - 1] == true) out[i] = true
        val count = marks.size
        val anyCurrent = marks.any { it == Marker.Current }
        val allUnmarked = marks.all { it == Marker.None }
        when {
            // 6. One field, not marked.
            count == 1 -> if (out[0] == null) out[0] = signupContext || saysSignup(passwords[0])
            // 4. Two fields, neither marked current: new and confirm.
            count == 2 && !anyCurrent -> { out[0] = out[0] ?: true; out[1] = out[1] ?: true }
            // 5. Three (or more) unmarked: current, new, new.
            allUnmarked -> for (i in out.indices) out[i] = i != 0
            else -> {
                // Mixed: an unmarked field before the first new one is the current password,
                // one after a current or new one is new (a change form's "new" without a hint).
                val firstNew = out.indexOfFirst { it == true }.takeIf { it >= 0 } ?: Int.MAX_VALUE
                for (i in out.indices) {
                    if (out[i] != null) continue
                    val afterMarked = (0 until i).any { out[it] != null }
                    out[i] = when {
                        i < firstNew && !afterMarked -> false
                        else -> true
                    }
                }
            }
        }
        return out.map { it ?: false }
    }
}
