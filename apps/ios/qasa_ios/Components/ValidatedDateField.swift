import SwiftUI

/// Date field (birth, issue, ID expiry): the picker stores ISO `YYYY-MM-DD`, typed text is
/// accepted as is (the repository's normalize rewrites readable dates to ISO on save), and
/// the stored value is shown localized next to the picker button. The only error shown is
/// the shared rule message the editor passes in (`VaultValidation`), so there is one
/// source of truth for what a valid date is.
struct ValidatedDateField: View {
    let title: String
    @Binding var text: String
    /// The field's rule message from `VaultValidation`, or `nil`.
    var error: String? = nil

    @State private var picked = Date()
    @State private var showPicker = false

    private var locale: Locale { AppLanguage.current.locale }

    /// The stored date in the app language, when it reads as a date.
    private var localized: String? {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, VaultValidation.date(fromStored: trimmed) != nil else { return nil }
        return VaultValidation.displayDate(trimmed, locale: locale)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            FormTextField(
                title: title,
                text: $text,
                placeholder: L10n.tr("field.datePlaceholder", locale: locale),
                keyboard: .numbersAndPunctuation,
                error: error
            )

            HStack {
                Button {
                    if let d = VaultValidation.date(fromStored: text) { picked = d }
                    showPicker = true
                } label: {
                    Label(L10n.tr("date.pick", locale: locale), systemImage: "calendar")
                        .font(.qasa(13, weight: .semibold))
                        .foregroundColor(Theme.accent)
                }
                Spacer()
                if let localized {
                    Text(localized)
                        .font(.qasa(13))
                        .foregroundColor(Theme.textSecondary)
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
                    text = VaultValidation.isoDate(from: newValue)
                }
                Button(L10n.tr("date.done", locale: locale)) { showPicker = false }
                    .font(.qasa(13, weight: .semibold))
                    .foregroundColor(Theme.accent)
            }
        }
    }
}
