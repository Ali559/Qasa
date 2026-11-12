import Foundation

/// What a protected item may show (`design/README.md` rule 6b), kept apart from the view so
/// it can be tested. A protected item starts locked: nothing of it is drawn until `unlock()`.
/// Once open every detail value is hidden until its eye is tapped. Leaving the item forgets
/// what was revealed; going to the background locks it again. Nothing is remembered between
/// openings.
struct ProtectedItemGate: Equatable {
    let isProtected: Bool
    private(set) var isUnlocked: Bool
    private(set) var revealed: Set<String> = []

    init(isProtected: Bool) {
        self.isProtected = isProtected
        isUnlocked = !isProtected
    }

    /// Whether the item itself may be drawn (else the plain cover and the unlock sheet).
    var showsItem: Bool { isUnlocked }

    /// Whether the value of field `id` is shown as dots. Only a protected item hides values
    /// here; other items keep their own per-field secrets.
    func isHidden(_ id: String) -> Bool {
        isProtected && !revealed.contains(id)
    }

    mutating func unlock() {
        isUnlocked = true
    }

    /// The eye: reveal or hide one value. Ignored while locked.
    mutating func toggle(_ id: String) {
        guard isUnlocked, isProtected else { return }
        if revealed.contains(id) { revealed.remove(id) } else { revealed.insert(id) }
    }

    /// The user left the item: revealed values hide again.
    mutating func leave() {
        revealed = []
    }

    /// The app went to the background: a protected item locks again.
    mutating func lock() {
        revealed = []
        if isProtected { isUnlocked = false }
    }
}
