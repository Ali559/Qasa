import SwiftUI

struct AppMenuHeader: View {
    var title: String
    var onMenu: () -> Void
    var trailing: AnyView? = nil

    var body: some View {
        HStack(spacing: 12) {
            Button(action: onMenu) {
                Image(systemName: "line.3.horizontal")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundColor(Theme.textPrimary)
                    .frame(width: 40, height: 40)
                    .background(Theme.cardBackground)
                    .clipShape(Circle())
                    .overlay(Circle().stroke(Theme.strokeChrome, lineWidth: 1))
            }
            .accessibilityLabel("Open menu")

            Text(title)
                .font(.system(size: 20, weight: .bold))
                .foregroundColor(Theme.textPrimary)
                .lineLimit(1)

            Spacer(minLength: 8)

            if let trailing { trailing }
        }
        .padding(.horizontal, 16)
        .frame(height: 64)
        .background(Theme.chrome)
        .overlay(alignment: .bottom) {
            Rectangle().fill(Theme.strokeChrome).frame(height: 1)
        }
    }
}

struct BrandMenuHeader: View {
    var onMenu: () -> Void
    var trailing: AnyView? = nil
    var titleSize: CGFloat = 20

    var body: some View {
        HStack(spacing: 12) {
            Button(action: onMenu) {
                Image(systemName: "line.3.horizontal")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundColor(Theme.textPrimary)
                    .frame(width: 40, height: 40)
                    .background(Theme.cardBackground)
                    .clipShape(Circle())
                    .overlay(Circle().stroke(Theme.strokeChrome, lineWidth: 1))
            }
            .accessibilityLabel("Open menu")

            HStack(spacing: 10) {
                Image(systemName: "shield.fill")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(Theme.mint)
                Text("Kaso")
                    .font(.system(size: titleSize, weight: .heavy))
                    .foregroundColor(Theme.mint)
            }

            Spacer(minLength: 8)

            if let trailing { trailing }
        }
        .padding(.horizontal, 16)
        .frame(height: 64)
        .background(Theme.chrome)
        .overlay(alignment: .bottom) {
            Rectangle().fill(Theme.strokeChrome).frame(height: 1)
        }
    }
}
