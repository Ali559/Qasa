import SwiftUI
import SwiftData

struct VaultItemEditor: View {
    enum Mode {
        case create(VaultPayload)
        case edit(UUID)
    }

    let mode: Mode
    var onDone: () -> Void

    @Environment(\.modelContext) private var modelContext

    @State private var payload: VaultPayload
    @State private var passwordVisible = false
    @State private var showPasswordGenerator = false
    @State private var loadError: String?
    @State private var saveError: String?

    init(mode: Mode, onDone: @escaping () -> Void) {
        self.mode = mode
        self.onDone = onDone
        switch mode {
        case .create(let p):
            _payload = State(initialValue: p)
        case .edit:
            _payload = State(initialValue: .login(LoginFields()))
        }
    }

    private var isEditing: Bool {
        if case .edit = mode { return true }
        return false
    }

    private var title: String {
        let locale = AppLanguage.current.locale
        let base: String
        switch payload {
        case .login: base = L10n.tr("vault.type.login", locale: locale)
        case .secureNote: base = L10n.tr("vault.type.secureNote", locale: locale)
        case .card: base = L10n.tr("vault.type.card", locale: locale)
        case .bankAccount: base = L10n.tr("vault.type.bankAccount", locale: locale)
        case .name: base = L10n.tr("vault.type.name", locale: locale)
        case .personalEmail: base = L10n.tr("vault.type.email", locale: locale)
        case .phone: base = L10n.tr("vault.type.phone", locale: locale)
        case .address: base = L10n.tr("vault.type.address", locale: locale)
        case .company: base = L10n.tr("vault.type.company", locale: locale)
        case .website: base = L10n.tr("vault.type.website", locale: locale)
        case .idCard: base = L10n.tr("vault.type.idCard", locale: locale)
        case .passport: base = L10n.tr("vault.type.passport", locale: locale)
        case .driverLicense: base = L10n.tr("vault.type.driverLicense", locale: locale)
        }
        let fmt = isEditing ? L10n.tr("vault.editFmt", locale: locale) : L10n.tr("vault.newFmt", locale: locale)
        return String(format: fmt, locale: locale, arguments: [base])
    }

    var body: some View {
        VaultFormChrome(
            title: title,
            canSave: !payload.displayName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
            isEditing: isEditing,
            onCancel: onDone,
            onSave: save
        ) {
            formFields
        }
        .appLocalization()
        .onAppear(perform: loadExistingIfNeeded)
        .alert(L10n.tr("vault.couldNotSave", locale: AppLanguage.current.locale), isPresented: Binding(
            get: { saveError != nil },
            set: { if !$0 { saveError = nil } }
        )) {
            Button(L10n.tr("common.ok", locale: AppLanguage.current.locale), role: .cancel) { saveError = nil }
        } message: {
            Text(saveError ?? "")
        }
        .sheet(isPresented: $showPasswordGenerator) {
            PasswordGeneratorModal { generated in
                guard case .login(var fields) = payload else { return }
                fields.password = generated
                payload = .login(fields)
                showPasswordGenerator = false
            }
            .appLocalization()
            .presentationDetents([.fraction(0.92), .large])
            .presentationDragIndicator(.visible)
            .presentationCornerRadius(20)
        }
    }

    @ViewBuilder
    private var formFields: some View {
        switch payload {
        case .login:
            loginFields
        case .secureNote:
            secureNoteFields
        case .card:
            cardFields
        case .bankAccount:
            bankFields
        case .name:
            nameFields
        case .personalEmail:
            emailFields
        case .phone:
            phoneFields
        case .address:
            addressFields
        case .company:
            companyFields
        case .website:
            websiteFields
        case .idCard, .passport, .driverLicense:
            idFields
        }
    }

    // MARK: - Login

    private var loginFields: some View {
        Group {
            FormTextField(title: L10n.tr("field.itemName"), text: loginBinding(\.itemName))
            FormTextField(title: L10n.tr("field.email"), text: loginBinding(\.email))
            FormTextField(title: L10n.tr("field.username"), text: loginBinding(\.username))
            passwordField
            TOTPEditorField(totp: loginTOTPBinding)
            FormTextField(title: L10n.tr("field.website"), text: loginBinding(\.website))
            FormTextField(title: L10n.tr("field.note"), text: loginBinding(\.note))
            CollectionPickerField(collectionId: loginCollectionBinding)
        }
    }

