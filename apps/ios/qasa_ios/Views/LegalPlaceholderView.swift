import SwiftUI

struct LegalPlaceholderView: View {
    let title: String
    var onOpenMenu: (() -> Void)? = nil
    let bodyText: String
    /// When this document last changed (the privacy summary: 2026-11-09, with the policy
    /// on qasa-app.com/privacy).
    var lastUpdated: Date = LegalPlaceholderView.termsUpdated

    static let termsUpdated = Calendar.current.date(from: DateComponents(year: 2026, month: 8, day: 25))!
    static let privacyUpdated = Calendar.current.date(from: DateComponents(year: 2026, month: 11, day: 9))!

    private var locale: Locale { AppLanguage.current.locale }

    private var lastUpdatedText: String {
        let formatted = lastUpdated.formatted(
            Date.FormatStyle(date: .abbreviated, time: .omitted).locale(locale)
        )
        return String(format: L10n.tr("legal.lastUpdated", locale: locale), formatted)
    }

    var body: some View {
        VStack(spacing: 0) {
            AppMenuHeader(title: title, onMenu: onOpenMenu)

            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text(title)
                        .font(.qasa(24, weight: .bold))
                        .foregroundColor(Theme.textPrimary)
                        .frame(maxWidth: .infinity, alignment: .leading)

                    Text(lastUpdatedText)
                        .font(.qasa(13))
                        .foregroundColor(Theme.textTertiary)
                        .frame(maxWidth: .infinity, alignment: .leading)

                    Text(bodyText)
                        .font(.qasa(15))
                        .foregroundColor(Theme.textSecondary)
                        .lineSpacing(4)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .padding(Theme.screenPadding)
                .padding(.bottom, 40)
            }
        }
        .background(Theme.background.ignoresSafeArea())
    }
}
