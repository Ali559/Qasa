import Foundation
import Testing
@testable import qasa

/// Checks the iOS field validation (Swift payload → JSON → `QasaFFI`) against the golden
/// vectors shared with the core and Android (`core/qasa-vault/tests/validation_vectors.json`).
/// If one fails, the platforms disagree about what a valid item is: fix the code, never
/// the vectors.
@MainActor
struct ValidationVectorTests {
    struct Vectors: Decodable {
        let today: Day
        let cases: [Case]
        let card_brands: [Pair]
        let format_card_number: [Pair]
        let format_card_expiry: [Pair]
        let parse_date: [Pair]
    }

    struct Day: Decodable {
        let year: Int32
        let month, day: UInt32
    }

    struct Case: Decodable {
        let name: String
        let payload: VaultPayload
        let issues: [String]
        let normalized: VaultPayload
    }

    struct Pair: Decodable {
        let input, output: String
    }

    static let vectors: Vectors = {
        // qasaTests/ → apps/ios → apps → repository root.
        let root = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent().deletingLastPathComponent()
            .deletingLastPathComponent().deletingLastPathComponent()
        let url = root.appendingPathComponent("core/qasa-vault/tests/validation_vectors.json")
        let data = try! Data(contentsOf: url)
        return try! JSONDecoder().decode(Vectors.self, from: data)
    }()

    private var today: FfiDate {
        FfiDate(year: Self.vectors.today.year, month: Self.vectors.today.month, day: Self.vectors.today.day)
    }

    @Test func casesValidateAndNormalize() throws {
        #expect(!Self.vectors.cases.isEmpty)
        for c in Self.vectors.cases {
            // Re-encoded with the app's stored-payload encoder, as the repository does.
            let codes = try VaultValidation.validate(c.payload, today: today).map(\.code)
            #expect(codes == c.issues, "issues: \(c.name)")
            #expect(try VaultValidation.normalized(c.payload) == c.normalized, "normalized: \(c.name)")
        }
    }

    @Test func cardBrands() {
        for c in Self.vectors.card_brands {
            #expect(Self.name(cardBrand(number: c.input)) == c.output, "brand: \(c.input)")
        }
    }

    @Test func cardNumberFormatting() {
        for c in Self.vectors.format_card_number {
            #expect(formatCardNumber(raw: c.input) == c.output, "number: \(c.input)")
        }
    }

    @Test func cardExpiryFormatting() {
        for c in Self.vectors.format_card_expiry {
            #expect(formatCardExpiry(raw: c.input) == c.output, "expiry: \(c.input)")
        }
    }

    @Test func dateParsing() {
        for c in Self.vectors.parse_date {
            let iso = parseVaultDate(raw: c.input).map { String(format: "%04d-%02d-%02d", $0.year, $0.month, $0.day) } ?? ""
            #expect(iso == c.output, "date: \(c.input)")
        }
    }

    @Test func securityCodeMessages() {
        #expect(VaultValidation.message(.securityCodeLength(min: 3, max: 3), locale: Locale(identifier: "en"))
            == String(format: L10n.tr("validation.securityCodeExact", locale: Locale(identifier: "en")), 3))
        #expect(VaultValidation.message(.securityCodeLength(min: 3, max: 4), locale: Locale(identifier: "en"))
            == String(format: L10n.tr("validation.securityCodeRange", locale: Locale(identifier: "en")), 3, 4))
    }

    @Test func pickedDatesStoreAsISO() throws {
        let date = try #require(VaultValidation.date(fromStored: "2000-11-06"))
        #expect(VaultValidation.isoDate(from: date) == "2000-11-06")
        #expect(VaultValidation.displayDate("not a date") == "not a date")
    }

    /// The brand's camelCase name, as the core serializes it.
    private static func name(_ brand: FfiCardBrand) -> String {
        switch brand {
        case .visa: return "visa"
        case .mastercard: return "mastercard"
        case .amex: return "amex"
        case .discover: return "discover"
        case .dinersClub: return "dinersClub"
        case .jcb: return "jcb"
        case .unionPay: return "unionPay"
        case .maestro: return "maestro"
        case .mir: return "mir"
        case .unknown: return "unknown"
        }
    }
}