    private var passwordField: some View {
        VStack(alignment: .leading, spacing: 8) {
            FormFieldLabel(text: L10n.tr("field.password"))
            HStack(spacing: 10) {
                Group {
                    if passwordVisible {
                        TextField(L10n.tr("field.password"), text: loginBinding(\.password))
                    } else {
                        SecureField(L10n.tr("field.password"), text: loginBinding(\.password))
                    }
                }
                .font(.qasa(16))
                .multilineTextAlignment(.leading)
                .foregroundColor(Theme.textPrimary)
                .tint(Theme.accent)

                Button {
                    showPasswordGenerator = true
                } label: {
                    Image(systemName: "wand.and.stars")
                        .font(.qasa(16, weight: .semibold))
                        .foregroundColor(Theme.accent)
                        .frame(width: 28, height: 28)
                }
                .accessibilityLabel(L10n.tr("vault.generatePassword"))

                Button {
                    passwordVisible.toggle()
                } label: {
                    Image(systemName: passwordVisible ? "eye.slash" : "eye")
                        .font(.qasa(16, weight: .medium))
                        .foregroundColor(Theme.textSecondary)
                        .frame(width: 28, height: 28)
                }
                .accessibilityLabel(passwordVisible ? L10n.tr("vault.hidePassword") : L10n.tr("vault.showPassword"))
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 14)
            .background(Theme.cardBackground)
            .cornerRadius(Theme.cornerRadius)
            .overlay(
                RoundedRectangle(cornerRadius: Theme.cornerRadius)
                    .stroke(Theme.stroke, lineWidth: 1)
            )
        }
    }

    // MARK: - Secure note

    private var secureNoteFields: some View {
        Group {
            FormTextField(title: L10n.tr("field.title"), text: noteBinding(\.title))
            FormTextField(title: L10n.tr("field.content"), text: noteBinding(\.content))
            NoteColorPicker(color: noteColorBinding)
            Toggle(isOn: noteProtectBinding) {
                Text(L10n.tr("vault.protectItem"))
                    .foregroundColor(Theme.textPrimary)
            }
            .tint(Theme.accent)
            CollectionPickerField(collectionId: noteCollectionBinding)
        }
    }

    // MARK: - Card

    private var cardFields: some View {
        Group {
            FormTextField(title: L10n.tr("field.itemName"), text: cardBinding(\.itemName))
            FormTextField(title: L10n.tr("field.accountHolder"), text: cardBinding(\.accountHolder))
            FormTextField(title: L10n.tr("field.number"), text: cardBinding(\.number))
            FormTextField(title: L10n.tr("field.securityCode"), text: cardBinding(\.securityCode), isSecure: true)
            ValidatedDateField(title: L10n.tr("field.expiryDate"), text: cardBinding(\.expiryDate))
            FormTextField(title: L10n.tr("field.note"), text: cardBinding(\.note))
            NoteColorPicker(color: cardColorBinding)
            Button {
                // NFC stub — gated in UI only
            } label: {
                Label(L10n.tr("vault.scanNFC"), systemImage: "wave.3.right")
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(Theme.cardBackground)
                    .foregroundColor(Theme.accent)
                    .cornerRadius(Theme.cornerRadius)
                    .overlay(
                        RoundedRectangle(cornerRadius: Theme.cornerRadius)
                            .stroke(Theme.accent.opacity(0.4), lineWidth: 1)
                    )
            }
            Text(L10n.tr("vault.nfcHint"))
                .font(.qasa(12))
                .foregroundColor(Theme.textSecondary)
        }
    }

    // MARK: - Bank

    private var bankFields: some View {
        Group {
            FormTextField(title: L10n.tr("field.itemName"), text: bankBinding(\.itemName))
            FormTextField(title: L10n.tr("field.accountHolder"), text: bankBinding(\.accountHolder))
            FormTextField(title: L10n.tr("field.accountNumber"), text: bankBinding(\.accountNumber))
            FormTextField(title: L10n.tr("field.routingNumber"), text: bankBinding(\.routingNumber))
            CountryPickerField(countryCode: bankCountryBinding)
            FormTextField(title: L10n.tr("field.bankName"), text: bankBinding(\.bankName))
        }
    }

    // MARK: - Name

