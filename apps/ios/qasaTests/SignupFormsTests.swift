import AuthenticationServices
import Foundation
import Testing
@testable import qasa

/// iOS side of docs/autofill-hardening.md: the generator honours every rule a sign-up form
/// can report (R2), text insertion offers the same password for the confirm field (R3), a
/// filled password is kept for saving (R4), and saved logins match the right site.
struct SignupFormsTests {
    private func maxRun(_ s: String) -> Int {
        var best = 0, run = 0
        var last: Character?
        for c in s {
            run = c == last ? run + 1 : 1
            last = c
            best = max(best, run)
        }
        return best
    }

    // MARK: R2: rules

    @Test func maxConsecutiveIsHonoured() {
        #expect(StrongPassword.parse("max-consecutive: 2").maxConsecutive == 2)
        for _ in 0..<200 {
            #expect(maxRun(StrongPassword.generate(rules: "max-consecutive: 1")) == 1)
            // A tiny alphabet makes repeats likely: still never more than 2 in a row.
            let p = StrongPassword.generate(rules: "required: [ab]; minlength: 30; max-consecutive: 2")
            #expect(p.count == 30)
            #expect(maxRun(p) <= 2)
            #expect(p.allSatisfy { $0 == "a" || $0 == "b" })
        }
    }

    @Test func maxLengthBelowTheClassCountWins() {
        for _ in 0..<50 {
            #expect(StrongPassword.generate(rules: "required: upper; required: lower; required: digit; required: special; maxlength: 3").count == 3)
            #expect(StrongPassword.generate(rules: "required: upper, lower, digit, special; maxlength: 2").count == 2)
            #expect(StrongPassword.generate(rules: "maxlength: 1").count == 1)
        }
    }

    @Test func emptyRulesAreNoRules() {
        #expect(StrongPassword.parse("").isEmpty)
        #expect(StrongPassword.parse("   ").isEmpty)
        // Empty page rules don't hide Apple's quirks for the site.
        let rules = StrongPassword.rules(password: "", confirm: nil, quirks: "required: digit; maxlength: 6")
        #expect(rules.maxLength == 6)
        let p = StrongPassword.generate(rules: rules)
        #expect(p.count == 6 && p.allSatisfy(\.isNumber))
        #expect(StrongPassword.generate(rules: StrongPassword.rules(password: "", confirm: "", quirks: "")).count == 16)
    }

    @Test func confirmFieldRulesCombine() {
        let rules = StrongPassword.rules(
            password: "minlength: 10; required: lower, upper, digit, special",
            confirm: "maxlength: 12; allowed: lower, upper, digit",
            quirks: nil
        )
        #expect(rules.minLength == 10 && rules.maxLength == 12)
        #expect(!rules.allowsSymbols)
        for _ in 0..<50 {
            let p = StrongPassword.generate(rules: rules)
            #expect(p.count == 12)
            #expect(p.allSatisfy { $0.isLetter || $0.isNumber })
        }
    }

    @Test func kindsFollowWhetherSymbolsAreAllowed() {
        let open = StrongPassword.suggestions(rules: StrongPassword.parse(nil))
        #expect(open.map(\.kind) == [.strong, .alphanumeric])
        #expect(open[1].value.allSatisfy { $0.isLetter || $0.isNumber })
        let noSymbols = StrongPassword.suggestions(rules: StrongPassword.parse("required: lower; required: digit"))
        #expect(noSymbols.map(\.kind) == [.alphanumeric])
        // A site that requires a symbol gets no alphanumeric alternative.
        let symbolRequired = StrongPassword.suggestions(rules: StrongPassword.parse("required: special; allowed: lower"))
        #expect(symbolRequired.map(\.kind) == [.strong])
    }

    // MARK: R3 / R4: the generated password is kept

    @Test func theSamePasswordIsOfferedForFiveMinutes() {
        let t0 = Date(timeIntervalSince1970: 1_000_000)
        let last = GeneratedPasswordStore.Record(host: nil, password: "Same-1!", createdAt: t0)
        #expect(GeneratedPasswordStore.reusable(last, now: t0.addingTimeInterval(10)) == "Same-1!")
        #expect(GeneratedPasswordStore.reusable(last, now: t0.addingTimeInterval(299)) == "Same-1!")
        #expect(GeneratedPasswordStore.reusable(last, now: t0.addingTimeInterval(301)) == nil)
        #expect(GeneratedPasswordStore.reusable(nil, now: t0) == nil)
    }

