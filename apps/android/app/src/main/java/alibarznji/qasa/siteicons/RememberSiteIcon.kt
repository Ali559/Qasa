package alibarznji.qasa.siteicons

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SiteIconsEntryPoint {
    fun siteIcons(): SiteIconService
}

/**
 * The icon to draw for a login with [website], or null to show its letter. Asks
 * [SiteIconService] for it the first time the tile is drawn; the tile swaps in the icon
 * when it arrives, without animation. Only the app's own screens call this: the autofill
 * service UI shows the letter.
 */
@Composable
fun rememberSiteIcon(website: String?): ImageBitmap? {
    if (LocalInspectionMode.current) return null
    val context = LocalContext.current
    val service = remember(context) {
        EntryPointAccessors.fromApplication(context.applicationContext, SiteIconsEntryPoint::class.java).siteIcons()
    }
    val key = remember(website) { service.keyFor(website) } ?: return null
    val icons by service.icons.collectAsState()
    val active by service.active.collectAsState()
    LaunchedEffect(key, active) { if (active) service.request(key) }
    return icons[key]
}
