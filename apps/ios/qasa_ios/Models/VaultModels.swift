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
    var encryptedEnvelope: Data
    var encryptedPayload: Data
    var schemaVersion: Int
    var legacyPayloadData: Data?
    var updatedAt: Date
    var createdAt: Date
    var lastUsedAt: Date?
    var collectionId: UUID?

    init(kind: VaultItemKind) {
        self.id = UUID()
        self.kindRaw = kind.rawValue
        self.encryptedEnvelope = Data()
        self.encryptedPayload = Data()
        self.schemaVersion = 1
        self.legacyPayloadData = nil
        self.updatedAt = Date()
        self.createdAt = Date()
        self.lastUsedAt = nil
        self.collectionId = nil
    }

    var kind: VaultItemKind {
        VaultItemKind(rawValue: kindRaw) ?? .login
    }
}

@Model
final class VaultSearchToken {
    var itemId: UUID
    var tokenHash: String

    init(itemId: UUID, tokenHash: String) {
        self.itemId = itemId
        self.tokenHash = tokenHash
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

@Model
final class MonitoredEmail {
    var id: UUID
    var email: String
    var statusRaw: String
    var createdAt: Date

    init(email: String, status: MonitorStatus = .secure) {
        self.id = UUID()
        self.email = email
        self.statusRaw = status.rawValue
        self.createdAt = Date()
    }

    var status: MonitorStatus {
        get { MonitorStatus(rawValue: statusRaw) ?? .secure }
        set { statusRaw = newValue.rawValue }
    }
}

enum MonitorStatus: String, Codable, CaseIterable {
    case secure
    case atRisk

    var title: String {
        switch self {
        case .secure: return L10n.tr("breach.secure")
        case .atRisk: return L10n.tr("breach.atRisk")
        }
    }
}

enum VaultSchema {
    static let models: [any PersistentModel.Type] = [
        VaultItemRecord.self,
        VaultSearchToken.self,
        VaultCollection.self,
        AppNotification.self,
        MonitoredEmail.self
    ]
}

enum VaultSeedData {
    static func seedIfNeeded(context: ModelContext) {
        guard VaultSession.shared.isUnlocked else { return }
        let descriptor = FetchDescriptor<VaultItemRecord>()
        let count = (try? context.fetchCount(descriptor)) ?? 0
        guard count == 0 else { return }

        let work = VaultCollection(name: "Work")
        let personal = VaultCollection(name: "Personal")
        context.insert(work)
        context.insert(personal)
        try? context.save()

        let samples: [VaultPayload] = [
            .login(LoginFields(itemName: "GitHub", email: "dev@qasa.io", username: "dev", password: "gh-secret-1", website: "https://github.com", note: "", collectionId: work.id)),
            .login(LoginFields(itemName: "Google", email: "ops@qasa.io", username: "ops", password: "google-pass", website: "https://accounts.google.com", collectionId: personal.id)),
            .login(LoginFields(itemName: "ProtonMail Main", email: "admin@qasa.io", username: "admin", password: "proton-x", website: "https://mail.proton.me")),
            .login(LoginFields(itemName: "AWS Root Account", email: "ops@qasa.io", username: "root", password: "aws-weak", website: "https://aws.amazon.com")),
            .login(LoginFields(itemName: "Apple ID", email: "me@icloud.com", username: "me", password: "apple-id", website: "https://appleid.apple.com")),
            .secureNote(SecureNoteFields(title: "Wifi Password", content: "Home-Net // qasa-wifi-2026", color: .mint, protectItem: false)),
            .card(CardFields(itemName: "FIB Corporate Card", accountHolder: "Jane Doe", number: "4111111111114921", securityCode: "123", expiryDate: "08/28", note: "Corp", color: .blue)),
        ]

        for payload in samples {
            _ = try? VaultRepository.shared.save(payload: payload)
        }

        context.insert(AppNotification(
            title: "Vault ready",
            body: "Your encrypted vault is set up. Link devices in Settings to sync entitlements."
        ))
        try? context.save()
    }
}
