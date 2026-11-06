import Foundation
import Testing
@testable import qasa

/// RFC 6238 Appendix B vectors and the shared parsing rules (same cases as Android `TotpTest`).
struct TOTPTests {
    private static let times: [UInt64] = [59, 1_111_111_109, 1_111_111_111, 1_234_567_890, 2_000_000_000, 20_000_000_000]

    private static let sha1Secret = Data("12345678901234567890".utf8)
    private static let sha256Secret = Data("12345678901234567890123456789012".utf8)
    private static let sha512Secret = Data("1234567890123456789012345678901234567890123456789012345678901234".utf8)

    @Test(arguments: [
        (TOTP.Algorithm.sha1, sha1Secret, ["94287082", "07081804", "14050471", "89005924", "69279037", "65353130"]),
        (TOTP.Algorithm.sha256, sha256Secret, ["46119246", "68084774", "67062674", "91819424", "90698825", "77737706"]),
        (TOTP.Algorithm.sha512, sha512Secret, ["90693936", "25091201", "99943326", "93441116", "38618901", "47863826"]),
    ])
    func rfc6238Vectors(algorithm: TOTP.Algorithm, secret: Data, expected: [String]) {
        for (time, code) in zip(Self.times, expected) {
            #expect(TOTP.code(secret: secret, unixTime: time, period: 30, digits: 8, algorithm: algorithm) == code,
                    "\(algorithm) at \(time)")
        }
    }

    @Test func parsedURIGeneratesRFCVectors() throws {
        let uri = "otpauth://totp/Test?secret=\(base32(Self.sha256Secret))&algorithm=SHA256&digits=8"
        let totp = try TOTP.parse(uri)
        #expect(totp.code(at: Date(timeIntervalSince1970: 59)) == "46119246")
        #expect(totp.code(at: Date(timeIntervalSince1970: 1_234_567_890)) == "91819424")
    }

    @Test func bareSecretWithSpacesLowercaseAndPadding() throws {
        let totp = try TOTP.parse("  jbsw y3dp-ehpk 3pxp====  ")
        #expect(totp.uri == "otpauth://totp/Qasa?secret=JBSWY3DPEHPK3PXP")
        #expect(totp.algorithm == .sha1)
        #expect(totp.digits == 6)
        #expect(totp.period == 30)
        #expect(totp.secret.count == 10)
    }

    @Test func fullURIWithIssuerSHA256EightDigitsSixtySeconds() throws {
        let totp = try TOTP.parse(
            "OTPAUTH://TOTP/ACME%20Co:jane@example.com?secret=jbsw y3dp ehpk 3pxp&issuer=ACME%20Co&algorithm=SHA256&digits=8&period=60"
        )
        #expect(totp.algorithm == .sha256)
        #expect(totp.digits == 8)
        #expect(totp.period == 60)
        #expect(totp.issuer == "ACME Co")
        #expect(totp.accountName == "jane@example.com")
        #expect(totp.uri == "otpauth://totp/ACME%20Co:jane@example.com?secret=JBSWY3DPEHPK3PXP&issuer=ACME%20Co&algorithm=SHA256&digits=8&period=60")
        #expect(totp.code().count == 8)
        // Normalised output parses to the same thing.
        #expect(try TOTP.parse(totp.uri) == totp)
    }

    @Test func issuerFallsBackToLabelPrefix() throws {
        let totp = try TOTP.parse("otpauth://totp/GitHub:dev?secret=JBSWY3DPEHPK3PXP")
        #expect(totp.issuer == "GitHub")
        #expect(totp.accountName == "dev")
    }

    @Test func hotpRejected() {
        #expect(throws: TOTP.ParseError.invalid) {
            try TOTP.parse("otpauth://hotp/ACME?secret=JBSWY3DPEHPK3PXP&counter=1")
        }
        #expect(throws: TOTP.ParseError.invalid) {
            try TOTP.parseScanned("otpauth://hotp/ACME?secret=JBSWY3DPEHPK3PXP&counter=1")
        }
    }

    @Test func badCharacterRejected() {
        #expect(throws: TOTP.ParseError.invalid) { try TOTP.parse("JBSWY3DPEHPK3PX1") }
        #expect(throws: TOTP.ParseError.invalid) { try TOTP.parse("otpauth://totp/A?secret=JBSWY3DP!HPK3PXP") }
    }

    @Test func tooShortSecretRejected() {
        // 8 base32 characters decode to 5 bytes; the minimum is 10.
        #expect(throws: TOTP.ParseError.invalid) { try TOTP.parse("JBSWY3DP") }
        #expect(throws: TOTP.ParseError.invalid) { try TOTP.parse("otpauth://totp/A?secret=JBSWY3DP") }
    }

    @Test func outOfRangeParametersRejected() {
        let base = "otpauth://totp/A?secret=JBSWY3DPEHPK3PXP"
        #expect(throws: TOTP.ParseError.invalid) { try TOTP.parse(base + "&digits=9") }
        #expect(throws: TOTP.ParseError.invalid) { try TOTP.parse(base + "&digits=5") }
        #expect(throws: TOTP.ParseError.invalid) { try TOTP.parse(base + "&period=10") }
        #expect(throws: TOTP.ParseError.invalid) { try TOTP.parse(base + "&period=121") }
        #expect(throws: TOTP.ParseError.invalid) { try TOTP.parse(base + "&algorithm=MD5") }
        #expect(throws: TOTP.ParseError.invalid) { try TOTP.parse("otpauth://totp/A?issuer=A") }
    }

    @Test func nonOTPAuthQRRejected() {
        #expect(throws: TOTP.ParseError.notOTPAuth) { try TOTP.parseScanned("https://example.com/login") }
        #expect(throws: TOTP.ParseError.notOTPAuth) { try TOTP.parseScanned("JBSWY3DPEHPK3PXP") }
        #expect(throws: TOTP.ParseError.notOTPAuth) { try TOTP.parse("https://example.com/?secret=JBSWY3DPEHPK3PXP") }
    }

    @Test func storedEmptyMeansNoCode() {
        #expect(TOTP(stored: nil) == nil)
        #expect(TOTP(stored: "") == nil)
        #expect(TOTP(stored: "otpauth://totp/Qasa?secret=JBSWY3DPEHPK3PXP") != nil)
    }

    @Test func secondsRemainingAndGrouping() throws {
        let totp = try TOTP.parse("JBSWY3DPEHPK3PXP")
        #expect(totp.secondsRemaining(at: Date(timeIntervalSince1970: 59)) == 1)
        #expect(totp.secondsRemaining(at: Date(timeIntervalSince1970: 60)) == 30)
        #expect(TOTP.grouped("123456") == "123 456")
        #expect(TOTP.grouped("12345678") == "1234 5678")
    }

    private func base32(_ data: Data) -> String {
        let alphabet = Array("ABCDEFGHIJKLMNOPQRSTUVWXYZ234567")
        var out = ""
        var buffer: UInt32 = 0
        var bits = 0
        for byte in data {
            buffer = (buffer << 8) | UInt32(byte)
            bits += 8
            while bits >= 5 {
                bits -= 5
                out.append(alphabet[Int((buffer >> UInt32(bits)) & 0x1F)])
            }
        }
        if bits > 0 { out.append(alphabet[Int((buffer << UInt32(5 - bits)) & 0x1F)]) }
        return out
    }
}

