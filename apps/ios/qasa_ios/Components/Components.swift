import SwiftUI

// MARK: - Brand header
struct KasoBrandHeader: View {
    var titleSize: CGFloat = 24
    var trailing: AnyView? = nil

    var body: some View {
        HStack(spacing: 12) {
            HStack(spacing: 12) {
                Image(systemName: "shield.fill")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(Theme.mint)
                    .frame(width: 16, height: 20)
                Text("Kaso")
                    .font(.system(size: titleSize, weight: .heavy))
                    .foregroundColor(Theme.mint)
            }
            Spacer()
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

// MARK: - Status / offline pill
struct StatusPill: View {
    let text: String
    var dotColor: Color? = nil
    var textColor: Color = Theme.textOnChrome
    var background: Color = Theme.cardBackgroundLight

    var body: some View {
        HStack(spacing: 8) {
            if let dotColor {
                Circle().fill(dotColor).frame(width: 8, height: 8)
            }
            Text(text)
                .font(.kasoMono(12))
                .tracking(0.6)
                .foregroundColor(textColor)
                .lineLimit(2)
                .multilineTextAlignment(.leading)
        }
        .padding(.horizontal, 13)
        .padding(.vertical, 5)
        .background(background)
        .overlay(
            Capsule().stroke(Theme.strokeChrome, lineWidth: 1)
        )
        .clipShape(Capsule())
    }
}

// MARK: - Primary Button
struct PrimaryButton: View {
    let title: String
    var icon: String? = nil
    var color: Color = Theme.mint
    var textColor: Color = Theme.mintOnPrimary
    var cornerRadius: CGFloat = Theme.cornerRadius
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if let icon {
                    Image(systemName: icon)
                        .font(.system(size: 16, weight: .semibold))
                }
                Text(title)
                    .font(.system(size: 18, weight: .semibold))
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
            .background(color)
            .foregroundColor(textColor)
            .cornerRadius(cornerRadius)
            .shadow(color: color == Theme.mint ? Theme.mint.opacity(0.3) : .clear, radius: 10, y: 0)
        }
    }
}

// MARK: - Card Container
struct CardContainer<Content: View>: View {
    var padding: CGFloat = 17
    var background: Color = Theme.cardBackground
    var borderColor: Color = Theme.stroke
    var cornerRadius: CGFloat = Theme.cornerRadius
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            content
        }
        .padding(padding)
        .background(background)
        .cornerRadius(cornerRadius)
        .overlay(
            RoundedRectangle(cornerRadius: cornerRadius)
                .stroke(borderColor, lineWidth: 1)
        )
    }
}

// MARK: - Section Label
struct SectionLabel: View {
    let text: String
    var body: some View {
        Text(text.uppercased())
            .font(.kasoMono(12))
            .tracking(0.6)
            .foregroundColor(Theme.textSecondary)
    }
}

// MARK: - Text field chrome
struct ThemedTextField: View {
    let icon: String
    let placeholder: String
    @Binding var text: String
    var isSecure: Bool = false
    var trailing: AnyView? = nil

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .foregroundColor(Theme.textSecondary)
                .frame(width: 20)
            Group {
                if isSecure {
                    SecureField(placeholder, text: $text)
                } else {
                    TextField(placeholder, text: $text)
                }
            }
            .foregroundColor(Theme.textPrimary)
            .tint(Theme.mint)
            if let trailing { trailing }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 18)
        .background(Theme.cardBackground)
        .cornerRadius(Theme.cornerRadius)
        .overlay(
            RoundedRectangle(cornerRadius: Theme.cornerRadius)
                .stroke(Theme.stroke, lineWidth: 1)
        )
    }
}

// MARK: - Segmented strength bar
struct StrengthBar: View {
    let segments: Int
    let filled: Int
    var color: Color = Theme.mint
    var height: CGFloat = 6

    var body: some View {
        HStack(spacing: 2) {
            ForEach(0..<segments, id: \.self) { i in
                Capsule()
                    .fill(i < filled ? color : Theme.stroke)
                    .frame(height: height)
            }
        }
    }
}

// MARK: - Stat Box (VPN)
struct StatBox: View {
    let label: String
    let value: String
    let unit: String
    let icon: String
    let iconColor: Color

    var body: some View {
        CardContainer {
            VStack(spacing: 8) {
                Image(systemName: icon)
                    .foregroundColor(iconColor)
                    .font(.system(size: 16, weight: .semibold))
                Text(label.uppercased())
                    .font(.kasoMono(12))
                    .tracking(0.6)
                    .foregroundColor(Theme.textSecondary)
                HStack(alignment: .firstTextBaseline, spacing: 4) {
                    Text(value)
                        .font(.system(size: 20, weight: .semibold))
                        .foregroundColor(Theme.textPrimary)
                    Text(unit)
                        .font(.system(size: 14))
                        .foregroundColor(Theme.textSecondary)
                }
            }
            .frame(maxWidth: .infinity)
        }
    }
}

// MARK: - Toggle Row
struct ToggleRow: View {
    let icon: String
    let iconColor: Color
    let title: String
    let subtitle: String
    @Binding var isOn: Bool
    var iconBackground: Color? = nil

