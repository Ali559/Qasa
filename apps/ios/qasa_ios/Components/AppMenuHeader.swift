import SwiftUI

struct AppMenuHeader: View {
    @Environment(\.themePalette) private var theme
    @Environment(AppPreferences.self) private var prefs
    var title: String
    /// Back action for pages opened on top of a tab; tab roots pass `nil` and show no button.
    var onMenu: (() -> Void)?
    var trailing: AnyView? = nil

    var body: some View {
        HStack(spacing: 12) {
            if let onMenu {
                HeaderBackButton(action: onMenu)
            }

            Text(title)
                .font(.qasa(20, weight: .bold))
                .foregroundColor(theme.textPrimary)
                .lineLimit(1)

            Spacer(minLength: 8)

            if let trailing { trailing }
        }
        .padding(.horizontal, 16)
        .frame(height: 64)
        // No divider: the calm redesign lets the header sit on the page background.
        .background(theme.chrome)
    }
}

struct BrandMenuHeader: View {
    @Environment(\.themePalette) private var theme
    @Environment(AppPreferences.self) private var prefs
    /// Back action for pages opened on top of a tab; tab roots pass `nil` and show no button.
    var onMenu: (() -> Void)?
    var trailing: AnyView? = nil
    var titleSize: CGFloat = 20

    var body: some View {
        HStack(spacing: 12) {
            if let onMenu {
                HeaderBackButton(action: onMenu)
            }

            HStack(spacing: 10) {
                QasaMark(size: 22)
                    .foregroundColor(theme.accent)
                Text(L10n.tr("brand.name", locale: prefs.locale))
                    .font(.qasa(titleSize, weight: .bold))
                    .foregroundColor(theme.accent)
            }

            Spacer(minLength: 8)

            if let trailing { trailing }
        }
        .padding(.horizontal, 16)
        .frame(height: 64)
        // No divider: the calm redesign lets the header sit on the page background.
        .background(theme.chrome)
    }
}

/// 40pt circle with a back chevron. The side menu became bottom tabs, so pages one level
/// below a tab get this instead of the menu button; the chevron flips in RTL.
private struct HeaderBackButton: View {
    @Environment(\.themePalette) private var theme
    @Environment(AppPreferences.self) private var prefs
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: "chevron.backward")
                .font(.qasa(17, weight: .semibold))
                .foregroundColor(theme.textPrimary)
                .frame(width: 40, height: 40)
                .background(theme.cardBackground)
                .clipShape(Circle())
        }
        .accessibilityLabel(L10n.tr("common.back", locale: prefs.locale))
    }
}
