import SwiftUI

struct LegalPlaceholderView: View {
    let title: String
    var onOpenMenu: () -> Void
    let bodyText: String

    var body: some View {
        VStack(spacing: 0) {
            AppMenuHeader(title: title, onMenu: onOpenMenu)

            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text(title)
                        .font(.system(size: 24, weight: .bold))
                        .foregroundColor(Theme.textPrimary)

                    Text("Last updated: Aug 25, 2026")
                        .font(.system(size: 13))
                        .foregroundColor(Theme.textTertiary)

                    Text(bodyText)
                        .font(.system(size: 15))
                        .foregroundColor(Theme.textSecondary)
                        .lineSpacing(4)
                }
                .padding(Theme.screenPadding)
                .padding(.bottom, 40)
            }
        }
        .background(Theme.background.ignoresSafeArea())
    }
}