    private var nameFields: some View {
        Group {
            VStack(alignment: .leading, spacing: 8) {
                FormFieldLabel(text: L10n.tr("field.type"))
                Picker(L10n.tr("field.type"), selection: nameHonorificBinding) {
                    ForEach(Honorific.allCases, id: \.self) { Text($0.displayName).tag($0) }
                }
                .pickerStyle(.menu)
                .tint(Theme.accent)
                .padding(12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Theme.cardBackground)
                .cornerRadius(Theme.cornerRadius)
            }
            FormTextField(title: L10n.tr("field.firstName"), text: nameBinding(\.firstName))
            FormTextField(title: L10n.tr("field.middleName"), text: nameBinding(\.middleName))
            FormTextField(title: L10n.tr("field.lastName"), text: nameBinding(\.lastName))
            FormTextField(title: L10n.tr("field.defaultUsername"), text: nameBinding(\.defaultUsername))
            ValidatedDateField(title: L10n.tr("field.birthDate"), text: nameBinding(\.birthDate))
            FormTextField(title: L10n.tr("field.birthPlace"), text: nameBinding(\.birthPlace))
        }
    }

    // MARK: - Email

    private var emailFields: some View {
        Group {
            FormTextField(title: L10n.tr("field.itemName"), text: emailBinding(\.itemName))
            VStack(alignment: .leading, spacing: 8) {
                FormFieldLabel(text: L10n.tr("field.type"))
                Picker(L10n.tr("field.type"), selection: emailKindBinding) {
                    ForEach(EmailKind.allCases, id: \.self) { Text($0.displayName).tag($0) }
                }
                .tint(Theme.accent)
                .padding(12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Theme.cardBackground)
                .cornerRadius(Theme.cornerRadius)
            }
            FormTextField(title: L10n.tr("field.email"), text: emailBinding(\.email))
        }
    }

    // MARK: - Phone

    private var phoneFields: some View {
        Group {
            FormTextField(title: L10n.tr("field.itemName"), text: phoneBinding(\.itemName))

            HStack(alignment: .top, spacing: 12) {
                VStack(alignment: .leading, spacing: 8) {
                    FormFieldLabel(text: L10n.tr("field.type"))
                    Picker(L10n.tr("field.type"), selection: phoneKindBinding) {
                        ForEach(PhoneKind.allCases, id: \.self) { Text($0.displayName).tag($0) }
                    }
                    .labelsHidden()
                    .tint(Theme.accent)
                    .padding(.horizontal, 12)
                    .frame(maxWidth: .infinity, minHeight: 50, alignment: .leading)
                    .background(Theme.cardBackground)
                    .cornerRadius(Theme.cornerRadius)
                    .overlay(
                        RoundedRectangle(cornerRadius: Theme.cornerRadius)
                            .stroke(Theme.stroke, lineWidth: 1)
                    )
                }
                .frame(maxWidth: .infinity)

                VStack(alignment: .leading, spacing: 8) {
                    FormFieldLabel(text: L10n.tr("vault.country"))
                    CountryMenuPicker(countryCode: phoneCountryBinding, showsDialCode: true)
                        .padding(.horizontal, 12)
                        .frame(maxWidth: .infinity, minHeight: 50, alignment: .leading)
                        .background(Theme.cardBackground)
                        .cornerRadius(Theme.cornerRadius)
                        .overlay(
                            RoundedRectangle(cornerRadius: Theme.cornerRadius)
                                .stroke(Theme.stroke, lineWidth: 1)
                        )
                }
                .frame(maxWidth: .infinity)
            }

            VStack(alignment: .leading, spacing: 8) {
                FormFieldLabel(text: L10n.tr("field.phoneNumber"))
                HStack(alignment: .center, spacing: 8) {
                    Text(Country.dialCode(for: phoneCountryBinding.wrappedValue))
                        .font(.qasa(16, weight: .semibold))
                        .foregroundColor(Theme.accent)
                        .frame(minWidth: 64)
                        .frame(height: 50)
                        .padding(.horizontal, 12)
                        .background(Theme.cardBackground)
                        .cornerRadius(Theme.cornerRadius)
                        .overlay(
                            RoundedRectangle(cornerRadius: Theme.cornerRadius)
                                .stroke(Theme.stroke, lineWidth: 1)
                        )

                    TextField(L10n.tr("field.phoneNumber"), text: phoneBinding(\.number))
                        .keyboardType(.phonePad)
                        .foregroundColor(Theme.textPrimary)
                        .tint(Theme.accent)
                        .padding(.horizontal, 14)
                        .frame(maxWidth: .infinity)
                        .frame(height: 50)
                        .background(Theme.cardBackground)
                        .cornerRadius(Theme.cornerRadius)
                        .overlay(
                            RoundedRectangle(cornerRadius: Theme.cornerRadius)
                                .stroke(Theme.stroke, lineWidth: 1)
                        )
                }
            }
        }
    }

