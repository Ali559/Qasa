package alibarznji.qasa.screens.settings.main

import alibarznji.qasa.R
import alibarznji.qasa.prefs.AppLanguage
import alibarznji.qasa.prefs.AppPreferences
import alibarznji.qasa.prefs.AppThemeMode
import alibarznji.qasa.theme.NavChevronRow
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
 * Appearance card (`SettingsView.swift:127-153`): Language then Theme, each a row whose
 * trailing text is the current choice and which opens a menu (iOS `Menu`).
 */
@Composable
internal fun AppearanceRows(prefs: AppPreferences) {
    val c = qasaColors
    val lang by prefs.language.collectAsState()
    val theme by prefs.themeMode.collectAsState()
    val themeLabels = AppThemeMode.entries.associateWith { stringResource(it.labelRes) }

    MenuRow(
        icon = Icons.Filled.Language,
        title = stringResource(R.string.settings_language),
        options = AppLanguage.entries,
        selected = lang,
        label = { it.nativeName },
        onSelect = prefs::setLanguage,
    )
    HorizontalDivider(color = c.stroke)
    MenuRow(
        icon = Icons.Filled.DarkMode,
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
    val c = qasaColors
    var expanded by remember { mutableStateOf(false) }
    Box {
        NavChevronRow(icon = icon, title = title, trailingText = label(selected), onClick = { expanded = true })
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = c.cardBackground,
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option), color = c.textPrimary, style = qasaText.bodyLarge) },
                    trailingIcon = if (option == selected) {
                        { Icon(Icons.Filled.Check, null, tint = c.accent) }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}
