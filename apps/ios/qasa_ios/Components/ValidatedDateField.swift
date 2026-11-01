import SwiftUI

/// Strict date field: DatePicker + optional typed "Nov 6 2000" parse.
struct ValidatedDateField: View {
    let title: String
    @Binding var text: String

    @State private var picked = Date()
    @State private var showPicker = false
    @State private var parseError: String?

    private var locale: Locale { AppLanguage.current.locale }

    private var displayFormatter: DateFormatter {
        let f = DateFormatter()
        f.locale = locale
        f.setLocalizedDateFormatFromTemplate("MMMdyyyy")
        return f
    }

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
                    Label(L10n.tr("date.pick", locale: locale), systemImage: "calendar")
                        .font(.qasa(13, weight: .semibold))
                        .foregroundColor(Theme.accent)
                }
                Spacer()
                if let parseError {
                    Text(parseError)
                        .font(.qasa(11))
                        .foregroundColor(Theme.danger)
                        .multilineTextAlignment(.trailing)
                }
            }

            if showPicker {
                DatePicker(
                    "",
                    selection: $picked,
                    displayedComponents: .date
                )
                .datePickerStyle(.graphical)
                .environment(\.locale, locale)
                .tint(Theme.accent)
                .onChange(of: picked) { _, newValue in
                    text = displayFormatter.string(from: newValue)
                    parseError = nil
                }
                Button(L10n.tr("date.done", locale: locale)) { showPicker = false }
                    .font(.qasa(13, weight: .semibold))
                    .foregroundColor(Theme.accent)
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
            parseError = L10n.tr("date.invalidFormat", locale: locale)
        }
    }

    static func parse(_ raw: String) -> Date? {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        for f in parsers {
            if let d = f.date(from: trimmed) { return d }
        }
        // Locale-aware fallback for typed dates in ckb/ar.
        let localized = DateFormatter()
        localized.locale = AppLanguage.current.locale
        localized.dateStyle = .medium
        localized.timeStyle = .none
        if let d = localized.date(from: trimmed) { return d }
        return nil
    }

    static func isValid(_ raw: String) -> Bool {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty || parse(trimmed) != nil
    }
}
