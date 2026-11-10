package alibarznji.qasa.screens.shell

import android.content.Context
import android.content.Intent
import android.net.Uri

/** The privacy policy and terms of use on qasa-app.com, opened in the browser in the app's language. */
object LegalLinks {
    private const val SITE = "https://qasa-app.com"

    fun url(language: String, terms: Boolean): String {
        val page = if (terms) "terms" else "privacy"
        return when (language) {
            "ar" -> "$SITE/ar/$page"
            "ckb", "ku" -> "$SITE/ckb/$page"
            else -> "$SITE/$page"
        }
    }

    fun open(context: Context, terms: Boolean) {
        val language = context.resources.configuration.locales[0].language
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url(language, terms))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
