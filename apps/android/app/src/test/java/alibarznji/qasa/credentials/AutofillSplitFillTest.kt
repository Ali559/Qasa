package alibarznji.qasa.credentials

import alibarznji.qasa.credentials.AutofillFieldType.Email
import alibarznji.qasa.credentials.AutofillFieldType.NewPassword
import alibarznji.qasa.credentials.AutofillFieldType.OneTimeCode
import alibarznji.qasa.credentials.AutofillFieldType.Password
import alibarznji.qasa.credentials.AutofillFieldType.Username
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 12: R7 (a password field never shows nothing), R8 (split fills), R4 two-screen saves. */
class AutofillSplitFillTest {
    private fun form(vararg types: AutofillFieldType) =
        AutofillForm(types.mapIndexed { i, t -> ClassifiedField(i, t, FieldSignals()) })

    private val app = AutofillCaller.App("com.dozarawa.app")
    private val otherApp = AutofillCaller.App("com.example.other")
    private val site = AutofillCaller.Web("example.com", "com.android.chrome")

    // R7

    @Test
    fun loginPasswordWithNoMatchOffersStrongPasswordFirst() {
        val offers = AutofillOffers.login(form(Username, Password), focused = 1)
        assertEquals(listOf(1), offers.fallbackStrongIds)
        assertEquals(listOf(LoginRow.StrongPassword(fallback = true), LoginRow.Search), AutofillOffers.loginRows(offers, emptyList<String>()))
    }

    @Test
    fun loginPasswordWithMatchesOffersLoginsThenStrongThenSearch() {
        val offers = AutofillOffers.login(form(Username, Password), focused = 1)
        assertEquals(
            listOf(LoginRow.Saved("a"), LoginRow.Saved("b"), LoginRow.StrongPassword(fallback = true), LoginRow.Search),
            AutofillOffers.loginRows(offers, listOf("a", "b")),
        )
    }

    @Test
    fun newFieldKeepsOnlyTheStrongPassword() {
        // Sign-up: the new field's only row is the strong password; the username keeps its logins.
        val signUp = AutofillOffers.login(form(Username, NewPassword, NewPassword), focused = 1)
        assertEquals(emptyList<Int>(), signUp.fallbackStrongIds)
        assertEquals(
            listOf(LoginRow.StrongPassword(fallback = false), LoginRow.Saved("a"), LoginRow.Search),
            AutofillOffers.loginRows(signUp, listOf("a")),
        )
        // A reset page (new fields only): nothing else.
        val reset = AutofillOffers.login(form(NewPassword, NewPassword))
        assertEquals(listOf(LoginRow.StrongPassword(fallback = false)), AutofillOffers.loginRows(reset, listOf("a")))
    }

    @Test
    fun changeFormsCurrentPasswordGetsNoFallback() {
        val change = AutofillOffers.login(form(Password, NewPassword, NewPassword), focused = 0)
        assertEquals(emptyList<Int>(), change.fallbackStrongIds)
    }

    @Test
    fun fallbackFillsOnlyTheLoginPassword() {
        val f = form(Username, Password)
        assertEquals(mapOf(1 to "pw"), AutofillOffers.fallbackStrongPasswordValues(f, listOf(1), "pw"))
        // A target that is no longer a password field fills nothing.
        assertEquals(emptyMap<Int, String>(), AutofillOffers.fallbackStrongPasswordValues(f, listOf(0), "pw"))
        // The username-only screen has no fallback row (its dataset would never show).
        assertEquals(emptyList<Int>(), AutofillOffers.login(form(Username), focused = 0).fallbackStrongIds)
    }

    // R8

    @Test
    fun recentPickComesFirstForAFieldItDidNotFill() {
        val cache = RecentLoginPickCache()
        cache.record("item-1", app, setOf(Username), now = 1_000)
        assertEquals("item-1", cache.lookup(app, Password, now = 1_000 + 60_000))
        // The same field type again: the user may want another account.
        assertNull(cache.lookup(app, Username, now = 1_000 + 60_000))
        // Never on a new-password field.
        assertNull(cache.lookup(app, NewPassword, now = 2_000))
        assertNull(cache.lookup(app, null, now = 2_000))

        val offers = AutofillOffers.login(form(Password), focused = 0)
        assertEquals(
            listOf(LoginRow.Saved("item-1", recent = true), LoginRow.Saved("b"), LoginRow.StrongPassword(fallback = true), LoginRow.Search),
            AutofillOffers.loginRows(offers, listOf("item-1", "b"), recent = "item-1"),
        )
    }

    @Test
    fun recentPickExpiresAfterTwoMinutes() {
        val cache = RecentLoginPickCache()
        cache.record("item-1", app, setOf(Username), now = 0)
        assertEquals("item-1", cache.lookup(app, Password, now = RecentLoginPickCache.TTL_MILLIS - 1))
        assertNull(cache.lookup(app, Password, now = RecentLoginPickCache.TTL_MILLIS))
        assertNull(cache.current(RecentLoginPickCache.TTL_MILLIS + 1))
        // A clock that went backwards is no pick either.
        cache.record("item-1", app, setOf(Username), now = 10_000)
        assertNull(cache.lookup(app, Password, now = 5_000))
    }

