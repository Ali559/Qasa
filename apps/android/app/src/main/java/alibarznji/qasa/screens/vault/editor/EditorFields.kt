package alibarznji.qasa.screens.vault.editor

import uniffi.qasa_ffi.FfiField
import alibarznji.qasa.R
import alibarznji.qasa.theme.QasaDimens
import alibarznji.qasa.theme.ToggleRow
import alibarznji.qasa.vault.VaultPayload
import alibarznji.qasa.vault.VaultCollection
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType

/** What the per-kind field lists need from the editor besides the payload itself. */
class EditorEnv(
    val collections: List<VaultCollection>,
    /** `null` until the vault's Phone items have loaded. */
    val phones: List<PhoneOption>?,
    val onCreateCollection: (name: String, onCreated: (String) -> Unit) -> Unit,
    val onGeneratePassword: () -> Unit,
)

/** iOS `VaultItemEditor.formFields` (VaultItemEditor.swift:94): one field list per kind. */
@Composable
fun EditorFields(payload: VaultPayload, env: EditorEnv, onChange: (VaultPayload) -> Unit) {
    when (val p = payload) {
        is VaultPayload.Login -> LoginFields(p, env, onChange)
        is VaultPayload.SecureNote -> SecureNoteFields(p, env, onChange)
        is VaultPayload.Card -> CardFields(p, onChange)
        is VaultPayload.BankAccount -> BankFields(p, onChange)
        is VaultPayload.Name -> NameFields(p, onChange)
        is VaultPayload.PersonalEmail -> EmailFields(p, onChange)
        is VaultPayload.Phone -> PhoneFields(p, onChange)
        is VaultPayload.Address -> AddressFields(p, env, onChange)
        is VaultPayload.Company -> CompanyFields(p, onChange)
        is VaultPayload.Website -> WebsiteFields(p, onChange)
        is VaultPayload.IdCard -> IdDocumentFields(
            IdDoc(p.name, p.countryCode, p.number, p.issueDate, p.expiryDate, p.issuePlace), showIssuePlace = false,
        ) { onChange(p.copy(name = it.name, countryCode = it.countryCode, number = it.number, issueDate = it.issueDate, expiryDate = it.expiryDate, issuePlace = it.issuePlace)) }
        is VaultPayload.Passport -> IdDocumentFields(
            IdDoc(p.name, p.countryCode, p.number, p.issueDate, p.expiryDate, p.issuePlace), showIssuePlace = true,
        ) { onChange(p.copy(name = it.name, countryCode = it.countryCode, number = it.number, issueDate = it.issueDate, expiryDate = it.expiryDate, issuePlace = it.issuePlace)) }
        is VaultPayload.DriverLicense -> IdDocumentFields(
            IdDoc(p.name, p.countryCode, p.number, p.issueDate, p.expiryDate, p.issuePlace), showIssuePlace = false,
        ) { onChange(p.copy(name = it.name, countryCode = it.countryCode, number = it.number, issueDate = it.issueDate, expiryDate = it.expiryDate, issuePlace = it.issuePlace)) }
    }
}

// MARK: - Login (VaultItemEditor.swift:123)

@Composable
private fun LoginFields(p: VaultPayload.Login, env: EditorEnv, onChange: (VaultPayload) -> Unit) {
    FormTextField(R.string.field_itemname, p.itemName, FfiField.ITEM_NAME) { onChange(p.copy(itemName = it)) }
    FormTextField(R.string.field_email, p.email, FfiField.EMAIL, keyboardType = KeyboardType.Email) { onChange(p.copy(email = it)) }
    FormTextField(R.string.field_username, p.username, FfiField.USERNAME) { onChange(p.copy(username = it)) }
    PasswordFormField(p.password, { onChange(p.copy(password = it)) }, env.onGeneratePassword)
    TotpFormField(p.totp) { onChange(p.copy(totp = it)) }
    FormTextField(R.string.field_website, p.website, FfiField.WEBSITE, keyboardType = KeyboardType.Uri) { onChange(p.copy(website = it)) }
    FormTextField(R.string.field_note, p.note, FfiField.NOTE) { onChange(p.copy(note = it)) }
    CollectionPickerField(p.collectionId, env.collections, { onChange(p.copy(collectionId = it)) }, env.onCreateCollection)
}

