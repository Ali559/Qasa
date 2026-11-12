package alibarznji.qasa.vault

/**
 * iOS `ProtectedItemGate` (`design/README.md` rule 6b): what a protected item may show,
 * kept apart from the screen so it can be tested. A protected item starts locked: nothing
 * of it is drawn until [unlock]. Once open every detail value is hidden until its eye is
 * tapped. [leave] forgets what was revealed; [lock] (the app going to the background)
 * locks it again. Nothing is remembered between openings.
 */
data class ProtectedItemGate(
    val isProtected: Boolean,
    val isUnlocked: Boolean = !isProtected,
    val revealed: Set<String> = emptySet(),
) {
    /** Whether the item itself may be drawn (else the plain cover and the unlock sheet). */
    val showsItem: Boolean get() = isUnlocked

    /** Whether field [id]'s value shows as dots. Only a protected item hides values here. */
    fun isHidden(id: String): Boolean = isProtected && id !in revealed

    fun unlock(): ProtectedItemGate = copy(isUnlocked = true)

    /** The eye: reveal or hide one value. Ignored while locked. */
    fun toggle(id: String): ProtectedItemGate =
        if (!isUnlocked || !isProtected) this else copy(revealed = if (id in revealed) revealed - id else revealed + id)

    /** The user left the item: revealed values hide again. */
    fun leave(): ProtectedItemGate = copy(revealed = emptySet())

    /** The app went to the background: a protected item locks again. */
    fun lock(): ProtectedItemGate = copy(revealed = emptySet(), isUnlocked = !isProtected)
}