    var body: some View {
        HStack(spacing: 12) {
            ZStack {
                Circle()
                    .fill(iconBackground ?? iconColor.opacity(0.2))
                    .frame(width: 40, height: 40)
                Image(systemName: icon)
                    .foregroundColor(iconColor)
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .foregroundColor(Theme.textPrimary)
                    .font(.system(size: 16))
                if !subtitle.isEmpty {
                    Text(subtitle)
                        .foregroundColor(Theme.textSecondary)
                        .font(.system(size: 14))
                }
            }
            Spacer()
            Toggle("", isOn: $isOn)
                .labelsHidden()
                .tint(Theme.mint)
        }
        .padding(.vertical, 8)
    }
}

// MARK: - Nav Chevron Row
struct NavChevronRow: View {
    let icon: String
    let title: String
    var subtitle: String? = nil
    var trailingText: String? = nil
    var destructive: Bool = false
    var iconColor: Color? = nil

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .foregroundColor(destructive ? Theme.danger : (iconColor ?? Theme.textPrimary))
                .frame(width: 28)
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .foregroundColor(destructive ? Theme.danger : Theme.textPrimary)
                    .font(.subheadline.weight(.medium))
                if let subtitle {
                    Text(subtitle)
                        .foregroundColor(Theme.textSecondary)
                        .font(.caption)
                }
            }
            Spacer()
            if let trailingText {
                Text(trailingText)
                    .font(.caption)
                    .foregroundColor(Theme.textSecondary)
            }
            if !destructive {
                Image(systemName: "chevron.right")
                    .font(.caption)
                    .foregroundColor(Theme.textTertiary)
            }
        }
        .padding(.vertical, 10)
    }
}

// MARK: - Pill badge
struct PillBadge: View {
    let text: String
    let color: Color
    var solid: Bool = false

    var body: some View {
        Text(text)
            .font(.kasoMono(12))
            .tracking(0.6)
            .padding(.horizontal, 8)
            .padding(.vertical, 2)
            .background(solid ? color : color.opacity(0.1))
            .foregroundColor(solid ? Theme.mintOnChip : color)
            .overlay(
                RoundedRectangle(cornerRadius: 4)
                    .stroke(solid ? Color.clear : color.opacity(0.2), lineWidth: 1)
            )
            .cornerRadius(4)
    }
}

// MARK: - Check Toggle Style (Generator)
struct CheckToggleStyle: ToggleStyle {
    func makeBody(configuration: Configuration) -> some View {
        Button {
            configuration.isOn.toggle()
        } label: {
            ZStack {
                Capsule()
                    .fill(configuration.isOn ? Theme.mint : Theme.stroke)
                    .frame(width: 48, height: 24)

                Circle()
                    .fill(configuration.isOn ? Color(hex: "2563EB") : Theme.stroke)
                    .frame(width: 24, height: 24)
                    .overlay(
                        Image(systemName: "checkmark")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(.white)
                            .opacity(configuration.isOn ? 1 : 0)
                    )
                    .offset(x: configuration.isOn ? 12 : -12)
                    .animation(.spring(response: 0.25, dampingFraction: 0.8), value: configuration.isOn)
            }
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Bilingual Toggle Row
struct BilingualToggleRow: View {
    let titleEN: String
    let titleCKB: String
    @Binding var isOn: Bool
    var disabled: Bool = false

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 3) {
                Text(titleEN)
                    .font(.system(size: 14))
                    .foregroundColor(disabled ? Theme.textTertiary : Theme.textPrimary)
                Text(titleCKB)
                    .font(.system(size: 12))
                    .foregroundColor(Theme.textSecondary)
                    .tracking(0.6)
                    .environment(\.layoutDirection, .rightToLeft)
            }
            Spacer()
            Toggle("", isOn: $isOn)
                .labelsHidden()
                .toggleStyle(CheckToggleStyle())
                .disabled(disabled)
                .opacity(disabled ? 0.7 : 1)
        }
        .padding(.vertical, 8)
        .opacity(disabled ? 0.7 : 1)
    }
}

// MARK: - Bilingual Nav Chevron Row
struct BilingualNavChevronRow: View {
    let icon: String
    let titleEN: String
    let titleCKB: String
    var iconColor: Color = Theme.mint
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: icon)
                    .foregroundColor(iconColor)
                    .frame(width: 28)
                VStack(alignment: .leading, spacing: 2) {
                    Text(titleEN)
                        .foregroundColor(Theme.textPrimary)
                        .font(.subheadline.weight(.medium))
                    Text(titleCKB)
                        .foregroundColor(Theme.textSecondary)
                        .font(.caption2)
                        .environment(\.layoutDirection, .rightToLeft)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.caption)
                    .foregroundColor(Theme.textTertiary)
            }
            .padding(.vertical, 10)
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Filter chip
struct FilterChip: View {
    let title: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 14, weight: isSelected ? .semibold : .regular))
                .padding(.horizontal, 17)
                .padding(.vertical, 7)
                .background(isSelected ? Theme.mintDark : Theme.cardBackground)
                .foregroundColor(isSelected ? Theme.mintOnChip : Theme.textSecondary)
                .overlay(
                    Capsule().stroke(isSelected ? Theme.mint : Theme.stroke, lineWidth: 1)
                )
                .clipShape(Capsule())
        }
    }
}

// MARK: - Floating action button
struct MintFAB: View {
    let systemImage: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(Theme.mintOnPrimary)
                .frame(width: 56, height: 56)
                .background(Theme.mint)
                .cornerRadius(Theme.cornerRadiusLarge)
                .overlay(
                    RoundedRectangle(cornerRadius: Theme.cornerRadiusLarge)
                        .stroke(Theme.mint.opacity(0.5), lineWidth: 1)
                )
                .shadow(color: .black.opacity(0.2), radius: 8, y: 4)
        }
    }
}
