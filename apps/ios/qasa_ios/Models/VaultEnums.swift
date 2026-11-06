import Foundation
import SwiftUI

// MARK: - Kinds & tabs

enum VaultItemKind: String, Codable, CaseIterable, Identifiable {
    case login
    case secureNote
    case payment
    case personalInfo
    case idDocument

    var id: String { rawValue }

    var tabTitle: String {
        let locale = AppLanguage.current.locale
        switch self {
        case .login: return L10n.tr("vault.kind.login.tab", locale: locale)
        case .secureNote: return L10n.tr("vault.kind.secureNote.tab", locale: locale)
        case .payment: return L10n.tr("vault.kind.payment.tab", locale: locale)
        case .personalInfo: return L10n.tr("vault.kind.personalInfo.tab", locale: locale)
        case .idDocument: return L10n.tr("vault.kind.idDocument.tab", locale: locale)
        }
    }

    var addTitle: String {
        let locale = AppLanguage.current.locale
        switch self {
        case .login: return L10n.tr("vault.kind.login.add", locale: locale)
        case .secureNote: return L10n.tr("vault.kind.secureNote.add", locale: locale)
        case .payment: return L10n.tr("vault.kind.payment.add", locale: locale)
        case .personalInfo: return L10n.tr("vault.kind.personalInfo.add", locale: locale)
        case .idDocument: return L10n.tr("vault.kind.idDocument.add", locale: locale)
        }
    }

    var systemImage: String {
        switch self {
        case .login: return "person.badge.key.fill"
        case .secureNote: return "note.text"
        case .payment: return "creditcard.fill"
        case .personalInfo: return "person.text.rectangle"
        case .idDocument: return "person.text.rectangle.fill"
        }
    }

    var subtypes: [VaultSubtype]? {
        switch self {
        case .login, .secureNote: return nil
        case .payment: return PaymentSubtype.allCases.map { .payment($0) }
        case .personalInfo: return PersonalInfoSubtype.allCases.map { .personal($0) }
        case .idDocument: return IDSubtype.allCases.map { .id($0) }
        }
    }
}

enum VaultFilterTab: String, CaseIterable, Identifiable {
    case all
    case login
    case secureNote
    case payment
    case personalInfo
    case idDocument

    var id: String { rawValue }

    var localizationKey: String {
        switch self {
        case .all: return "vault.tab.all"
        case .login: return "vault.kind.login.tab"
        case .secureNote: return "vault.kind.secureNote.tab"
        case .payment: return "vault.kind.payment.tab"
        case .personalInfo: return "vault.kind.personalInfo.tab"
        case .idDocument: return "vault.kind.idDocument.tab"
        }
    }

    var title: String {
        L10n.tr(localizationKey, locale: AppLanguage.current.locale)
    }

    var kind: VaultItemKind? {
        switch self {
        case .all: return nil
        case .login: return .login
        case .secureNote: return .secureNote
        case .payment: return .payment
        case .personalInfo: return .personalInfo
        case .idDocument: return .idDocument
        }
    }
}

enum VaultSubtype: Hashable, Identifiable {
    case payment(PaymentSubtype)
    case personal(PersonalInfoSubtype)
    case id(IDSubtype)

    var id: String {
        switch self {
        case .payment(let s): return "payment.\(s.rawValue)"
        case .personal(let s): return "personal.\(s.rawValue)"
        case .id(let s): return "id.\(s.rawValue)"
        }
    }

    var title: String {
        switch self {
        case .payment(let s): return s.title
        case .personal(let s): return s.title
        case .id(let s): return s.title
        }
    }
}

enum PaymentSubtype: String, Codable, CaseIterable {
    case creditDebitCard
    case bankAccount

    var title: String {
        let locale = AppLanguage.current.locale
        switch self {
        case .creditDebitCard: return L10n.tr("vault.subtype.card", locale: locale)
        case .bankAccount: return L10n.tr("vault.subtype.bank", locale: locale)
        }
    }
}

enum PersonalInfoSubtype: String, Codable, CaseIterable {
    case name
    case email
    case phone
    case address
    case company
    case website

