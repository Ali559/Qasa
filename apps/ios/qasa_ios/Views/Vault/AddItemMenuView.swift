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
            ScrollView {
                SettingsSection {
                    switch tab {
                    case .all:
                        ForEach(Array(VaultItemKind.allCases.enumerated()), id: \.element.id) { index, kind in
                            if index > 0 { RowDivider() }
                            if kind.subtypes != nil {
                                row(kind.addTitle, icon: kind.icon, chevron: true) {
                                    path.append(.subtypes(kind))
                                }
                            } else {
                                row(kind.addTitle, icon: kind.icon) {
                                    onSelect(defaultPayload(for: kind))
                                }
                            }
                        }
                    case .login:
                        row(
                            L10n.tr("vault.type.login", locale: AppPreferences.shared.locale),
                            icon: .login
                        ) { onSelect(.login(LoginFields())) }
                    case .secureNote:
                        row(
                            L10n.tr("vault.type.secureNote", locale: AppPreferences.shared.locale),
                            icon: .note
                        ) { onSelect(.secureNote(SecureNoteFields())) }
                    case .payment:
                        subtypeButtons(for: .payment)
                    case .personalInfo:
                        subtypeButtons(for: .personalInfo)
                    case .idDocument:
                        subtypeButtons(for: .idDocument)
                    }
                }
                .padding(Theme.screenPadding)
            }
            .background(Theme.background.ignoresSafeArea())
            .sheetHeader(L10n.tr("vault.addNew", locale: AppPreferences.shared.locale))
            .navigationDestination(for: AddRoute.self) { route in
                switch route {
                case .subtypes(let kind):
                    ScrollView {
                        SettingsSection {
                            subtypeButtons(for: kind)
                        }
                        .padding(Theme.screenPadding)
                    }
                    .background(Theme.background.ignoresSafeArea())
                    .navigationTitle(kind.addTitle)
                }
            }
        }
        .tint(Theme.accent)
    }

    private func row(_ title: String, icon: QasaIcon, chevron: Bool = false, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            SettingsRow(icon: icon, title: title) {
                if chevron { RowChevron() }
            }
        }
        .buttonStyle(.qasaRow)
    }

    @ViewBuilder
    private func subtypeButtons(for kind: VaultItemKind) -> some View {
        if let subtypes = kind.subtypes {
            ForEach(Array(subtypes.enumerated()), id: \.element.id) { index, subtype in
                if index > 0 { RowDivider() }
                row(subtype.title, icon: payload(for: subtype).icon) {
                    onSelect(payload(for: subtype))
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
