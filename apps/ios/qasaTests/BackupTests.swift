import CryptoKit
import Foundation
import SwiftData
import Testing
@testable import qasa

private func backupContainer() throws -> ModelContainer {
    try ModelContainer(
        for: Schema(VaultSchema.models),
        configurations: [ModelConfiguration(isStoredInMemoryOnly: true)]
    )
}

private func freshDefaults() -> UserDefaults {
    let name = "qasa.tests.backup.\(UUID().uuidString)"
    let defaults = UserDefaults(suiteName: name)!
    defaults.removePersistentDomain(forName: name)
    return defaults
}

// MARK: - Preferences and schedule glue

@MainActor
struct BackupPreferencesTests {
    private let day: Int64 = 24 * 60 * 60 * 1000

    @Test func defaultsAreOffWithNothingRecorded() {
        let prefs = BackupPreferences(defaults: freshDefaults())
        #expect(prefs.frequency == .off)
        #expect(prefs.folderBookmark == nil)
        #expect(prefs.lastBackupMs == nil)
        #expect(!prefs.lastBackupFailed)
        #expect(prefs.reminderSnoozedUntilMs == nil)
    }

    @Test func valuesSurviveARelaunch() {
        let defaults = freshDefaults()
        let prefs = BackupPreferences(defaults: defaults)
        prefs.setLocation(bookmark: Data([1, 2, 3]), name: "Qasa")
        prefs.frequency = .weekly
        prefs.recordSuccess(atMs: 1_700_000_000_123)
        prefs.reminderSnoozedUntilMs = 1_800_000_000_000

        let again = BackupPreferences(defaults: defaults)
        #expect(again.folderBookmark == Data([1, 2, 3]))
        #expect(again.folderName == "Qasa")
        #expect(again.frequency == .weekly)
        #expect(again.lastBackupMs == 1_700_000_000_123)
        #expect(again.reminderSnoozedUntilMs == 1_800_000_000_000)
    }

    @Test func successClearsTheFailureFlag() {
        let prefs = BackupPreferences(defaults: freshDefaults())
        prefs.recordFailure()
        #expect(prefs.lastBackupFailed)
        prefs.recordSuccess(atMs: 5)
        #expect(!prefs.lastBackupFailed)
        #expect(prefs.lastBackupMs == 5)
    }

    @Test func automaticBackupNeedsALocationAndAFrequency() {
        let prefs = BackupPreferences(defaults: freshDefaults())
        let now: Int64 = 1_700_000_000_000
        prefs.frequency = .daily
        #expect(!prefs.isAutomaticBackupDue(nowMs: now))
        prefs.setLocation(bookmark: Data([1]), name: "x")
        #expect(prefs.isAutomaticBackupDue(nowMs: now))
        prefs.recordSuccess(atMs: now - day / 2)
        #expect(!prefs.isAutomaticBackupDue(nowMs: now))
        prefs.recordSuccess(atMs: now - day)
        #expect(prefs.isAutomaticBackupDue(nowMs: now))
        prefs.frequency = .off
        #expect(!prefs.isAutomaticBackupDue(nowMs: now))
    }

    @Test func reminderSnoozesAndNeedsItems() {
        let prefs = BackupPreferences(defaults: freshDefaults())
        let now: Int64 = 1_700_000_000_000
        #expect(!prefs.shouldRemind(itemCount: 0, nowMs: now))
        #expect(prefs.shouldRemind(itemCount: 3, nowMs: now))
        prefs.snoozeReminder(nowMs: now)
        #expect(prefs.reminderSnoozedUntilMs == now + backupReminderSnoozeMs())
        #expect(!prefs.shouldRemind(itemCount: 3, nowMs: now + 1))
        #expect(prefs.shouldRemind(itemCount: 3, nowMs: now + backupReminderSnoozeMs()))
        prefs.recordSuccess(atMs: now + backupReminderSnoozeMs())
        #expect(!prefs.shouldRemind(itemCount: 3, nowMs: now + backupReminderSnoozeMs() + 1))
    }

    @Test func clearForgetsEverything() {
        let defaults = freshDefaults()
        let prefs = BackupPreferences(defaults: defaults)
        prefs.setLocation(bookmark: Data([1]), name: "x")
        prefs.frequency = .monthly
        prefs.recordSuccess(atMs: 9)
        prefs.recordFailure()
        prefs.snoozeReminder(nowMs: 9)
        prefs.clear()

        for key in BackupPreferences.Key.all {
            #expect(defaults.object(forKey: key) == nil)
        }
        let again = BackupPreferences(defaults: defaults)
        #expect(again.folderBookmark == nil)
        #expect(again.frequency == .off)
        #expect(again.lastBackupMs == nil)
        #expect(!again.lastBackupFailed)
        #expect(again.reminderSnoozedUntilMs == nil)
    }