    var title: String {
        let locale = AppLanguage.current.locale
        switch self {
        case .name: return L10n.tr("vault.subtype.name", locale: locale)
        case .email: return L10n.tr("vault.subtype.email", locale: locale)
        case .phone: return L10n.tr("vault.subtype.phone", locale: locale)
        case .address: return L10n.tr("vault.subtype.address", locale: locale)
        case .company: return L10n.tr("vault.subtype.company", locale: locale)
        case .website: return L10n.tr("vault.subtype.website", locale: locale)
        }
    }
}

enum IDSubtype: String, Codable, CaseIterable {
    case idCard
    case passport
    case driverLicense

    var title: String {
        let locale = AppLanguage.current.locale
        switch self {
        case .idCard: return L10n.tr("vault.subtype.idCard", locale: locale)
        case .passport: return L10n.tr("vault.subtype.passport", locale: locale)
        case .driverLicense: return L10n.tr("vault.subtype.driverLicense", locale: locale)
        }
    }
}

enum Honorific: String, Codable, CaseIterable {
    case mr = "Mr"
    case mrs = "Mrs"
    case ms = "Ms"
    case mx = "Mx"
    case miss = "Miss"
    case none = "None of these"

    var displayName: String {
        switch self {
        case .mr: return L10n.tr("honorific.mr")
        case .mrs: return L10n.tr("honorific.mrs")
        case .ms: return L10n.tr("honorific.ms")
        case .mx: return L10n.tr("honorific.mx")
        case .miss: return L10n.tr("honorific.miss")
        case .none: return L10n.tr("honorific.none")
        }
    }
}

enum EmailKind: String, Codable, CaseIterable {
    case personal = "Personal"
    case work = "Work"

    var displayName: String {
        switch self {
        case .personal: return L10n.tr("emailKind.personal")
        case .work: return L10n.tr("emailKind.work")
        }
    }
}

enum PhoneKind: String, Codable, CaseIterable {
    case cell = "Cell"
    case work = "Work"
    case workCell = "Work Cell"
    case workFax = "Work Fax"
    case fax = "Fax"
    case homePhone = "Home Phone"
    case other = "Other"

    var displayName: String {
        switch self {
        case .cell: return L10n.tr("phoneKind.cell")
        case .work: return L10n.tr("phoneKind.work")
        case .workCell: return L10n.tr("phoneKind.workCell")
        case .workFax: return L10n.tr("phoneKind.workFax")
        case .fax: return L10n.tr("phoneKind.fax")
        case .homePhone: return L10n.tr("phoneKind.homePhone")
        case .other: return L10n.tr("phoneKind.other")
        }
    }
}

enum NoteColor: String, Codable, CaseIterable {
    case mint, gold, blue, purple, red, gray

    var displayName: String {
        L10n.tr("color.\(rawValue)")
    }

    var color: Color {
        switch self {
        case .mint: return Color(hex: "2BB58A")
        case .gold: return Theme.gold
        case .blue: return Color(hex: "3B82F6")
        case .purple: return Color(hex: "A855F7")
        case .red: return Theme.danger
        case .gray: return Theme.textSecondary
        }
    }
}

struct DetailField: Identifiable {
    let label: String
    let value: String
    var isSecret: Bool = false
    var isMultiline: Bool = false

    var id: String { label }
}

// MARK: - Field payloads

struct LoginFields: Codable, Equatable {
    var itemName: String = ""
    var email: String = ""
    var username: String = ""
    var password: String = ""
    var website: String = ""
    var note: String = ""
    var collectionId: UUID?
    /// Normalised `otpauth://totp/...` URI (see `TOTP`). Optional so logins saved before
    /// 2FA codes existed still decode; nil and "" both mean no code. Android: `totp: String = ""`.
    var totp: String?

    var hasTOTP: Bool { !(totp ?? "").isEmpty }
}

struct SecureNoteFields: Codable, Equatable {
    var title: String = ""
    var content: String = ""
    var color: NoteColor = .mint
    var collectionId: UUID?
    var protectItem: Bool = false
}

struct CardFields: Codable, Equatable {
    var itemName: String = ""
    var accountHolder: String = ""
    var number: String = ""
    var securityCode: String = ""
    var expiryDate: String = ""
    var note: String = ""
    var color: NoteColor = .blue
}

