import Foundation

/// The iOS side of the shared field rules (`core/qasa-vault/src/validate.rs`, through
/// `QasaFFI`). The rules themselves live only in the core; this encodes the Swift payload
/// in its stored JSON shape, calls the core and maps each `FfiProblem` to a localized
/// message. Every user save goes through `VaultRepository`, which runs `normalize` then
/// `issues` and refuses the write while any issue remains.
enum VaultValidation {
    /// The stored payload's encoder: `VaultRepository` encrypts `JSONEncoder().encode(payload)`,
    /// so the core sees exactly the bytes that would be stored (the iOS shape).
    static func encode(_ payload: VaultPayload) throws -> Data {
        try JSONEncoder().encode(payload)
    }

    /// Today's local calendar day. The core counts Gregorian years, so the day comes from a
    /// Gregorian calendar in `Calendar.current`'s time zone (the user's calendar may be
    /// Islamic or Persian).
    static func today(now: Date = Date()) -> FfiDate {
        let parts = gregorian.dateComponents([.year, .month, .day], from: now)
        return FfiDate(year: Int32(parts.year ?? 1970), month: UInt32(parts.month ?? 1), day: UInt32(parts.day ?? 1))
    }

    /// The canonical stored form (ISO dates, `MM/YY` expiry, grouped card numbers, ASCII
    /// digits…). Throws `FfiPayloadError.Unreadable` if the core can't read the payload.
    static func normalized(_ payload: VaultPayload) throws -> VaultPayload {
        try JSONDecoder().decode(VaultPayload.self, from: normalizePayload(json: encode(payload)))
    }

    /// Every issue, at most one per field, in form order.
    static func validate(_ payload: VaultPayload, today: FfiDate = today()) throws -> [FfiFieldIssue] {
        try validatePayload(json: encode(payload), today: today)
    }

    /// For the UI: the issues of `payload`, or none if the core can't read it.
    static func issues(_ payload: VaultPayload, today: FfiDate = today()) -> [FfiFieldIssue] {
        (try? validate(payload, today: today)) ?? []
    }

    /// For the UI: the normalized payload, or `payload` unchanged if the core can't read it.
    static func normalizedOrSame(_ payload: VaultPayload) -> VaultPayload {
        (try? normalized(payload)) ?? payload
    }

    /// The issue's field as its JSON key (`expiryDate`), from the stable `field:problem` code.
    static func fieldKey(_ issue: FfiFieldIssue) -> String {
        String(issue.code.prefix { $0 != ":" })
    }

    /// One localized message per field key.
    static func messages(_ issues: [FfiFieldIssue], locale: Locale = AppLanguage.current.locale) -> [String: String] {
        var out: [String: String] = [:]
        for issue in issues where out[fieldKey(issue)] == nil {
            out[fieldKey(issue)] = message(issue.problem, locale: locale)
        }
        return out
    }

    static func message(_ problem: FfiProblem, locale: Locale = AppLanguage.current.locale) -> String {
        func fmt(_ key: String, _ args: CVarArg...) -> String {
            String(format: L10n.tr(key, locale: locale), locale: locale, arguments: args)
        }
        switch problem {
        case .required: return L10n.tr("validation.required", locale: locale)
        case .tooLong(let max): return fmt("validation.tooLong", Int(max))
        case .digitsOnly: return L10n.tr("validation.digitsOnly", locale: locale)
        case .lettersDigitsOnly: return L10n.tr("validation.lettersDigitsOnly", locale: locale)
        case .cardNumberLength: return L10n.tr("validation.cardNumberLength", locale: locale)
        case .cardNumberChecksum: return L10n.tr("validation.cardNumberChecksum", locale: locale)
        case .securityCodeLength(let min, let max):
            return min == max
                ? fmt("validation.securityCodeExact", Int(min))
                : fmt("validation.securityCodeRange", Int(min), Int(max))
        case .expiryFormat: return L10n.tr("validation.expiryFormat", locale: locale)
        case .expiryMonth: return L10n.tr("validation.expiryMonth", locale: locale)
        case .expiryTooFar(let years), .dateTooFar(let years): return fmt("validation.yearsAway", Int(years))
        case .dateInvalid: return L10n.tr("validation.dateInvalid", locale: locale)
        case .dateInFuture: return L10n.tr("validation.dateInFuture", locale: locale)
        case .dateTooOld: return L10n.tr("validation.dateTooOld", locale: locale)
        case .dateBeforeIssue: return L10n.tr("validation.dateBeforeIssue", locale: locale)
        case .emailInvalid: return L10n.tr("validation.emailInvalid", locale: locale)
        case .urlInvalid: return L10n.tr("validation.urlInvalid", locale: locale)
        case .phoneLength(let min, let max): return fmt("validation.phoneLength", Int(min), Int(max))
        case .phoneCountryMismatch: return L10n.tr("validation.phoneCountryMismatch", locale: locale)
        case .ibanInvalid: return L10n.tr("validation.ibanInvalid", locale: locale)
        case .ibanChecksum: return L10n.tr("validation.ibanChecksum", locale: locale)
        case .routingInvalid: return L10n.tr("validation.routingInvalid", locale: locale)
        case .countryInvalid: return L10n.tr("validation.countryInvalid", locale: locale)
        case .totpInvalid: return L10n.tr("validation.totpInvalid", locale: locale)
        }
    }

    // MARK: - Dates

    static let gregorian: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = Calendar.current.timeZone
        return calendar
    }()

    /// The stored form of a picked day: `YYYY-MM-DD`.
    static func isoDate(from date: Date) -> String {
        let p = gregorian.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", p.year ?? 0, p.month ?? 0, p.day ?? 0)
    }

    /// A stored date (ISO, or a form older versions stored) as a `Date` at local midnight.
    static func date(fromStored raw: String) -> Date? {
        guard let d = parseVaultDate(raw: raw) else { return nil }
        return gregorian.date(from: DateComponents(year: Int(d.year), month: Int(d.month), day: Int(d.day)))
    }

    /// A stored date shown in the app language ("6 Nov 2000"); unreadable text as is.
    static func displayDate(_ raw: String, locale: Locale = AppLanguage.current.locale) -> String {
        guard let date = date(fromStored: raw) else { return raw }
        let formatter = DateFormatter()
        formatter.locale = locale
        formatter.timeZone = gregorian.timeZone
        formatter.setLocalizedDateFormatFromTemplate("yMMMd")
        return formatter.string(from: date)
    }
}

/// A user save the shared rules refused. Nothing was written.
struct VaultValidationError: LocalizedError {
    let issues: [FfiFieldIssue]

    var errorDescription: String? { L10n.tr("validation.fixToSave") }
}

/// Structural checks for rows written without decrypting them (Wi‑Fi sync apply, backup
/// restore). Their payload is vault-key ciphertext, so field rules can't apply; these are
/// the only constraints. SwiftData has no CHECK constraints, so this guard *is* the
/// constraint: every write path that stores ciphertext as is must call it.
nonisolated enum VaultRecordGuard {
    static let kinds: Set<String> = ["login", "secureNote", "payment", "personalInfo", "idDocument"]

    /// Kind is a known one, envelope and payload are non-empty unless the row is a
    /// tombstone, and the schema version is at least 1.
    static func isStructurallyValid(kind: String, envelope: Data, payload: Data, schemaVersion: Int64, isDeleted: Bool) -> Bool {
        guard kinds.contains(kind), schemaVersion >= 1 else { return false }
        return isDeleted || (!envelope.isEmpty && !payload.isEmpty)
    }
}
