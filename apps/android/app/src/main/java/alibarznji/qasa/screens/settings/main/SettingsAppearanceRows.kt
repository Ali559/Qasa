package alibarznji.qasa.screens.settings.main

import alibarznji.qasa.R
import alibarznji.qasa.prefs.AppLanguage
import alibarznji.qasa.prefs.AppPreferences
import alibarznji.qasa.prefs.AppThemeMode
import alibarznji.qasa.theme.NavChevronRow
import alibarznji.qasa.theme.QasaIcons
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import alibarznji.qasa.theme.RowDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import alibarznji.qasa.theme.PickerOption
import alibarznji.qasa.theme.QasaPickerSheet
import alibarznji.qasa.theme.QasaTextInputSheet
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization

/** Native names, never translated (iOS `AppLanguage.displayName`). */
private val AppLanguage.nativeName: String
    get() = when (this) {
        AppLanguage.EN -> "English"
        AppLanguage.CKB -> "کوردی"
        AppLanguage.AR -> "العربية"
    }

/** iOS `AppThemePreference.displayNameKey`. */
@get:StringRes
private val AppThemeMode.labelRes: Int
    get() = when (this) {
        AppThemeMode.SYSTEM -> R.string.settings_theme_system
        AppThemeMode.LIGHT -> R.string.settings_theme_light
        AppThemeMode.DARK -> R.string.settings_theme_dark
    }

/**
 * Appearance card (`SettingsView.swift`): Your name, Language, then Theme, each a row whose
 * trailing text is the current value. The name opens the text-prompt sheet; the other two
 * open a menu (iOS `Menu`).
 */
@Composable
internal fun AppearanceRows(prefs: AppPreferences) {
    val c = qasaColors
    val lang by prefs.language.collectAsState()
    val theme by prefs.themeMode.collectAsState()
    val themeLabels = AppThemeMode.entries.associateWith { stringResource(it.labelRes) }

    YourNameRow(prefs)
    RowDivider()

    MenuRow(
        icon = QasaIcons.Language,
        title = stringResource(R.string.settings_language),
        options = AppLanguage.entries,
        selected = lang,
        label = { it.nativeName },
        onSelect = prefs::setLanguage,
    )
    RowDivider()
    MenuRow(
        icon = QasaIcons.ThemeDark,
        title = stringResource(R.string.settings_theme),
        options = AppThemeMode.entries,
        selected = theme,
        label = { themeLabels.getValue(it) },
        onSelect = prefs::setThemeMode,
    )
}

@Composable
private fun <T> MenuRow(
    icon: ImageVector,
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    // iOS: the row opens the app's own picker sheet, titled with the row's title.
    var open by remember { mutableStateOf(false) }
    NavChevronRow(icon = icon, title = title, trailingText = label(selected), onClick = { open = true })
    if (open) {
        QasaPickerSheet(
            title = title,
            options = options.map { PickerOption(it, label(it)) },
            selected = selected,
            onSelect = onSelect,
            onDismiss = { open = false },
        )
    }
}

/** iOS "Your name": edits the name kept on this device; saving it empty clears it. */
@Composable
private fun YourNameRow(prefs: AppPreferences) {
    val userName by prefs.userName.collectAsState()
    var open by remember { mutableStateOf(false) }
    val title = stringResource(R.string.settings_yourname)
    NavChevronRow(
        icon = QasaIcons.Person,
        title = title,
        trailingText = userName.ifEmpty { null },
        onClick = { open = true },
    )
    if (open) {
        QasaTextInputSheet(
            title = title,
            initial = userName,
            placeholder = stringResource(R.string.onboarding_nameplaceholder),
            confirmLabel = stringResource(R.string.common_save),
            onConfirm = {
                prefs.setUserName(it)
                open = false
            },
            onDismiss = { open = false },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            allowEmpty = true,
        )
    }
}