struct BankAccountFields: Codable, Equatable {
    var itemName: String = ""
    var accountHolder: String = ""
    var accountNumber: String = ""
    var routingNumber: String = ""
    var countryCode: String = "IQ"
    var bankName: String = ""
}

struct NameFields: Codable, Equatable {
    var type: Honorific = .none
    var firstName: String = ""
    var lastName: String = ""
    var middleName: String = ""
    var defaultUsername: String = ""
    var birthDate: String = ""
    var birthPlace: String = ""
}

struct PersonalEmailFields: Codable, Equatable {
    var itemName: String = ""
    var type: EmailKind = .personal
    var email: String = ""
}

struct PhoneFields: Codable, Equatable {
    var itemName: String = ""
    var type: PhoneKind = .cell
    var countryCode: String = "IQ"
    var number: String = ""
}

struct AddressFields: Codable, Equatable {
    var itemName: String = ""
    var address: String = ""
    var city: String = ""
    var zipCode: String = ""
    var countryCode: String = "IQ"
    var recipient: String = ""
    var building: String = ""
    var floor: String = ""
    var apartment: String = ""
    var doorCode: String = ""
    var phoneItemId: UUID?
}

struct CompanyFields: Codable, Equatable {
    var companyName: String = ""
    var jobTitle: String = ""
}

struct WebsiteFields: Codable, Equatable {
    var itemName: String = ""
    var websiteURL: String = ""
}

struct IDDocumentFields: Codable, Equatable {
    var name: String = ""
    var countryCode: String = "IQ"
    var number: String = ""
    var issueDate: String = ""
    var expiryDate: String = ""
    var issuePlace: String = "" // passport only
}

enum VaultPayload: Codable, Equatable, Sendable {
    case login(LoginFields)
    case secureNote(SecureNoteFields)
    case card(CardFields)
    case bankAccount(BankAccountFields)
    case name(NameFields)
    case personalEmail(PersonalEmailFields)
    case phone(PhoneFields)
    case address(AddressFields)
    case company(CompanyFields)
    case website(WebsiteFields)
    case idCard(IDDocumentFields)
    case passport(IDDocumentFields)
    case driverLicense(IDDocumentFields)

    var displayName: String {
        switch self {
        case .login(let f): return f.itemName
        case .secureNote(let f): return f.title
        case .card(let f): return f.itemName
        case .bankAccount(let f): return f.itemName
        case .name(let f): return [f.firstName, f.lastName].filter { !$0.isEmpty }.joined(separator: " ")
        case .personalEmail(let f): return f.itemName.isEmpty ? f.email : f.itemName
        case .phone(let f): return f.itemName.isEmpty ? f.number : f.itemName
        case .address(let f): return f.itemName
        case .company(let f): return f.companyName
        case .website(let f): return f.itemName
        case .idCard(let f), .passport(let f), .driverLicense(let f): return f.name
        }
    }

    var subtitle: String {
        switch self {
        case .login(let f): return f.email.isEmpty ? f.username : f.email
        case .secureNote(let f): return f.protectItem ? "Protected" : String(f.content.prefix(40))
        case .card(let f):
            let digits = f.number.filter(\.isNumber)
            let last4 = String(digits.suffix(4))
            return last4.isEmpty ? "Card" : "•••• \(last4)"
        case .bankAccount(let f): return f.bankName.isEmpty ? f.accountNumber : f.bankName
        case .name(let f): return f.defaultUsername
        case .personalEmail(let f): return f.email
        case .phone(let f):
            let dial = Country.dialCode(for: f.countryCode)
            return "\(dial) \(f.number)"
        case .address(let f): return [f.city, f.countryCode].filter { !$0.isEmpty }.joined(separator: ", ")
        case .company(let f): return f.jobTitle
        case .website(let f): return f.websiteURL
        case .idCard(let f), .passport(let f), .driverLicense(let f): return f.number
        }
    }

    var websiteURL: String? {
        switch self {
        case .login(let f):
            let w = f.website.trimmingCharacters(in: .whitespacesAndNewlines)
            return w.isEmpty ? nil : w
        case .website(let f):
            let w = f.websiteURL.trimmingCharacters(in: .whitespacesAndNewlines)
            return w.isEmpty ? nil : w
        default: return nil
        }
    }

