package alibarznji.qasa.screens.vault

import alibarznji.qasa.R
import alibarznji.qasa.vault.Country
import alibarznji.qasa.vault.VaultPayload
import androidx.annotation.StringRes

/**
 * The list row's copy button target — iOS `VaultPayload.primaryCopy`. A login copies its
 * password (the row subtitle is the email, which is what Android used to copy).
 * `ephemeral` routes card and bank numbers through the expiring clipboard.
 */
internal data class RowCopy(
    /** ClipData label — not user-facing, stays English. */
    val label: String,
    /** Field name shown in the "Copied %@" toast. */
    @StringRes val labelRes: Int,
    val value: String,
    val ephemeral: Boolean = false,
)

/**
 * One entry of the row's overflow menu — iOS `CopyAction` (`VaultEnums.swift:435`).
 * [gated] marks a protected note's content, which iOS only copies after
 * `unlockIfNeeded` (`VaultItemRow.swift:166`).
 */
internal data class RowCopyAction(
    @StringRes val menuLabelRes: Int,
    val copy: RowCopy,
    val gated: Boolean = false,
)

/** iOS `Country.dialCode(for:) + number` — the copied phone number carries its prefix. */
private fun VaultPayload.Phone.fullNumber(): String = "${Country.dialCode(countryCode)}$number"

/**
 * iOS routes every card and bank copy through `PaymentClipboard` (30 s TTL), not just the
 * number; Android's equivalent is the expiring clipboard.
 */
private val VaultPayload.isPayment: Boolean
    get() = this is VaultPayload.Card || this is VaultPayload.BankAccount

internal fun VaultPayload.rowPrimaryCopy(): RowCopy? = when (this) {
    is VaultPayload.Login -> password.takeIf { it.isNotEmpty() }?.let { RowCopy("Password", R.string.field_password, it) }
    is VaultPayload.SecureNote -> RowCopy("Title", R.string.field_title, title)
    is VaultPayload.Card -> number.takeIf { it.isNotEmpty() }?.let { RowCopy("Number", R.string.field_number, it, ephemeral = true) }
    is VaultPayload.BankAccount -> accountNumber.takeIf { it.isNotEmpty() }
        ?.let { RowCopy("Account Number", R.string.field_accountnumber, it, ephemeral = true) }
    is VaultPayload.Name -> listOf(firstName, lastName).filter { it.isNotEmpty() }.joinToString(" ")
        .takeIf { it.isNotEmpty() }?.let { RowCopy("Name", R.string.field_name, it) }
    is VaultPayload.PersonalEmail -> email.takeIf { it.isNotEmpty() }?.let { RowCopy("Email", R.string.field_email, it) }
    is VaultPayload.Phone -> number.takeIf { it.isNotEmpty() }
        ?.let { RowCopy("Phone Number", R.string.field_phonenumber, fullNumber()) }
    is VaultPayload.Address -> address.takeIf { it.isNotEmpty() }?.let { RowCopy("Address", R.string.field_address, it) }
    is VaultPayload.Company -> companyName.takeIf { it.isNotEmpty() }?.let { RowCopy("Company", R.string.field_company, it) }
    is VaultPayload.Website -> websiteURL.takeIf { it.isNotEmpty() }?.let { RowCopy("Website", R.string.field_website, it) }
    is VaultPayload.IdCard -> number.takeIf { it.isNotEmpty() }?.let { RowCopy("Number", R.string.field_number, it) }
    is VaultPayload.Passport -> number.takeIf { it.isNotEmpty() }?.let { RowCopy("Number", R.string.field_number, it) }
    is VaultPayload.DriverLicense -> number.takeIf { it.isNotEmpty() }?.let { RowCopy("Number", R.string.field_number, it) }
}

