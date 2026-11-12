package alibarznji.qasa.screens.auth.welcome

import alibarznji.qasa.R
import alibarznji.qasa.prefs.AppLanguage
import alibarznji.qasa.prefs.AppPreferences
import alibarznji.qasa.theme.BrandHeader
import alibarznji.qasa.theme.PrimaryButton
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.QasaFieldGroup
import alibarznji.qasa.theme.QasaIcons
import alibarznji.qasa.theme.QasaMotion
import alibarznji.qasa.theme.QasaRow
import alibarznji.qasa.theme.QasaRowPressable
import alibarznji.qasa.theme.QasaTextRow
import alibarznji.qasa.theme.RowDivider
import alibarznji.qasa.theme.ScreenScaffold
import alibarznji.qasa.theme.SecondaryButton
import alibarznji.qasa.theme.SettingsSection
import alibarznji.qasa.theme.StickyBottomBar
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaReduceMotion
import alibarznji.qasa.theme.qasaText
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.LayoutDirection
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class WelcomeViewModel @Inject constructor(val prefs: AppPreferences) : ViewModel() {
    /** The last step: keeps the (optional) name and marks the welcome done. */
    fun finish(name: String) {
        prefs.setUserName(name)
        prefs.hasCompletedWelcome = true
    }
}

private enum class WelcomeStep { Language, Tour, Name }

/**
 * First launch (`design/README.md` rule 6d, iOS `WelcomeFlowView`): the language, then the
 * live tour of free features, then an optional name. Finishing marks the welcome done and
 * [onFinished] shows the main password screen. The step is saved, so the activity
 * recreating for a new language stays on it.
 */
@Composable
fun WelcomeFlowScreen(
    onFinished: () -> Unit,
    vm: WelcomeViewModel = hiltViewModel(),
) {
    var step by rememberSaveable { mutableStateOf(WelcomeStep.Language) }
    val reduceMotion = qasaReduceMotion.enabled
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    // Android back steps back through the flow; on the language screen it leaves the app.
    BackHandler(enabled = step != WelcomeStep.Language) {
        step = if (step == WelcomeStep.Name) WelcomeStep.Tour else WelcomeStep.Language
    }

    AnimatedContent(
        targetState = step,
        transitionSpec = {
            if (reduceMotion) {
                fadeIn(QasaMotion.fadeTween()) togetherWith fadeOut(QasaMotion.fadeTween())
            } else {
                // iOS: the next step moves in from the trailing edge and fades in.
                (slideInHorizontally(QasaMotion.standardTween()) { if (rtl) -it else it } +
                    fadeIn(QasaMotion.standardTween())) togetherWith fadeOut(QasaMotion.standardTween())
            }
        },
        label = "welcomeStep",
    ) { current ->
        when (current) {
            WelcomeStep.Language -> WelcomeLanguageStep(vm.prefs, onContinue = { step = WelcomeStep.Tour })
            WelcomeStep.Tour -> OnboardingTour(
                onSkip = { step = WelcomeStep.Name },
                onDone = { step = WelcomeStep.Name },
            )
            WelcomeStep.Name -> WelcomeNameStep(onDone = { name ->
                vm.finish(name)
                onFinished()
            })
        }
    }
}

/** Native names, never translated (iOS `AppLanguage.displayName`). */
private val AppLanguage.nativeName: String
    get() = when (this) {
        AppLanguage.EN -> "English"
        AppLanguage.CKB -> "کوردی"
        AppLanguage.AR -> "العربية"
    }

/** Step 1: the language only. The theme follows the system until it is changed in Settings. */
@Composable
private fun WelcomeLanguageStep(prefs: AppPreferences, onContinue: () -> Unit) {
    val c = qasaColors
    val lang by prefs.language.collectAsState()

    Box(Modifier.fillMaxSize().background(c.background)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(QasaDimens.onboardingArt)
                .align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(c.accent.copy(alpha = 0.12f), Color.Transparent))),
        )
        Column(Modifier.fillMaxSize()) {
            BrandHeader()
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = QasaDimens.screenPadding)
                    .padding(top = QasaDimens.sectionGap, bottom = QasaDimens.sectionGap),
            ) {
                Text(stringResource(R.string.onboarding_welcometitle), color = c.textPrimary, style = qasaText.displayLarge)
                Spacer(Modifier.height(QasaDimens.gapMedium))
                Text(stringResource(R.string.onboarding_welcomesubtitle), color = c.textSecondary, style = qasaText.bodyLegal)
                Spacer(Modifier.height(QasaDimens.sectionGap))
                SettingsSection {
                    AppLanguage.entries.forEachIndexed { i, l ->
                        if (i > 0) RowDivider()
                        LanguageRow(l, selected = lang == l, onClick = { prefs.setLanguage(l) })
                    }
                }
            }
            StickyBottomBar {
                PrimaryButton(title = stringResource(R.string.onboarding_getstarted), onClick = onContinue)
            }
        }
    }
}

/** iOS `languageRow`: language badge, the native name in its own direction, a check when picked. */
@Composable
private fun LanguageRow(language: AppLanguage, selected: Boolean, onClick: () -> Unit) {
    val c = qasaColors
    val check by animateFloatAsState(if (selected) 1f else 0f, QasaMotion.fastTween(), label = "languageCheck")
    QasaRowPressable(
        onClick = onClick,
        role = Role.RadioButton,
        modifier = Modifier.semantics { this.selected = selected },
    ) {
        CompositionLocalProvider(
            LocalLayoutDirection provides if (language.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
        ) {
            QasaRow(
                title = language.nativeName,
                icon = QasaIcons.Language,
                iconColor = if (selected) c.accent else c.textSecondary,
                trailing = {
                    Icon(
                        QasaIcons.Check,
                        contentDescription = null,
                        tint = c.accent,
                        modifier = Modifier
                            .size(QasaDimens.rowIconBadgeGlyph)
                            .graphicsLayer {
                                alpha = check
                                val s = 0.6f + 0.4f * check
                                scaleX = s
                                scaleY = s
                            },
                    )
                },
            )
        }
    }
}

/** The last step: an optional name, kept on this device and used in greetings. */
@Composable
private fun WelcomeNameStep(onDone: (String) -> Unit) {
    val c = qasaColors
    var name by rememberSaveable { mutableStateOf("") }
    ScreenScaffold(
        header = { BrandHeader(applyStatusBars = false) },
        stickyBottom = {
            StickyBottomBar {
                PrimaryButton(title = stringResource(R.string.onboarding_namego), onClick = { onDone(name) })
                SecondaryButton(title = stringResource(R.string.common_skip), onClick = { onDone("") })
            }
        },
    ) {
        Text(stringResource(R.string.onboarding_nametitle), color = c.textPrimary, style = qasaText.displayLarge)
        Spacer(Modifier.height(QasaDimens.gapMedium))
        Text(stringResource(R.string.onboarding_namebody), color = c.textSecondary, style = qasaText.bodyLegal)
        Spacer(Modifier.height(QasaDimens.sectionGap))
        QasaFieldGroup {
            QasaTextRow(
                label = stringResource(R.string.common_name),
                value = name,
                onValueChange = { name = it },
                placeholder = stringResource(R.string.onboarding_nameplaceholder),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(onDone = { onDone(name) }),
            )
        }
    }
}
