package alibarznji.qasa.theme

import androidx.compose.ui.graphics.Color

/**
 * A recognisable stand-in for a site's logo: its first letter on its brand colour, so a
 * login is spotted at a glance without fetching icons from the network (which would leak
 * the user's list of sites). Unknown sites get a stable colour picked from their name.
 */
data class SiteLogo(val letter: String, val color: Color)

private val KnownBrands: List<Pair<String, Color>> = listOf(
    "facebook" to Color(0xFF1877F2),
    "messenger" to Color(0xFF0084FF),
    "whatsapp" to Color(0xFF25D366),
    "instagram" to Color(0xFFD62976),
    "gmail" to Color(0xFFEA4335),
    "google" to Color(0xFF4285F4),
    "youtube" to Color(0xFFFF0000),
    "snapchat" to Color(0xFFF7C600),
    "tiktok" to Color(0xFF111111),
    "telegram" to Color(0xFF229ED9),
    "viber" to Color(0xFF7360F2),
    "twitter" to Color(0xFF1D9BF0),
    "linkedin" to Color(0xFF0A66C2),
    "apple" to Color(0xFF333333),
    "icloud" to Color(0xFF3693F3),
    "microsoft" to Color(0xFF00A4EF),
    "outlook" to Color(0xFF0078D4),
    "amazon" to Color(0xFFFF9900),
    "netflix" to Color(0xFFE50914),
    "spotify" to Color(0xFF1DB954),
    "github" to Color(0xFF24292F),
    "fib" to Color(0xFF0E7A5A),
    "fastpay" to Color(0xFFE2136E),
    "superqi" to Color(0xFFF57C20),
    "zain" to Color(0xFF6B2C91),
    "asiacell" to Color(0xFFE30613),
    "korek" to Color(0xFF00843D),
)

/** Soft, readable-with-white colours for sites we do not recognise. */
private val FallbackColors = listOf(
    Color(0xFF722F99),
    Color(0xFFE07A5F),
    Color(0xFF3FA7C9),
    Color(0xFF2F9E6C),
    Color(0xFFD4537E),
    Color(0xFFB7791F),
    Color(0xFF5B7CF0),
)

fun siteLogoFor(name: String): SiteLogo {
    val trimmed = name.trim()
    val key = trimmed.lowercase()
    val brand = KnownBrands.firstOrNull { (brand, _) -> key.contains(brand) }?.second
    val letter = trimmed.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?"
    val color = brand ?: FallbackColors[Math.floorMod(key.hashCode(), FallbackColors.size)]
    return SiteLogo(letter, color)
}
