import SwiftUI

struct LegalPlaceholderView: View {
    let title: String
    var onOpenMenu: (() -> Void)? = nil
    let bodyText: String

    /// Placeholder publish date for legal copy until real docs ship.
    private static let lastUpdated = Calendar.current.date(from: DateComponents(year: 2026, month: 8, day: 25))!

    private var locale: Locale { AppLanguage.current.locale }

    private var lastUpdatedText: String {
        let formatted = Self.lastUpdated.formatted(
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
