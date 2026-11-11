package alibarznji.qasa.screens.premium

import alibarznji.qasa.theme.QasaSheetHeader
import androidx.compose.runtime.Composable

/**
 * iOS `sheetChrome` (`PremiumUpgradeView.swift`): the shared sheet header, used when the
 * upgrade screen is presented as a sheet. It closes by dragging down; no X.
 */
@Composable
internal fun PremiumSheetHeader(title: String, @Suppress("UNUSED_PARAMETER") onClose: () -> Unit) {
    QasaSheetHeader(title)
}