    @Test
    fun recentPickBelongsToItsCaller() {
        val cache = RecentLoginPickCache()
        cache.record("item-1", app, setOf(Username), now = 0)
        assertNull(cache.lookup(otherApp, Password, now = 1))
        assertNull(cache.lookup(AutofillCaller.Web("com.dozarawa.app", "com.android.chrome"), Password, now = 1))
        cache.record("item-2", site, setOf(Username), now = 0)
        assertEquals("item-2", cache.lookup(AutofillCaller.Web("example.com", "org.mozilla.firefox"), Password, now = 1))
        assertNull(cache.lookup(AutofillCaller.Web("other.com", "com.android.chrome"), Password, now = 1))
    }

    @Test
    fun recentPickFillsFieldsCumulativelyForTheSameItem() {
        val cache = RecentLoginPickCache()
        cache.record("item-1", app, setOf(Username), now = 0)
        cache.recordAgain("item-1", setOf(Password), now = 10)
        assertNull(cache.lookup(app, Password, now = 20))
        assertEquals("item-1", cache.lookup(app, OneTimeCode, now = 20))
        // A history report for another item changes nothing.
        cache.recordAgain("item-9", setOf(OneTimeCode), now = 30)
        assertEquals("item-1", cache.lookup(app, OneTimeCode, now = 40))
        // The history doesn't restart the 2 minutes.
        assertEquals(0L, cache.current(50)!!.pickedAt)
        // A pick of another login replaces it.
        cache.record("item-2", app, setOf(Password), now = 60)
        assertEquals("item-2", cache.lookup(app, Username, now = 70))
    }

    @Test
    fun recentPickIsClearedOnLock() {
        // VaultSession.lock() calls RecentLoginPicks.clear().
        RecentLoginPicks.cache.record("item-1", app, setOf(Username), now = 0)
        RecentLoginPicks.clear()
        assertNull(RecentLoginPicks.cache.lookup(app, Password, now = 1))
        val cache = RecentLoginPickCache()
        cache.record("item-1", app, setOf(Username), now = 0)
        cache.clear()
        assertNull(cache.current(1))
    }

    @Test
    fun recentPickNeverStoresValues() {
        // Only the id, the caller key, the filled field types and the time.
        val fields = RecentLoginPick::class.java.declaredFields.filter { !java.lang.reflect.Modifier.isStatic(it.modifiers) }.map { it.name }.toSet()
        assertEquals(setOf("itemId", "callerKey", "filled", "pickedAt"), fields)
        val cache = RecentLoginPickCache()
        cache.record("item-1", app, setOf(Username, Password), now = 0)
        val pick = cache.current(1)!!
        assertEquals(RecentLoginPick("item-1", "app:com.dozarawa.app", setOf(Username, Password), 0), pick)
    }

    @Test
    fun recentDatasetIdRoundTrips() {
        val id = RecentLoginPicks.datasetId("0b6f-uuid", setOf(Password, OneTimeCode))
        assertEquals("0b6f-uuid" to setOf(Password, OneTimeCode), RecentLoginPicks.parseDatasetId(id))
        assertNull(RecentLoginPicks.parseDatasetId(GeneratedPasswordLog.DATASET_PREFIX + "x"))
    }

    // R4 two-screen

    @Test
    fun twoScreenSaveIsForAppsOnAndroid12Plus() {
        assertTrue(TwoScreenSave.applies(app, 31, compatMode = false))
        assertFalse(TwoScreenSave.applies(app, 30, compatMode = false))
        assertFalse(TwoScreenSave.applies(app, 34, compatMode = true))
        assertFalse(TwoScreenSave.applies(site, 34, compatMode = false))
        // A browser without a web domain is still a browser.
        assertFalse(TwoScreenSave.applies(AutofillCaller.App("com.android.chrome"), 34, compatMode = false))
    }

    @Test
    fun usernameScreenDelaysItsSaveAndThePasswordScreenRequiresBoth() {
        // Screen 1: the username alone.
        val first = form(Username)
        val delayed = TwoScreenSave.delayedOf(first)!!
        assertEquals(0, delayed.usernameId)
        assertFalse(delayed.isEmail)
        // An email-only screen: classified as the username step, saved as an email.
        val emailStep = AutofillForm(listOf(ClassifiedField(0, Email, FieldSignals(htmlAttributes = mapOf("type" to "email")))))
        assertTrue(TwoScreenSave.delayedOf(emailStep)!!.isEmail)
        // A screen with a password is never delayed.
        assertNull(TwoScreenSave.delayedOf(form(Username, Password)))

        // Screen 2 (ids from screen 1 carried in the client state; here id 7).
        val second = form(Password)
        val plan = TwoScreenSave.plan(second, previousUsername = 7)!!
        assertEquals(0, plan.passwordId)
        assertEquals(7, plan.usernameId)
        // Without a delayed username, the plan is today's.
        assertEquals(AutofillOffers.save(second), TwoScreenSave.plan(second, null))
        // A screen with its own username keeps it.
        assertEquals(0, TwoScreenSave.plan(form(Username, Password), previousUsername = 7)!!.usernameId)
    }

    @Test
    fun saveReadsTheUsernameFromTheEarlierScreen() {
        assertEquals("ali", TwoScreenSave.username(null, listOf(mapOf(7 to "ali")), 7, listOf("ali")))
        assertEquals("ali", TwoScreenSave.username("", listOf(mapOf(3 to "ali")), null, listOf("ali")))
        assertEquals("typed", TwoScreenSave.username("typed", listOf(mapOf(7 to "ali")), 7, listOf("ali")))
        assertEquals("", TwoScreenSave.username(null, emptyList<Map<Int, String>>(), 7, emptyList()))
    }
}
