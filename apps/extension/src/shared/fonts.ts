// Where the build puts the fonts (scripts/build.ts: the popup's assets, under stable names),
// so the service worker can read them for the in-page menu (content/fonts.ts).
export const FONT_FILES = {
  rubik: 'fonts/Rubik-Variable.ttf',
  rudaw: 'fonts/rudawregular2.ttf',
} as const
