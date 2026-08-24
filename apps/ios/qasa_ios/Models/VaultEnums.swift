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
        switch self {
        case .login: return "Logins"
        case .secureNote: return "Secure Notes"
        case .payment: return "Payments"
        case .personalInfo: return "Personal Info"
        case .idDocument: return "IDs"
        }
    }

    var addTitle: String {
        switch self {
        case .login: return "Login"
        case .secureNote: return "Secure Note"
        case .payment: return "Payment"
        case .personalInfo: return "Personal Information"
        case .idDocument: return "ID"
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

    var title: String {
        switch self {
        case .all: return "All"
        case .login: return VaultItemKind.login.tabTitle
        case .secureNote: return VaultItemKind.secureNote.tabTitle
        case .payment: return VaultItemKind.payment.tabTitle
        case .personalInfo: return VaultItemKind.personalInfo.tabTitle
        case .idDocument: return VaultItemKind.idDocument.tabTitle
        }
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
        switch self {
        case .creditDebitCard: return "Credit / Debit Card"
        case .bankAccount: return "Bank Account"
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
        switch self {
        case .name: return "Name"
        case .email: return "Email"
        case .phone: return "Phone Number"
        case .address: return "Address"
        case .company: return "Company"
        case .website: return "Website"
        }
    }
}

enum IDSubtype: String, Codable, CaseIterable {
    case idCard
    case passport
    case driverLicense

    var title: String {
        switch self {
        case .idCard: return "ID Card"
        case .passport: return "Passport"
        case .driverLicense: return "Driver License"
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
}

enum EmailKind: String, Codable, CaseIterable {
    case personal = "Personal"
    case work = "Work"
}

enum PhoneKind: String, Codable, CaseIterable {
    case cell = "Cell"
    case work = "Work"
    case workCell = "Work Cell"
    case workFax = "Work Fax"
    case fax = "Fax"
    case homePhone = "Home Phone"
    case other = "Other"
}

enum NoteColor: String, Codable, CaseIterable {
    case mint, gold, blue, purple, red, gray

    var color: Color {
        switch self {
        case .mint: return Theme.mint
        case .gold: return Theme.gold
        case .blue: return Color(hex: "3B82F6")
        case .purple: return Color(hex: "A855F7")
        case .red: return Theme.danger
        case .gray: return Theme.textSecondary
        }
    }
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
                CopyAction(label: "Copy Email", value: f.email, systemImage: "doc.on.doc"),
                CopyAction(label: "Copy Username", value: f.username, systemImage: "doc.on.doc"),
                CopyAction(label: "Copy Password", value: f.password, systemImage: "doc.on.doc"),
                CopyAction(label: "Copy Website", value: f.website, systemImage: "doc.on.doc")
            ].filter { !$0.value.isEmpty }
        case .secureNote(let f):
            guard !f.content.isEmpty else { return [] }
            return [CopyAction(label: "Copy Content", value: f.content, systemImage: "doc.on.doc")]
        case .card(let f):
            return [
                CopyAction(label: "Copy Number", value: f.number, systemImage: "doc.on.doc"),
                CopyAction(label: "Copy Account Holder", value: f.accountHolder, systemImage: "doc.on.doc"),
                CopyAction(label: "Copy Expiry", value: f.expiryDate, systemImage: "doc.on.doc"),
                CopyAction(label: "Copy Security Code", value: f.securityCode, systemImage: "doc.on.doc")
            ].filter { !$0.value.isEmpty }
        case .bankAccount(let f):
            return [
                CopyAction(label: "Copy Account Number", value: f.accountNumber, systemImage: "doc.on.doc"),
                CopyAction(label: "Copy Routing Number", value: f.routingNumber, systemImage: "doc.on.doc"),
                CopyAction(label: "Copy Account Holder", value: f.accountHolder, systemImage: "doc.on.doc")
            ].filter { !$0.value.isEmpty }
        case .name(let f):
            let full = [f.firstName, f.middleName, f.lastName].filter { !$0.isEmpty }.joined(separator: " ")
            return [
                CopyAction(label: "Copy Name", value: full, systemImage: "doc.on.doc"),
                CopyAction(label: "Copy Username", value: f.defaultUsername, systemImage: "doc.on.doc")
            ].filter { !$0.value.isEmpty }
        case .personalEmail(let f):
            return [CopyAction(label: "Copy Email", value: f.email, systemImage: "doc.on.doc")].filter { !$0.value.isEmpty }
        case .phone(let f):
            guard !f.number.isEmpty else { return [] }
            let full = "\(Country.dialCode(for: f.countryCode))\(f.number)"
            return [CopyAction(label: "Copy Phone Number", value: full, systemImage: "doc.on.doc")]
        case .address(let f):
            return [
                CopyAction(label: "Copy Address", value: f.address, systemImage: "doc.on.doc"),
                CopyAction(label: "Copy City", value: f.city, systemImage: "doc.on.doc"),
                CopyAction(label: "Copy Zip Code", value: f.zipCode, systemImage: "doc.on.doc")
            ].filter { !$0.value.isEmpty }
        case .company(let f):
            return [
                CopyAction(label: "Copy Company", value: f.companyName, systemImage: "doc.on.doc"),
                CopyAction(label: "Copy Job Title", value: f.jobTitle, systemImage: "doc.on.doc")
            ].filter { !$0.value.isEmpty }
        case .website(let f):
            return [CopyAction(label: "Copy Website", value: f.websiteURL, systemImage: "doc.on.doc")].filter { !$0.value.isEmpty }
        case .idCard(let f), .passport(let f), .driverLicense(let f):
            return [
                CopyAction(label: "Copy Name", value: f.name, systemImage: "doc.on.doc"),
                CopyAction(label: "Copy Number", value: f.number, systemImage: "doc.on.doc")
            ].filter { !$0.value.isEmpty }
        }
    }

    var isProtectedNote: Bool {
        if case .secureNote(let f) = self { return f.protectItem }
        return false
    }
}
