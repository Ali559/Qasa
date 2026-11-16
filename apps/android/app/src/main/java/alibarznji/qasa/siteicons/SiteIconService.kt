package alibarznji.qasa.siteicons

import alibarznji.qasa.prefs.AppPreferences
import alibarznji.qasa.vault.VaultSession
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * A login's own icon (docs/site-icons.md). [icons] holds what the tiles draw, keyed by
 * [keyFor]; a tile with no entry shows its letter and calls [request], which reads the
 * encrypted cache and, when the entry is missing or due, queues a fetch from the site
 * itself (at most 4 at a time, one per host). `androidapp://` logins take the installed
 * app's icon from [PackageManager], with no network.
 *
 * Works only while the vault is unlocked and the **Website icons** setting is on: locking
 * drops everything held in memory and stops the queue; turning the setting off also
 * deletes the cache.
 */
@Singleton
class SiteIconService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val session: VaultSession,
    private val prefs: AppPreferences,
) {
    /** Cache reads and fetches; cancelled as a whole on lock or when the setting goes off. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Watches the lock and the setting; never cancelled. */
    private val watcher = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val cache = SiteIconCache(File(context.filesDir, CACHE_DIR))
    private val fetcher by lazy { SiteIconFetcher(SiteIconImages::normalise) }
    private val permits = Semaphore(MAX_CONCURRENT)

    private val _icons = MutableStateFlow<Map<String, ImageBitmap>>(emptyMap())

    /** Icons ready to draw, by [keyFor]. Empty while locked or with the setting off. */
    val icons: StateFlow<Map<String, ImageBitmap>> = _icons.asStateFlow()

    /** Keys looked at since the unlock (cache read, fetch queued or done): asked once each. */
    private val seen = ConcurrentHashMap.newKeySet<String>()

    private val enabled: Boolean get() = prefs.siteIconsEnabled.value && session.isUnlocked

    /** Unlocked with the setting on: the only state in which anything is read or fetched. */
    val active: StateFlow<Boolean> =
        combine(session.unlocked, prefs.siteIconsEnabled) { unlocked, on -> unlocked && on }
            .stateIn(watcher, SharingStarted.Eagerly, session.isUnlocked && prefs.siteIconsEnabled.value)

    init {
        combine(session.unlocked, prefs.siteIconsEnabled) { unlocked, on -> unlocked to on }
            .distinctUntilChanged()
            .drop(1)
            .onEach { (unlocked, on) -> if (!unlocked || !on) forget(deleteCache = !on) }
            .launchIn(watcher)
    }

    /** The [icons] key for a login's website: its host, `app:<package>`, or null (letter only). */
    fun keyFor(website: String?): String? {
        if (website.isNullOrBlank()) return null
        SiteIconRules.androidPackage(website)?.let { return APP_PREFIX + it }
        return SiteIconRules.hostFor(website)
    }

    /** A tile for [key] was drawn: load its cached icon, and fetch it when missing or due. */
    fun request(key: String) {
        if (!enabled || !seen.add(key)) return
        scope.launch {
            if (key.startsWith(APP_PREFIX)) loadAppIcon(key) else load(key, force = false)
        }
    }

    /** A login was saved with [website]: fetch its icon unless the cache holds a fresh one. */
    fun onLoginSaved(website: String) {
        val key = keyFor(website) ?: return
        if (!enabled) return
        seen.add(key)
        scope.launch {
            if (key.startsWith(APP_PREFIX)) loadAppIcon(key) else load(key, force = false)
        }
    }

    /** Deletes the cache and everything in memory: sign out, reset, a new master password. */
    fun clearAll() = forget(deleteCache = true)

    private fun forget(deleteCache: Boolean) {
        scope.coroutineContext.cancelChildren()
        seen.clear()
        _icons.value = emptyMap()
        if (deleteCache) scope.launch { cache.clear() }
    }

    private suspend fun load(host: String, force: Boolean) {
        val cached = withKey { cache.read(host, it) }
        cached?.png?.let { show(host, it) }
        if (!force && !cache.isDue(cached)) return
        permits.withPermit {
            if (!enabled) return
            val result = fetcher.fetch(host)
            // Locked or switched off meanwhile: nothing is written.
            if (!enabled) return
            val entry = withKey { key ->
                when (result) {
                    is SiteIconFetchResult.Icon -> cache.record(host, SiteIconStatus.Icon, result.png, key)
                    SiteIconFetchResult.None -> cache.record(host, SiteIconStatus.None, null, key)
                    SiteIconFetchResult.Failure -> cache.record(host, SiteIconStatus.Failure, null, key)
                }
            } ?: return
            val png = entry.png
            if (png != null) show(host, png) else _icons.value = _icons.value - host
        }
    }

    private fun loadAppIcon(key: String) {
        val pkg = key.removePrefix(APP_PREFIX)
        val drawable = try {
            context.packageManager.getApplicationIcon(pkg)
        } catch (_: PackageManager.NameNotFoundException) {
            return
        }
        if (!enabled) return
        val bitmap = SiteIconImages.fromDrawable(drawable).asImageBitmap()
        _icons.value = _icons.value + (key to bitmap)
    }

    private fun show(key: String, png: ByteArray) {
        if (!enabled) return
        val bitmap = BitmapFactory.decodeByteArray(png, 0, png.size) ?: return
        _icons.value = _icons.value + (key to bitmap.asImageBitmap())
    }

    /** Runs [block] with a copy of the vault key (wiped after), or returns null when locked. */
    private inline fun <T> withKey(block: (ByteArray) -> T): T? {
        val key = runCatching { session.requireEncKey().copyOf() }.getOrNull() ?: return null
        return try {
            block(key)
        } catch (_: Exception) {
            null
        } finally {
            key.fill(0)
        }
    }

    private companion object {
        const val CACHE_DIR = "site-icons"
        const val APP_PREFIX = "app:"
        const val MAX_CONCURRENT = 4
    }
}
