import Testing
@testable import qasa

/// A protected note never shows without the unlock and a tap on the eye (design/README.md
/// rule 6b, TODO Phase 4).
struct ProtectedItemGateTests {
    @Test func protectedStartsLockedAndHidesEverything() {
        let gate = ProtectedItemGate(isProtected: true)
        #expect(!gate.showsItem)
        #expect(gate.isHidden("Title"))
        #expect(gate.isHidden("Content"))
    }

    @Test func unlockingShowsTheItemButKeepsValuesHidden() {
        var gate = ProtectedItemGate(isProtected: true)
        gate.unlock()
        #expect(gate.showsItem)
        #expect(gate.isHidden("Content"))
    }

    @Test func theEyeRevealsOneValueAtATime() {
        var gate = ProtectedItemGate(isProtected: true)
        gate.unlock()
        gate.toggle("Content")
        #expect(!gate.isHidden("Content"))
        #expect(gate.isHidden("Title"))
        gate.toggle("Content")
        #expect(gate.isHidden("Content"))
    }

    @Test func theEyeDoesNothingWhileLocked() {
        var gate = ProtectedItemGate(isProtected: true)
        gate.toggle("Content")
        #expect(gate.isHidden("Content"))
        #expect(!gate.showsItem)
    }

    @Test func leavingHidesRevealedValues() {
        var gate = ProtectedItemGate(isProtected: true)
        gate.unlock()
        gate.toggle("Content")
        gate.leave()
        #expect(gate.isHidden("Content"))
    }

    @Test func theBackgroundLocksAgain() {
        var gate = ProtectedItemGate(isProtected: true)
        gate.unlock()
        gate.toggle("Content")
        gate.lock()
        #expect(!gate.showsItem)
        #expect(gate.isHidden("Content"))
    }

    @Test func anOrdinaryItemIsNeverGated() {
        var gate = ProtectedItemGate(isProtected: false)
        #expect(gate.showsItem)
        #expect(!gate.isHidden("Password"))
        gate.lock()
        #expect(gate.showsItem)
    }
}
