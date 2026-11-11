// Geometry of the in-page icon and menu. The menu lives in a closed shadow root, so the
// Playwright tests click it by coordinates computed from these numbers.

/** The Qasa icon: a square inside the field, `ICON_INSET` from its inline-end edge. */
export const ICON_SIZE = 22
export const ICON_INSET = 6

/** The menu opens `MENU_GAP` below the field, aligned with its inline-start edge. */
export const MENU_GAP = 4
export const MENU_WIDTH = 280
/** `space.sectionInsetVertical`: rows sit 4 in, plus their own 10, so their content is 14
 *  (`space.sectionInsetHorizontal`) from the edge, as in an iOS `SettingsSection`. */
export const MENU_PADDING = 4
export const MENU_HEADER = 36
/** An iOS `SettingsRow` with a subtitle: `space.rowVertical` × 2 + the 15 title and 12
 *  subtitle at the 1.5 line height + `space.rowTextGap` (62.5, over `size.rowMinHeight`). */
export const ROW_HEIGHT = 10 * 2 + 15 * 1.5 + 2 + 12 * 1.5
/** Menu clicks sooner than this after it appears are ignored (clickjacking). */
export const MENU_ARM_MS = 500
/** The menu waits at most this long for its fonts (content/fonts.ts) before showing. */
export const FONT_WAIT_MS = 400
