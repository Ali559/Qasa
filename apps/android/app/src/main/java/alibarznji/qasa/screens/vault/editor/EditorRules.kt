package alibarznji.qasa.screens.vault.editor

import alibarznji.qasa.R
import alibarznji.qasa.vault.VaultPayload
import androidx.annotation.StringRes

/**
 * iOS `VaultPayload.displayName` (VaultEnums.swift:357) — the raw name with no "Untitled"
 * fallback. The editor's Save is enabled only when this is non-blank
 * (VaultItemEditor.swift:62), so it must not use Android's `displayName()`, which
 * substitutes "Untitled" and would always pass.
 */
fun VaultPayload.editorName(): String = when (this) {
    is VaultPayload.Login -> itemName
    is VaultPayload.SecureNote -> title
    is VaultPayload.Card -> itemName
    is VaultPayload.BankAccount -> itemName
    is VaultPayload.Name -> listOf(firstName, lastName).filter { it.isNotEmpty() }.joinToString(" ")
    is VaultPayload.PersonalEmail -> itemName.ifEmpty { email }
    is VaultPayload.Phone -> itemName.ifEmpty { number }
    is VaultPayload.Address -> itemName
    is VaultPayload.Company -> companyName
    is VaultPayload.Website -> itemName
    is VaultPayload.IdCard -> name
    is VaultPayload.Passport -> name
    is VaultPayload.DriverLicense -> name
}

fun VaultPayload.canSave(): Boolean = editorName().isNotBlank()

/** iOS `VaultItemEditor.title` base: the item type shown in "New %@" / "Edit %@". */
@StringRes
fun VaultPayload.typeLabelRes(): Int = when (this) {
    is VaultPayload.Login -> R.string.vault_type_login
    is VaultPayload.SecureNote -> R.string.vault_type_securenote
    is VaultPayload.Card -> R.string.vault_type_card
    is VaultPayload.BankAccount -> R.string.vault_type_bankaccount
    is VaultPayload.Name -> R.string.vault_type_name
    is VaultPayload.PersonalEmail -> R.string.vault_type_email
    is VaultPayload.Phone -> R.string.vault_type_phone
    is VaultPayload.Address -> R.string.vault_type_address
    is VaultPayload.Company -> R.string.vault_type_company
    is VaultPayload.Website -> R.string.vault_type_website
    is VaultPayload.IdCard -> R.string.vault_type_idcard
    is VaultPayload.Passport -> R.string.vault_type_passport
    is VaultPayload.DriverLicense -> R.string.vault_type_driverlicense
}
