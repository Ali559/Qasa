import Foundation
import SwiftData

enum VaultCSVError: LocalizedError {
    case empty
    case invalidHeader

    var errorDescription: String? {
        switch self {
        case .empty: return "Nothing to export."
        case .invalidHeader: return "CSV header must include title,kind,username,password,notes."
        }
    }
}

enum VaultCSV {
    static func exportCSV(rows: [VaultListRow]) -> String {
        var lines = ["title,kind,username,password,url,notes"]
        for row in rows {
            let title = csvEscape(row.displayName)
            let kind = csvEscape(row.kind.rawValue)
            lines.append("\(title),\(kind),,,,")
        }
        return lines.joined(separator: "\n")
    }

    static func parseImport(_ csv: String) throws -> [(title: String, kind: String, username: String, password: String, notes: String)] {
        let lines = csv.split(whereSeparator: \.isNewline).map(String.init).filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty }
        guard let header = lines.first else { throw VaultCSVError.empty }
        let cols = header.lowercased().split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) }
        guard cols.contains("title") else { throw VaultCSVError.invalidHeader }

        let titleIdx = cols.firstIndex(of: "title") ?? 0
        let kindIdx = cols.firstIndex(of: "kind")
        let userIdx = cols.firstIndex(of: "username")
        let passIdx = cols.firstIndex(of: "password")
        let notesIdx = cols.firstIndex(of: "notes")

        var out: [(String, String, String, String, String)] = []
        for line in lines.dropFirst() {
            let parts = parseLine(line)
            let title = parts[safe: titleIdx] ?? ""
            guard !title.isEmpty else { continue }
            out.append((
                title,
                parts[safe: kindIdx ?? -1] ?? "login",
                parts[safe: userIdx ?? -1] ?? "",
                parts[safe: passIdx ?? -1] ?? "",
                parts[safe: notesIdx ?? -1] ?? ""
            ))
        }
        return out
    }

    private static func csvEscape(_ value: String) -> String {
        if value.contains(",") || value.contains("\"") || value.contains("\n") {
            return "\"\(value.replacingOccurrences(of: "\"", with: "\"\""))\""
        }
        return value
    }

    private static func parseLine(_ line: String) -> [String] {
        var result: [String] = []
        var current = ""
        var inQuotes = false
        for ch in line {
            if ch == "\"" {
                inQuotes.toggle()
            } else if ch == "," && !inQuotes {
                result.append(current)
                current = ""
            } else {
                current.append(ch)
            }
        }
        result.append(current)
        return result
    }
}

private extension Array {
    subscript(safe index: Int) -> Element? {
        guard index >= 0, index < count else { return nil }
        return self[index]
    }
}