/** Per-field copy actions, same fields and order as iOS `copyActions` (`VaultEnums.swift:467`). */
internal fun VaultPayload.rowCopyActions(): List<RowCopyAction> {
    val eph = isPayment
    fun a(@StringRes menu: Int, label: String, @StringRes field: Int, value: String) =
        RowCopyAction(menu, RowCopy(label, field, value, ephemeral = eph))
    val actions = when (this) {
        is VaultPayload.Login -> listOf(
            a(R.string.copy_email, "Email", R.string.field_email, email),
            a(R.string.copy_username, "Username", R.string.field_username, username),
            a(R.string.copy_password, "Password", R.string.field_password, password),
            a(R.string.copy_website, "Website", R.string.field_website, website),
        )
        is VaultPayload.SecureNote -> listOf(
            a(R.string.copy_content, "Content", R.string.field_content, content).copy(gated = protectItem),
        )
        is VaultPayload.Card -> listOf(
            a(R.string.copy_number, "Number", R.string.field_number, number),
            a(R.string.copy_accountholder, "Account Holder", R.string.field_accountholder, accountHolder),
            a(R.string.copy_expiry, "Expiry", R.string.field_expirydate, expiryDate),
            a(R.string.copy_securitycode, "Security Code", R.string.field_securitycode, securityCode),
        )
        is VaultPayload.BankAccount -> listOf(
            a(R.string.copy_accountnumber, "Account Number", R.string.field_accountnumber, accountNumber),
            a(R.string.copy_routingnumber, "Routing Number", R.string.field_routingnumber, routingNumber),
            a(R.string.copy_accountholder, "Account Holder", R.string.field_accountholder, accountHolder),
        )
        is VaultPayload.Name -> listOf(
            a(
                R.string.copy_name, "Name", R.string.field_name,
                listOf(firstName, middleName, lastName).filter { it.isNotEmpty() }.joinToString(" "),
            ),
            a(R.string.copy_username, "Username", R.string.field_username, defaultUsername),
        )
        is VaultPayload.PersonalEmail -> listOf(a(R.string.copy_email, "Email", R.string.field_email, email))
        is VaultPayload.Phone -> if (number.isEmpty()) emptyList() else listOf(
            a(R.string.copy_phone, "Phone Number", R.string.field_phonenumber, fullNumber()),
        )
        is VaultPayload.Address -> listOf(
            a(R.string.copy_address, "Address", R.string.field_address, address),
            a(R.string.copy_city, "City", R.string.field_city, city),
            a(R.string.copy_zip, "Zip Code", R.string.field_zipcode, zipCode),
        )
        is VaultPayload.Company -> listOf(
            a(R.string.copy_company, "Company", R.string.field_company, companyName),
            a(R.string.copy_jobtitle, "Job Title", R.string.field_jobtitle, jobTitle),
        )
        is VaultPayload.Website -> listOf(a(R.string.copy_website, "Website", R.string.field_website, websiteURL))
        is VaultPayload.IdCard -> idActions(name, number, ::a)
        is VaultPayload.Passport -> idActions(name, number, ::a)
        is VaultPayload.DriverLicense -> idActions(name, number, ::a)
    }
    return actions.filter { it.copy.value.isNotEmpty() }
}

private fun idActions(
    name: String,
    number: String,
    a: (Int, String, Int, String) -> RowCopyAction,
) = listOf(
    a(R.string.copy_name, "Name", R.string.field_name, name),
    a(R.string.copy_number, "Number", R.string.field_number, number),
)

/** iOS `VaultPayload.websiteURL`: only logins and website items open a page. */
internal val VaultPayload.websiteUrl: String?
    get() = when (this) {
        is VaultPayload.Login -> website.trim().ifEmpty { null }
        is VaultPayload.Website -> websiteURL.trim().ifEmpty { null }
        else -> null
    }

/**
 * iOS `normalizedURL` (`VaultItemRow.swift:182`): keep a URL that already has a scheme,
 * otherwise assume https.
 */
internal fun normalizedWebsiteUrl(raw: String): String {
    val schemeEnd = raw.indexOf("://")
    return if (schemeEnd > 0 && raw.substring(0, schemeEnd).all { it.isLetterOrDigit() || it in "+-." }) raw else "https://$raw"
}
