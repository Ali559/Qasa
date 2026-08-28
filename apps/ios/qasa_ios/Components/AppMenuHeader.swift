import SwiftUI

struct AppMenuHeader: View {
    @Environment(\.themePalette) private var theme
    var title: String
    var onMenu: () -> Void
    var trailing: AnyView? = nil

    var body: some View {
        HStack(spacing: 12) {
            Button(action: onMenu) {
                Image(systemName: "line.3.horizontal")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundColor(theme.textPrimary)
                    .frame(width: 40, height: 40)
                    .background(theme.cardBackground)
                    .clipShape(Circle())
                    .overlay(Circle().stroke(theme.strokeChrome, lineWidth: 1))
            }
            .accessibilityLabel(Text("nav.openMenu"))

            Text(title)
                .font(.qasa(20, weight: .bold))
                .foregroundColor(theme.textPrimary)
                .lineLimit(1)

            Spacer(minLength: 8)

            if let trailing { trailing }
        }
        .padding(.horizontal, 16)
        .frame(height: 64)
        .background(theme.chrome)
        .overlay(alignment: .bottom) {
            Rectangle().fill(theme.strokeChrome).frame(height: 1)
        }
    }
}

struct BrandMenuHeader: View {
    @Environment(\.themePalette) private var theme
    var onMenu: () -> Void
    var trailing: AnyView? = nil
    var titleSize: CGFloat = 20

    var body: some View {
        HStack(spacing: 12) {
            Button(action: onMenu) {
                Image(systemName: "line.3.horizontal")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundColor(theme.textPrimary)
                    .frame(width: 40, height: 40)
                    .background(theme.cardBackground)
                    .clipShape(Circle())
                    .overlay(Circle().stroke(theme.strokeChrome, lineWidth: 1))
            }
            .accessibilityLabel(Text("nav.openMenu"))

            HStack(spacing: 10) {
                Image(systemName: "shield.fill")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(theme.mint)
                Text("brand.name")
                    .font(.system(size: titleSize, weight: .heavy))
                    .foregroundColor(theme.mint)
            }

            Spacer(minLength: 8)

            if let trailing { trailing }
        }
        .padding(.horizontal, 16)
        .frame(height: 64)
        .background(theme.chrome)
        .overlay(alignment: .bottom) {
            Rectangle().fill(theme.strokeChrome).frame(height: 1)
        }
    }
}
