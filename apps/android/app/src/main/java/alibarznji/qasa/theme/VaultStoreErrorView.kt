package alibarznji.qasa.theme

import alibarznji.qasa.R
import alibarznji.qasa.vault.db.VaultStoreFailure
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily

/**
 * Blocking screen shown instead of the vault when the store would not open —
 * `VaultStoreErrorView.swift:23`. Padding 24, 20 between header / remedy / diagnostics,
 * header lines 10 apart: icon 40 `danger`, title 26 bold, body 16; remedy 16 on a card
 * with 16 padding; diagnostics in 12 monospaced, 8 apart. Capped at 520 wide and centred.
 *
 * Edge-to-edge: the background runs under the status bar like iOS `ignoresSafeArea()`,
 * while the content is padded below it.
 *
 * Deliberately offers no retry and no reset: it never deletes a store to "recover",
 * because that is exactly the destructive action this screen exists to prevent.
 */
@Composable
fun VaultStoreErrorView(
    failure: VaultStoreFailure,
    modifier: Modifier = Modifier,
) {
    val c = qasaColors
    // iOS uses `.system(size: 12, design: .monospaced)` in every language, so a path keeps
    // its fixed-width shape in ckb/ar too.
    val mono = qasaText.labelRegular.copy(fontFamily = FontFamily.Monospace)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .padding(QasaDimens.sectionGap)
                .widthIn(max = QasaDimens.vaultErrorMaxWidth)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(QasaDimens.screenPadding),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(QasaDimens.gapMedium)) {
                Icon(
                    QasaIcons.Warning,
                    contentDescription = null,
                    tint = c.danger,
                    modifier = Modifier.size(QasaIconSize.x6l),
                )
                Text(
                    stringResource(R.string.error_vaultstoretitle),
                    color = c.textPrimary,
                    style = qasaText.errorTitle,
                )
                Text(
                    stringResource(R.string.error_vaultstorebody),
                    color = c.textSecondary,
                    style = qasaText.bodyLarge,
                )
            }
            Text(
                stringResource(R.string.error_vaultstorehelp),
                color = c.textPrimary,
                style = qasaText.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(QasaDimens.cornerRadius))
                    .background(c.cardBackground)
                    .padding(QasaDimens.gap),
            )
            // Selectable: these two lines are how a user copies the path to a bug report.
            SelectionContainer {
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(QasaDimens.contentGap),
                ) {
                    Text(
                        stringResource(R.string.error_vaultstorepath, failure.storePath),
                        color = c.textSecondary,
                        style = mono,
                    )
                    Text(
                        stringResource(R.string.error_vaultstoredetail, failure.underlying),
                        color = c.textSecondary,
                        style = mono,
                    )
                }
            }
        }
    }
}
