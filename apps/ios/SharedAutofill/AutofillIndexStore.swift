import Foundation

enum AutofillIndexStore {
    static func load() -> AutofillIndexFile {
        guard let url = AppGroup.autofillIndexURL,
              let data = try? Data(contentsOf: url) else {
            return AutofillIndexFile(updatedAt: .distantPast, entries: [])
        }
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        guard let file = try? decoder.decode(AutofillIndexFile.self, from: data) else {
            return AutofillIndexFile(updatedAt: .distantPast, entries: [])
        }
        return file
    }

    static func save(_ file: AutofillIndexFile) {
        guard let url = AppGroup.autofillIndexURL else { return }
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        guard let data = try? encoder.encode(file) else { return }
        try? data.write(to: url, options: [.atomic])
    }

    static func replaceAll(entries: [AutofillIndexEntry]) {
        save(AutofillIndexFile(updatedAt: Date(), entries: entries))
    }

    static func clear() {
        guard let url = AppGroup.autofillIndexURL else { return }
        try? FileManager.default.removeItem(at: url)
    }
}
