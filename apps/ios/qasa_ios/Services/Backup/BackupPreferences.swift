import Foundation
import Observation

/// How often an automatic backup runs. The raw values are what `backupIsDue` expects.
enum BackupFrequency: String, CaseIterable, Identifiable {
    case off, daily, weekly, monthly

    var id: String { rawValue }

    /// `backup.freq.*`
    var labelKey: String { "backup.freq.\(rawValue)" }
}

/// This device's backup settings (spec: one backup file per device, at a place the user
/// picked). Nothing here is secret: the folder is kept as a security-scoped bookmark, and
/// the file itself is sealed by the core. Cleared by a local vault reset; the backup file
/// is never deleted.
@Observable
final class BackupPreferences {
    static let shared = BackupPreferences()

    enum Key {
        static let bookmark = "qasa.backup.folderBookmark"
        static let folderName = "qasa.backup.folderName"
        static let frequency = "qasa.backup.frequency"
        static let lastBackupMs = "qasa.backup.lastBackupMs"
        static let failed = "qasa.backup.failed"
        static let snoozedUntilMs = "qasa.backup.reminderSnoozedUntilMs"

        static let all = [bookmark, folderName, frequency, lastBackupMs, failed, snoozedUntilMs]
    }

    @ObservationIgnored private let defaults: UserDefaults

    /// Security-scoped bookmark of the folder the backup is written into.
    var folderBookmark: Data? {
        didSet { set(folderBookmark, Key.bookmark) }
    }

    /// The folder's name as shown in Settings.
    var folderName: String? {
        didSet { set(folderName, Key.folderName) }
    }

    var frequency: BackupFrequency {
        didSet { defaults.set(frequency.rawValue, forKey: Key.frequency) }
    }

    /// When the last backup (automatic or manual) was written, in ms since 1970.
    var lastBackupMs: Int64? {
        didSet { set(lastBackupMs.map(NSNumber.init(value:)), Key.lastBackupMs) }
    }

    /// The last attempt failed; shown on the Backups screen until a backup succeeds.
    var lastBackupFailed: Bool {
        didSet { defaults.set(lastBackupFailed, forKey: Key.failed) }
    }

    /// The Home reminder stays hidden until then (ms since 1970).
    var reminderSnoozedUntilMs: Int64? {
        didSet { set(reminderSnoozedUntilMs.map(NSNumber.init(value:)), Key.snoozedUntilMs) }
    }

    var hasLocation: Bool { folderBookmark != nil }

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        folderBookmark = defaults.data(forKey: Key.bookmark)
        folderName = defaults.string(forKey: Key.folderName)
        frequency = defaults.string(forKey: Key.frequency).flatMap(BackupFrequency.init(rawValue:)) ?? .off
        lastBackupMs = (defaults.object(forKey: Key.lastBackupMs) as? NSNumber)?.int64Value
        lastBackupFailed = defaults.bool(forKey: Key.failed)
        reminderSnoozedUntilMs = (defaults.object(forKey: Key.snoozedUntilMs) as? NSNumber)?.int64Value
    }

    /// Whether the chosen folder is in iCloud Drive. Qasa never uploads backups, so this is
    /// what tells the user whether the file leaves the phone. Nil without a location.
    var isInICloudDrive: Bool? {
        guard let folderBookmark else { return nil }
        var stale = false
        guard let url = try? URL(resolvingBookmarkData: folderBookmark, options: [.withoutUI], relativeTo: nil, bookmarkDataIsStale: &stale) else {
            return nil
        }
        return Self.isICloudDrivePath(url.path)
    }

    /// iCloud Drive folders live under `Mobile Documents` (the ubiquity containers).
    static func isICloudDrivePath(_ path: String) -> Bool {
        path.contains("/Mobile Documents/")
    }

    func setLocation(bookmark: Data, name: String) {
        folderBookmark = bookmark
        folderName = name
        lastBackupFailed = false
    }

    func recordSuccess(atMs ms: Int64) {
        lastBackupMs = ms
        lastBackupFailed = false
    }

    func recordFailure() {
        lastBackupFailed = true
    }

    /// An automatic backup runs only with a location and when the core says it is due.
    func isAutomaticBackupDue(nowMs: Int64) -> Bool {
        hasLocation && backupIsDue(frequency: frequency.rawValue, lastBackupMs: lastBackupMs, nowMs: nowMs)
    }

    func shouldRemind(itemCount: Int, nowMs: Int64) -> Bool {
        backupShouldRemind(
            lastBackupMs: lastBackupMs,
            itemCount: UInt32(clamping: max(0, itemCount)),
            snoozedUntilMs: reminderSnoozedUntilMs,
            nowMs: nowMs
        )
    }

    func snoozeReminder(nowMs: Int64) {
        reminderSnoozedUntilMs = nowMs + backupReminderSnoozeMs()
    }

    /// Local vault reset, clear local data and account deletion. The file stays where it is.
    func clear() {
        Key.all.forEach(defaults.removeObject(forKey:))
        folderBookmark = nil
        folderName = nil
        frequency = .off
        lastBackupMs = nil
        lastBackupFailed = false
        reminderSnoozedUntilMs = nil
        Key.all.forEach(defaults.removeObject(forKey:))
    }

    static func nowMs(_ date: Date = Date()) -> Int64 {
        Int64((date.timeIntervalSince1970 * 1000).rounded())
    }

    private func set(_ value: Any?, _ key: String) {
        if let value { defaults.set(value, forKey: key) } else { defaults.removeObject(forKey: key) }
    }
}