    // MARK: - Address

    private var addressFields: some View {
        Group {
            FormTextField(title: L10n.tr("field.itemName"), text: addressBinding(\.itemName))
            FormTextField(title: L10n.tr("field.address"), text: addressBinding(\.address))
            FormTextField(title: L10n.tr("field.city"), text: addressBinding(\.city))
            FormTextField(title: L10n.tr("field.zipCode"), text: addressBinding(\.zipCode))
            CountryPickerField(countryCode: addressCountryBinding)
            FormTextField(title: L10n.tr("field.recipient"), text: addressBinding(\.recipient))
            FormTextField(title: L10n.tr("field.building"), text: addressBinding(\.building))
            FormTextField(title: L10n.tr("field.floor"), text: addressBinding(\.floor))
            FormTextField(title: L10n.tr("field.apartment"), text: addressBinding(\.apartment))
            FormTextField(title: L10n.tr("field.doorCode"), text: addressBinding(\.doorCode))
            AddressPhonePicker(phoneItemId: addressPhoneBinding)
        }
    }

    // MARK: - Company / Website / ID

    private var companyFields: some View {
        Group {
            FormTextField(title: L10n.tr("field.companyName"), text: companyBinding(\.companyName))
            FormTextField(title: L10n.tr("field.jobTitle"), text: companyBinding(\.jobTitle))
        }
    }

    private var websiteFields: some View {
        Group {
            FormTextField(title: L10n.tr("field.itemName"), text: websiteBinding(\.itemName))
            FormTextField(title: L10n.tr("field.websiteURL"), text: websiteBinding(\.websiteURL))
        }
    }

    private var idFields: some View {
        Group {
            FormTextField(title: L10n.tr("field.name"), text: idBinding(\.name))
            CountryPickerField(countryCode: idCountryBinding)
            FormTextField(title: L10n.tr("field.number"), text: idBinding(\.number))
            // Validated like card expiry and birth date (Android parity).
            ValidatedDateField(title: L10n.tr("field.issueDate"), text: idBinding(\.issueDate))
            if case .passport = payload {
                FormTextField(title: L10n.tr("field.issuePlace"), text: idBinding(\.issuePlace))
            }
            ValidatedDateField(title: L10n.tr("field.expiryDate"), text: idBinding(\.expiryDate))
        }
    }

    // MARK: - Bindings

    private func loginBinding(_ keyPath: WritableKeyPath<LoginFields, String>) -> Binding<String> {
        Binding(
            get: {
                if case .login(let f) = payload { return f[keyPath: keyPath] }
                return ""
            },
            set: { newValue in
                guard case .login(var f) = payload else { return }
                f[keyPath: keyPath] = newValue
                payload = .login(f)
            }
        )
    }

    private var loginTOTPBinding: Binding<String?> {
        Binding(
            get: { if case .login(let f) = payload { return f.totp }; return nil },
            set: { newValue in
                guard case .login(var f) = payload else { return }
                f.totp = (newValue ?? "").isEmpty ? nil : newValue
                payload = .login(f)
            }
        )
    }

    private var loginCollectionBinding: Binding<UUID?> {
        Binding(
            get: { if case .login(let f) = payload { return f.collectionId }; return nil },
            set: { newValue in
                guard case .login(var f) = payload else { return }
                f.collectionId = newValue
                payload = .login(f)
            }
        )
    }

    private func noteBinding(_ keyPath: WritableKeyPath<SecureNoteFields, String>) -> Binding<String> {
        Binding(
            get: { if case .secureNote(let f) = payload { return f[keyPath: keyPath] }; return "" },
            set: { v in
                guard case .secureNote(var f) = payload else { return }
                f[keyPath: keyPath] = v
                payload = .secureNote(f)
            }
        )
    }

    private var noteColorBinding: Binding<NoteColor> {
        Binding(
            get: { if case .secureNote(let f) = payload { return f.color }; return .mint },
            set: { v in
                guard case .secureNote(var f) = payload else { return }
                f.color = v
                payload = .secureNote(f)
            }
        )
    }