    var systemImage: String {
        switch self {
        case .login: return "globe"
        case .secureNote: return "note.text"
        case .card: return "creditcard"
        case .bankAccount: return "building.columns"
        case .name: return "person"
        case .personalEmail: return "envelope"
        case .phone: return "phone"
        case .address: return "mappin.and.ellipse"
        case .company: return "briefcase"
        case .website: return "link"
        case .idCard: return "person.text.rectangle"
        case .passport: return "globe.europe.africa"
        case .driverLicense: return "car"
        }
    }

    var kind: VaultItemKind {
        switch self {
        case .login: return .login
        case .secureNote: return .secureNote
        case .card, .bankAccount: return .payment
        case .name, .personalEmail, .phone, .address, .company, .website: return .personalInfo
        case .idCard, .passport, .driverLicense: return .idDocument
        }
    }
}

struct CopyAction: Identifiable {
    let id = UUID()
    let label: String
    let value: String
    let systemImage: String
}

extension VaultPayload {
    var primaryCopy: (label: String, value: String)? {
        switch self {
        case .login(let f): return f.password.isEmpty ? nil : ("Password", f.password)
        case .secureNote(let f):
            if f.protectItem { return ("Title", f.title) }
            return ("Title", f.title)
        case .card(let f): return f.number.isEmpty ? nil : ("Number", f.number)
        case .bankAccount(let f): return f.accountNumber.isEmpty ? nil : ("Account Number", f.accountNumber)
        case .name(let f):
            let n = [f.firstName, f.lastName].filter { !$0.isEmpty }.joined(separator: " ")
            return n.isEmpty ? nil : ("Name", n)
        case .personalEmail(let f): return f.email.isEmpty ? nil : ("Email", f.email)
        case .phone(let f):
            let dial = Country.dialCode(for: f.countryCode)
            let full = "\(dial)\(f.number)"
            return f.number.isEmpty ? nil : ("Phone Number", full)
        case .address(let f): return f.address.isEmpty ? nil : ("Address", f.address)
        case .company(let f): return f.companyName.isEmpty ? nil : ("Company", f.companyName)
        case .website(let f): return f.websiteURL.isEmpty ? nil : ("Website", f.websiteURL)
        case .idCard(let f), .passport(let f), .driverLicense(let f):
            return f.number.isEmpty ? nil : ("Number", f.number)
        }
    }

