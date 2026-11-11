package alibarznji.qasa.screens.auth

import alibarznji.qasa.R
import alibarznji.qasa.prefs.AppLanguage
import alibarznji.qasa.prefs.AppPreferences
import alibarznji.qasa.prefs.AppThemeMode
import alibarznji.qasa.theme.BrandHeader
import alibarznji.qasa.theme.PrimaryButton
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.QasaIconSize
import alibarznji.qasa.theme.QasaIcons
import alibarznji.qasa.theme.SectionLabel
import alibarznji.qasa.theme.StickyBottomBar
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class WelcomeViewModel @Inject constructor(val prefs: AppPreferences) : ViewModel()

@Composable
fun WelcomePreferencesScreen(
    onContinue: () -> Unit,
    vm: WelcomeViewModel = hiltViewModel(),
) {
    val c = qasaColors
    val lang by vm.prefs.language.collectAsState()
    val theme by vm.prefs.themeMode.collectAsState()

    Box(Modifier.fillMaxSize().background(c.background)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(QasaDimens.onboardingArt)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(c.accent.copy(alpha = 0.12f), Color.Transparent),
                    ),
                ),
        )
        Column(Modifier.fillMaxSize()) {
            BrandHeader()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = QasaDimens.screenPadding)
                    .padding(top = QasaDimens.sectionGap, bottom = QasaDimens.sectionGap),
            ) {
                Text(stringResource(R.string.onboarding_headline), color = c.textPrimary, style = qasaText.displayLarge)
                Spacer(Modifier.height(QasaDimens.contentGap))
                Text(
                    stringResource(R.string.onboarding_subtitle),
                    color = c.textSecondary,
                    style = qasaText.bodyMedium
                )
                Spacer(Modifier.height(QasaDimens.screenPadding))
                SectionLabel(stringResource(R.string.onboarding_language))
                Spacer(Modifier.height(QasaDimens.contentGap))
                RowGroup {
                    AppLanguage.entries.forEachIndexed { i, l ->
                        if (i > 0) HorizontalDivider(color = c.stroke, thickness = QasaDimens.stroke)
                        SelectRow(
                            title = when (l) {
                                AppLanguage.EN -> "English"
                                AppLanguage.CKB -> "کوردی"
                                AppLanguage.AR -> "العربية"
                            },
                            selected = lang == l,
                            onClick = { vm.prefs.setLanguage(l) },
                        )
                    }
                }
                Spacer(Modifier.height(QasaDimens.gap))
                SectionLabel(stringResource(R.string.onboarding_theme))
                Spacer(Modifier.height(QasaDimens.contentGap))
                RowGroup {
                    AppThemeMode.entries.forEachIndexed { i, t ->
                        if (i > 0) HorizontalDivider(color = c.stroke, thickness = QasaDimens.stroke)
                        SelectRow(
                            title = stringResource(
                                when (t) {
                                    AppThemeMode.SYSTEM -> R.string.settings_theme_system
                                    AppThemeMode.LIGHT -> R.string.settings_theme_light
                                    AppThemeMode.DARK -> R.string.settings_theme_dark
                                },
                            ),
                            selected = theme == t,
                            onClick = { vm.prefs.setThemeMode(t) },
                            // iOS `themeIcon`: circle.lefthalf.filled / sun.max / moon.
                            icon = when (t) {
                                AppThemeMode.SYSTEM -> QasaIcons.ThemeSystem
                                AppThemeMode.LIGHT -> QasaIcons.ThemeLight
                                AppThemeMode.DARK -> QasaIcons.ThemeDark
                            },
                        )
                    }
                }
                Spacer(Modifier.height(QasaDimens.heroArtHeight))
            }
            StickyBottomBar {
                PrimaryButton(
                    title = stringResource(R.string.onboarding_continue),
                    icon = QasaIcons.Forward,
                    onClick = {
                        vm.prefs.hasCompletedWelcome = true
                        onContinue()
                    },
                )
            }
        }
    }
}

/** iOS groups the rows edge to edge in one rounded, stroked card with dividers between them. */
@Composable
private fun RowGroup(content: @Composable () -> Unit) {
    val c = qasaColors
    val shape = RoundedCornerShape(QasaDimens.cornerRadius)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.cardBackground)
            .border(QasaDimens.stroke, c.stroke, shape),
    ) { content() }
}

@Composable
private fun SelectRow(title: String, selected: Boolean, onClick: () -> Unit, icon: ImageVector? = null) {
    val c = qasaColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) c.accent.copy(alpha = 0.08f) else Color.Transparent)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(QasaDimens.gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) c.accent else c.textSecondary,
                modifier = Modifier.size(QasaIconSize.lg),
            )
            Spacer(Modifier.width(QasaDimens.gapMedium))
        }
        Text(title, color = c.textPrimary, style = qasaText.ctaLabel, modifier = Modifier.weight(1f))
        if (selected) {
            Icon(QasaIcons.Check, contentDescription = null, tint = c.accent, modifier = Modifier.size(QasaIconSize.md))
        }
    }
}
