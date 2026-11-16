// The grouped-card form fields (design/README.md rule 4): a `FieldGroup` card holds
// `GroupRow`s; each row shows its own message right under its value.

/** The id of the message under a row's value, for the row whose control is `id`. */
export const errorIdFor = (id: string) => `${id}-error`

let next = 0
/** A unique control id for a row. */
export const rowId = (prefix = 'row') => `${prefix}-${++next}`