/// `totp` in the CSV `fields` JSON, the format Android's `IosFieldsMapper` reads and writes.
@MainActor
struct VaultCSVTOTPTests {
    private let uri = "otpauth://totp/GitHub:dev?secret=JBSWY3DPEHPK3PXP&issuer=GitHub"

    @Test func exportWritesTOTPOnlyWhenSet() {
        let withCode = VaultCSV.exportCSV(payloads: [.login(LoginFields(itemName: "GitHub", password: "p", totp: uri))])
        #expect(withCode.contains("\"\"totp\"\":\"\"otpauth:"))

        let empty = VaultCSV.exportCSV(payloads: [.login(LoginFields(itemName: "GitHub", password: "p", totp: ""))])
        let none = VaultCSV.exportCSV(payloads: [.login(LoginFields(itemName: "GitHub", password: "p"))])
        #expect(!empty.contains("totp"))
        #expect(empty == none)
    }

    @Test func roundTripKeepsTOTP() throws {
        let csv = VaultCSV.exportCSV(payloads: [.login(LoginFields(itemName: "GitHub", password: "p", totp: uri))])
        let imported = try VaultCSV.parseImport(csv).imported
        guard case .login(let f) = imported.first else {
            Issue.record("no login imported")
            return
        }
        #expect(f.totp == uri)
    }

    @Test func importsAndroidFieldsJSON() throws {
        // Android writes the same keys; an empty totp means none.
        let json = #"{"itemName":"GitHub","email":"","username":"dev","password":"p","website":"","note":"","totp":"\#(uri)"}"#
        let csv = "type,title,fields\nlogin,GitHub,\(VaultCSV.csvEscape(json))\n"
        guard case .login(let f) = try VaultCSV.parseImport(csv).imported.first else {
            Issue.record("no login imported")
            return
        }
        #expect(f.totp == uri)

        let emptyJSON = #"{"itemName":"GitHub","password":"p","totp":""}"#
        let emptyCSV = "type,title,fields\nlogin,GitHub,\(VaultCSV.csvEscape(emptyJSON))\n"
        guard case .login(let g) = try VaultCSV.parseImport(emptyCSV).imported.first else {
            Issue.record("no login imported")
            return
        }
        #expect(g.totp == nil)
    }

    @Test func importsFlatTOTPColumns() throws {
        let csv = "type,title,password,login_totp\nlogin,GitHub,p,jbsw y3dp ehpk 3pxp\n"
        guard case .login(let f) = try VaultCSV.parseImport(csv).imported.first else {
            Issue.record("no login imported")
            return
        }
        #expect(f.totp == "otpauth://totp/Qasa?secret=JBSWY3DPEHPK3PXP")

        let bad = "type,title,password,totp\nlogin,GitHub,p,not-a-secret!\n"
        guard case .login(let g) = try VaultCSV.parseImport(bad).imported.first else {
            Issue.record("no login imported")
            return
        }
        #expect(g.totp == nil)
    }

    @Test func savedLoginsWithoutTOTPStillDecode() throws {
        let old = #"{"itemName":"GitHub","email":"","username":"dev","password":"p","website":"","note":""}"#
        let f = try JSONDecoder().decode(LoginFields.self, from: Data(old.utf8))
        #expect(f.totp == nil)
        #expect(!f.hasTOTP)
    }
}
