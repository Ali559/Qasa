import SwiftUI

// MARK: - Primary Button
struct PrimaryButton: View {
    let title: String
    var icon: String? = nil
    var color: Color = Theme.mint
    var textColor: Color = .black
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if let icon {
                    Image(systemName: icon)
                }
                Text(title)
                    .fontWeight(.semibold)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(color)
            .foregroundColor(textColor)
            .cornerRadius(Theme.cornerRadius)
        }
    }
}

// MARK: - Card Container
struct CardContainer<Content: View>: View {
    var padding: CGFloat = 16
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            content
        }
        .padding(padding)
        .background(Theme.cardBackground)
        .cornerRadius(Theme.cornerRadius)
        .overlay(
            RoundedRectangle(cornerRadius: Theme.cornerRadius)
                .stroke(Theme.stroke, lineWidth: 1)
        )
    }
}

// MARK: - Section Label
struct SectionLabel: View {
    let text: String
    var body: some View {
        Text(text.uppercased())
            .font(.caption2)
            .fontWeight(.semibold)
            .foregroundColor(Theme.textSecondary)
            .tracking(1)
    }
}

// MARK: - Stat Box (used in VPN screen)
struct StatBox: View {
    let label: String
    let value: String
    let unit: String
    let icon: String
    let iconColor: Color

    var body: some View {
        CardContainer {
            HStack {
                VStack(alignment: .leading, spacing: 6) {
                    HStack(spacing: 4) {
                        Image(systemName: icon)
                            .foregroundColor(iconColor)
                        Text(label.uppercased())
                            .font(.caption2)
                            .foregroundColor(Theme.textSecondary)
                    }
                    HStack(alignment: .firstTextBaseline, spacing: 4) {
                        Text(value)
                            .font(.title3.bold())
                            .foregroundColor(Theme.textPrimary)
                        Text(unit)
                            .font(.caption)
                            .foregroundColor(Theme.textSecondary)
                    }
                }
                Spacer()
            }
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

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .foregroundColor(iconColor)
                .frame(width: 28)
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .foregroundColor(Theme.textPrimary)
                    .font(.subheadline.weight(.medium))
                Text(subtitle)
                    .foregroundColor(Theme.textSecondary)
                    .font(.caption)
            }
            Spacer()
            Toggle("", isOn: $isOn)
                .labelsHidden()
                .tint(Theme.mint)
        }
        .padding(.vertical, 8)
    }
}

// MARK: - Nav Chevron Row (Settings)
struct NavChevronRow: View {
    let icon: String
    let title: String
    var subtitle: String? = nil
    var trailingText: String? = nil
    var destructive: Bool = false

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .foregroundColor(destructive ? Theme.danger : Theme.textPrimary)
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
    var body: some View {
        Text(text)
            .font(.caption2.weight(.bold))
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .background(color.opacity(0.18))
            .foregroundColor(color)
            .cornerRadius(6)
    }
}

// MARK: - Check Toggle Style (green track, blue circle checkmark — used in Generator)
struct CheckToggleStyle: ToggleStyle {
    func makeBody(configuration: Configuration) -> some View {
        Button {
            configuration.isOn.toggle()
        } label: {
            ZStack {
                Capsule()
                    .fill(configuration.isOn ? Theme.mint : Theme.cardBackgroundLight)
                    .frame(width: 52, height: 30)

                Circle()
                    .fill(Color(hex: "2563EB")) // blue circle
                    .frame(width: 26, height: 26)
                    .overlay(
                        Image(systemName: "checkmark")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(.white)
                            .opacity(configuration.isOn ? 1 : 0)
                    )
                    .offset(x: configuration.isOn ? 11 : -11)
                    .animation(.spring(response: 0.25, dampingFraction: 0.8), value: configuration.isOn)
            }
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Bilingual Toggle Row (English + Kurdish Sorani subtitle)
struct BilingualToggleRow: View {
    let titleEN: String
    let titleCKB: String
    @Binding var isOn: Bool
    var disabled: Bool = false

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 3) {
                Text(titleEN)
                    .font(.subheadline)
                    .foregroundColor(disabled ? Theme.textTertiary : Theme.textPrimary)
                Text(titleCKB)
                    .font(.caption)
                    .foregroundColor(Theme.textTertiary)
                    .environment(\.layoutDirection, .rightToLeft)
            }
            Spacer()
            Toggle("", isOn: $isOn)
                .labelsHidden()
                .toggleStyle(CheckToggleStyle())
                .disabled(disabled)
                .opacity(disabled ? 0.4 : 1)
        }
        .padding(.vertical, 10)
    }
}

// MARK: - Bilingual Nav Chevron Row (for launching feature screens, e.g. Generator)
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