// MARK: - Secure note (VaultItemEditor.swift:184)

@Composable
private fun SecureNoteFields(p: VaultPayload.SecureNote, env: EditorEnv, onChange: (VaultPayload) -> Unit) {
    FormTextField(R.string.field_title, p.title, FfiField.TITLE) { onChange(p.copy(title = it)) }
    FormTextField(R.string.field_content, p.content, FfiField.CONTENT) { onChange(p.copy(content = it)) }
    NoteColorPicker(NoteColor.fromRaw(p.color, NoteColor.Mint)) { onChange(p.copy(color = it.raw)) }
    ToggleRow(
        title = stringResource(R.string.vault_protectitem),
        checked = p.protectItem,
        onCheckedChange = { onChange(p.copy(protectItem = it)) },
    )
    Spacer(Modifier.height(QasaDimens.gap))
    CollectionPickerField(p.collectionId, env.collections, { onChange(p.copy(collectionId = it)) }, env.onCreateCollection)
}

// MARK: - Card (VaultItemEditor.swift:200)

@Composable
private fun CardFields(p: VaultPayload.Card, onChange: (VaultPayload) -> Unit) {
    FormTextField(R.string.field_itemname, p.itemName, FfiField.ITEM_NAME) { onChange(p.copy(itemName = it)) }
    FormTextField(R.string.field_accountholder, p.accountHolder, FfiField.ACCOUNT_HOLDER) { onChange(p.copy(accountHolder = it)) }
    CardNumberField(p.number) { onChange(p.copy(number = it)) }
    // Expiry and security code side by side, as printed on the card; the code is the
    // narrower one (at most 4 digits, American Express).
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(QasaDimens.gap)) {
        CardExpiryField(p.expiryDate, Modifier.weight(1.25f)) { onChange(p.copy(expiryDate = it)) }
        FormTextField(
            R.string.field_securitycode,
            p.securityCode,
            FfiField.SECURITY_CODE,
            secure = true,
            keyboardType = KeyboardType.NumberPassword,
            modifier = Modifier.weight(1f),
        ) { onChange(p.copy(securityCode = it.filter(Char::isDigit).take(4))) }
    }
    FormTextField(R.string.field_note, p.note, FfiField.NOTE) { onChange(p.copy(note = it)) }
    NoteColorPicker(NoteColor.fromRaw(p.color, NoteColor.Blue)) { onChange(p.copy(color = it.raw)) }
}

// MARK: - Bank (VaultItemEditor.swift:231)

@Composable
private fun BankFields(p: VaultPayload.BankAccount, onChange: (VaultPayload) -> Unit) {
    FormTextField(R.string.field_itemname, p.itemName, FfiField.ITEM_NAME) { onChange(p.copy(itemName = it)) }
    FormTextField(R.string.field_accountholder, p.accountHolder, FfiField.ACCOUNT_HOLDER) { onChange(p.copy(accountHolder = it)) }
    // Text keyboard: an IBAN has letters.
    FormTextField(R.string.field_accountnumber, p.accountNumber, FfiField.ACCOUNT_NUMBER) {
        onChange(p.copy(accountNumber = it))
    }
    FormTextField(R.string.field_routingnumber, p.routingNumber, FfiField.ROUTING_NUMBER) { onChange(p.copy(routingNumber = it)) }
    CountryPickerField(p.countryCode) { onChange(p.copy(countryCode = it)) }
    FormTextField(R.string.field_bankname, p.bankName, FfiField.BANK_NAME) { onChange(p.copy(bankName = it)) }
}
