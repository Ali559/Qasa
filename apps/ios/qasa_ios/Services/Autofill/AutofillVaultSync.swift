import CryptoKit
import Foundation

/// Builds / refreshes the App Group Autofill index + ASCredentialIdentityStore from vault contents.
enum AutofillVaultSync {
    static func rebuild(from payloads: [(id: UUID, payload: VaultPayload)]) {
        guard VaultSession.shared.isUnlocked,
              let encKey = try? VaultSession.shared.requireEncKey() else {
            return
        }

        var entries: [AutofillIndexEntry] = []
        for pair in payloads {
            if let entry = makeEntry(id: pair.id, payload: pair.payload, encKey: encKey) {
                entries.append(entry)
            }
        }
        AutofillIndexStore.replaceAll(entries: entries)
        AutofillIdentitySync.sync(entries: entries)

        // Keep shared keychain flags aligned for the extension.
        SharedVaultKeychain.vaultExists = VaultKeychainStore.vaultExists
        SharedVaultKeychain.kdfSalt = VaultKeychainStore.kdfSalt
        SharedVaultKeychain.passwordVerifier = VaultKeychainStore.passwordVerifier
        SharedVaultKeychain.biometricsEnabled = VaultKeychainStore.biometricsEnabled
    }

    private static func makeEntry(id: UUID, payload: VaultPayload, encKey: SymmetricKey) -> AutofillIndexEntry? {
        switch payload {
        case .login(let f):
            let secrets = AutofillSecrets.login(
                .init(
                    email: f.email,
                    username: f.username,
                    password: f.password,
                    website: f.website,
                    totp: f.hasTOTP ? f.totp : nil
                )
            )
            guard let blob = try? encodeSecrets(secrets, key: encKey) else { return nil }
            let host = AutofillContextMatcher.normalizeHost(f.website)
            return AutofillIndexEntry(
                id: id,
                kind: .login,
                title: payload.displayName,
                subtitle: payload.subtitle,
                websiteHost: host,
                last4: nil,
                encryptedSecrets: blob,
                hasTOTP: f.hasTOTP
            )
        case .card(let f):
            let secrets = AutofillSecrets.card(
                .init(
                    itemName: f.itemName,
                    accountHolder: f.accountHolder,
                    number: f.number,
                    securityCode: f.securityCode,
                    expiryDate: f.expiryDate
                )
            )
            guard let blob = try? encodeSecrets(secrets, key: encKey) else { return nil }
            let digits = f.number.filter(\.isNumber)
            let last4 = String(digits.suffix(4))
            return AutofillIndexEntry(
                id: id,
                kind: .card,
                title: payload.displayName,
                subtitle: payload.subtitle,
                websiteHost: nil,
                last4: last4.isEmpty ? nil : last4,
                encryptedSecrets: blob
            )
        case .bankAccount(let f):
            let secrets = AutofillSecrets.bank(
                .init(
                    itemName: f.itemName,
                    accountHolder: f.accountHolder,
                    accountNumber: f.accountNumber,
                    routingNumber: f.routingNumber,
                    bankName: f.bankName
                )
            )
            guard let blob = try? encodeSecrets(secrets, key: encKey) else { return nil }
            let digits = f.accountNumber.filter(\.isNumber)
            let last4 = String(digits.suffix(4))
            return AutofillIndexEntry(
                id: id,
                kind: .bankAccount,
                title: payload.displayName,
                subtitle: payload.subtitle,
                websiteHost: nil,
                last4: last4.isEmpty ? nil : last4,
                encryptedSecrets: blob
            )
        // Profile items: text insertion only (docs/autofill-plan.md §3.4).
        case .name(let f):
            return profileEntry(id: id, kind: .name, payload: payload, subtitle: "", key: encKey, secrets: .name(
                .init(firstName: f.firstName, middleName: f.middleName, lastName: f.lastName)
            ))
        case .personalEmail(let f):
            return profileEntry(id: id, kind: .email, payload: payload, subtitle: f.email, key: encKey, secrets: .email(
                .init(email: f.email)
            ))
        case .phone(let f):
            // The index's plain part is readable while locked: only the last digits.
            let tail = String(f.number.filter(\.isNumber).suffix(2))
            return profileEntry(id: id, kind: .phone, payload: payload, subtitle: tail.isEmpty ? "" : "•••• \(tail)", key: encKey, secrets: .phone(
                .init(number: f.number, dialCode: Country.dialCode(for: f.countryCode))
            ))
        case .address(let f):
            return profileEntry(id: id, kind: .address, payload: payload, subtitle: payload.subtitle, key: encKey, secrets: .address(
                .init(
                    recipient: f.recipient,
                    address: f.address,
                    building: f.building,
                    floor: f.floor,
                    apartment: f.apartment,
                    city: f.city,
                    zipCode: f.zipCode,
                    countryCode: f.countryCode,
                    countryName: Country.name(for: f.countryCode)
                )
            ))
        case .company(let f):
            return profileEntry(id: id, kind: .company, payload: payload, subtitle: f.jobTitle, key: encKey, secrets: .company(
                .init(companyName: f.companyName, jobTitle: f.jobTitle)
            ))
        default:
            return nil
        }
    }

    private static func profileEntry(
        id: UUID,
        kind: AutofillItemKind,
        payload: VaultPayload,
        subtitle: String,
        key: SymmetricKey,
        secrets: AutofillSecrets
    ) -> AutofillIndexEntry? {
        guard let blob = try? encodeSecrets(secrets, key: key) else { return nil }
        return AutofillIndexEntry(
            id: id,
            kind: kind,
            title: payload.displayName,
            subtitle: subtitle,
            websiteHost: nil,
            last4: nil,
            encryptedSecrets: blob
        )
    }

    private static func encodeSecrets(_ secrets: AutofillSecrets, key: SymmetricKey) throws -> Data {
        let data = try JSONEncoder().encode(secrets)
        return try SharedVaultCrypto.encrypt(data, key: key)
    }
}
