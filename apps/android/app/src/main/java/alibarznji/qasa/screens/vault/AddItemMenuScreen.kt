package alibarznji.qasa.screens.vault

import alibarznji.qasa.R
import alibarznji.qasa.theme.CardContainer
import alibarznji.qasa.theme.NavChevronRow
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.SheetChromeHeader
import alibarznji.qasa.vault.VaultItemKind
import alibarznji.qasa.vault.VaultPayload
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** One add-menu entry — iOS `VaultSubtype` + the empty payload it creates. */
private data class AddOption(@StringRes val title: Int, val icon: ImageVector, val payload: VaultPayload)

/** iOS `VaultItemKind.addTitle`. */
@get:StringRes
private val VaultItemKind.addTitle: Int
    get() = when (this) {
        VaultItemKind.Login -> R.string.vault_kind_login_add
        VaultItemKind.SecureNote -> R.string.vault_kind_securenote_add
        VaultItemKind.Payment -> R.string.vault_kind_payment_add
        VaultItemKind.PersonalInfo -> R.string.vault_kind_personalinfo_add
        VaultItemKind.IdDocument -> R.string.vault_kind_iddocument_add
    }

/**
 * iOS `VaultItemKind.subtypes` (`VaultEnums.swift:47`) in the same order; `null` for
 * login and note, which go straight to the editor.
 */
private fun VaultItemKind.subtypes(): List<AddOption>? = when (this) {
    VaultItemKind.Login, VaultItemKind.SecureNote -> null
    VaultItemKind.Payment -> listOf(
        VaultPayload.Card() to R.string.vault_subtype_card,
        VaultPayload.BankAccount() to R.string.vault_subtype_bank,
    )
    VaultItemKind.PersonalInfo -> listOf(
        VaultPayload.Name() to R.string.vault_subtype_name,
        VaultPayload.PersonalEmail() to R.string.vault_subtype_email,
        VaultPayload.Phone() to R.string.vault_subtype_phone,
        VaultPayload.Address() to R.string.vault_subtype_address,
        VaultPayload.Company() to R.string.vault_subtype_company,
        VaultPayload.Website() to R.string.vault_subtype_website,
    )
    VaultItemKind.IdDocument -> listOf(
        VaultPayload.IdCard() to R.string.vault_subtype_idcard,
        VaultPayload.Passport() to R.string.vault_subtype_passport,
        VaultPayload.DriverLicense() to R.string.vault_subtype_driverlicense,
    )
}?.map { (payload, title) -> AddOption(title, payload.subtypeIcon, payload) }

/** The editor payload for a kind without subtypes (iOS `defaultPayload(for:)`). */
internal fun VaultItemKind.directAddPayload(): VaultPayload? = when (this) {
    VaultItemKind.Login -> VaultPayload.Login()
    VaultItemKind.SecureNote -> VaultPayload.SecureNote()
    else -> null
}

/**
 * Port of iOS `AddItemMenuView`: "Add New" lists the five kinds on the All tab (drilling
 * into a kind's subtypes), or the current tab's subtypes directly. Login and Note tabs
 * never reach this sheet — the FAB opens their editor ([directAddPayload]).
 */
@Composable
fun AddItemMenuSheetContent(
    /** The list's current tab; `null` is "All". */
    tab: VaultItemKind?,
    onSelect: (VaultPayload) -> Unit,
    onCancel: () -> Unit,
) {
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.92f
    // The drilled-into kind on the All tab — iOS pushes `AddRoute.subtypes(kind)`.
    var drilled by rememberSaveable { mutableStateOf<VaultItemKind?>(null) }
    BackHandler(enabled = drilled != null) { drilled = null }

    val shownKind = tab ?: drilled
    val options: List<AddOption> = if (shownKind == null) {
        VaultItemKind.entries.map { kind ->
            AddOption(kind.addTitle, kind.icon, kind.directAddPayload() ?: VaultPayload.Login())
        }
    } else {
        shownKind.subtypes()
            ?: listOf(AddOption(shownKind.addTitle, shownKind.icon, shownKind.directAddPayload()!!))
    }

    Column(Modifier.fillMaxWidth().heightIn(max = maxHeight)) {
        // A drilled-in list gets "Back" in the leading slot, like the iOS navigation bar.
        SheetChromeHeader(
            title = stringResource(drilled?.addTitle ?: R.string.vault_addnew),
            onDismiss = { if (drilled != null) drilled = null else onCancel() },
            dismissLabel = stringResource(if (drilled != null) R.string.a11y_back else R.string.common_cancel),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = QasaDimens.screenPadding)
                .padding(top = QasaDimens.contentGap, bottom = QasaDimens.sectionGap),
        ) {
            CardContainer {
                options.forEachIndexed { index, option ->
                    NavChevronRow(
                        title = stringResource(option.title),
                        icon = option.icon,
                        onClick = {
                            val kind = VaultItemKind.entries.getOrNull(index)
                            if (shownKind == null && kind != null && kind.directAddPayload() == null) {
                                drilled = kind
                            } else {
                                onSelect(option.payload)
                            }
                        },
                    )
                    if (index < options.lastIndex) {
                        Spacer(Modifier.height(QasaDimens.spaceMicro))
                    }
                }
            }
        }
    }
}
