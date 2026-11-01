package alibarznji.qasa.screens.vault.detail

import alibarznji.qasa.R
import alibarznji.qasa.vault.Country
import alibarznji.qasa.vault.VaultPayload
import androidx.annotation.StringRes

/**
 * One row of the detail card — iOS `DetailField` (`Models/VaultEnums.swift:238`).
 *
 * [ephemeral] marks card and bank rows: iOS sends every field of those payloads through
 * `PaymentClipboard` (30s expiry), so Android routes them through `EphemeralClipboard`.
 */
internal data class DetailField(
    @StringRes val label: Int,
    val value: String,
    val secret: Boolean = false,
    val multiline: Boolean = false,
    val ephemeral: Boolean = false,
)

/**
 * Port of iOS `VaultPayload.detailFields` (`Models/VaultEnums.swift:548`): same fields,
 * order, labels and secrecy; blank values are dropped (iOS `.filter { !$0.value.isEmpty }`)
 * rather than rendered as "—". [str] resolves the localized enum labels (honorific,
 * email/phone kind, Yes/No) in the caller's locale.
 */
internal fun VaultPayload.detailFields(str: (Int) -> String): List<DetailField> = when (this) {
    is VaultPayload.Login -> listOf(
        DetailField(R.string.field_itemname, itemName),
        DetailField(R.string.field_email, email),
        DetailField(R.string.field_username, username),
        DetailField(R.string.field_password, password, secret = true),
        DetailField(R.string.field_website, website),
        DetailField(R.string.field_note, note, multiline = true),
    )
    is VaultPayload.SecureNote -> listOf(
        DetailField(R.string.field_title, title),
        // Only a protected note's content is a secret on iOS.
        DetailField(R.string.field_content, content, secret = protectItem, multiline = true),
        DetailField(R.string.field_protected, str(if (protectItem) R.string.common_yes else R.string.common_no)),
    )
    is VaultPayload.Card -> listOf(
        DetailField(R.string.field_itemname, itemName, ephemeral = true),
        DetailField(R.string.field_accountholder, accountHolder, ephemeral = true),
        DetailField(R.string.field_number, number, secret = true, ephemeral = true),
        DetailField(R.string.field_securitycode, securityCode, secret = true, ephemeral = true),
        DetailField(R.string.field_expirydate, expiryDate, ephemeral = true),
        DetailField(R.string.field_note, note, multiline = true, ephemeral = true),
    )
    is VaultPayload.BankAccount -> listOf(
        DetailField(R.string.field_itemname, itemName, ephemeral = true),
        DetailField(R.string.field_accountholder, accountHolder, ephemeral = true),
        DetailField(R.string.field_accountnumber, accountNumber, secret = true, ephemeral = true),
        // Routing number is not a secret on iOS.
        DetailField(R.string.field_routingnumber, routingNumber, ephemeral = true),
        DetailField(R.string.field_bankname, bankName, ephemeral = true),
        DetailField(R.string.vault_country, countryName(countryCode), ephemeral = true),
    )
    is VaultPayload.Name -> listOf(
        // iOS hides the "None of these" honorific entirely.
        DetailField(R.string.field_type, honorificLabel(type)?.let(str).orEmpty()),
        DetailField(R.string.field_firstname, firstName),
        DetailField(R.string.field_middlename, middleName),
        DetailField(R.string.field_lastname, lastName),
        DetailField(R.string.field_defaultusername, defaultUsername),
        DetailField(R.string.field_birthdate, birthDate),
        DetailField(R.string.field_birthplace, birthPlace),
    )
    is VaultPayload.PersonalEmail -> listOf(
        DetailField(R.string.field_itemname, itemName),
        DetailField(R.string.field_type, emailKindLabel(type)?.let(str) ?: type),
        DetailField(R.string.field_email, email),
    )
    is VaultPayload.Phone -> listOf(
        DetailField(R.string.field_itemname, itemName),
        DetailField(R.string.field_type, phoneKindLabel(type)?.let(str) ?: type),
        DetailField(R.string.vault_country, countryName(countryCode)),
        // iOS: "\(Country.dialCode(for:)) \(number)" — dial code always prefixed.
        DetailField(R.string.field_phonenumber, "${Country.dialCode(countryCode)} $number".takeIf { number.isNotEmpty() }.orEmpty()),
    )
    is VaultPayload.Address -> listOf(
        DetailField(R.string.field_itemname, itemName),
        DetailField(R.string.field_recipient, recipient),
        DetailField(R.string.field_address, address, multiline = true),
        DetailField(R.string.field_building, building),
        DetailField(R.string.field_floor, floor),
        DetailField(R.string.field_apartment, apartment),
        DetailField(R.string.field_doorcode, doorCode),
        DetailField(R.string.field_city, city),
        DetailField(R.string.field_zipcode, zipCode),
        DetailField(R.string.vault_country, countryName(countryCode)),
    )
    is VaultPayload.Company -> listOf(
        DetailField(R.string.field_company, companyName),
        DetailField(R.string.field_jobtitle, jobTitle),
    )
    is VaultPayload.Website -> listOf(
        DetailField(R.string.field_itemname, itemName),
        DetailField(R.string.field_website, websiteURL),
    )
    is VaultPayload.IdCard -> idFields(name, countryCode, number, issueDate, expiryDate)
    // iOS appends Issue Place for passports only.
    is VaultPayload.Passport -> idFields(name, countryCode, number, issueDate, expiryDate) +
        DetailField(R.string.field_issueplace, issuePlace)
    is VaultPayload.DriverLicense -> idFields(name, countryCode, number, issueDate, expiryDate)
}.filter { it.value.isNotEmpty() }

/** ID / passport / licence — the ID number is not a secret on iOS. */
private fun idFields(name: String, countryCode: String, number: String, issue: String, expiry: String) = listOf(
    DetailField(R.string.field_name, name),
    DetailField(R.string.vault_country, countryName(countryCode)),
    DetailField(R.string.field_number, number),
    DetailField(R.string.field_issuedate, issue),
    DetailField(R.string.field_expirydate, expiry),
)

/** iOS `Country.name(for:)`; an empty code stays empty so the row is hidden. */
private fun countryName(code: String): String = if (code.isEmpty()) "" else Country.name(code)

/**
 * Enum raw values: payloads store the iOS display-cased raws ("Mr", "Work Cell",
 * "None of these"); the older lowercase spelling ("none", "cell") is matched too.
 */
private fun normalize(raw: String) = raw.lowercase().replace(" ", "")

@StringRes
private fun honorificLabel(raw: String): Int? = when (normalize(raw)) {
    "mr" -> R.string.honorific_mr
    "mrs" -> R.string.honorific_mrs
    "ms" -> R.string.honorific_ms
    "mx" -> R.string.honorific_mx
    "miss" -> R.string.honorific_miss
    else -> null // "none" / "noneofthese" / blank: iOS shows no Type row
}

@StringRes
private fun emailKindLabel(raw: String): Int? = when (normalize(raw)) {
    "personal" -> R.string.emailkind_personal
    "work" -> R.string.emailkind_work
    else -> null
}

@StringRes
private fun phoneKindLabel(raw: String): Int? = when (normalize(raw)) {
    "cell" -> R.string.phonekind_cell
    "work" -> R.string.phonekind_work
    "workcell" -> R.string.phonekind_workcell
    "workfax" -> R.string.phonekind_workfax
    "fax" -> R.string.phonekind_fax
    "homephone" -> R.string.phonekind_homephone
    "other" -> R.string.phonekind_other
    else -> null
}
