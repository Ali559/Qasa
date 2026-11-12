package alibarznji.qasa.vault

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A protected note never shows without the unlock and a tap on the eye (rule 6b). */
class ProtectedItemGateTest {
    @Test fun protectedStartsLockedAndHidesEverything() {
        val gate = ProtectedItemGate(isProtected = true)
        assertFalse(gate.showsItem)
        assertTrue(gate.isHidden("Title"))
        assertTrue(gate.isHidden("Content"))
    }

    @Test fun unlockingShowsTheItemButKeepsValuesHidden() {
        val gate = ProtectedItemGate(isProtected = true).unlock()
        assertTrue(gate.showsItem)
        assertTrue(gate.isHidden("Content"))
    }

    @Test fun theEyeRevealsOneValueAtATime() {
        var gate = ProtectedItemGate(isProtected = true).unlock().toggle("Content")
        assertFalse(gate.isHidden("Content"))
        assertTrue(gate.isHidden("Title"))
        gate = gate.toggle("Content")
        assertTrue(gate.isHidden("Content"))
    }

    @Test fun theEyeDoesNothingWhileLocked() {
        val gate = ProtectedItemGate(isProtected = true).toggle("Content")
        assertTrue(gate.isHidden("Content"))
        assertFalse(gate.showsItem)
    }

    @Test fun leavingHidesRevealedValues() {
        val gate = ProtectedItemGate(isProtected = true).unlock().toggle("Content").leave()
        assertTrue(gate.isHidden("Content"))
    }

    @Test fun theBackgroundLocksAgain() {
        val gate = ProtectedItemGate(isProtected = true).unlock().toggle("Content").lock()
        assertFalse(gate.showsItem)
        assertTrue(gate.isHidden("Content"))
    }

    @Test fun anOrdinaryItemIsNeverGated() {
        val gate = ProtectedItemGate(isProtected = false)
        assertTrue(gate.showsItem)
        assertFalse(gate.isHidden("Password"))
        assertTrue(gate.lock().showsItem)
    }
}
