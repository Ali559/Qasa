import Foundation

/// Strong passwords the AutoFill extension handed to a form (docs/autofill-hardening.md R3, R4).
/// Both live in the shared keychain (`EphemeralSecretStore`: this device only, only while
/// unlocked, never backed up):
///   - `last`: the most recent one, for 5 minutes, so text insertion can offer "Use the same
///     strong password" on the confirm field (iOS 18–26.1 fills one field at a time).
///   - `unsaved`: every one filled, for 24 hours or until it is in a saved login; the app
///     offers the newest on its next unlock ("Save the password you created for <site>?").
nonisolated enum GeneratedPasswordStore {
    struct Record: Codable, Equatable {
        /// The site's host when the request said (iOS 26.2); text insertion doesn't know it.
        var host: String?
        var password: String
        var createdAt: Date
    }

    static let reuseWindow: TimeInterval = 5 * 60
    static let lifetime: TimeInterval = 24 * 60 * 60

    private static let lastAccount = "generated-last"
    private static let unsavedAccount = "generated-unsaved"

    // MARK: pure rules (unit tested)

    /// The password to offer again, if the last one is younger than 5 minutes.
    static func reusable(_ last: Record?, now: Date) -> String? {
        guard let last, now.timeIntervalSince(last.createdAt) < reuseWindow,
              now >= last.createdAt.addingTimeInterval(-60) else { return nil }
        return last.password
    }

    /// Adds a filled password (once per value; a later host fills in a missing one) and drops
    /// anything older than 24 hours.
    static func adding(_ record: Record, to records: [Record], now: Date) -> [Record] {
        var out = pruned(records, now: now)
        if let i = out.firstIndex(where: { $0.password == record.password }) {
            if out[i].host == nil { out[i].host = record.host }
        } else {
            out.append(record)
        }
        return out
    }

    static func pruned(_ records: [Record], now: Date) -> [Record] {
        records.filter { now.timeIntervalSince($0.createdAt) < lifetime }
    }

    /// The newest record whose password no saved login has.
    static func offer(from records: [Record], savedPasswords: Set<String>, now: Date) -> Record? {
        pruned(records, now: now)
            .filter { !savedPasswords.contains($0.password) }
            .max { $0.createdAt < $1.createdAt }
    }

    // MARK: keychain

    /// A password was just handed to a field: keep it for reuse and for saving.
    static func recordFilled(_ password: String, host: String?, now: Date = Date()) {
        guard !password.isEmpty else { return }
        let host = host.flatMap { $0.isEmpty ? nil : $0 }
        if reusable(loadLast(), now: now) != password {
            store(Record(host: host, password: password, createdAt: now), account: lastAccount)
        }
        let records = adding(Record(host: host, password: password, createdAt: now), to: loadUnsaved(), now: now)
        store(records, account: unsavedAccount)
    }

    static func reusablePassword(now: Date = Date()) -> String? {
        reusable(loadLast(), now: now)
    }

    static func loadUnsaved(now: Date = Date()) -> [Record] {
        pruned(load([Record].self, account: unsavedAccount) ?? [], now: now)
    }

    static func replaceUnsaved(_ records: [Record]) {
        if records.isEmpty {
            EphemeralSecretStore.clear(account: unsavedAccount)
        } else {
            store(records, account: unsavedAccount)
        }
    }

    /// The password is in a saved login now (or the user was asked about it).
    static func forget(password: String) {
        replaceUnsaved(loadUnsaved().filter { $0.password != password })
    }

    static func clearAll() {
        EphemeralSecretStore.clear(account: lastAccount)
        EphemeralSecretStore.clear(account: unsavedAccount)
    }

    private static func loadLast() -> Record? { load(Record.self, account: lastAccount) }

    private static func store<T: Encodable>(_ value: T, account: String) {
        guard let data = try? JSONEncoder().encode(value) else { return }
        EphemeralSecretStore.putData(data, account: account)
    }

    private static func load<T: Decodable>(_ type: T.Type, account: String) -> T? {
        guard let data = EphemeralSecretStore.loadData(account: account) else { return nil }
        return try? JSONDecoder().decode(type, from: data)
    }
}
