import Foundation
import SwiftData

@Model
final class VaultCollection {
    var id: UUID
    var name: String
    var createdAt: Date

    init(name: String) {
        self.id = UUID()
        self.name = name
        self.createdAt = Date()
    }
}

@Model
final class VaultItemRecord {
    var id: UUID
    var kindRaw: String
    var displayName: String
    var sortKey: String
    var updatedAt: Date
    var createdAt: Date
    var collectionId: UUID?
    var payloadData: Data

    init(payload: VaultPayload, collectionId: UUID? = nil) {
        self.id = UUID()
        self.kindRaw = payload.kind.rawValue
        let name = payload.displayName.isEmpty ? "Untitled" : payload.displayName
        self.displayName = name
        self.sortKey = name
        self.updatedAt = Date()
        self.createdAt = Date()
        self.collectionId = collectionId
        self.payloadData = (try? JSONEncoder().encode(payload)) ?? Data()
    }

    var kind: VaultItemKind {
        VaultItemKind(rawValue: kindRaw) ?? .login
    }

    var payload: VaultPayload {
        get {
            (try? JSONDecoder().decode(VaultPayload.self, from: payloadData))
                ?? .login(LoginFields(itemName: displayName))
        }
        set {
            payloadData = (try? JSONEncoder().encode(newValue)) ?? payloadData
            kindRaw = newValue.kind.rawValue
            let name = newValue.displayName.isEmpty ? "Untitled" : newValue.displayName
            displayName = name
            sortKey = name
            updatedAt = Date()
            if case .login(let f) = newValue { collectionId = f.collectionId }
            if case .secureNote(let f) = newValue { collectionId = f.collectionId }
        }
    }

    var letterSection: String {
        let trimmed = sortKey.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let first = trimmed.first else { return "#" }
        let upper = String(first).uppercased()
        if upper.rangeOfCharacter(from: .letters) != nil {
            return upper
        }
        return "#"
    }
}

@Model
final class AppNotification {
    var id: UUID
    var title: String
    var body: String
    var createdAt: Date
    var isRead: Bool

    init(title: String, body: String, isRead: Bool = false) {
        self.id = UUID()
        self.title = title
        self.body = body
        self.createdAt = Date()
        self.isRead = isRead
    }
}

enum VaultSchema {
    static let models: [any PersistentModel.Type] = [
        VaultItemRecord.self,
        VaultCollection.self,
        AppNotification.self
    ]
}

enum VaultSeedData {
    static func seedIfNeeded(context: ModelContext) {
        let descriptor = FetchDescriptor<VaultItemRecord>()
        let count = (try? context.fetchCount(descriptor)) ?? 0
        guard count == 0 else { return }

        let work = VaultCollection(name: "Work")
        let personal = VaultCollection(name: "Personal")
        context.insert(work)
        context.insert(personal)

        let samples: [VaultPayload] = [
            .login(LoginFields(itemName: "GitHub", email: "dev@kaso.io", username: "dev", password: "gh-secret-1", website: "https://github.com", note: "", collectionId: work.id)),
            .login(LoginFields(itemName: "Google", email: "ops@kaso.io", username: "ops", password: "google-pass", website: "https://accounts.google.com", collectionId: personal.id)),
            .login(LoginFields(itemName: "ProtonMail Main", email: "admin@kaso.io", username: "admin", password: "proton-x", website: "https://mail.proton.me")),
            .login(LoginFields(itemName: "AWS Root Account", email: "ops@kaso.io", username: "root", password: "aws-weak", website: "https://aws.amazon.com")),
            .login(LoginFields(itemName: "Apple ID", email: "me@icloud.com", username: "me", password: "apple-id", website: "https://appleid.apple.com")),
            .login(LoginFields(itemName: "Notion", email: "dev@kaso.io", username: "dev", password: "notion1", website: "https://notion.so")),
            .login(LoginFields(itemName: "Slack Workspace", email: "dev@kaso.io", username: "dev", password: "slack1", website: "https://slack.com")),
            .login(LoginFields(itemName: "Figma", email: "design@kaso.io", username: "design", password: "figma1", website: "https://figma.com")),
            .login(LoginFields(itemName: "Linear", email: "dev@kaso.io", username: "dev", password: "linear1", website: "https://linear.app")),
            .login(LoginFields(itemName: "Vercel", email: "dev@kaso.io", username: "dev", password: "vercel1", website: "https://vercel.com")),
            .login(LoginFields(itemName: "Stripe Dashboard", email: "ops@kaso.io", username: "ops", password: "stripe1", website: "https://dashboard.stripe.com")),
            .login(LoginFields(itemName: "Zoom", email: "me@kaso.io", username: "me", password: "zoom1", website: "https://zoom.us")),
            .secureNote(SecureNoteFields(title: "Wifi Password", content: "Home-Net // kaso-wifi-2026", color: .mint, protectItem: false)),
            .secureNote(SecureNoteFields(title: "Recovery Codes", content: "A1B2-C3D4-E5F6", color: .gold, protectItem: true)),
            .card(CardFields(itemName: "FIB Corporate Card", accountHolder: "Jane Doe", number: "4111111111114921", securityCode: "123", expiryDate: "08/28", note: "Corp", color: .blue)),
            .card(CardFields(itemName: "Personal Visa", accountHolder: "Jane Doe", number: "4000000000000002", securityCode: "456", expiryDate: "01/27", color: .purple)),
            .bankAccount(BankAccountFields(itemName: "FIB Checking", accountHolder: "Jane Doe", accountNumber: "IQ98FIB0123456789", routingNumber: "001122", countryCode: "IQ", bankName: "First Iraqi Bank")),
            .name(NameFields(type: .ms, firstName: "Jane", lastName: "Doe", middleName: "", defaultUsername: "jane.doe", birthDate: "1994-05-12", birthPlace: "Baghdad")),
            .personalEmail(PersonalEmailFields(itemName: "Work Email", type: .work, email: "jane.doe@kaso.io")),
            .phone(PhoneFields(itemName: "Mobile", type: .cell, countryCode: "IQ", number: "7501234567")),
            .company(CompanyFields(companyName: "Kaso Security", jobTitle: "Engineer")),
            .website(WebsiteFields(itemName: "Personal Site", websiteURL: "https://janedoe.dev")),
            .idCard(IDDocumentFields(name: "Jane Doe", countryCode: "IQ", number: "A1234567", issueDate: "2020-01-01", expiryDate: "2030-01-01")),
            .passport(IDDocumentFields(name: "Jane Doe", countryCode: "IQ", number: "P998877", issueDate: "2019-06-01", expiryDate: "2029-06-01", issuePlace: "Baghdad")),
            .driverLicense(IDDocumentFields(name: "Jane Doe", countryCode: "IQ", number: "DL445566", issueDate: "2021-03-01", expiryDate: "2027-03-01"))
        ]

        for (index, payload) in samples.enumerated() {
            let item = VaultItemRecord(payload: payload)
            // Stagger updatedAt so Most Recent ordering is visible
            item.updatedAt = Date().addingTimeInterval(TimeInterval(-index * 3600))
            context.insert(item)
        }

        context.insert(AppNotification(
            title: "Weak password detected",
            body: "AWS Root Account uses a weak password. Rotate it soon."
        ))
        context.insert(AppNotification(
            title: "New device signed in",
            body: "A login was recorded from Baghdad, IQ.",
            isRead: true
        ))
        context.insert(AppNotification(
            title: "Breach watch",
            body: "1 monitored address has an unresolved breach."
        ))

        try? context.save()
    }
}
