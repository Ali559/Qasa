// Where the build puts the fonts (scripts/build.ts: the popup's assets, under stable names),
// so the service worker can read them for the in-page menu (content/fonts.ts). The menu
// draws text at all four bundled weights (row titles are 500, as iOS `type.rowTitle`).
export const FONT_FILES = {
  400: 'fonts/IBMPlexSansArabic-Regular.ttf',
  500: 'fonts/IBMPlexSansArabic-Medium.ttf',
  600: 'fonts/IBMPlexSansArabic-SemiBold.ttf',
  700: 'fonts/IBMPlexSansArabic-Bold.ttf',
} as const

export type FontWeight = keyof typeof FONT_FILES
export const FONT_WEIGHTS = Object.keys(FONT_FILES).map(Number) as FontWeight[]
