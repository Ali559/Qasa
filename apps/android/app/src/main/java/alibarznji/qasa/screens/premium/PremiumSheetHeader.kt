package alibarznji.qasa.screens.premium

import alibarznji.qasa.R
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.QasaIconSize
import alibarznji.qasa.theme.qasaColors
import alibarznji.qasa.theme.qasaText
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

/**
 * `sheetChrome` (PremiumUpgradeView.swift:145): 64pt chrome bar, 14pt X on the leading side,
 * centred 20pt bold title, 1pt `strokeChrome` bottom edge. Used when the upgrade screen is
 * presented as a sheet rather than inside the shell.
 */
@Composable
internal fun PremiumSheetHeader(title: String, onClose: () -> Unit) {
    val c = qasaColors
    Column(Modifier.fillMaxWidth().background(c.chrome)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(QasaDimens.headerHeight)
                .padding(horizontal = QasaDimens.gap),
        ) {
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.common_cancel),
                    tint = c.textPrimary,
                    modifier = Modifier.size(QasaIconSize.sm),
                )
            }
            Text(
                title,
                color = c.textOnChrome,
                style = qasaText.pageTitle.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = QasaDimens.touchTarget),
            )
        }
        HorizontalDivider(color = c.strokeChrome, thickness = QasaDimens.stroke)
    }
}
