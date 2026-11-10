import Foundation
import Security

/// The card or bank fields behind the 30-second payment notification
/// (docs/autofill-plan.md §3.4). They used to travel in the notification's `userInfo`,
/// which iOS keeps in its notification database; now they sit in the shared keychain,
/// readable only while the phone is unlocked, never backed up, and gone when they expire.
/// The notification itself carries only the item's title and masked number.
nonisolated enum EphemeralSecretStore {
    private static let service = "io.kaso.qasa.ephemeral"
    private static let account = "payment"

    private struct Stored: Codable {
        let fields: [EphemeralPaymentNotificationSupport.FieldPayload]
        let expiresAt: Date
    }

    private static var baseQuery: [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecAttrAccessGroup as String: AppGroup.keychainAccessGroup,
        ]
    }

    static func put(_ fields: [EphemeralPaymentNotificationSupport.FieldPayload], expiresAt: Date) {
        clear()
        guard let data = try? JSONEncoder().encode(Stored(fields: fields, expiresAt: expiresAt)) else { return }
        var query = baseQuery
        query[kSecValueData as String] = data
        query[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        SecItemAdd(query as CFDictionary, nil)
    }

    /// The fields while they are valid; an expired entry is deleted and reads as empty.
    static func load(now: Date = Date()) -> [EphemeralPaymentNotificationSupport.FieldPayload] {
        var query = baseQuery
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: AnyObject?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data,
              let stored = try? JSONDecoder().decode(Stored.self, from: data) else {
            return []
        }
        guard stored.expiresAt > now else {
            clear()
            return []
        }
        return stored.fields
    }

    static func clear() {
        SecItemDelete(baseQuery as CFDictionary)
    }
}
