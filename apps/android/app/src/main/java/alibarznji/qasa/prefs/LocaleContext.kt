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
        // `-u-nu-latn`: Western digits in every language, like iOS and every string (ckb would
        // otherwise format ٣٩). Resource lookup ignores the extension.
        val locale = Locale.forLanguageTag("${language.code}-u-nu-latn")
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        // Not `setLayoutDirection(locale)`: older ICU data resolves bare `ckb` as LTR.
        config.setLayoutDirection(if (language.isRtl) Locale("ar") else Locale.ENGLISH)
        return base.createConfigurationContext(config)
    }

    /**
     * [context] in the language picked in the app's settings, read now. For strings built
     * outside an activity (services, the autofill UI, application-context singletons), whose
     * own resources follow the device language.
     */
    fun forApp(context: Context): Context = wrap(context, AppPreferences.storedLanguage(context))
}