    @Test func errorsMapToTheirStrings() {
        #expect(BackupService.messageKey(FfiBackupError.NotABackup) == "backup.error.notABackup")
        #expect(BackupService.messageKey(FfiBackupError.NewerVersion) == "backup.error.newerVersion")
        #expect(BackupService.messageKey(FfiBackupError.WrongPassword) == "backup.error.wrongPassword")
        #expect(BackupService.messageKey(FfiBackupError.DifferentVault) == "backup.error.differentVault")
        #expect(BackupService.messageKey(FfiBackupError.NoVault) == "backup.error.noVault")
        #expect(BackupService.messageKey(FfiBackupError.Store(detail: "x")) == "backup.error.unreadable")
        #expect(BackupService.messageKey(CocoaError(.fileReadNoPermission)) == "backup.error.unreadable")
    }
}

// MARK: - File writes

@MainActor
struct BackupFileIOTests {
    @Test func writesIntoTheBookmarkedFolderAndReplacesTheOldFile() throws {
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent("backup-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: folder) }

        let picked = try BackupFileIO.bookmark(forPickedFolder: folder)
        let name = backupFileName(deviceName: "Test iPhone")
        #expect(name == "Qasa backup - Test iPhone.qasabackup")

        _ = try BackupFileIO.write(Data([1, 2, 3]), named: name, inFolder: picked.bookmark)
        _ = try BackupFileIO.write(Data([4, 5]), named: name, inFolder: picked.bookmark)

        let file = folder.appendingPathComponent(name)
        #expect(try BackupFileIO.read(pickedFile: file) == Data([4, 5]))
        let names = try FileManager.default.contentsOfDirectory(atPath: folder.path)
        #expect(names == [name])
    }
}

// MARK: - Whole backup through the Rust core

/// A real vault's key material, so the core's password check passes.
private struct TestVault {
    let meta: MemoryVaultMeta
    let encKey: Data

    init(password: String, account: String) throws {
        let salt = MasterKeyDeriver.generateSalt()
        let root = try MasterKeyDeriver.deriveRootKey(password: password, salt: salt)
        let m = MemoryVaultMeta()
        m.vaultExists = true
        m.kdfSalt = salt
        m.passwordVerifier = MasterKeyDeriver.passwordVerifier(rootKey: root)
        m.kdfVersion = 1
        m.accountId = account
        m.deviceFingerprint = "fp"
        meta = m
        encKey = MasterKeyDeriver.deriveSubkeys(rootKey: root).encKey.withUnsafeBytes { Data($0) }
    }
}

@Suite(.serialized)
@MainActor
struct BackupRoundTripTests {
    private func store(_ container: ModelContainer, _ meta: MemoryVaultMeta) -> VaultSyncStore {
        VaultSyncStore(container: container, deviceName: "Ali's iPhone", meta: meta, onApplied: {})
    }

    private func seed(_ container: ModelContainer) throws -> UUID {
        let ctx = ModelContext(container)
        let item = VaultItemRecord(kind: .login)
        item.encryptedEnvelope = Data([1])
        item.encryptedPayload = Data([2, 3])
        ctx.insert(item)
        ctx.insert(VaultCollection(nameEncrypted: Data([7, 7, 7])))
        try ctx.save()
        return item.id
    }

    @Test func newDeviceAdoptsVaultAccountAndRows() async throws {
        let vault = try TestVault(password: "correct horse", account: "acct-backup")
        let source = try backupContainer()
        let itemId = try seed(source)
        let sourceStore = store(source, vault.meta)
        let encKey = vault.encKey

        let file = try await BackupService.offMain { Result { try backupCreate(encKey: encKey, restoreKey: nil, store: sourceStore) } }.get()
        let info = try backupInfo(file: file)
        #expect(info.deviceName == "Ali's iPhone")
        #expect(info.platform == "ios")

        let target = try backupContainer()
        let targetMeta = MemoryVaultMeta()
        let targetStore = store(target, targetMeta)
        let outcome = try await BackupService.offMain {
            Result { try backupRestore(file: file, password: "correct horse", store: targetStore) }
        }.get()
        #expect(outcome.adopted)
        #expect(outcome.itemsInBackup == 1)
        #expect(outcome.written == 2)
        #expect(targetMeta.vaultExists)
        #expect(targetMeta.accountId == "acct-backup")
        #expect(targetMeta.kdfSalt == vault.meta.kdfSalt)
        #expect(targetMeta.passwordVerifier == vault.meta.passwordVerifier)
        let rows = try ModelContext(target).fetch(FetchDescriptor<VaultItemRecord>())
        #expect(rows.map(\.id) == [itemId])
        #expect(rows.first?.encryptedPayload == Data([2, 3]))

        // Restoring the same file again changes nothing.
        let again = try await BackupService.offMain {
            Result { try backupRestore(file: file, password: "correct horse", store: targetStore) }
        }.get()
        #expect(!again.adopted)
        #expect(again.written == 0)
    }

