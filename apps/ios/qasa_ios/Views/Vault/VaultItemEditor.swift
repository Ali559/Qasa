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
        let base: String
        switch payload {
        case .login: base = "Login"
        case .secureNote: base = "Secure Note"
        case .card: base = "Card"
        case .bankAccount: base = "Bank Account"
        case .name: base = "Name"
        case .personalEmail: base = "Email"
        case .phone: base = "Phone Number"
        case .address: base = "Address"
        case .company: base = "Company"
        case .website: base = "Website"
        case .idCard: base = "ID Card"
        case .passport: base = "Passport"
        case .driverLicense: base = "Driver License"
        }
        return isEditing ? "Edit \(base)" : "New \(base)"
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
        .onAppear(perform: loadExistingIfNeeded)
        .alert("Could Not Save", isPresented: Binding(
            get: { saveError != nil },
            set: { if !$0 { saveError = nil } }
        )) {
            Button("OK", role: .cancel) { saveError = nil }
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
            FormTextField(title: "Item Name", text: loginBinding(\.itemName))
            FormTextField(title: "Email", text: loginBinding(\.email))
            FormTextField(title: "Username", text: loginBinding(\.username))
            passwordField
            FormTextField(title: "Website", text: loginBinding(\.website))
            FormTextField(title: "Note", text: loginBinding(\.note))
            CollectionPickerField(collectionId: loginCollectionBinding)
        }
    }

    private var passwordField: some View {
        VStack(alignment: .leading, spacing: 8) {
            FormFieldLabel(text: "Password")
            HStack(spacing: 10) {
                Group {
                    if passwordVisible {
                        TextField("Password", text: loginBinding(\.password))
                    } else {
                        SecureField("Password", text: loginBinding(\.password))
                    }
                }
                .foregroundColor(Theme.textPrimary)
                .tint(Theme.mint)

                Button {
                    showPasswordGenerator = true
                } label: {
                    Image(systemName: "wand.and.stars")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(Theme.mint)
                        .frame(width: 28, height: 28)
                }
                .accessibilityLabel("Generate password")

                Button {
                    passwordVisible.toggle()
                } label: {
                    Image(systemName: passwordVisible ? "eye.slash" : "eye")
                        .font(.system(size: 16, weight: .medium))
                        .foregroundColor(Theme.textSecondary)
                        .frame(width: 28, height: 28)
                }
                .accessibilityLabel(passwordVisible ? "Hide password" : "Show password")
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
            FormTextField(title: "Title", text: noteBinding(\.title))
            FormTextField(title: "Content", text: noteBinding(\.content))
            NoteColorPicker(color: noteColorBinding)
            Toggle(isOn: noteProtectBinding) {
                Text("Protect Item")
                    .foregroundColor(Theme.textPrimary)
            }
            .tint(Theme.mint)
            CollectionPickerField(collectionId: noteCollectionBinding)
        }
    }

    // MARK: - Card

    private var cardFields: some View {
        Group {
            FormTextField(title: "Item Name", text: cardBinding(\.itemName))
            FormTextField(title: "Account Holder", text: cardBinding(\.accountHolder))
            FormTextField(title: "Number", text: cardBinding(\.number))
            FormTextField(title: "Security Code", text: cardBinding(\.securityCode), isSecure: true)
            FormTextField(title: "Expiry Date", text: cardBinding(\.expiryDate))
            FormTextField(title: "Note", text: cardBinding(\.note))
            NoteColorPicker(color: cardColorBinding)
            Button {
                // NFC stub — gated in UI only
            } label: {
                Label("Scan with NFC", systemImage: "wave.3.right")
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(Theme.cardBackground)
                    .foregroundColor(Theme.mint)
                    .cornerRadius(Theme.cornerRadius)
                    .overlay(
                        RoundedRectangle(cornerRadius: Theme.cornerRadius)
                            .stroke(Theme.mint.opacity(0.4), lineWidth: 1)
                    )
            }
            Text("NFC scanning requires a compatible device and card.")
                .font(.caption)
                .foregroundColor(Theme.textSecondary)
        }
    }

    // MARK: - Bank

    private var bankFields: some View {
        Group {
            FormTextField(title: "Item Name", text: bankBinding(\.itemName))
            FormTextField(title: "Account Holder", text: bankBinding(\.accountHolder))
            FormTextField(title: "Account Number", text: bankBinding(\.accountNumber))
            FormTextField(title: "Routing Number", text: bankBinding(\.routingNumber))
            CountryPickerField(countryCode: bankCountryBinding)
            FormTextField(title: "Bank Name", text: bankBinding(\.bankName))
        }
    }

    // MARK: - Name

    private var nameFields: some View {
        Group {
            VStack(alignment: .leading, spacing: 8) {
                FormFieldLabel(text: "Type")
                Picker("Type", selection: nameHonorificBinding) {
                    ForEach(Honorific.allCases, id: \.self) { Text($0.rawValue).tag($0) }
                }
                .pickerStyle(.menu)
                .tint(Theme.mint)
                .padding(12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Theme.cardBackground)
                .cornerRadius(Theme.cornerRadius)
            }
            FormTextField(title: "First Name", text: nameBinding(\.firstName))
            FormTextField(title: "Middle Name", text: nameBinding(\.middleName))
            FormTextField(title: "Last Name", text: nameBinding(\.lastName))
            FormTextField(title: "Default Username", text: nameBinding(\.defaultUsername))
            FormTextField(title: "Birth Date", text: nameBinding(\.birthDate))
            FormTextField(title: "Birth Place", text: nameBinding(\.birthPlace))
        }
    }

    // MARK: - Email

    private var emailFields: some View {
        Group {
            FormTextField(title: "Item Name", text: emailBinding(\.itemName))
            VStack(alignment: .leading, spacing: 8) {
                FormFieldLabel(text: "Type")
                Picker("Type", selection: emailKindBinding) {
                    ForEach(EmailKind.allCases, id: \.self) { Text($0.rawValue).tag($0) }
                }
                .tint(Theme.mint)
                .padding(12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Theme.cardBackground)
                .cornerRadius(Theme.cornerRadius)
            }
            FormTextField(title: "Email", text: emailBinding(\.email))
        }
    }

    // MARK: - Phone

    private var phoneFields: some View {
        Group {
            FormTextField(title: "Item Name", text: phoneBinding(\.itemName))

            HStack(alignment: .top, spacing: 12) {
                VStack(alignment: .leading, spacing: 8) {
                    FormFieldLabel(text: "Type")
                    Picker("Type", selection: phoneKindBinding) {
                        ForEach(PhoneKind.allCases, id: \.self) { Text($0.rawValue).tag($0) }
                    }
                    .labelsHidden()
                    .tint(Theme.mint)
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
                    FormFieldLabel(text: "Country")
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
                FormFieldLabel(text: "Phone Number")
                HStack(alignment: .center, spacing: 8) {
                    Text(Country.dialCode(for: phoneCountryBinding.wrappedValue))
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(Theme.mint)
                        .frame(minWidth: 64)
                        .frame(height: 50)
                        .padding(.horizontal, 12)
                        .background(Theme.cardBackground)
                        .cornerRadius(Theme.cornerRadius)
                        .overlay(
                            RoundedRectangle(cornerRadius: Theme.cornerRadius)
                                .stroke(Theme.stroke, lineWidth: 1)
                        )

                    TextField("Phone Number", text: phoneBinding(\.number))
                        .keyboardType(.phonePad)
                        .foregroundColor(Theme.textPrimary)
                        .tint(Theme.mint)
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
            FormTextField(title: "Item Name", text: addressBinding(\.itemName))
            FormTextField(title: "Address", text: addressBinding(\.address))
            FormTextField(title: "City", text: addressBinding(\.city))
            FormTextField(title: "Zip Code", text: addressBinding(\.zipCode))
            CountryPickerField(countryCode: addressCountryBinding)
            FormTextField(title: "Recipient", text: addressBinding(\.recipient))
            FormTextField(title: "Building", text: addressBinding(\.building))
            FormTextField(title: "Floor", text: addressBinding(\.floor))
            FormTextField(title: "Apartment", text: addressBinding(\.apartment))
            FormTextField(title: "Door Code", text: addressBinding(\.doorCode))
            AddressPhonePicker(phoneItemId: addressPhoneBinding)
        }
    }

    // MARK: - Company / Website / ID

    private var companyFields: some View {
        Group {
            FormTextField(title: "Company Name", text: companyBinding(\.companyName))
            FormTextField(title: "Job Title", text: companyBinding(\.jobTitle))
        }
    }

    private var websiteFields: some View {
        Group {
            FormTextField(title: "Item Name", text: websiteBinding(\.itemName))
            FormTextField(title: "Website URL", text: websiteBinding(\.websiteURL))
        }
    }

    private var idFields: some View {
        Group {
            FormTextField(title: "Name", text: idBinding(\.name))
            CountryPickerField(countryCode: idCountryBinding)
            FormTextField(title: "Number", text: idBinding(\.number))
            FormTextField(title: "Issue Date", text: idBinding(\.issueDate))
            if case .passport = payload {
                FormTextField(title: "Issue Place", text: idBinding(\.issuePlace))
            }
            FormTextField(title: "Expiry Date", text: idBinding(\.expiryDate))
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
            FormFieldLabel(text: "Phone Number")
            Picker("Phone", selection: $phoneItemId) {
                Text("None").tag(Optional<UUID>.none)
                ForEach(phones) { row in
                    Text(row.displayName).tag(Optional(row.id))
                }
            }
            .tint(Theme.mint)
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
