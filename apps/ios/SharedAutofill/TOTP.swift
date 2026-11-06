import CryptoKit
import Foundation

/// Time-based one-time codes (RFC 6238) for a login's 2FA field.
///
/// A login stores one normalised `otpauth://totp/...` URI (`LoginFields.totp`). Input comes
/// from a QR code or a pasted setup key; both go through `parse` so the stored value is the
/// same on iOS and Android. Lives in SharedAutofill because the AutoFill extension also
/// generates the code it puts on the pasteboard. Mirrors Android `Totp`.
nonisolated struct TOTP: Equatable, Sendable {
    enum Algorithm: String, Sendable {
        case sha1 = "SHA1"
        case sha256 = "SHA256"
        case sha512 = "SHA512"
    }

    enum ParseError: Error, Equatable {
        /// Not an `otpauth://` link at all (a scanned QR for something else).
        case notOTPAuth
        /// An otpauth link or key Qasa can't use: hotp, bad base32, too short, out-of-range parameters.
        case invalid
    }

    /// Normalised URI, the value saved on the login.
    let uri: String
    let secret: Data
    let algorithm: Algorithm
    let digits: Int
    let period: Int
    let issuer: String?
    /// Label without the issuer prefix, usually the account email.
    let accountName: String?

    static let minSecretBytes = 10
    static let digitRange = 6...8
    static let periodRange = 15...120
    private static let base32Alphabet = Array("ABCDEFGHIJKLMNOPQRSTUVWXYZ234567")

    // MARK: - Parsing

    /// Pasted text: an `otpauth://totp/...` link or a bare base32 setup key.
    static func parse(_ input: String) throws(ParseError) -> TOTP {
        let trimmed = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { throw .invalid }
        if trimmed.contains("://") || trimmed.lowercased().hasPrefix("otpauth:") {
            return try parseURI(trimmed)
        }
        let cleaned = cleanBase32(trimmed)
        let secret = try decodeSecret(cleaned)
        return TOTP(
            uri: "otpauth://totp/Qasa?secret=\(cleaned)",
            secret: secret,
            algorithm: .sha1,
            digits: 6,
            period: 30,
            issuer: nil,
            accountName: nil
        )
    }

    /// A scanned QR code. Only otpauth links count; anything else is `.notOTPAuth`.
    static func parseScanned(_ payload: String) throws(ParseError) -> TOTP {
        let trimmed = payload.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.lowercased().hasPrefix("otpauth://") else { throw .notOTPAuth }
        return try parseURI(trimmed)
    }

    private static func parseURI(_ raw: String) throws(ParseError) -> TOTP {
        // Some sites put bare spaces in the label or key. Encode those ourselves first: left to
        // URLComponents, a string with any invalid character gets every `%` re-encoded too.
        let spaced = raw.replacingOccurrences(of: " ", with: "%20")
        guard let components = URLComponents(string: spaced, encodingInvalidCharacters: false)
                ?? URLComponents(string: spaced),
              components.scheme?.lowercased() == "otpauth" else {
            throw .notOTPAuth
        }
        guard components.host?.lowercased() == "totp" else { throw .invalid }

        let items = components.percentEncodedQueryItems ?? []
        func param(_ name: String) -> String? {
            items.first { $0.name.lowercased() == name }?.value?.removingPercentEncoding
        }

        guard let rawSecret = param("secret") else { throw .invalid }
        let cleaned = cleanBase32(rawSecret)
        let secret = try decodeSecret(cleaned)

        var algorithm = Algorithm.sha1
        if let value = param("algorithm") {
            guard let parsed = Algorithm(rawValue: value.uppercased()) else { throw .invalid }
            algorithm = parsed
        }
        var digits = 6
        if let value = param("digits") {
            guard let parsed = Int(value), digitRange.contains(parsed) else { throw .invalid }
            digits = parsed
        }
        var period = 30
        if let value = param("period") {
            guard let parsed = Int(value), periodRange.contains(parsed) else { throw .invalid }
            period = parsed
        }

        let label = String(components.path.drop(while: { $0 == "/" }))
        var issuer = param("issuer").flatMap { $0.isEmpty ? nil : $0 }
        var account: String? = label.isEmpty ? nil : label
        if let colon = label.firstIndex(of: ":") {
            let prefix = label[..<colon].trimmingCharacters(in: .whitespaces)
            let rest = label[label.index(after: colon)...].trimmingCharacters(in: .whitespaces)
            if issuer == nil, !prefix.isEmpty { issuer = prefix }
            account = rest.isEmpty ? nil : rest
        }

        // Keep the label and every parameter as given; only the secret is rewritten.
        var out = URLComponents()
        out.scheme = "otpauth"
        out.host = "totp"
        out.percentEncodedPath = components.percentEncodedPath.isEmpty ? "/" : components.percentEncodedPath
        out.percentEncodedQueryItems = items.map { item in
            item.name.lowercased() == "secret" ? URLQueryItem(name: item.name, value: cleaned) : item
        }
        guard let uri = out.string else { throw .invalid }

        return TOTP(
            uri: uri,
            secret: secret,
            algorithm: algorithm,
            digits: digits,
            period: period,
            issuer: issuer,
            accountName: account
        )
    }

    /// Spaces and dashes out, uppercase, no `=` padding.
    static func cleanBase32(_ input: String) -> String {
        input
            .filter { !$0.isWhitespace && $0 != "-" && $0 != "=" }
            .uppercased()
    }

    private static func decodeSecret(_ cleaned: String) throws(ParseError) -> Data {
        guard let data = base32Decode(cleaned), data.count >= minSecretBytes else { throw .invalid }
        return data
    }

    /// RFC 4648 base32 without padding. Trailing bits that don't fill a byte are dropped.
    static func base32Decode(_ cleaned: String) -> Data? {
        var bytes: [UInt8] = []
        bytes.reserveCapacity(cleaned.count * 5 / 8)
        var buffer: UInt32 = 0
        var bits = 0
        for ch in cleaned {
            guard let value = base32Alphabet.firstIndex(of: ch) else { return nil }
            buffer = (buffer << 5) | UInt32(value)
            bits += 5
            if bits >= 8 {
                bits -= 8
                bytes.append(UInt8((buffer >> UInt32(bits)) & 0xFF))
            }
        }
        return Data(bytes)
    }

    // MARK: - Codes

    /// Code for the period that contains `date`.
    func code(at date: Date = Date()) -> String {
        Self.code(
            secret: secret,
            unixTime: UInt64(max(0, date.timeIntervalSince1970)),
            period: period,
            digits: digits,
            algorithm: algorithm
        )
    }

    /// Whole seconds left before the code changes.
    func secondsRemaining(at date: Date = Date()) -> Int {
        let now = UInt64(max(0, date.timeIntervalSince1970))
        return period - Int(now % UInt64(period))
    }

    /// RFC 6238: counter = floor(time / period), HMAC over the big-endian counter, RFC 4226
    /// dynamic truncation, mod 10^digits, zero-padded.
    static func code(secret: Data, unixTime: UInt64, period: Int, digits: Int, algorithm: Algorithm) -> String {
        var counter = (unixTime / UInt64(period)).bigEndian
        let message = withUnsafeBytes(of: &counter) { Data($0) }
        let key = SymmetricKey(data: secret)
        let mac: [UInt8]
        switch algorithm {
        case .sha1: mac = Array(HMAC<Insecure.SHA1>.authenticationCode(for: message, using: key))
        case .sha256: mac = Array(HMAC<SHA256>.authenticationCode(for: message, using: key))
        case .sha512: mac = Array(HMAC<SHA512>.authenticationCode(for: message, using: key))
        }
        let offset = Int(mac[mac.count - 1] & 0x0F)
        let binary = (UInt32(mac[offset] & 0x7F) << 24)
            | (UInt32(mac[offset + 1]) << 16)
            | (UInt32(mac[offset + 2]) << 8)
            | UInt32(mac[offset + 3])
        var modulus: UInt32 = 1
        for _ in 0..<digits { modulus *= 10 }
        let value = String(binary % modulus)
        return String(repeating: "0", count: max(0, digits - value.count)) + value
    }

    /// "123 456" / "1234 5678": two groups for reading aloud or typing.
    static func grouped(_ code: String) -> String {
        guard code.count > 4 else { return code }
        let split = code.index(code.startIndex, offsetBy: code.count / 2)
        return "\(code[..<split]) \(code[split...])"
    }
}

nonisolated extension TOTP {
    /// Reads a stored `LoginFields.totp` value. Nil, empty or unreadable means no 2FA code.
    /// (In an extension so the memberwise initialiser stays available.)
    init?(stored: String?) {
        guard let stored, !stored.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              let parsed = try? Self.parse(stored) else { return nil }
        self = parsed
    }
}
