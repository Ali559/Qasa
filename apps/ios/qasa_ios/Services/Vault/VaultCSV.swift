import Foundation
import SwiftData

enum VaultCSVError: LocalizedError {
    case empty
    case invalidHeader(missing: String)

    var errorDescription: String? {
        let locale = L10n.preferredLocale
        switch self {
        case .empty:
            return L10n.tr("settings.importNothing", locale: locale)
        case .invalidHeader(let missing):
            return String(
                format: L10n.tr("settings.importBadHeader", locale: locale),
                missing
            )
        }
    }
}

/// Plaintext CSV backup of the vault.
///
/// The previous implementation emitted a header row and then, for every item, a line
/// with only the display name and kind filled in — `username`, `password`, `url` and
/// `notes` were hardcoded empty. It was presented to the user as a successful export,
/// so anyone who relied on it had no backup at all.
///
/// Format: a wide set of human-readable columns for the common fields, plus a `fields`
/// column holding compact JSON of the concrete `*Fields` struct. Import prefers `fields`
/// so the round trip is exact; the wide columns keep the file usable in a spreadsheet.
enum VaultCSV {
    static let columns = [
        "type", "title", "username", "password", "url", "email",
        "account_holder", "number", "security_code", "expiry",
        "account_number", "routing_number", "bank_name", "content",
        "notes", "fields"
    ]

    // MARK: - Export

    static func exportCSV(payloads: [VaultPayload]) -> String {
        var lines = [columns.joined(separator: ",")]
        for payload in payloads {
            lines.append(row(for: payload))
        }
        return lines.joined(separator: "\n")
    }

    private static func row(for payload: VaultPayload) -> String {
        var cells: [String: String] = [:]
        cells["type"] = typeName(for: payload)
        cells["title"] = payload.displayName

        switch payload {
        case .login(let f):
            cells["username"] = f.username
            cells["password"] = f.password
            cells["url"] = f.website
            cells["email"] = f.email
            cells["notes"] = f.note
        case .secureNote(let f):
            cells["content"] = f.content
            cells["notes"] = String(f.protectItem)
        case .card(let f):
            cells["account_holder"] = f.accountHolder
            cells["number"] = f.number
            cells["security_code"] = f.securityCode
            cells["expiry"] = f.expiryDate
            cells["notes"] = f.note
        case .bankAccount(let f):
            cells["account_holder"] = f.accountHolder
            cells["account_number"] = f.accountNumber
            cells["routing_number"] = f.routingNumber
            cells["bank_name"] = f.bankName
        case .name(let f):
            cells["email"] = f.defaultUsername
            cells["expiry"] = f.birthDate
            cells["content"] = f.birthPlace
        case .personalEmail(let f):
            cells["email"] = f.email
        case .phone(let f):
            cells["number"] = f.number
        case .address(let f):
            cells["content"] = [f.address, f.city, f.zipCode].filter { !$0.isEmpty }.joined(separator: " ")
            cells["notes"] = [f.building, f.floor, f.apartment, f.doorCode]
                .filter { !$0.isEmpty }.joined(separator: " ")
            cells["account_holder"] = f.recipient
        case .company(let f):
            cells["account_holder"] = f.jobTitle
        case .website(let f):
            cells["url"] = f.websiteURL
        case .idCard(let f), .passport(let f), .driverLicense(let f):
            cells["account_holder"] = f.name
            cells["number"] = f.number
            cells["expiry"] = f.expiryDate
            cells["content"] = f.issueDate
            cells["notes"] = f.issuePlace
        }

        cells["fields"] = fieldsJSON(for: payload)
        return columns.map { csvEscape(cells[$0] ?? "") }.joined(separator: ",")
    }

