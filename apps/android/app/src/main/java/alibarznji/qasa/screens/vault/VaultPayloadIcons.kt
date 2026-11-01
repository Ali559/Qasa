package alibarznji.qasa.screens.vault

import alibarznji.qasa.vault.VaultItemKind
import alibarznji.qasa.vault.VaultPayload
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Per-subtype icon (iOS `VaultPayload.systemImage`, `VaultEnums.swift:406`): bank, phone,
 * car… rather than one icon per kind. Shared by the list row and the detail header.
 */
internal val VaultPayload.subtypeIcon: ImageVector
    get() = when (this) {
        is VaultPayload.Login -> Icons.Default.Language // globe
        is VaultPayload.SecureNote -> Icons.AutoMirrored.Filled.Notes // note.text
        is VaultPayload.Card -> Icons.Default.CreditCard // creditcard
        is VaultPayload.BankAccount -> Icons.Default.AccountBalance // building.columns
        is VaultPayload.Name -> Icons.Default.Person // person
        is VaultPayload.PersonalEmail -> Icons.Default.Email // envelope
        is VaultPayload.Phone -> Icons.Default.Phone // phone
        is VaultPayload.Address -> Icons.Default.Place // mappin.and.ellipse
        is VaultPayload.Company -> Icons.Default.Work // briefcase
        is VaultPayload.Website -> Icons.Default.Link // link
        is VaultPayload.IdCard -> Icons.Default.Badge // person.text.rectangle
        is VaultPayload.Passport -> Icons.Default.Public // globe.europe.africa
        is VaultPayload.DriverLicense -> Icons.Default.DirectionsCar // car
    }

/** Kind icon — the row's fallback until its payload is decrypted, and the add menu's. */
internal val VaultItemKind.icon: ImageVector
    get() = when (this) {
        VaultItemKind.Login -> Icons.Default.Key
        VaultItemKind.SecureNote -> Icons.AutoMirrored.Filled.Notes
        VaultItemKind.Payment -> Icons.Default.CreditCard
        VaultItemKind.PersonalInfo -> Icons.Default.Person
        VaultItemKind.IdDocument -> Icons.Default.Badge
    }
