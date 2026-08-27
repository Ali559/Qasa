import SwiftUI

/// Strict date field: DatePicker + optional typed "Nov 6 2000" parse.
struct ValidatedDateField: View {
    let title: String
    @Binding var text: String

    @State private var picked = Date()
    @State private var showPicker = false
    @State private var parseError: String?

    private static let displayFormatter: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "MMM d yyyy"
        return f
    }()

    private static let parsers: [DateFormatter] = {
        ["MMM d yyyy", "MMM d, yyyy", "yyyy-MM-dd", "dd/MM/yyyy", "MM/dd/yyyy"].map { format in
            let f = DateFormatter()
            f.locale = Locale(identifier: "en_US_POSIX")
            f.dateFormat = format
            f.isLenient = false
            return f
        }
    }()

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            FormTextField(title: title, text: $text)
                .onChange(of: text) { _, newValue in
                    validateTyped(newValue)
                }

            HStack {
                Button {
                    if let d = Self.parse(text) { picked = d }
                    showPicker = true
                } label: {
                    Label("Pick date", systemImage: "calendar")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(Theme.mint)
                }
                Spacer()
                if let parseError {
                    Text(parseError)
                        .font(.system(size: 11))
                        .foregroundColor(Theme.danger)
                }
            }

            if showPicker {
                DatePicker(
                    "",
                    selection: $picked,
                    displayedComponents: .date
                )
                .datePickerStyle(.graphical)
                .tint(Theme.mint)
                .onChange(of: picked) { _, newValue in
                    text = Self.displayFormatter.string(from: newValue)
                    parseError = nil
                }
                Button("Done") { showPicker = false }
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(Theme.mint)
            }
        }
    }

    private func validateTyped(_ raw: String) {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            parseError = nil
            return
        }
        if Self.parse(trimmed) != nil {
            parseError = nil
        } else {
            parseError = "Use e.g. Nov 6 2000"
        }
    }

    static func parse(_ raw: String) -> Date? {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        for f in parsers {
            if let d = f.date(from: trimmed) { return d }
        }
        return nil
    }

    static func isValid(_ raw: String) -> Bool {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty || parse(trimmed) != nil
    }
}