    /// Compact JSON of the concrete `*Fields` struct. Deterministic key order so that
    /// exporting the same vault twice produces byte-identical files.
    private static func fieldsJSON(for payload: VaultPayload) -> String {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        let data: Data?
        switch payload {
        case .login(let f): data = try? encoder.encode(f)
        case .secureNote(let f): data = try? encoder.encode(f)
        case .card(let f): data = try? encoder.encode(f)
        case .bankAccount(let f): data = try? encoder.encode(f)
        case .name(let f): data = try? encoder.encode(f)
        case .personalEmail(let f): data = try? encoder.encode(f)
        case .phone(let f): data = try? encoder.encode(f)
        case .address(let f): data = try? encoder.encode(f)
        case .company(let f): data = try? encoder.encode(f)
        case .website(let f): data = try? encoder.encode(f)
        case .idCard(let f), .passport(let f), .driverLicense(let f): data = try? encoder.encode(f)
        }
        guard let data else { return "" }
        return String(data: data, encoding: .utf8) ?? ""
    }

    // MARK: - Import

    struct ImportResult {
        var imported: [VaultPayload]
    }

    /// Stable identity for a payload, used to skip rows that are already in the vault
    /// so a repeated import does not duplicate every item.
    static func fingerprint(_ payload: VaultPayload) -> String {
        typeName(for: payload) + "|" + fieldsJSON(for: payload)
    }

    static func parseImport(_ csv: String) throws -> ImportResult {
        // Split into RFC 4180 *records*, not physical lines: a quoted field may legally
        // contain newlines, so a secure note's body would otherwise be torn into several
        // broken rows on import.
        let records = parseRecords(csv)
            .map { $0.trimmingCharacters(in: CharacterSet(charactersIn: "\r")) }
            .filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty }
        guard let header = records.first else { throw VaultCSVError.empty }

        let headerCells = parseLine(header).map {
            $0.trimmingCharacters(in: .whitespaces).lowercased()
        }
        guard headerCells.contains("type"), headerCells.contains("title") else {
            throw VaultCSVError.invalidHeader(missing: "'type', 'title'")
        }

        func index(_ name: String) -> Int? { headerCells.firstIndex(of: name) }

        let titleIdx = index("title")
        let fieldsIdx = index("fields")

