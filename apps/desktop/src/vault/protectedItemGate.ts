// What a protected item may show (design/README.md rule 6b), kept apart from the view so it
// can be tested; iOS `ProtectedItemGate`. A protected item starts locked: nothing of it is
// drawn until `unlock()`. Once open every detail value is hidden until its eye is clicked.
// Leaving the item forgets what was revealed; going to the background (the window hidden,
// or the vault locking) locks it again. Nothing is remembered between openings: each
// opening makes a new gate.
//
// Plain TS `private` (not `#private`) so the instance works inside a Vue `ref`/`reactive`.
export class ProtectedItemGate {
  readonly isProtected: boolean
  private unlocked: boolean
  private revealed = new Set<string>()

  constructor(isProtected: boolean) {
    this.isProtected = isProtected
    this.unlocked = !isProtected
  }

  get isUnlocked(): boolean {
    return this.unlocked
  }

  /** Whether the item itself may be drawn (else the plain cover and the unlock sheet). */
  get showsItem(): boolean {
    return this.unlocked
  }

  /** Whether the value of field `id` is shown as dots. Only a protected item hides values
   * here; other items keep their own per-field secrets. */
  isHidden(id: string): boolean {
    return this.isProtected && !this.revealed.has(id)
  }

  unlock(): void {
    this.unlocked = true
  }

  /** The eye: reveal or hide one value. Ignored while locked. */
  toggle(id: string): void {
    if (!this.unlocked || !this.isProtected) return
    if (this.revealed.has(id)) this.revealed.delete(id)
    else this.revealed.add(id)
  }

  /** The user left the item: revealed values hide again. */
  leave(): void {
    this.revealed.clear()
  }

  /** The app went to the background: a protected item locks again. */
  lock(): void {
    this.revealed.clear()
    if (this.isProtected) this.unlocked = false
  }
}
