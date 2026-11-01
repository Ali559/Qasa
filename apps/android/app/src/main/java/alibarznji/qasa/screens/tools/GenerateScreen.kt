package alibarznji.qasa.screens.tools

import alibarznji.qasa.R
import alibarznji.qasa.theme.AppMenuHeader
import alibarznji.qasa.theme.CardContainer
import alibarznji.qasa.theme.CheckToggle
import alibarznji.qasa.theme.PrimaryButton
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.QasaIconSize
import alibarznji.qasa.theme.QasaSlider
import alibarznji.qasa.theme.ScreenScaffold
import alibarznji.qasa.theme.SectionLabel
import alibarznji.qasa.theme.StrengthBar
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import alibarznji.qasa.vault.crypto.PasswordGenerator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.delay

@Composable
fun GenerateScreen(onOpenMenu: (() -> Unit)? = null) {
    val state = rememberPasswordGeneratorState()
    ScreenScaffold(
        header = { AppMenuHeader(title = stringResource(R.string.nav_generator), onMenu = onOpenMenu, applyStatusBars = false) },
    ) {
        // iOS `GeneratorView` uses the roomy (`compact: false`) layout; the sheet keeps compact.
        PasswordGeneratorContent(state, compact = false)
        Spacer(Modifier.height(QasaDimens.sectionGap))
        // Save to vault is a no-op on iOS GeneratorView — parity
        PrimaryButton(title = stringResource(R.string.gen_savetovault), onClick = { /* no-op */ })
    }
}

/**
 * iOS `PasswordGeneratorModel` (PasswordGeneratorModal.swift:49): one state shared by the
 * Generator page and the editor's generator sheet, so both behave identically.
 */
@Stable
class PasswordGeneratorState {
    var length by mutableFloatStateOf(16f)
    var useUppercase by mutableStateOf(true)
    var useLowercase by mutableStateOf(true)
    var useDigits by mutableStateOf(true)
    var useSymbols by mutableStateOf(true)
    var password by mutableStateOf(gen(16, uppercase = true, lowercase = true, digits = true, symbols = true))
    var copied by mutableStateOf(false)

    /** Bumped on every copy so a second tap restarts the 1.5 s "Copied" timer. */
    var copyTick by mutableIntStateOf(0)
        private set

    fun markCopied() {
        copied = true
        copyTick++
    }

    fun regenerate() {
        password = gen(
            length.toInt(),
            uppercase = useUppercase,
            lowercase = useLowercase,
            digits = useDigits,
            symbols = useSymbols,
        )
        copied = false
    }
}

@Composable
fun rememberPasswordGeneratorState(): PasswordGeneratorState = remember { PasswordGeneratorState() }

/**
 * iOS `PasswordGeneratorContent`: generated-password, strength and settings cards.
 * `compact` mirrors iOS: true in the editor sheet (22pt password), false on the page (28pt).
 */