    var copyActions: [CopyAction] {
        switch self {
        case .login(let f):
            return [
                CopyAction(label: L10n.tr("copy.email"), value: f.email, systemImage: "doc.on.doc"),
                CopyAction(label: L10n.tr("copy.username"), value: f.username, systemImage: "doc.on.doc"),
                CopyAction(label: L10n.tr("copy.password"), value: f.password, systemImage: "doc.on.doc"),
                CopyAction(label: L10n.tr("copy.website"), value: f.website, systemImage: "doc.on.doc")
            ].filter { !$0.value.isEmpty }
        case .secureNote(let f):
            guard !f.content.isEmpty else { return [] }
            return [CopyAction(label: L10n.tr("copy.content"), value: f.content, systemImage: "doc.on.doc")]
        case .card(let f):
            return [
                CopyAction(label: L10n.tr("copy.number"), value: f.number, systemImage: "doc.on.doc"),
                CopyAction(label: L10n.tr("copy.accountHolder"), value: f.accountHolder, systemImage: "doc.on.doc"),
                CopyAction(label: L10n.tr("copy.expiry"), value: f.expiryDate, systemImage: "doc.on.doc"),
                CopyAction(label: L10n.tr("copy.securityCode"), value: f.securityCode, systemImage: "doc.on.doc")
            ].filter { !$0.value.isEmpty }
        case .bankAccount(let f):
            return [
                CopyAction(label: L10n.tr("copy.accountNumber"), value: f.accountNumber, systemImage: "doc.on.doc"),
                CopyAction(label: L10n.tr("copy.routingNumber"), value: f.routingNumber, systemImage: "doc.on.doc"),
                CopyAction(label: L10n.tr("copy.accountHolder"), value: f.accountHolder, systemImage: "doc.on.doc")
            ].filter { !$0.value.isEmpty }
        case .name(let f):
            let full = [f.firstName, f.middleName, f.lastName].filter { !$0.isEmpty }.joined(separator: " ")
            return [
                CopyAction(label: L10n.tr("copy.name"), value: full, systemImage: "doc.on.doc"),
                CopyAction(label: L10n.tr("copy.username"), value: f.defaultUsername, systemImage: "doc.on.doc")
            ].filter { !$0.value.isEmpty }
        case .personalEmail(let f):
            return [CopyAction(label: L10n.tr("copy.email"), value: f.email, systemImage: "doc.on.doc")].filter { !$0.value.isEmpty }
        case .phone(let f):
            guard !f.number.isEmpty else { return [] }
            let full = "\(Country.dialCode(for: f.countryCode))\(f.number)"
            return [CopyAction(label: L10n.tr("copy.phone"), value: full, systemImage: "doc.on.doc")]
        case .address(let f):
            return [
                CopyAction(label: L10n.tr("copy.address"), value: f.address, systemImage: "doc.on.doc"),
                CopyAction(label: L10n.tr("copy.city"), value: f.city, systemImage: "doc.on.doc"),
                CopyAction(label: L10n.tr("copy.zip"), value: f.zipCode, systemImage: "doc.on.doc")
            ].filter { !$0.value.isEmpty }
        case .company(let f):
            return [
                CopyAction(label: L10n.tr("copy.company"), value: f.companyName, systemImage: "doc.on.doc"),
                CopyAction(label: L10n.tr("copy.jobTitle"), value: f.jobTitle, systemImage: "doc.on.doc")
            ].filter { !$0.value.isEmpty }
        case .website(let f):
            return [CopyAction(label: L10n.tr("copy.website"), value: f.websiteURL, systemImage: "doc.on.doc")].filter { !$0.value.isEmpty }
        case .idCard(let f), .passport(let f), .driverLicense(let f):
            return [
                CopyAction(label: L10n.tr("copy.name"), value: f.name, systemImage: "doc.on.doc"),
                CopyAction(label: L10n.tr("copy.number"), value: f.number, systemImage: "doc.on.doc")
            ].filter { !$0.value.isEmpty }
        }
    }

    var isProtectedNote: Bool {
        if case .secureNote(let f) = self { return f.protectItem }
        return false
    }

    /// Color used in Organization (notes & cards).
    var organizationColor: NoteColor? {
        switch self {
        case .secureNote(let f): return f.color
        case .card(let f): return f.color
        default: return nil
        }
    }

    /// Collection id from payloads that support it.
    var organizationCollectionId: UUID? {
        switch self {
        case .login(let f): return f.collectionId
        case .secureNote(let f): return f.collectionId
        default: return nil
        }
    }