        var out: [VaultPayload] = []
        for record in records.dropFirst() {
            let cells = parseLine(record)
            func value(_ name: String) -> String {
                guard let i = index(name), i < cells.count else { return "" }
                return cells[i]
            }

            let title = titleIdx.flatMap { $0 < cells.count ? cells[$0] : nil } ?? ""
            guard !title.isEmpty else { continue }

            // Prefer the exact JSON round-trip when present.
            let fieldsRaw = fieldsIdx.flatMap { $0 < cells.count ? cells[$0] : nil } ?? ""
            let type = value("type")

            if !fieldsRaw.isEmpty, let data = fieldsRaw.data(using: .utf8),
               let payload = decodePayload(type: type, json: data) {
                out.append(payload)
                continue
            }

            // Fall back to the wide columns. Only the kinds those columns fully
            // describe can be reconstructed; anything else is skipped rather than
            // silently losing its secrets.
            switch type {
            case "login":
                out.append(.login(LoginFields(
                    itemName: title,
                    email: value("email"),
                    username: value("username"),
                    password: value("password"),
                    website: value("url"),
                    note: value("notes")
                )))
            case "secureNote":
                out.append(.secureNote(SecureNoteFields(
                    title: title,
                    content: value("content")
                )))
            case "card":
                out.append(.card(CardFields(
                    itemName: title,
                    accountHolder: value("account_holder"),
                    number: value("number"),
                    securityCode: value("security_code"),
                    expiryDate: value("expiry"),
                    note: value("notes")
                )))
            case "bankAccount":
                out.append(.bankAccount(BankAccountFields(
                    itemName: title,
                    accountHolder: value("account_holder"),
                    accountNumber: value("account_number"),
                    routingNumber: value("routing_number"),
                    bankName: value("bank_name")
                )))
            default:
                continue
            }
        }
        return ImportResult(imported: out)
    }

    private static func decodePayload(type: String, json: Data) -> VaultPayload? {
        let decoder = JSONDecoder()
        switch type {
        case "login": return (try? decoder.decode(LoginFields.self, from: json)).map(VaultPayload.login)
        case "secureNote": return (try? decoder.decode(SecureNoteFields.self, from: json)).map(VaultPayload.secureNote)
        case "card": return (try? decoder.decode(CardFields.self, from: json)).map(VaultPayload.card)
        case "bankAccount": return (try? decoder.decode(BankAccountFields.self, from: json)).map(VaultPayload.bankAccount)
        case "name": return (try? decoder.decode(NameFields.self, from: json)).map(VaultPayload.name)
        case "personalEmail": return (try? decoder.decode(PersonalEmailFields.self, from: json)).map(VaultPayload.personalEmail)
        case "phone": return (try? decoder.decode(PhoneFields.self, from: json)).map(VaultPayload.phone)
        case "address": return (try? decoder.decode(AddressFields.self, from: json)).map(VaultPayload.address)
        case "company": return (try? decoder.decode(CompanyFields.self, from: json)).map(VaultPayload.company)
        case "website": return (try? decoder.decode(WebsiteFields.self, from: json)).map(VaultPayload.website)
        case "idCard": return (try? decoder.decode(IDDocumentFields.self, from: json)).map(VaultPayload.idCard)
        case "passport": return (try? decoder.decode(IDDocumentFields.self, from: json)).map(VaultPayload.passport)
        case "driverLicense": return (try? decoder.decode(IDDocumentFields.self, from: json)).map(VaultPayload.driverLicense)
        default: return nil
        }
    }

    private static func typeName(for payload: VaultPayload) -> String {
        switch payload {
        case .login: return "login"
        case .secureNote: return "secureNote"
        case .card: return "card"
        case .bankAccount: return "bankAccount"
        case .name: return "name"
        case .personalEmail: return "personalEmail"
        case .phone: return "phone"
        case .address: return "address"
        case .company: return "company"
        case .website: return "website"
        case .idCard: return "idCard"
        case .passport: return "passport"
        case .driverLicense: return "driverLicense"
        }
    }

    // MARK: - RFC 4180 primitives

    static func csvEscape(_ value: String) -> String {
        if value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r") {
            return "\"\(value.replacingOccurrences(of: "\"", with: "\"\""))\""
        }
        return value
    }

    /// Splits CSV text into records on newlines that are *not* inside a quoted field.
    ///
    /// `csv.split(whereSeparator: \.isNewline)` gets this wrong: a secure note
    /// containing a blank line exports as one quoted field but re-imports as two
    /// fragments, quietly corrupting the note.
    static func parseRecords(_ csv: String) -> [String] {
        var records: [String] = []
        var current = ""
        var inQuotes = false
        var iterator = csv.makeIterator()
        var pending: Character?

        while let ch = pending ?? iterator.next() {
            pending = nil
            if ch == "\"" {
                current.append(ch)
                if inQuotes {
                    // Peek: a doubled quote is a literal quote, not the field terminator.
                    if let next = iterator.next() {
                        if next == "\"" {
                            current.append("\"")
                        } else {
                            inQuotes = false
                            pending = next
                        }
                    } else {
                        inQuotes = false
                    }
                } else {
                    inQuotes = true
                }
            } else if ch.isNewline && !inQuotes {
                records.append(current)
                current = ""
            } else {
                current.append(ch)
            }
        }
        records.append(current)
        return records
    }

    /// RFC 4180 field splitter. Handles quoted fields containing commas, newlines, and
    /// doubled quotes (`""` → literal `"`).
    ///
    /// The previous implementation toggled `inQuotes` on *every* quote character, so an
    /// escaped quote inside a quoted field silently terminated the field early and
    /// shifted every remaining column.
    static func parseLine(_ line: String) -> [String] {
        var result: [String] = []
        var current = ""
        var inQuotes = false
        var iterator = line.makeIterator()
        var pending: Character?

        while let ch = pending ?? iterator.next() {
            pending = nil
            if inQuotes {
                if ch == "\"" {
                    if let next = iterator.next() {
                        if next == "\"" {
                            current.append("\"")   // escaped quote
                        } else {
                            inQuotes = false
                            pending = next
                        }
                    } else {
                        inQuotes = false
                    }
                } else {
                    current.append(ch)
                }
            } else {
                switch ch {
                case "\"": inQuotes = true
                case ",":
                    result.append(current)
                    current = ""
                default: current.append(ch)
                }
            }
        }
        result.append(current)
        return result
    }
}