    @Test func unsavedPasswordsAreKeptADayAndOfferedOnce() {
        let t0 = Date(timeIntervalSince1970: 1_000_000)
        var records: [GeneratedPasswordStore.Record] = []
        records = GeneratedPasswordStore.adding(.init(host: nil, password: "A", createdAt: t0), to: records, now: t0)
        // New and confirm field: the same value once; a later host fills in the site.
        records = GeneratedPasswordStore.adding(.init(host: "example.com", password: "A", createdAt: t0.addingTimeInterval(5)), to: records, now: t0.addingTimeInterval(5))
        records = GeneratedPasswordStore.adding(.init(host: "b.com", password: "B", createdAt: t0.addingTimeInterval(60)), to: records, now: t0.addingTimeInterval(60))
        #expect(records.count == 2)
        #expect(records[0].host == "example.com")
        #expect(GeneratedPasswordStore.offer(from: records, savedPasswords: [], now: t0.addingTimeInterval(120))?.password == "B")
        #expect(GeneratedPasswordStore.offer(from: records, savedPasswords: ["B"], now: t0.addingTimeInterval(120))?.password == "A")
        #expect(GeneratedPasswordStore.offer(from: records, savedPasswords: ["A", "B"], now: t0) == nil)
        #expect(GeneratedPasswordStore.pruned(records, now: t0.addingTimeInterval(24 * 3600 + 61)).isEmpty)
    }

    // MARK: what reaches the extension

    @Test func profileItemsReachTextInsertion() {
        #expect(VaultRepository.isAutofillIndexed(.login(LoginFields())))
        #expect(VaultRepository.isAutofillIndexed(.card(CardFields())))
        #expect(VaultRepository.isAutofillIndexed(.name(NameFields())))
        #expect(VaultRepository.isAutofillIndexed(.personalEmail(PersonalEmailFields())))
        #expect(VaultRepository.isAutofillIndexed(.phone(PhoneFields())))
        #expect(VaultRepository.isAutofillIndexed(.address(AddressFields())))
        #expect(VaultRepository.isAutofillIndexed(.company(CompanyFields())))
        #expect(!VaultRepository.isAutofillIndexed(.secureNote(SecureNoteFields())))
    }

    @Test func aLoginWithoutAWebsiteRegistersNoDomain() {
        func login(_ host: String?) -> AutofillIndexEntry {
            AutofillIndexEntry(id: UUID(), kind: .login, title: "Netflix", subtitle: "me", websiteHost: host, last4: nil, encryptedSecrets: Data())
        }
        #expect(AutofillIdentitySync.serviceIdentifier(for: login(nil)) == nil)
        #expect(AutofillIdentitySync.identities(for: [login(nil)]).isEmpty)
        #expect(AutofillIdentitySync.serviceIdentifier(for: login("Netflix")) == nil)
        #expect(AutofillIdentitySync.serviceIdentifier(for: login("netflix.com/login"))?.identifier == "netflix.com")
    }

    // MARK: hosts and matching

    @Test func hostsParseWithOrWithoutAScheme() {
        let n = AutofillContextMatcher.normalizeHost
        #expect(n("https://www.example.com/login") == "example.com")
        #expect(n("example.com/login") == "example.com")
        #expect(n("example.com:8443") == "example.com")
        #expect(n("www.Example.com:8443/a?b#c") == "example.com")
        #expect(n("http://user@shop.example.co.uk:80/") == "shop.example.co.uk")
        #expect(n("192.168.1.1:8080") == "192.168.1.1")
        #expect(n("  ") == nil)
        #expect(n("my bank") == nil)
    }

    @Test func registrableDomainsKnowMultiLabelSuffixes() {
        let r = AutofillContextMatcher.registrableDomain
        #expect(r("login.amazon.co.uk") == "amazon.co.uk")
        #expect(r("amazon.co.uk") == "amazon.co.uk")
        #expect(r("accounts.google.com") == "google.com")
        #expect(r("shop.example.com.au") == "example.com.au")
        #expect(r("alice.github.io") == "alice.github.io")
        #expect(r("10.0.0.1") == "10.0.0.1")
    }

    @Test func matchingIsOnLabelBoundaries() {
        let score = AutofillContextMatcher.hostScore
        #expect(score("example.com", "example.com") == 100)
        #expect(score("example.com", "login.example.com") == 80)
        #expect(score("a.example.com", "b.example.com") == 60)
        #expect(score("amazon.co.uk", "bbc.co.uk") == 0)
        #expect(score("x.com", "netflix.com") == 0)
        #expect(score("alice.github.io", "bob.github.io") == 0)
        #expect(score("co.uk", "bbc.co.uk") == 0)
    }

    @Test func candidatesRankTheRightSiteOnly() {
        func login(_ host: String?, _ title: String = "T") -> AutofillIndexEntry {
            AutofillIndexEntry(id: UUID(), kind: .login, title: title, subtitle: "", websiteHost: host, last4: nil, encryptedSecrets: Data())
        }
        let entries = [login("x.com", "X"), login("netflix.com", "Netflix"), login("amazon.co.uk", "Amazon")]
        let list = AutofillContextMatcher.candidates(from: entries, serviceIdentifiers: ["https://www.netflix.com/login"], hint: .login)
        #expect(list.first?.title == "Netflix")
        #expect(list.first { $0.title == "X" }?.score ?? 0 < list[0].score)
        let bbc = AutofillContextMatcher.candidates(from: entries, serviceIdentifiers: ["bbc.co.uk"], hint: .login)
        #expect(!bbc.contains { $0.title == "Amazon" && $0.score > 10 })
    }
}