    @Test func wrongPasswordAndDifferentVaultChangeNothing() async throws {
        let vault = try TestVault(password: "pw-one-123", account: "a1")
        let source = try backupContainer()
        _ = try seed(source)
        let sourceStore = store(source, vault.meta)
        let encKey = vault.encKey
        let file = try await BackupService.offMain { Result { try backupCreate(encKey: encKey, restoreKey: nil, store: sourceStore) } }.get()

        let emptyMeta = MemoryVaultMeta()
        let emptyStore = store(try backupContainer(), emptyMeta)
        let wrong = await BackupService.offMain { () -> String? in
            do { _ = try backupRestore(file: file, password: "nope-nope", store: emptyStore); return nil }
            catch { return BackupService.messageKey(error) }
        }
        #expect(wrong == "backup.error.wrongPassword")
        #expect(!emptyMeta.vaultExists)

        let other = try TestVault(password: "pw-one-123", account: "a2")
        let otherContainer = try backupContainer()
        let otherStore = store(otherContainer, other.meta)
        let different = await BackupService.offMain { () -> String? in
            do { _ = try backupRestore(file: file, password: "pw-one-123", store: otherStore); return nil }
            catch { return BackupService.messageKey(error) }
        }
        #expect(different == "backup.error.differentVault")
        #expect(try ModelContext(otherContainer).fetch(FetchDescriptor<VaultItemRecord>()).isEmpty)
    }

    @Test func restoreKeyTravelsInTheBackupAndBecomesPending() async throws {
        let vault = try TestVault(password: "restore-key-pw", account: "acct-rk")
        let source = try backupContainer()
        _ = try seed(source)
        let sourceStore = store(source, vault.meta)
        let encKey = vault.encKey
        let key = backupNewRestoreKey()
        #expect(key.count == 32)

        let file = try await BackupService.offMain {
            Result { try backupCreate(encKey: encKey, restoreKey: key, store: sourceStore) }
        }.get()
        let targetStore = store(try backupContainer(), MemoryVaultMeta())
        let outcome = try await BackupService.offMain {
            Result { try backupRestore(file: file, password: "restore-key-pw", store: targetStore) }
        }.get()
        #expect(outcome.adopted)
        #expect(outcome.restoreKey == key)

        let secrets = MemoryRestoreKeySecrets()
        RestoreKeyRotation.rememberRestored(outcome, secrets: secrets)
        #expect(secrets.pendingRestoreKey == key)

        // A merge (not adopted) never sets the pending key.
        let merged = try await BackupService.offMain {
            Result { try backupRestore(file: file, password: "restore-key-pw", store: targetStore) }
        }.get()
        #expect(!merged.adopted)
        let other = MemoryRestoreKeySecrets()
        RestoreKeyRotation.rememberRestored(merged, secrets: other)
        #expect(other.pendingRestoreKey == nil)
    }

    @Test func backupWithoutAKeyRestoresWithoutOne() async throws {
        let vault = try TestVault(password: "no-key-pw", account: "acct-nk")
        let source = try backupContainer()
        _ = try seed(source)
        let sourceStore = store(source, vault.meta)
        let encKey = vault.encKey
        let file = try await BackupService.offMain {
            Result { try backupCreate(encKey: encKey, restoreKey: nil, store: sourceStore) }
        }.get()
        let targetStore = store(try backupContainer(), MemoryVaultMeta())
        let outcome = try await BackupService.offMain {
            Result { try backupRestore(file: file, password: "no-key-pw", store: targetStore) }
        }.get()
        #expect(outcome.adopted)
        #expect(outcome.restoreKey == nil)
        let secrets = MemoryRestoreKeySecrets()
        RestoreKeyRotation.rememberRestored(outcome, secrets: secrets)
        #expect(secrets.pendingRestoreKey == nil)
    }

    @Test func notABackupIsRefusedByTheHeaderCheck() {
        #expect(throws: FfiBackupError.self) { try backupInfo(file: Data("hello".utf8)) }
        if case .failure(.message(let key)) = BackupService.readInfo(Data("hello".utf8)) {
            #expect(key == "backup.error.notABackup")
        } else {
            Issue.record("expected a failure")
        }
    }
}
