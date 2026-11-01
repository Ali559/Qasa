package alibarznji.qasa.prefs

import alibarznji.qasa.R
import android.util.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The in-app language must win over the device locale: on an English device, picking
 * ckb/ar has to resolve the translated catalog and flip layout direction to RTL.
 */
@RunWith(AndroidJUnit4::class)
class LocaleContextTest {
    private val base = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun arabicResolvesArabicStringsAndRtl() {
        val ctx = LocaleContext.wrap(base, AppLanguage.AR)
        assertEquals("فتح الخزنة", ctx.getString(R.string.auth_unlock_title))
        assertEquals(LayoutDirection.RTL, ctx.resources.configuration.layoutDirection)
    }

    @Test
    fun kurdishResolvesKurdishStringsAndRtl() {
        val ctx = LocaleContext.wrap(base, AppLanguage.CKB)
        assertEquals("کردنەوەی کۆگا", ctx.getString(R.string.auth_unlock_title))
        assertEquals(LayoutDirection.RTL, ctx.resources.configuration.layoutDirection)
    }

    @Test
    fun englishIsLtr() {
        val ctx = LocaleContext.wrap(base, AppLanguage.EN)
        assertEquals("Unlock Vault", ctx.getString(R.string.auth_unlock_title))
        assertEquals(LayoutDirection.LTR, ctx.resources.configuration.layoutDirection)
    }
}
