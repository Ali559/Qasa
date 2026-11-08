// Geometry of the in-page icon and menu. The menu lives in a closed shadow root, so the
// Playwright tests click it by coordinates computed from these numbers.

/** The Qasa icon: a square inside the field, `ICON_INSET` from its inline-end edge. */
export const ICON_SIZE = 22
export const ICON_INSET = 6

/** The menu opens `MENU_GAP` below the field, aligned with its inline-start edge. */
export const MENU_GAP = 4
export const MENU_WIDTH = 280
export const MENU_PADDING = 6
export const MENU_HEADER = 36
export const ROW_HEIGHT = 52
/** Menu clicks sooner than this after it appears are ignored (clickjacking). */
export const MENU_ARM_MS = 500
