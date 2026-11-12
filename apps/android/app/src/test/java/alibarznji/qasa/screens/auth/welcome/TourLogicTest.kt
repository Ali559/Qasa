package alibarznji.qasa.screens.auth.welcome

import alibarznji.qasa.prefs.AppPreferences
import alibarznji.qasa.screens.AppSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TourLogicTest {
    private val width = 1000f
    private val swipe = 100f
    private val slop = 50f

    private fun gesture(startX: Float, endX: Float, dy: Float = 0f, rtl: Boolean = false) =
        TourLogic.gestureDelta(startX, endX, dy, width, rtl, swipe, slop)

    @Test
    fun movesThroughThePagesThenFinishes() {
        assertEquals(TourMove.Page(1), TourLogic.move(0, 1))
        assertEquals(TourMove.Page(2), TourLogic.move(3, -1))
        assertEquals(TourMove.Done, TourLogic.move(3, 1))
        assertEquals(TourMove.Stay, TourLogic.move(0, -1))
        assertTrue(TourLogic.isLast(3))
        assertFalse(TourLogic.isLast(2))
    }

    @Test
    fun tapOnTheTrailingHalfGoesForward() {
        assertEquals(1, gesture(800f, 800f))
        assertEquals(-1, gesture(200f, 200f))
    }

    @Test
    fun tapSidesMirrorInRtl() {
        assertEquals(-1, gesture(800f, 800f, rtl = true))
        assertEquals(1, gesture(200f, 200f, rtl = true))
    }

    @Test
    fun swipeTowardsTheLeadingEdgeGoesForward() {
        assertEquals(1, gesture(700f, 400f))
        assertEquals(-1, gesture(400f, 700f))
        assertEquals(-1, gesture(700f, 400f, rtl = true))
        assertEquals(1, gesture(400f, 700f, rtl = true))
    }

    @Test
    fun aVerticalDragIsIgnored() {
        assertEquals(0, gesture(800f, 820f, dy = 200f))
    }

    @Test
    fun theNameIsStoredTrimmed() {
        assertEquals("Ali", AppPreferences.normalizedUserName("  Ali \n"))
        assertEquals("", AppPreferences.normalizedUserName("   "))
    }

    @Test
    fun thePickedNameComesBeforeTheAccountName() {
        assertEquals("Ali", AppSession.chosenDisplayName(" Ali ", "Account"))
        assertEquals("Account", AppSession.chosenDisplayName("", "Account"))
        assertNull(AppSession.chosenDisplayName("", AppSession.PLACEHOLDER_USERNAME))
        assertNull(AppSession.chosenDisplayName("  ", ""))
    }
}