@Composable
fun PasswordGeneratorContent(
    state: PasswordGeneratorState,
    modifier: Modifier = Modifier,
    compact: Boolean = true,
) {
    val c = qasaColors
    val context = LocalContext.current
    // iOS `copyPassword()` resets `copied` after 1.5 s (PasswordGeneratorModal.swift:91).
    LaunchedEffect(state.copyTick) {
        if (state.copied) {
            delay(COPIED_RESET_MS)
            state.copied = false
        }
    }
    val strength = strengthLabel(
        length = state.length.toInt(),
        charsetCount = listOf(state.useUppercase, state.useLowercase, state.useDigits, state.useSymbols).count { it },
    )

    Column(modifier.fillMaxWidth()) {
        CardContainer(background = c.cardBackgroundLight) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionLabel(stringResource(R.string.gen_generatedpassword))
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.gen_regenerate),
                    tint = c.cyan,
                    modifier = Modifier
                        .size(QasaIconSize.x5l)
                        .clickable { state.regenerate() }
                        .padding(QasaDimens.gapTiny),
                )
            }
            Spacer(Modifier.height(QasaDimens.gap))
            // iOS: monospaced bold, one line, `minimumScaleFactor(0.4)` (PasswordGeneratorModal.swift:131).
            val passwordStyle = if (compact) qasaText.passwordCompact else qasaText.passwordFull
            // A password is Latin text; keep it LTR so RTL locales don't reorder symbols.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                BasicText(
                    state.password.ifEmpty { "—" },
                    style = passwordStyle.copy(color = c.textPrimary, textAlign = TextAlign.Center),
                    maxLines = 1,
                    softWrap = false,
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = passwordStyle.fontSize * 0.4f,
                        maxFontSize = passwordStyle.fontSize,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(QasaDimens.gap))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.accentDark.copy(alpha = 0.2f), RoundedCornerShape(QasaDimens.radiusSmall))
                    .border(QasaDimens.stroke, c.accent.copy(alpha = 0.3f), RoundedCornerShape(QasaDimens.radiusSmall))
                    .clickable {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("password", state.password))
                        state.markCopied()
                    }
                    .padding(vertical = QasaDimens.padTight),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (state.copied) Icons.Default.Check else Icons.Default.ContentCopy,
                    contentDescription = null,
                    tint = c.accent,
                    modifier = Modifier.size(QasaIconSize.md),
                )
                Spacer(Modifier.width(QasaDimens.contentGap))
                Text(
                    if (state.copied) stringResource(R.string.gen_copied) else stringResource(R.string.gen_copypassword),
                    color = c.accent,
                    style = qasaText.bodyMedium
                )
            }
        }

        Spacer(Modifier.height(QasaDimens.gap))
        CardContainer {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.gen_strength), color = c.textSecondary, style = qasaText.sectionLabel)
                Spacer(Modifier.weight(1f))
                Text(stringResource(strength.label), color = c.accent, style = qasaText.bodyMedium)
            }
            Spacer(Modifier.height(QasaDimens.contentGap))
            StrengthBar(
                segments = 5,
                filled = strength.filled,
                color = strength.color,
                height = QasaDimens.contentGap,
            )
        }

        Spacer(Modifier.height(QasaDimens.gap))
        CardContainer {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.gen_length), color = c.textPrimary, style = qasaText.bodyMedium)
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .background(c.cardMuted, RoundedCornerShape(QasaDimens.radiusSmall))
                        .border(QasaDimens.stroke, c.stroke, RoundedCornerShape(QasaDimens.radiusSmall))
                        .padding(horizontal = QasaDimens.padTight, vertical = QasaDimens.spaceNano),
                ) {
                    Text(
                        "${state.length.toInt()}",
                        color = c.accent,
                        style = qasaText.statValue
                    )
                }
            }
            QasaSlider(
                value = state.length,
                onValueChange = {
                    state.length = it
                    state.regenerate()
                },
                valueRange = 8f..32f,
            )
            HorizontalDivider(color = c.stroke, thickness = QasaDimens.stroke)
            Spacer(Modifier.height(QasaDimens.contentGap))
            CharsetRow(stringResource(R.string.gen_uppercase), state.useUppercase) {
                state.useUppercase = it
                state.regenerate()
            }
            CharsetRow(stringResource(R.string.gen_lowercase), state.useLowercase) {
                state.useLowercase = it
                state.regenerate()
            }
            CharsetRow(stringResource(R.string.gen_numbers), state.useDigits) {
                state.useDigits = it
                state.regenerate()
            }
            CharsetRow(stringResource(R.string.gen_symbols), state.useSymbols) {
                state.useSymbols = it
                state.regenerate()
            }
            // iOS shows "Pronounceable" disabled and off after a divider (not implemented on
            // either platform yet), so the row is display-only here too.
            HorizontalDivider(
                color = c.stroke,
                thickness = QasaDimens.stroke,
                modifier = Modifier.padding(vertical = QasaDimens.gapTiny),
            )
            CharsetRow(
                stringResource(R.string.gen_pronounceable),
                checked = false,
                enabled = false,
                onCheckedChange = {},
            )
        }
    }
}

@Composable
private fun CharsetRow(
    title: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    val c = qasaColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // iOS `LocalizedToggleRow(disabled:)` dims the row to 0.7 with a tertiary title.
            .alpha(if (enabled) 1f else DISABLED_ROW_ALPHA)
            .padding(vertical = QasaDimens.contentGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            color = if (enabled) c.textPrimary else c.textTertiary,
            style = qasaText.bodyLegal,
            modifier = Modifier.weight(1f),
        )
        CheckToggle(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

private const val COPIED_RESET_MS = 1_500L
private const val DISABLED_ROW_ALPHA = 0.7f

private data class StrengthInfo(
    @StringRes val label: Int,
    val filled: Int,
    val color: androidx.compose.ui.graphics.Color,
)

@Composable
private fun strengthLabel(length: Int, charsetCount: Int): StrengthInfo {
    val c = qasaColors
    val score = length / 4 + charsetCount
    return when (score) {
        in 0..3 -> StrengthInfo(R.string.gen_weak, 1, c.danger)
        in 4..5 -> StrengthInfo(R.string.gen_fair, 2, c.gold)
        in 6..7 -> StrengthInfo(R.string.gen_strong, 4, c.accent)
        else -> StrengthInfo(R.string.gen_verystrong, 5, c.accent)
    }
}

private fun gen(
    len: Int,
    uppercase: Boolean,
    lowercase: Boolean,
    digits: Boolean,
    symbols: Boolean,
): String {
    // Moved to `PasswordGenerator`: this previously used `kotlin.random.Random`, a
    // non-cryptographic PRNG, so every generated password was predictable.
    return PasswordGenerator.generateOrNull(
        length = len,
        uppercase = uppercase,
        lowercase = lowercase,
        digits = digits,
        symbols = symbols,
    ) ?: ""
}