    private var noteProtectBinding: Binding<Bool> {
        Binding(
            get: { if case .secureNote(let f) = payload { return f.protectItem }; return false },
            set: { v in
                guard case .secureNote(var f) = payload else { return }
                f.protectItem = v
                payload = .secureNote(f)
            }
        )
    }

    private var noteCollectionBinding: Binding<UUID?> {
        Binding(
            get: { if case .secureNote(let f) = payload { return f.collectionId }; return nil },
            set: { v in
                guard case .secureNote(var f) = payload else { return }
                f.collectionId = v
                payload = .secureNote(f)
            }
        )
    }

    private func cardBinding(_ keyPath: WritableKeyPath<CardFields, String>) -> Binding<String> {
        Binding(
            get: { if case .card(let f) = payload { return f[keyPath: keyPath] }; return "" },
            set: { v in
                guard case .card(var f) = payload else { return }
                f[keyPath: keyPath] = v
                payload = .card(f)
            }
        )
    }

    private var cardColorBinding: Binding<NoteColor> {
        Binding(
            get: { if case .card(let f) = payload { return f.color }; return .blue },
            set: { v in
                guard case .card(var f) = payload else { return }
                f.color = v
                payload = .card(f)
            }
        )
    }

    private func bankBinding(_ keyPath: WritableKeyPath<BankAccountFields, String>) -> Binding<String> {
        Binding(
            get: { if case .bankAccount(let f) = payload { return f[keyPath: keyPath] }; return "" },
            set: { v in
                guard case .bankAccount(var f) = payload else { return }
                f[keyPath: keyPath] = v
                payload = .bankAccount(f)
            }
        )
    }

    private var bankCountryBinding: Binding<String> {
        Binding(
            get: { if case .bankAccount(let f) = payload { return f.countryCode }; return "IQ" },
            set: { v in
                guard case .bankAccount(var f) = payload else { return }
                f.countryCode = v
                payload = .bankAccount(f)
            }
        )
    }

    private func nameBinding(_ keyPath: WritableKeyPath<NameFields, String>) -> Binding<String> {
        Binding(
            get: { if case .name(let f) = payload { return f[keyPath: keyPath] }; return "" },
            set: { v in
                guard case .name(var f) = payload else { return }
                f[keyPath: keyPath] = v
                payload = .name(f)
            }
        )
    }

    private var nameHonorificBinding: Binding<Honorific> {
        Binding(
            get: { if case .name(let f) = payload { return f.type }; return .none },
            set: { v in
                guard case .name(var f) = payload else { return }
                f.type = v
                payload = .name(f)
            }
        )
    }

    private func emailBinding(_ keyPath: WritableKeyPath<PersonalEmailFields, String>) -> Binding<String> {
        Binding(
            get: { if case .personalEmail(let f) = payload { return f[keyPath: keyPath] }; return "" },
            set: { v in
                guard case .personalEmail(var f) = payload else { return }
                f[keyPath: keyPath] = v
                payload = .personalEmail(f)
            }
        )
    }

    private var emailKindBinding: Binding<EmailKind> {
        Binding(
            get: { if case .personalEmail(let f) = payload { return f.type }; return .personal },
            set: { v in
                guard case .personalEmail(var f) = payload else { return }
                f.type = v
                payload = .personalEmail(f)
            }
        )
    }

    private func phoneBinding(_ keyPath: WritableKeyPath<PhoneFields, String>) -> Binding<String> {
        Binding(
            get: { if case .phone(let f) = payload { return f[keyPath: keyPath] }; return "" },
            set: { v in
                guard case .phone(var f) = payload else { return }
                f[keyPath: keyPath] = v
                payload = .phone(f)
            }
        )
    }

    private var phoneKindBinding: Binding<PhoneKind> {
        Binding(
            get: { if case .phone(let f) = payload { return f.type }; return .cell },
            set: { v in
                guard case .phone(var f) = payload else { return }
                f.type = v
                payload = .phone(f)
            }
        )
    }

    private var phoneCountryBinding: Binding<String> {
        Binding(
            get: { if case .phone(let f) = payload { return f.countryCode }; return "IQ" },
            set: { v in
                guard case .phone(var f) = payload else { return }
                f.countryCode = v
                payload = .phone(f)
            }
        )
    }

