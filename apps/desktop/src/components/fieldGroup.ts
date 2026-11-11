// The grouped-card form fields (design/README.md rule 4): a `FieldGroup` card holds
// `GroupRow`s; each row registers its label and error so the group can print the
// messages 6 under the card ("Email: Enter an email like …" when it has more than one row).
import type { InjectionKey } from 'vue'

export interface GroupRowEntry {
  /** The row's control id; its message gets `${id}-error`. */
  id: string
  label: () => string
  error: () => string | null | undefined
  el: () => HTMLElement | null
}

export interface FieldGroupContext {
  register(entry: GroupRowEntry): void
  unregister(id: string): void
}

export const FIELD_GROUP: InjectionKey<FieldGroupContext> = Symbol('fieldGroup')

/** The id of the message under the card for the row whose control is `id`. */
export const errorIdFor = (id: string) => `${id}-error`

let next = 0
/** A unique control id for a row. */
export const rowId = (prefix = 'row') => `${prefix}-${++next}`
