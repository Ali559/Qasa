package alibarznji.qasa.theme

import androidx.compose.ui.input.pointer.PointerEventType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserActivityTest {
    @Test
    fun touchesAndScrollsCount() {
        assertTrue(countsAsUserActivity(PointerEventType.Press, anyPressed = true))
        assertTrue(countsAsUserActivity(PointerEventType.Release, anyPressed = true))
        assertTrue(countsAsUserActivity(PointerEventType.Scroll, anyPressed = false))
        assertTrue(countsAsUserActivity(PointerEventType.Move, anyPressed = true))
    }

    @Test
    fun hoverBookkeepingDoesNotCount() {
        // A layout shift under a resting mouse synthesizes a move with nothing pressed.
        assertFalse(countsAsUserActivity(PointerEventType.Move, anyPressed = false))
        assertFalse(countsAsUserActivity(PointerEventType.Enter, anyPressed = false))
        assertFalse(countsAsUserActivity(PointerEventType.Exit, anyPressed = false))
        assertFalse(countsAsUserActivity(PointerEventType.Unknown, anyPressed = false))
    }
}
