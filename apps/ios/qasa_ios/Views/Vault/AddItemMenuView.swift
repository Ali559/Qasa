import SwiftUI

struct AddItemMenuView: View {
    let tab: VaultFilterTab
    var onSelect: (VaultPayload) -> Void
    var onCancel: () -> Void

    @State private var path: [AddRoute] = []

    enum AddRoute: Hashable {
        case subtypes(VaultItemKind)
    }

    var body: some View {
        NavigationStack(path: $path) {
            List {
                switch tab {
                case .all:
                    ForEach(VaultItemKind.allCases) { kind in
                        if let subtypes = kind.subtypes {
                            Button {
                                path.append(.subtypes(kind))
                            } label: {
                                Label(kind.addTitle, systemImage: kind.systemImage)
                                    .foregroundColor(Theme.textPrimary)
                            }
                        } else {
                            Button {
                                onSelect(defaultPayload(for: kind))
                            } label: {
                                Label(kind.addTitle, systemImage: kind.systemImage)
                                    .foregroundColor(Theme.textPrimary)
                            }
                        }
                    }
                case .login:
                    Button { onSelect(.login(LoginFields())) } label: {
                        Label("Login", systemImage: "person.badge.key.fill")
                            .foregroundColor(Theme.textPrimary)
                    }
                case .secureNote:
                    Button { onSelect(.secureNote(SecureNoteFields())) } label: {
                        Label("Secure Note", systemImage: "note.text")
                            .foregroundColor(Theme.textPrimary)
                    }
                case .payment:
                    subtypeButtons(for: .payment)
                case .personalInfo:
                    subtypeButtons(for: .personalInfo)
                case .idDocument:
                    subtypeButtons(for: .idDocument)
                }
            }
            .scrollContentBackground(.hidden)
            .background(Theme.background)
            .navigationTitle("Add New")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel", action: onCancel)
                }
            }
            .navigationDestination(for: AddRoute.self) { route in
                switch route {
                case .subtypes(let kind):
                    List {
                        subtypeButtons(for: kind)
                    }
                    .scrollContentBackground(.hidden)
                    .background(Theme.background)
                    .navigationTitle(kind.addTitle)
                }
            }
        }
        .preferredColorScheme(.dark)
        .tint(Theme.mint)
    }

    @ViewBuilder
    private func subtypeButtons(for kind: VaultItemKind) -> some View {
        if let subtypes = kind.subtypes {
            ForEach(subtypes) { subtype in
                Button {
                    onSelect(payload(for: subtype))
                } label: {
                    Text(subtype.title)
                        .foregroundColor(Theme.textPrimary)
                }
            }
        }
    }

    private func defaultPayload(for kind: VaultItemKind) -> VaultPayload {
        switch kind {
        case .login: return .login(LoginFields())
        case .secureNote: return .secureNote(SecureNoteFields())
        case .payment: return .card(CardFields())
        case .personalInfo: return .name(NameFields())
        case .idDocument: return .idCard(IDDocumentFields())
        }
    }

    private func payload(for subtype: VaultSubtype) -> VaultPayload {
        switch subtype {
        case .payment(.creditDebitCard): return .card(CardFields())
        case .payment(.bankAccount): return .bankAccount(BankAccountFields())
        case .personal(.name): return .name(NameFields())
        case .personal(.email): return .personalEmail(PersonalEmailFields())
        case .personal(.phone): return .phone(PhoneFields())
        case .personal(.address): return .address(AddressFields())
        case .personal(.company): return .company(CompanyFields())
        case .personal(.website): return .website(WebsiteFields())
        case .id(.idCard): return .idCard(IDDocumentFields())
        case .id(.passport): return .passport(IDDocumentFields())
        case .id(.driverLicense): return .driverLicense(IDDocumentFields())
        }
    }
}
