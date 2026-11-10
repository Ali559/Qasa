package alibarznji.qasa.screens.vault.editor

import alibarznji.qasa.theme.qasaSheetMaxHeight
import alibarznji.qasa.R
import alibarznji.qasa.screens.tools.PasswordGeneratorContent
import alibarznji.qasa.screens.tools.rememberPasswordGeneratorState
import alibarznji.qasa.theme.PrimaryButton
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.QasaModalBottomSheet
import alibarznji.qasa.theme.qasaColors
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * iOS `PasswordGeneratorModal`: the Generator page's content in a sheet, with no title
 * chrome (dismiss by swipe) and a pinned "Use Password" button that fills the field.
 * Each presentation gets a fresh state, so it opens on a new password like iOS `onAppear`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordGeneratorSheet(onDismiss: () -> Unit, onUsePassword: (String) -> Unit) {
    val c = qasaColors
    val state = rememberPasswordGeneratorState()
    val maxHeight = qasaSheetMaxHeight()
    QasaModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = maxHeight)) {
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = QasaDimens.screenPadding)
                    .padding(top = QasaDimens.contentGap, bottom = QasaDimens.gapSmall),
            ) {
                PasswordGeneratorContent(state)
            }
            Column(Modifier.background(c.chrome)) {
                HorizontalDivider(color = c.stroke, thickness = QasaDimens.stroke)
                Box(
                    Modifier.padding(
                        start = QasaDimens.screenPadding,
                        end = QasaDimens.screenPadding,
                        top = QasaDimens.gapSmall,
                        bottom = QasaDimens.screenPadding,
                    ),
                ) {
                    PrimaryButton(
                        title = stringResource(R.string.gen_usepassword),
                        enabled = state.password.isNotEmpty(),
                        onClick = { onUsePassword(state.password) },
                    )
                }
            }
        }
    }
}
