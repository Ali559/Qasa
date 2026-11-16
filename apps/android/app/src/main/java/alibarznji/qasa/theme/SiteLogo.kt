package alibarznji.qasa.theme

/**
 * The calm letter a login shows when it has no site icon (docs/site-icons.md, option C):
 * the first letter or digit of the item name, upper-cased, or `?` when there is none. It is
 * drawn in `accent` on `cardMuted`; there is no colour per site.
 */
fun siteLetterFor(name: String): String {
    val first = name.trim().codePoints().filter(Character::isLetterOrDigit).findFirst()
    if (!first.isPresent) return "?"
    return String(Character.toChars(first.asInt)).uppercase()
}
