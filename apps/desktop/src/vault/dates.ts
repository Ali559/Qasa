// Typed-date parsing for iOS `ValidatedDateField`: the en_US_POSIX formats it accepts,
// non-lenient ("Nov 6 2000", "Nov 6, 2000", "2000-11-06", "06/11/2000", "11/06/2000").
const MONTHS = ['jan', 'feb', 'mar', 'apr', 'may', 'jun', 'jul', 'aug', 'sep', 'oct', 'nov', 'dec']

function valid(y: number, m: number, d: number): boolean {
  const date = new Date(y, m - 1, d)
  return date.getFullYear() === y && date.getMonth() === m - 1 && date.getDate() === d
}

export function parseDate(raw: string): Date | null {
  const s = raw.trim()
  let m = s.match(/^([A-Za-z]{3})\s+(\d{1,2}),?\s+(\d{4})$/)
  if (m) {
    const month = MONTHS.indexOf(m[1].toLowerCase()) + 1
    return month && valid(+m[3], month, +m[2]) ? new Date(+m[3], month - 1, +m[2]) : null
  }
  m = s.match(/^(\d{4})-(\d{2})-(\d{2})$/)
  if (m) return valid(+m[1], +m[2], +m[3]) ? new Date(+m[1], +m[2] - 1, +m[3]) : null
  m = s.match(/^(\d{2})\/(\d{2})\/(\d{4})$/)
  if (m) {
    if (valid(+m[3], +m[2], +m[1])) return new Date(+m[3], +m[2] - 1, +m[1])
    if (valid(+m[3], +m[1], +m[2])) return new Date(+m[3], +m[1] - 1, +m[2])
  }
  // Locale-aware fallback, like iOS's medium-style DateFormatter, for dates the picker
  // wrote in the current language.
  const loose = Date.parse(s)
  return Number.isNaN(loose) ? null : new Date(loose)
}
