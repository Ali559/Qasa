import Foundation
import SwiftData

@Model
final class VaultCollection {
    var id: UUID
    var name: String
    var createdAt: Date
    /// Last rename (or create). `nil` on folders made before Wi‑Fi sync: read as `createdAt`.
    var updatedAt: Date?
    /// A tombstone: the folder was deleted at this time. The row stays so the delete
    /// reaches other devices over Wi‑Fi sync (plan §6.3). Every read filters these out.
    var deletedAt: Date?

    init(name: String) {
        self.id = UUID()
        self.name = name
        self.createdAt = Date()
        self.updatedAt = self.createdAt
        self.deletedAt = nil
    }

    /// Only live folders, for `@Query` and fetches.
    static var live: Predicate<VaultCollection> {
        #Predicate<VaultCollection> { $0.deletedAt == nil }
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
    /// A tombstone: the item was deleted at this time. Envelope and payload are empty and
    /// the row stays so the delete reaches other devices over Wi‑Fi sync (plan §6.3).
    /// Every read filters these out.
    var deletedAt: Date?

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
        self.deletedAt = nil
    }

    /// Only live items (no tombstones), for fetches.
    static var live: Predicate<VaultItemRecord> {
        #Predicate<VaultItemRecord> { $0.deletedAt == nil }
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
    /// Sample credentials for local development. Gated to DEBUG so these can never be
    /// written into a shipping vault — the trigger is "item count is zero", which is
    /// exactly the state a user reaches after purging compromised logins.
    static func seedIfNeeded(context: ModelContext) {
        #if DEBUG
        guard VaultSession.shared.isUnlocked else { return }
        let descriptor = FetchDescriptor<VaultItemRecord>()
        let count = (try? context.fetchCount(descriptor)) ?? 0
        guard count == 0 else { return }

        if ProcessInfo.processInfo.arguments.contains("-screenshotVault") {
            seedScreenshotVault(context: context)
            return
        }

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
        #endif
    }

    #if DEBUG
    /// Marketing screenshots (landing page, store listings): launch with `-screenshotVault`.
    /// Realistic local entries with fake values. Facebook and Instagram share a password,
    /// Snapchat's is weak, and Gmail's is in Pwned Passwords, so Home, Password health and
    /// Alerts all have something to show.
    private static func seedScreenshotVault(context: ModelContext) {
        let work = VaultCollection(name: "Work")
        let personal = VaultCollection(name: "Personal")
        let bank = VaultCollection(name: "Bank")
        [work, personal, bank].forEach(context.insert)
        try? context.save()

        let samples: [VaultPayload] = [
            .login(LoginFields(itemName: "Facebook", email: "sara.ahmed@gmail.com", password: "Hawler-Dusk-2024", website: "https://facebook.com", collectionId: personal.id)),
            .login(LoginFields(itemName: "Instagram", username: "sara.ahmed", password: "Hawler-Dusk-2024", website: "https://instagram.com", collectionId: personal.id)),
            .login(LoginFields(itemName: "FIB", username: "0750 123 4821", password: "Tz8#qLm2vR!x", website: "https://fib.iq", collectionId: bank.id)),
            .login(LoginFields(itemName: "Gmail", email: "sara.ahmed@gmail.com", password: "sara1234", website: "https://mail.google.com", collectionId: personal.id)),
            .login(LoginFields(itemName: "Asiacell", username: "0770 555 1290", password: "k9V$wQ1z!pA3", website: "https://asiacell.com", collectionId: personal.id)),
            .login(LoginFields(itemName: "Snapchat", username: "sara.ahmed", password: "snapqasa9", website: "https://snapchat.com", collectionId: personal.id)),
            .login(LoginFields(itemName: "Netflix", email: "sara.ahmed@gmail.com", password: "r7N@eB4u#Lx2", website: "https://netflix.com", collectionId: personal.id)),
            .login(LoginFields(itemName: "Slack", email: "sara@company.iq", password: "fR5!mZ8q@Kd1", website: "https://slack.com", collectionId: work.id)),
            .card(CardFields(itemName: "FIB Card", accountHolder: "Sara Ahmed", number: "4111111111111111", securityCode: "123", expiryDate: "08/29", note: "", color: .blue)),
            .secureNote(SecureNoteFields(title: "Home Wi‑Fi", content: "Network: Ahmed-Home", color: .mint, protectItem: false)),
        ]
        for payload in samples {
            _ = try? VaultRepository.shared.save(payload: payload)
        }
        try? context.save()
    }
    #endif
}