    var detailFields: [DetailField] {
        switch self {
        case .login(let f):
            return [
                DetailField(label: L10n.tr("field.itemName"), value: f.itemName),
                DetailField(label: L10n.tr("field.email"), value: f.email),
                DetailField(label: L10n.tr("field.username"), value: f.username),
                DetailField(label: L10n.tr("field.password"), value: f.password, isSecret: true),
                DetailField(label: L10n.tr("field.website"), value: f.website),
                DetailField(label: L10n.tr("field.note"), value: f.note, isMultiline: true)
            ].filter { !$0.value.isEmpty }
        case .secureNote(let f):
            return [
                DetailField(label: L10n.tr("field.title"), value: f.title),
                DetailField(label: L10n.tr("field.content"), value: f.content, isSecret: f.protectItem, isMultiline: true),
                DetailField(label: L10n.tr("field.protected"), value: f.protectItem ? L10n.tr("common.yes") : L10n.tr("common.no"))
            ].filter { !$0.value.isEmpty }
        case .card(let f):
            return [
                DetailField(label: L10n.tr("field.itemName"), value: f.itemName),
                DetailField(label: L10n.tr("field.accountHolder"), value: f.accountHolder),
                DetailField(label: L10n.tr("field.number"), value: f.number, isSecret: true),
                DetailField(label: L10n.tr("field.securityCode"), value: f.securityCode, isSecret: true),
                DetailField(label: L10n.tr("field.expiryDate"), value: f.expiryDate),
                DetailField(label: L10n.tr("field.note"), value: f.note, isMultiline: true)
            ].filter { !$0.value.isEmpty }
        case .bankAccount(let f):
            return [
                DetailField(label: L10n.tr("field.itemName"), value: f.itemName),
                DetailField(label: L10n.tr("field.accountHolder"), value: f.accountHolder),
                DetailField(label: L10n.tr("field.accountNumber"), value: f.accountNumber, isSecret: true),
                DetailField(label: L10n.tr("field.routingNumber"), value: f.routingNumber),
                DetailField(label: L10n.tr("field.bankName"), value: f.bankName),
                DetailField(label: L10n.tr("vault.country"), value: Country.name(for: f.countryCode))
            ].filter { !$0.value.isEmpty }
        case .name(let f):
            return [
                DetailField(label: L10n.tr("field.type"), value: f.type == .none ? "" : f.type.displayName),
                DetailField(label: L10n.tr("field.firstName"), value: f.firstName),
                DetailField(label: L10n.tr("field.middleName"), value: f.middleName),
                DetailField(label: L10n.tr("field.lastName"), value: f.lastName),
                DetailField(label: L10n.tr("field.defaultUsername"), value: f.defaultUsername),
                DetailField(label: L10n.tr("field.birthDate"), value: f.birthDate),
                DetailField(label: L10n.tr("field.birthPlace"), value: f.birthPlace)
            ].filter { !$0.value.isEmpty }
        case .personalEmail(let f):
            return [
                DetailField(label: L10n.tr("field.itemName"), value: f.itemName),
                DetailField(label: L10n.tr("field.type"), value: f.type.displayName),
                DetailField(label: L10n.tr("field.email"), value: f.email)
            ].filter { !$0.value.isEmpty }
        case .phone(let f):
            return [
                DetailField(label: L10n.tr("field.itemName"), value: f.itemName),
                DetailField(label: L10n.tr("field.type"), value: f.type.displayName),
                DetailField(label: L10n.tr("vault.country"), value: Country.name(for: f.countryCode)),
                DetailField(label: L10n.tr("field.phoneNumber"), value: "\(Country.dialCode(for: f.countryCode)) \(f.number)")
            ].filter { !$0.value.isEmpty }
        case .address(let f):
            return [
                DetailField(label: L10n.tr("field.itemName"), value: f.itemName),
                DetailField(label: L10n.tr("field.recipient"), value: f.recipient),
                DetailField(label: L10n.tr("field.address"), value: f.address, isMultiline: true),
                DetailField(label: L10n.tr("field.building"), value: f.building),
                DetailField(label: L10n.tr("field.floor"), value: f.floor),
                DetailField(label: L10n.tr("field.apartment"), value: f.apartment),
                DetailField(label: L10n.tr("field.doorCode"), value: f.doorCode),
                DetailField(label: L10n.tr("field.city"), value: f.city),
                DetailField(label: L10n.tr("field.zipCode"), value: f.zipCode),
                DetailField(label: L10n.tr("vault.country"), value: Country.name(for: f.countryCode))
            ].filter { !$0.value.isEmpty }
        case .company(let f):
            return [
                DetailField(label: L10n.tr("field.company"), value: f.companyName),
                DetailField(label: L10n.tr("field.jobTitle"), value: f.jobTitle)
            ].filter { !$0.value.isEmpty }
        case .website(let f):
            return [
                DetailField(label: L10n.tr("field.itemName"), value: f.itemName),
                DetailField(label: L10n.tr("field.website"), value: f.websiteURL)
            ].filter { !$0.value.isEmpty }
        case .idCard(let f), .passport(let f), .driverLicense(let f):
            var fields = [
                DetailField(label: L10n.tr("field.name"), value: f.name),
                DetailField(label: L10n.tr("vault.country"), value: Country.name(for: f.countryCode)),
                DetailField(label: L10n.tr("field.number"), value: f.number),
                DetailField(label: L10n.tr("field.issueDate"), value: f.issueDate),
                DetailField(label: L10n.tr("field.expiryDate"), value: f.expiryDate)
            ]
            if case .passport = self, !f.issuePlace.isEmpty {
                fields.append(DetailField(label: L10n.tr("field.issuePlace"), value: f.issuePlace))
            }
            return fields.filter { !$0.value.isEmpty }
        }
    }
}
