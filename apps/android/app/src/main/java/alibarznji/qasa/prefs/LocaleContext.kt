package alibarznji.qasa.prefs

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * The in-app language overrides the device locale, matching iOS where `qasaApp` injects
 * `locale` + `layoutDirection` at the root. Wrapping the activity's base context makes
 * `stringResource`, plural rules and Compose's layout direction (read from the
 * configuration) all follow the picked language, so ckb/ar render RTL on an English phone.
 */
object LocaleContext {
    fun wrap(base: Context, language: AppLanguage): Context {
        val locale = Locale.forLanguageTag(language.code)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        // Not `setLayoutDirection(locale)`: older ICU data resolves bare `ckb` as LTR.
        config.setLayoutDirection(if (language.isRtl) Locale("ar") else Locale.ENGLISH)
        return base.createConfigurationContext(config)
    }
}
