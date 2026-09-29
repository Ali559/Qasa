import SwiftUI
import UniformTypeIdentifiers

/// Wraps exported CSV text in a document so it can be handed to `ShareLink` and saved
/// to a real file.
///
/// The export used to be written straight to `UIPasteboard.general`, which puts every
/// password in the vault into a system-wide pasteboard that any app can read and that
/// persists in the system clipboard history. A file the user explicitly saves is the
/// only acceptable channel for a plaintext vault backup.
struct CSVDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.commaSeparatedText, .plainText] }

    var text: String

    init(text: String) {
        self.text = text
    }

    init(configuration: ReadConfiguration) throws {
        guard let data = configuration.file.regularFileContents,
              let text = String(data: data, encoding: .utf8) else {
            throw CocoaError(.fileReadCorruptFile)
        }
        self.text = text
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: Data(text.utf8))
    }

    /// Spills the CSV to a temporary file so `FileRepresentation` can hand a real URL to
    /// the share sheet. This is plaintext vault data, so it lives in the app's temporary
    /// directory (purged by the OS) and is excluded from backups.
    var spillURL: URL {
        let dir = FileManager.default.temporaryDirectory
            .appending(path: "qasa-export", directoryHint: .isDirectory)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let file = dir.appending(path: "qasa-vault-backup.csv")
        try? Data(text.utf8).write(to: file, options: .atomic)
        var mutable = file
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        try? mutable.setResourceValues(values)
        return file
    }
}

/// `FileDocument` alone is not `Transferable`, so `ShareLink` needs the conformance
/// declared explicitly along with a `FileRepresentation`.
extension CSVDocument: Transferable {
    static var transferRepresentation: some TransferRepresentation {
        FileRepresentation(exportedContentType: .commaSeparatedText) { document in
            SentTransferredFile(document.spillURL)
        }
    }
}
