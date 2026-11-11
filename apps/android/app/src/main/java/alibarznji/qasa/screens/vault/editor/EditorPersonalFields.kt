package alibarznji.qasa.screens.vault.editor

import alibarznji.qasa.R
import alibarznji.qasa.theme.QasaFieldGroup
import alibarznji.qasa.vault.Country
import alibarznji.qasa.vault.VaultPayload
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import uniffi.qasa_ffi.FfiField

// MARK: - Name (VaultItemEditor.swift:244)

@Composable
internal fun NameFields(p: VaultPayload.Name, onChange: (VaultPayload) -> Unit) = QasaFieldGroup {
    KindPicker(
        selected = Honorific.fromRaw(p.type),
        options = Honorific.entries,
        label = { stringResource(it.label) },
        onChange = { onChange(p.copy(type = it.iosRaw)) },
    )
    FormTextField(R.string.field_firstname, p.firstName, FfiField.FIRST_NAME) { onChange(p.copy(firstName = it)) }
    FormTextField(R.string.field_middlename, p.middleName, FfiField.MIDDLE_NAME) { onChange(p.copy(middleName = it)) }
    FormTextField(R.string.field_lastname, p.lastName, FfiField.LAST_NAME) { onChange(p.copy(lastName = it)) }
    FormTextField(R.string.field_defaultusername, p.defaultUsername, FfiField.DEFAULT_USERNAME) { onChange(p.copy(defaultUsername = it)) }
    FormDateField(R.string.field_birthdate, p.birthDate, FfiField.BIRTH_DATE) { onChange(p.copy(birthDate = it)) }
    FormTextField(R.string.field_birthplace, p.birthPlace, FfiField.BIRTH_PLACE) { onChange(p.copy(birthPlace = it)) }
}

// MARK: - Email (VaultItemEditor.swift:269)

@Composable
internal fun EmailFields(p: VaultPayload.PersonalEmail, onChange: (VaultPayload) -> Unit) = QasaFieldGroup {
    FormTextField(R.string.field_itemname, p.itemName, FfiField.ITEM_NAME) { onChange(p.copy(itemName = it)) }
    KindPicker(
        selected = EmailKind.fromRaw(p.type),
        options = EmailKind.entries,
        label = { stringResource(it.label) },
        onChange = { onChange(p.copy(type = it.iosRaw)) },
    )
    FormTextField(
        R.string.field_email, p.email, FfiField.EMAIL,
        keyboardType = KeyboardType.Email, placeholderRes = R.string.field_emailplaceholder,
    ) { onChange(p.copy(email = it)) }
}

// MARK: - Phone (VaultItemEditor.swift:289)

@Composable
internal fun PhoneFields(p: VaultPayload.Phone, onChange: (VaultPayload) -> Unit) = QasaFieldGroup {
    FormTextField(R.string.field_itemname, p.itemName, FfiField.ITEM_NAME) { onChange(p.copy(itemName = it)) }
    KindPicker(
        selected = PhoneKind.fromRaw(p.type),
        options = PhoneKind.entries,
        label = { stringResource(it.label) },
        onChange = { onChange(p.copy(type = it.iosRaw)) },
    )
    CountryPickerField(p.countryCode, showsDialCode = true) { onChange(p.copy(countryCode = it)) }
    PhoneNumberField(Country.dialCode(p.countryCode), p.number) { onChange(p.copy(number = it)) }
}

// MARK: - Address (VaultItemEditor.swift:363)

@Composable
internal fun AddressFields(p: VaultPayload.Address, env: EditorEnv, onChange: (VaultPayload) -> Unit) = QasaFieldGroup {
    FormTextField(R.string.field_itemname, p.itemName, FfiField.ITEM_NAME) { onChange(p.copy(itemName = it)) }
    FormTextField(R.string.field_address, p.address, FfiField.ADDRESS) { onChange(p.copy(address = it)) }
    FormTextField(R.string.field_city, p.city, FfiField.CITY) { onChange(p.copy(city = it)) }
    FormTextField(R.string.field_zipcode, p.zipCode, FfiField.ZIP_CODE) { onChange(p.copy(zipCode = it)) }
    CountryPickerField(p.countryCode) { onChange(p.copy(countryCode = it)) }
    FormTextField(R.string.field_recipient, p.recipient, FfiField.RECIPIENT) { onChange(p.copy(recipient = it)) }
    FormTextField(R.string.field_building, p.building, FfiField.BUILDING) { onChange(p.copy(building = it)) }
    FormTextField(R.string.field_floor, p.floor, FfiField.FLOOR) { onChange(p.copy(floor = it)) }
    FormTextField(R.string.field_apartment, p.apartment, FfiField.APARTMENT) { onChange(p.copy(apartment = it)) }
    FormTextField(R.string.field_doorcode, p.doorCode, FfiField.DOOR_CODE) { onChange(p.copy(doorCode = it)) }
    AddressPhonePicker(p.phoneItemId, env.phones) { onChange(p.copy(phoneItemId = it)) }
}

// MARK: - Company / Website / ID (VaultItemEditor.swift:381)

@Composable
internal fun CompanyFields(p: VaultPayload.Company, onChange: (VaultPayload) -> Unit) = QasaFieldGroup {
    FormTextField(R.string.field_companyname, p.companyName, FfiField.COMPANY_NAME) { onChange(p.copy(companyName = it)) }
    FormTextField(R.string.field_jobtitle, p.jobTitle, FfiField.JOB_TITLE) { onChange(p.copy(jobTitle = it)) }
}

@Composable
internal fun WebsiteFields(p: VaultPayload.Website, onChange: (VaultPayload) -> Unit) = QasaFieldGroup {
    FormTextField(R.string.field_itemname, p.itemName, FfiField.ITEM_NAME) { onChange(p.copy(itemName = it)) }
    FormTextField(
        R.string.field_websiteurl, p.websiteURL, FfiField.WEBSITE_URL,
        keyboardType = KeyboardType.Uri, placeholderRes = R.string.field_websiteplaceholder,
    ) { onChange(p.copy(websiteURL = it)) }
}

/** iOS `IDDocumentFields`, shared by ID card, passport and driver licence. */
internal data class IdDoc(
    val name: String,
    val countryCode: String,
    val number: String,
    val issueDate: String,
    val expiryDate: String,
    val issuePlace: String,
)

/**
 * iOS `idFields`: name, country, number, issue date, issue place (passport only), expiry.
 * The two dates are date rows: free-text entry with the format hint, plus the calendar.
 */
@Composable
internal fun IdDocumentFields(d: IdDoc, showIssuePlace: Boolean, onChange: (IdDoc) -> Unit) = QasaFieldGroup {
    FormTextField(R.string.field_name, d.name, FfiField.NAME) { onChange(d.copy(name = it)) }
    CountryPickerField(d.countryCode) { onChange(d.copy(countryCode = it)) }
    FormTextField(R.string.field_number, d.number, FfiField.NUMBER) { onChange(d.copy(number = it)) }
    FormDateField(R.string.field_issuedate, d.issueDate, FfiField.ISSUE_DATE) { onChange(d.copy(issueDate = it)) }
    if (showIssuePlace) {
        FormTextField(R.string.field_issueplace, d.issuePlace, FfiField.ISSUE_PLACE) { onChange(d.copy(issuePlace = it)) }
    }
    FormDateField(R.string.field_expirydate, d.expiryDate, FfiField.EXPIRY_DATE) { onChange(d.copy(expiryDate = it)) }
}
