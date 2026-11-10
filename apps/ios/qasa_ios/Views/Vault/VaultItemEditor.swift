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
    @State private var showNFCScan = false
    @State private var loadError: String?
    @State private var saveError: String?
    /// Rule messages for the current (normalized) payload, by field key.
    @State private var issueMessages: [String: String] = [:]
    /// Field keys the user has changed: their messages show live.
    @State private var touched: Set<String> = []
    /// After a Save attempt every field's message shows.
    @State private var attemptedSave = false
    /// The load of an edited item sets `payload` once without it counting as an edit.
    @State private var ignoreNextChange = false

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
            // Always tappable: a Save with issues shows every field's message instead.
            canSave: true,
            isEditing: isEditing,
            onCancel: onDone,
            onSave: save
        ) {
            if attemptedSave, !issueMessages.isEmpty {
                Text(L10n.tr("validation.fixToSave", locale: AppLanguage.current.locale))
                    .font(.qasa(13, weight: .semibold))
                    .foregroundColor(Theme.danger)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            formFields
        }
        .appLocalization()
        .onAppear {
            loadExistingIfNeeded()
            revalidate()
        }
        .onChange(of: payload) { old, new in
            if ignoreNextChange {
                ignoreNextChange = false
            } else {
                touched.formUnion(Self.changedKeys(old, new))
            }
            revalidate()
        }
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
            FormTextField(title: L10n.tr("field.itemName"), text: loginBinding(\.itemName), error: error("itemName"))
            FormTextField(title: L10n.tr("field.email"), text: loginBinding(\.email), keyboard: .emailAddress, error: error("email"))
            FormTextField(title: L10n.tr("field.username"), text: loginBinding(\.username), error: error("username"))
            passwordField
            InlineFieldError(message: error("password"))
            TOTPEditorField(totp: loginTOTPBinding)
            InlineFieldError(message: error("totp"))
            FormTextField(title: L10n.tr("field.website"), text: loginBinding(\.website), keyboard: .URL, error: error("website"))
            FormTextField(title: L10n.tr("field.note"), text: loginBinding(\.note), error: error("note"))
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
            FormTextField(title: L10n.tr("field.title"), text: noteBinding(\.title), error: error("title"))
            FormTextField(title: L10n.tr("field.content"), text: noteBinding(\.content), error: error("content"))
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
            FormTextField(title: L10n.tr("field.itemName"), text: cardBinding(\.itemName), error: error("itemName"))
            FormTextField(title: L10n.tr("field.accountHolder"), text: cardBinding(\.accountHolder), error: error("accountHolder"))
            FormTextField(title: L10n.tr("field.number"), text: cardNumberBinding, keyboard: .numberPad, error: error("number"))
            // Expiry and security code side by side, as printed on the card; the code (at
            // most 4 digits) gets the narrower box.
            HStack(alignment: .top, spacing: 12) {
                // MM/YY, its own format (not a calendar date).
                FormTextField(
                    title: L10n.tr("field.expiryDate"),
                    text: cardExpiryBinding,
                    placeholder: L10n.tr("field.expiryPlaceholder"),
                    keyboard: .numberPad,
                    error: error("expiryDate")
                )
                .frame(maxWidth: .infinity)
                FormTextField(title: L10n.tr("field.securityCode"), text: cardSecurityCodeBinding, isSecure: true, keyboard: .numberPad, error: error("securityCode"))
                    .frame(minWidth: 96, maxWidth: 150)
            }
            FormTextField(title: L10n.tr("field.note"), text: cardBinding(\.note), error: error("note"))
            NoteColorPicker(color: cardColorBinding)
            // No NFC here: iOS doesn't let third-party apps read payment cards over NFC.
        }
    }

    // MARK: - Bank

    private var bankFields: some View {
        Group {
            FormTextField(title: L10n.tr("field.itemName"), text: bankBinding(\.itemName), error: error("itemName"))
            FormTextField(title: L10n.tr("field.accountHolder"), text: bankBinding(\.accountHolder), error: error("accountHolder"))
            FormTextField(title: L10n.tr("field.accountNumber"), text: bankBinding(\.accountNumber), error: error("accountNumber"))
            FormTextField(title: L10n.tr("field.routingNumber"), text: bankBinding(\.routingNumber), error: error("routingNumber"))
            CountryPickerField(countryCode: bankCountryBinding, error: error("countryCode"))
            FormTextField(title: L10n.tr("field.bankName"), text: bankBinding(\.bankName), error: error("bankName"))
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
            FormTextField(title: L10n.tr("field.firstName"), text: nameBinding(\.firstName), error: error("firstName"))
            FormTextField(title: L10n.tr("field.middleName"), text: nameBinding(\.middleName), error: error("middleName"))
            FormTextField(title: L10n.tr("field.lastName"), text: nameBinding(\.lastName), error: error("lastName"))
            FormTextField(title: L10n.tr("field.defaultUsername"), text: nameBinding(\.defaultUsername), error: error("defaultUsername"))
            ValidatedDateField(title: L10n.tr("field.birthDate"), text: nameBinding(\.birthDate), error: error("birthDate"))
            FormTextField(title: L10n.tr("field.birthPlace"), text: nameBinding(\.birthPlace), error: error("birthPlace"))
        }
    }

    // MARK: - Email

    private var emailFields: some View {
        Group {
            FormTextField(title: L10n.tr("field.itemName"), text: emailBinding(\.itemName), error: error("itemName"))
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
            FormTextField(title: L10n.tr("field.email"), text: emailBinding(\.email), keyboard: .emailAddress, error: error("email"))
        }
    }

    // MARK: - Phone

    private var phoneFields: some View {
        Group {
            FormTextField(title: L10n.tr("field.itemName"), text: phoneBinding(\.itemName), error: error("itemName"))

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
                                .stroke(error("countryCode") == nil ? Theme.stroke : Theme.danger, lineWidth: 1)
                        )
                    InlineFieldError(message: error("countryCode"))
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
                                .stroke(error("number") == nil ? Theme.stroke : Theme.danger, lineWidth: 1)
                        )
                }
                InlineFieldError(message: error("number"))
            }
        }
    }

    // MARK: - Address

    private var addressFields: some View {
        Group {
            FormTextField(title: L10n.tr("field.itemName"), text: addressBinding(\.itemName), error: error("itemName"))
            FormTextField(title: L10n.tr("field.address"), text: addressBinding(\.address), error: error("address"))
            FormTextField(title: L10n.tr("field.city"), text: addressBinding(\.city), error: error("city"))
            FormTextField(title: L10n.tr("field.zipCode"), text: addressBinding(\.zipCode), error: error("zipCode"))
            CountryPickerField(countryCode: addressCountryBinding, error: error("countryCode"))
            FormTextField(title: L10n.tr("field.recipient"), text: addressBinding(\.recipient), error: error("recipient"))
            FormTextField(title: L10n.tr("field.building"), text: addressBinding(\.building), error: error("building"))
            FormTextField(title: L10n.tr("field.floor"), text: addressBinding(\.floor), error: error("floor"))
            FormTextField(title: L10n.tr("field.apartment"), text: addressBinding(\.apartment), error: error("apartment"))
            FormTextField(title: L10n.tr("field.doorCode"), text: addressBinding(\.doorCode), error: error("doorCode"))
            AddressPhonePicker(phoneItemId: addressPhoneBinding)
        }
    }

    // MARK: - Company / Website / ID

    private var companyFields: some View {
        Group {
            FormTextField(title: L10n.tr("field.companyName"), text: companyBinding(\.companyName), error: error("companyName"))
            FormTextField(title: L10n.tr("field.jobTitle"), text: companyBinding(\.jobTitle), error: error("jobTitle"))
        }
    }

    private var websiteFields: some View {
        Group {
            FormTextField(title: L10n.tr("field.itemName"), text: websiteBinding(\.itemName), error: error("itemName"))
            FormTextField(title: L10n.tr("field.websiteURL"), text: websiteBinding(\.websiteURL), keyboard: .URL, error: error("websiteURL"))
        }
    }

    private var idFields: some View {
        Group {
            FormTextField(title: L10n.tr("field.name"), text: idBinding(\.name), error: error("name"))
            CountryPickerField(countryCode: idCountryBinding, error: error("countryCode"))
            FormTextField(title: L10n.tr("field.number"), text: idBinding(\.number), error: error("number"))
            ValidatedDateField(title: L10n.tr("field.issueDate"), text: idBinding(\.issueDate), error: error("issueDate"))
            if case .passport = payload {
                FormTextField(title: L10n.tr("field.issuePlace"), text: idBinding(\.issuePlace), error: error("issuePlace"))
            }
            ValidatedDateField(title: L10n.tr("field.expiryDate"), text: idBinding(\.expiryDate), error: error("expiryDate"))
            if let kind = nfcDocumentKind {
                nfcScanButton(kind)
            }
        }
    }

    /// ID cards and passports carry an ICAO chip; driving licences don't.
    private var nfcDocumentKind: NFCDocumentKind? {
        switch payload {
        case .idCard: return .idCard
        case .passport: return .passport
        default: return nil
        }
    }

    private func nfcScanButton(_ kind: NFCDocumentKind) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Button {
                showNFCScan = true
            } label: {
                Label(L10n.tr("vault.scanNFC"), systemImage: "wave.3.right")
                    .font(.qasa(15, weight: .semibold))
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
            .buttonStyle(.plain)
            Text(L10n.tr("vault.nfcHint"))
                .font(.qasa(12))
                .foregroundColor(Theme.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .sheet(isPresented: $showNFCScan) {
            NFCDocumentScanSheet(
                kind: kind,
                documentNumber: idBinding(\.number).wrappedValue,
                expiryDate: idBinding(\.expiryDate).wrappedValue,
                onRead: applyNFCScan
            )
            .appLocalization()
            .presentationDetents([.large])
            .presentationDragIndicator(.visible)
            .presentationCornerRadius(20)
        }
    }

    /// Fills the ID fields from the chip in one change.
    private func applyNFCScan(_ document: MRZDocument) {
        func fill(_ f: inout IDDocumentFields) {
            if !document.fullName.isEmpty { f.name = document.fullName }
            if !document.documentNumber.isEmpty { f.number = document.documentNumber }
            if let country = document.issuingCountryISO2 { f.countryCode = country }
            if let expiry = document.expiryISO { f.expiryDate = expiry }
        }
        switch payload {
        case .idCard(var f):
            fill(&f)
            payload = .idCard(f)
        case .passport(var f):
            fill(&f)
            payload = .passport(f)
        default:
            break
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

    /// Digits grouped for the brand as the user types (`formatCardNumber`).
    private var cardNumberBinding: Binding<String> {
        let base = cardBinding(\.number)
        return Binding(get: { base.wrappedValue }, set: { base.wrappedValue = formatCardNumber(raw: $0) })
    }

    /// `MM/YY` as the user types (`formatCardExpiry`).
    private var cardExpiryBinding: Binding<String> {
        let base = cardBinding(\.expiryDate)
        return Binding(get: { base.wrappedValue }, set: { base.wrappedValue = formatCardExpiry(raw: $0) })
    }

    /// Digits only, at most 4 (the longest security code).
    private var cardSecurityCodeBinding: Binding<String> {
        let base = cardBinding(\.securityCode)
        return Binding(get: { base.wrappedValue }, set: { base.wrappedValue = String($0.filter(\.isNumber).prefix(4)) })
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
            // Opened in stored canonical form (ISO dates, MM/YY…); what still has issues
            // shows inline, and saving requires fixing it.
            let loaded = VaultValidation.normalizedOrSame(try VaultRepository.shared.payload(for: id))
            if loaded != payload {
                ignoreNextChange = true
                payload = loaded
            }
        } catch {
            loadError = error.localizedDescription
        }
    }

    // MARK: - Validation

    private func revalidate() {
        issueMessages = VaultValidation.messages(VaultValidation.issues(VaultValidation.normalizedOrSame(payload)))
    }

    /// The message for a field the user has edited, or for any field after a Save attempt.
    private func error(_ key: String) -> String? {
        guard attemptedSave || touched.contains(key) else { return nil }
        return issueMessages[key]
    }

    /// Payload JSON keys whose values differ (same item type only).
    static func changedKeys(_ old: VaultPayload, _ new: VaultPayload) -> Set<String> {
        func fields(_ p: VaultPayload) -> (variant: String, values: [String: Any])? {
            guard let data = try? VaultValidation.encode(p),
                  let outer = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let first = outer.first,
                  let inner = (first.value as? [String: Any])?["_0"] as? [String: Any] else { return nil }
            return (first.key, inner)
        }
        guard let lhsFields = fields(old), let rhsFields = fields(new), lhsFields.variant == rhsFields.variant else { return [] }
        let a = lhsFields.values, b = rhsFields.values
        var changed = Set<String>()
        for key in Set(a.keys).union(b.keys) {
            let lhs = a[key].map { "\($0)" }
            let rhs = b[key].map { "\($0)" }
            if lhs != rhs { changed.insert(key) }
        }
        return changed
    }

    private func save() {
        VaultRepository.shared.ensureConfigured(context: modelContext)
        revalidate()
        guard issueMessages.isEmpty else {
            attemptedSave = true
            return
        }
        do {
            switch mode {
            case .create:
                _ = try VaultRepository.shared.save(payload: payload)
            case .edit(let id):
                _ = try VaultRepository.shared.save(payload: payload, id: id)
            }
            onDone()
        } catch let refused as VaultValidationError {
            issueMessages = VaultValidation.messages(refused.issues)
            attemptedSave = true
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
