import AuthenticationServices
import Foundation
import Testing
@testable import qasa

/// AutoFill pieces that run without the extension (docs/autofill-plan.md §3.4): what text
/// insertion offers per item, which identities iOS gets, and that the payment notification
/// carries no secret.
struct AutofillTests {
    // MARK: text insertion

    @Test func cardOffersEachFieldWithMaskedPreviews() {
        let choices = AutofillTextChoices.choices(for: .card(.init(
            itemName: "Visa", accountHolder: "Ali Barznji", number: "4242 4242 4242 4242", securityCode: "123", expiryDate: "03/29"
        )))
        #expect(choices.map(\.id) == ["number", "expiry", "cvv", "holder"])
        #expect(choices[0].value == "4242424242424242")
        #expect(choices[0].preview == "•••• 4242")
        #expect(choices[1].preview == "03/29")
        #expect(choices[2].value == "123")
        #expect(!choices[2].preview.contains("123"))
    }

    @Test func addressPhoneAndNameValues() {
        let address = AutofillTextChoices.choices(for: .address(.init(
            recipient: "", address: "100 Salim St", building: "Building 4", floor: "2", apartment: "",
            city: "Sulaymaniyah", zipCode: "46001", countryCode: "IQ", countryName: "Iraq"
        )))
        #expect(address.map(\.id) == ["street", "line2", "city", "zip", "country"])
        #expect(address.first { $0.id == "line2" }?.value == "Building 4, 2")
        #expect(address.first { $0.id == "country" }?.value == "Iraq")

        let phone = AutofillTextChoices.choices(for: .phone(.init(number: "0750 123 4567", dialCode: "+964")))
        #expect(phone.map(\.value) == ["+9647501234567", "07501234567"])

        let name = AutofillTextChoices.choices(for: .name(.init(firstName: "Ali", middleName: "", lastName: "Barznji")))
        #expect(name.map(\.value) == ["Ali Barznji", "Ali", "Barznji"])
    }

    @Test func loginListsAnEmailUsernameOnce() {
        let choices = AutofillTextChoices.choices(for: .login(.init(email: "me@x.io", username: "", password: "pw", website: "x.io")))
        #expect(choices.map(\.id) == ["username", "password"])
        #expect(choices[0].value == "me@x.io")
    }

    // MARK: identities

    private func entry(_ kind: AutofillItemKind, host: String? = nil, user: String = "", totp: Bool? = nil) -> AutofillIndexEntry {
        AutofillIndexEntry(id: UUID(), kind: kind, title: "T", subtitle: user, websiteHost: host, last4: nil, encryptedSecrets: Data(), hasTOTP: totp)
    }

    @Test func passwordAndCodeIdentitiesOnlyForLogins() {
        let entries = [
            entry(.login, host: "github.com", user: "octo", totp: true),
            entry(.login, host: "google.com", user: "me"),
            entry(.card),
            entry(.address),
        ]
        let ids = AutofillIdentitySync.identities(for: entries)
        let passwords = ids.compactMap { $0 as? ASPasswordCredentialIdentity }
        let codes = ids.compactMap { $0 as? ASOneTimeCodeCredentialIdentity }
        #expect(passwords.map(\.serviceIdentifier.identifier) == ["github.com", "google.com"])
        #expect(passwords.map(\.user) == ["octo", "me"])
        #expect(codes.map(\.serviceIdentifier.identifier) == ["github.com"])
        #expect(codes.first?.recordIdentifier == entries[0].id.uuidString)
    }

    @Test func profileKindsStayOutOfThePasswordList() {
        #expect(AutofillItemKind.allCases.filter(\.isCredentialListKind) == [.login, .card, .bankAccount])
    }

    @Test func indexEntriesWrittenBeforeHasTOTPStillDecode() throws {
        let old = #"{"id":"6F9619FF-8B86-D011-B42D-00C04FC964FF","kind":"login","title":"A","subtitle":"b","websiteHost":"a.com","encryptedSecrets":""}"#
        let decoded = try JSONDecoder().decode(AutofillIndexEntry.self, from: Data(old.utf8))
        #expect(decoded.hasTOTP == nil)
    }

    // MARK: payment notification

    @Test func notificationCarriesNoFieldValue() {
        let info = EphemeralPaymentNotificationSupport.userInfo(title: "Visa", subtitle: "•••• 4242", expiresAt: Date())
        let text = info.values.map { "\($0)" }.joined(separator: " ")
        #expect(!text.contains("4242 4242"))
        #expect(info[EphemeralPaymentNotificationSupport.userInfoFields] == nil)
        #expect(EphemeralPaymentNotificationSupport.title(from: info) == "Visa")
    }
}
