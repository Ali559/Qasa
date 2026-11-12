package alibarznji.qasa.screens.vault

import alibarznji.qasa.theme.QasaIcons
import alibarznji.qasa.vault.VaultItemKind
import alibarznji.qasa.vault.VaultPayload
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Per-subtype icon (iOS `VaultPayload.systemImage`, `VaultEnums.swift:406`): bank, phone,
 * car… rather than one icon per kind. Shared by the list row and the detail header.
 */
internal val VaultPayload.subtypeIcon: ImageVector
    get() = when (this) {
        is VaultPayload.Login -> QasaIcons.Website // globe
        is VaultPayload.SecureNote -> QasaIcons.Note // note.text
        is VaultPayload.Card -> QasaIcons.Card // creditcard
        is VaultPayload.BankAccount -> QasaIcons.Bank // building.columns
        is VaultPayload.Name -> QasaIcons.Person // person
        is VaultPayload.PersonalEmail -> QasaIcons.Email // envelope
        is VaultPayload.Phone -> QasaIcons.Phone // phone
        is VaultPayload.Address -> QasaIcons.Address // mappin.and.ellipse
        is VaultPayload.Company -> QasaIcons.Work // briefcase
        is VaultPayload.Website -> QasaIcons.Link // link
        is VaultPayload.IdCard -> QasaIcons.Identity // person.text.rectangle
        is VaultPayload.Passport -> QasaIcons.Location // globe.europe.africa
        is VaultPayload.DriverLicense -> QasaIcons.Car // car
    }

/** Kind icon — the row's fallback until its payload is decrypted, and the add menu's. */
internal val VaultItemKind.icon: ImageVector
    get() = when (this) {
        VaultItemKind.Login -> QasaIcons.Login
        VaultItemKind.SecureNote -> QasaIcons.Note
        VaultItemKind.Payment -> QasaIcons.Card
        VaultItemKind.PersonalInfo -> QasaIcons.Identity
        VaultItemKind.IdDocument -> QasaIcons.Identity
    }
