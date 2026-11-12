import CryptoKit
import Foundation
import Testing
@testable import qasa

/// Suggesting a strong password at sign-up and saving it (design/README.md rule 6c, TODO
/// Phase 5): the generator, its `passwordrules`, the sealed inbox and the app's import.
struct StrongPasswordSignupTests {
    private func has(_ password: String, _ set: [Character]) -> Bool { password.contains { set.contains($0) } }

    @Test func defaultsMatchTheSharedGenerator() {
        for _ in 0..<50 {
            let p = StrongPassword.generate()
            #expect(p.count == 16)
            #expect(has(p, StrongPassword.upper) && has(p, StrongPassword.lower))
            #expect(has(p, StrongPassword.digits) && has(p, StrongPassword.symbols))
        }
    }

    @Test func rulesSetTheLength() {
        #expect(StrongPassword.generate(rules: "minlength: 24").count == 24)
        #expect(StrongPassword.generate(rules: "maxlength: 10; required: lower").count == 10)
        #expect(StrongPassword.generate(rules: "minlength: 8; maxlength: 12; required: digit").count == 12)
    }

    @Test func rulesLimitTheCharacters() {
        for _ in 0..<50 {
            let digitsOnly = StrongPassword.generate(rules: "required: digit; maxlength: 6")
            #expect(digitsOnly.allSatisfy { StrongPassword.digits.contains($0) })
            let custom = StrongPassword.generate(rules: "required: lower; required: [-_]; minlength: 20")
            #expect(custom.allSatisfy { StrongPassword.lower.contains($0) || $0 == "-" || $0 == "_" })
            #expect(custom.contains("-") || custom.contains("_"))
        }
    }

    @Test func unknownRulesFallBackToTheDefaults() {
        #expect(StrongPassword.generate(rules: "unicode-ish: nope").count == 16)
        #expect(StrongPassword.parse(nil).isEmpty)
    }

    @Test func theInboxIsSealedAndDrainsOnce() throws {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("pending-\(UUID()).json")
        let key = SymmetricKey(size: .bits256)
        let login = PendingSaveInbox.Login(title: "example.com", website: "example.com", username: "ali", password: "Gen-3rated!", savedAt: Date(timeIntervalSince1970: 0))
        try PendingSaveInbox.append(login, key: key, at: url)
        #expect(!String(decoding: try Data(contentsOf: url), as: UTF8.self).contains("Gen-3rated"))
        #expect(PendingSaveInbox.drain(key: SymmetricKey(size: .bits256), at: url).isEmpty)
        try PendingSaveInbox.append(login, key: key, at: url)
        #expect(PendingSaveInbox.drain(key: key, at: url) == [login])
        #expect(PendingSaveInbox.drain(key: key, at: url).isEmpty)
    }

    @Test func importSkipsUpdatesOrCreates() {
        let id = UUID()
        let saved = LoginFields(itemName: "Example", username: "ali", password: "old", website: "https://www.example.com")
        func login(_ user: String, _ password: String, site: String = "example.com") -> PendingSaveInbox.Login {
            .init(title: site, website: site, username: user, password: password, savedAt: Date())
        }
        #expect(VaultRepository.pendingSaveAction(for: login("ali", "old"), existing: [(id, saved)]) == .skip)
        #expect(VaultRepository.pendingSaveAction(for: login("ALI", "new"), existing: [(id, saved)]) == .updatePassword(id))
        #expect(VaultRepository.pendingSaveAction(for: login("someone", "new"), existing: [(id, saved)]) == .create)
        #expect(VaultRepository.pendingSaveAction(for: login("ali", "new", site: "other.com"), existing: [(id, saved)]) == .create)
    }
}
