package alibarznji.qasa.theme

import alibarznji.qasa.credentials.AutofillMatcher
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Monochrome icons for common sites, bundled (`assets/site-icons`, generated into
 * [SiteIconData]) so nothing is fetched and no third party learns which sites the user
 * has. A login whose website isn't in the table keeps its [SiteLogo] letter tile.
 * Same table and matching as iOS `SiteIcon` and the desktop `siteIcon.ts`.
 */
object SiteIcons {
    /**
     * The icon id for a login's website, or null. The host is read like autofill reads it
     * ([AutofillMatcher.hostOf]); then the host and each parent domain are looked up, so
     * `accounts.google.com` and `mail.google.co.uk` find their site. The table lists only
     * registrable domains (the desktop tests check each against the Public Suffix List), so
     * the first hit is the host's registrable domain, the same answer on every platform.
     */
    fun idFor(website: String): String? {
        var host = AutofillMatcher.hostOf(website) ?: return null
        while (host.contains('.')) {
            SiteIconData.domains[host]?.let { return it }
            host = host.substringAfter('.')
        }
        return null
    }

    private val cache = HashMap<String, ImageVector>()

    /** The icon as a 24dp vector, filled black: tint it with a theme colour when drawing. */
    fun vector(id: String): ImageVector? = synchronized(cache) {
        cache[id] ?: SiteIconData.paths[id]?.let { path ->
            ImageVector.Builder(
                name = "site-$id",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black))
                .build()
                .also { cache[id] = it }
        }
    }

    /** [vector] for a login's website, or null to keep the letter tile. */
    fun forWebsite(website: String): ImageVector? = idFor(website)?.let(::vector)
}