    private func addressBinding(_ keyPath: WritableKeyPath<AddressFields, String>) -> Binding<String> {
        Binding(
            get: { if case .address(let f) = payload { return f[keyPath: keyPath] }; return "" },
            set: { v in
                guard case .address(var f) = payload else { return }
                f[keyPath: keyPath] = v
                payload = .address(f)
            }
        )
    }

    private var addressCountryBinding: Binding<String> {
        Binding(
            get: { if case .address(let f) = payload { return f.countryCode }; return "IQ" },
            set: { v in
                guard case .address(var f) = payload else { return }
                f.countryCode = v
                payload = .address(f)
            }
        )
    }

    private var addressPhoneBinding: Binding<UUID?> {
        Binding(
            get: { if case .address(let f) = payload { return f.phoneItemId }; return nil },
            set: { v in
                guard case .address(var f) = payload else { return }
                f.phoneItemId = v
                payload = .address(f)
            }
        )
    }

    private func companyBinding(_ keyPath: WritableKeyPath<CompanyFields, String>) -> Binding<String> {
        Binding(
            get: { if case .company(let f) = payload { return f[keyPath: keyPath] }; return "" },
            set: { v in
                guard case .company(var f) = payload else { return }
                f[keyPath: keyPath] = v
                payload = .company(f)
            }
        )
    }

    private func websiteBinding(_ keyPath: WritableKeyPath<WebsiteFields, String>) -> Binding<String> {
        Binding(
            get: { if case .website(let f) = payload { return f[keyPath: keyPath] }; return "" },
            set: { v in
                guard case .website(var f) = payload else { return }
                f[keyPath: keyPath] = v
                payload = .website(f)
            }
        )
    }

    private func idBinding(_ keyPath: WritableKeyPath<IDDocumentFields, String>) -> Binding<String> {
        Binding(
            get: {
                switch payload {
                case .idCard(let f), .passport(let f), .driverLicense(let f): return f[keyPath: keyPath]
                default: return ""
                }
            },
            set: { v in
                switch payload {
                case .idCard(var f):
                    f[keyPath: keyPath] = v
                    payload = .idCard(f)
                case .passport(var f):
                    f[keyPath: keyPath] = v
                    payload = .passport(f)
                case .driverLicense(var f):
                    f[keyPath: keyPath] = v
                    payload = .driverLicense(f)
                default: break
                }
            }
        )
    }

    private var idCountryBinding: Binding<String> {
        Binding(
            get: {
                switch payload {
                case .idCard(let f), .passport(let f), .driverLicense(let f): return f.countryCode
                default: return "IQ"
                }
            },
            set: { v in
                switch payload {
                case .idCard(var f):
                    f.countryCode = v
                    payload = .idCard(f)
                case .passport(var f):
                    f.countryCode = v
                    payload = .passport(f)
                case .driverLicense(var f):
                    f.countryCode = v
                    payload = .driverLicense(f)
                default: break
                }
            }
        )
    }

    private func loadExistingIfNeeded() {
        guard case .edit(let id) = mode else { return }
        do {
            payload = try VaultRepository.shared.payload(for: id)
        } catch {
            loadError = error.localizedDescription
        }
    }

    private func save() {
        VaultRepository.shared.ensureConfigured(context: modelContext)
        do {
            switch mode {
            case .create:
                _ = try VaultRepository.shared.save(payload: payload)
            case .edit(let id):
                _ = try VaultRepository.shared.save(payload: payload, id: id)
            }
            onDone()
        } catch {
            saveError = error.localizedDescription
        }
    }
}

struct AddressPhonePicker: View {
    @Binding var phoneItemId: UUID?

    private var phones: [VaultListRow] {
        VaultRepository.shared.cache.rows.filter { row in
            guard case .phone = try? VaultRepository.shared.payload(for: row.id) else { return false }
            return true
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            FormFieldLabel(text: L10n.tr("field.phoneNumber"))
            Picker(L10n.tr("field.phoneNumber"), selection: $phoneItemId) {
                Text(L10n.tr("common.none")).tag(Optional<UUID>.none)
                ForEach(phones) { row in
                    Text(row.displayName).tag(Optional(row.id))
                }
            }
            .tint(Theme.accent)
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.cardBackground)
            .cornerRadius(Theme.cornerRadius)
        }
        .onAppear {
            if let id = phoneItemId, !phones.contains(where: { $0.id == id }) {
                phoneItemId = nil
            }
        }
    }
}
