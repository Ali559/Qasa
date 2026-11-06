import CryptoKit
import Foundation

enum VaultSearchIndex {
    private static let minNGram = 2
    private static let maxNGram = 3

    static func tokenize(_ text: String) -> [String] {
        let lowered = text.lowercased()
        var tokens = Set<String>()

        let words = lowered
            .components(separatedBy: CharacterSet.alphanumerics.inverted)
            .filter { !$0.isEmpty }
        for word in words {
            tokens.insert(word)
            for length in minNGram...maxNGram {
                guard word.count >= length else { continue }
                for start in word.indices {
                    let end = word.index(start, offsetBy: length, limitedBy: word.endIndex)
                    guard let end else { break }
                    tokens.insert(String(word[start..<end]))
                }
            }
        }
        return tokens.sorted()
    }

    static func tokenHashes(for text: String, searchKey: SymmetricKey) -> [String] {
        tokenize(text).map { hashToken($0, searchKey: searchKey) }
    }

    static func hashToken(_ token: String, searchKey: SymmetricKey) -> String {
        let mac = HMAC<SHA256>.authenticationCode(for: Data(token.utf8), using: searchKey)
        return mac.map { String(format: "%02x", $0) }.joined()
    }

    static func searchableStrings(from payload: VaultPayload) -> [String] {
        switch payload {
        case .login(let f):
            // The 2FA issuer only; the secret never goes into the index.
            return [f.itemName, f.email, f.username, f.website, f.note, TOTP(stored: f.totp)?.issuer ?? ""]
        case .secureNote(let f):
            return [f.title, f.content]
        case .card(let f):
            return [f.itemName, f.accountHolder, f.note]
        case .bankAccount(let f):
            return [f.itemName, f.accountHolder, f.bankName]
        case .name(let f):
            return [f.firstName, f.middleName, f.lastName, f.defaultUsername]
        case .personalEmail(let f):
            return [f.itemName, f.email]
        case .phone(let f):
            return [f.itemName, f.number]
        case .address(let f):
            return [f.itemName, f.address, f.city, f.recipient]
        case .company(let f):
            return [f.companyName, f.jobTitle]
        case .website(let f):
            return [f.itemName, f.websiteURL]
        case .idCard(let f), .passport(let f), .driverLicense(let f):
            return [f.name, f.number]
        }
    }
}
