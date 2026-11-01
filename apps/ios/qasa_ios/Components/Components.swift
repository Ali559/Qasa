import SwiftUI

// MARK: - Brand header
struct KasoBrandHeader: View {
    @Environment(\.themePalette) private var theme
    @Environment(AppPreferences.self) private var prefs
    var titleSize: CGFloat = 24
    var trailing: AnyView? = nil

    var body: some View {
        HStack(spacing: 12) {
            HStack(spacing: 12) {
                Image(systemName: "shield.fill")
                    .font(.qasa(16, weight: .semibold))
                    .foregroundColor(theme.accent)
                    .frame(width: 16, height: 20)
                Text(L10n.tr("brand.name", locale: prefs.locale))
                    .font(.system(size: titleSize, weight: .heavy))
                    .foregroundColor(theme.accent)
            }
            Spacer()
            if let trailing { trailing }
        }
        .padding(.horizontal, 16)
        .frame(height: 64)
        // No divider: the calm redesign lets the header sit on the page background.
        .background(theme.chrome)
    }
}

// MARK: - Status / offline pill
struct StatusPill: View {
    @Environment(\.themePalette) private var theme
    let text: String
    var dotColor: Color? = nil
    var textColor: Color? = nil
    var background: Color? = nil

    var body: some View {
        HStack(spacing: 8) {
            if let dotColor {
                Circle().fill(dotColor).frame(width: 8, height: 8)
            }
            Text(text)
                .font(.qasaLabel(12))
                .foregroundColor(textColor ?? theme.textOnChrome)
                .lineLimit(2)
                .multilineTextAlignment(.leading)
        }
        .padding(.horizontal, 13)
        .padding(.vertical, 5)
        .background(background ?? theme.cardBackgroundLight)
        .overlay(
            Capsule().stroke(theme.strokeChrome, lineWidth: 1)
        )
        .clipShape(Capsule())
    }
}

// MARK: - Primary Button
struct PrimaryButton: View {
    @Environment(\.themePalette) private var theme
    let title: String
    var icon: String? = nil
    var color: Color? = nil
    var textColor: Color? = nil
    var cornerRadius: CGFloat = Theme.cornerRadius
    var action: () -> Void

    var body: some View {
        let fill = color ?? theme.accent
        let label = textColor ?? theme.onAccent
        Button(action: action) {
            HStack(spacing: 8) {
                if let icon {
                    Image(systemName: icon)
                        .font(.qasa(16, weight: .semibold))
                }
                Text(title)
                    .font(.qasa(18, weight: .semibold))
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
            .background(fill)
            .foregroundColor(label)
            .cornerRadius(cornerRadius)
            .shadow(color: fill == theme.accent ? theme.accent.opacity(0.3) : .clear, radius: 10, y: 0)
        }
    }
}

// MARK: - Card Container
struct CardContainer<Content: View>: View {
    @Environment(\.themePalette) private var theme
    var padding: CGFloat = 17
    var background: Color? = nil
    var borderColor: Color? = nil
    var cornerRadius: CGFloat = Theme.cornerRadius
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            content
        }
        .padding(padding)
        .background(background ?? theme.cardBackground)
        .cornerRadius(cornerRadius)
        .overlay(
            RoundedRectangle(cornerRadius: cornerRadius)
                .stroke(borderColor ?? theme.stroke, lineWidth: 1)
        )
    }
}

// MARK: - Section Label
struct SectionLabel: View {
    @Environment(\.themePalette) private var theme
    let text: String
    var body: some View {
        Text(text)
            .font(AppTypography.usesRudaw ? .qasa(12, weight: .semibold) : .qasaLabel(12))
            .foregroundColor(theme.textSecondary)
    }
}

// MARK: - Text field chrome
struct ThemedTextField: View {
    @Environment(\.themePalette) private var theme
    let icon: String
    let placeholder: String
    @Binding var text: String
    var isSecure: Bool = false
    var trailing: AnyView? = nil

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .foregroundColor(theme.textSecondary)
                .frame(width: 20)
            Group {
                if isSecure {
                    SecureField(placeholder, text: $text)
                } else {
                    TextField(placeholder, text: $text)
                }
            }
            .foregroundColor(theme.textPrimary)
            .tint(theme.accent)
            if let trailing { trailing }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 18)
        .background(theme.cardBackground)
        .cornerRadius(Theme.cornerRadius)
        .overlay(
            RoundedRectangle(cornerRadius: Theme.cornerRadius)
                .stroke(theme.stroke, lineWidth: 1)
        )
    }
}

// MARK: - Segmented strength bar
struct StrengthBar: View {
    @Environment(\.themePalette) private var theme
    let segments: Int
    let filled: Int
    var color: Color? = nil
    var height: CGFloat = 6

    var body: some View {
        HStack(spacing: 2) {
            ForEach(0..<segments, id: \.self) { i in
                Capsule()
                    .fill(i < filled ? (color ?? theme.accent) : theme.stroke)
                    .frame(height: height)
            }
        }
    }
}

// MARK: - Stat Box (VPN)
struct StatBox: View {
    @Environment(\.themePalette) private var theme
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
                    .font(.qasa(16, weight: .semibold))
                Text(label)
                    .font(.qasaLabel(12))
                    .foregroundColor(theme.textSecondary)
                HStack(alignment: .firstTextBaseline, spacing: 4) {
                    Text(value)
                        .font(.qasa(20, weight: .semibold))
                        .foregroundColor(theme.textPrimary)
                    Text(unit)
                        .font(.qasa(14))
                        .foregroundColor(theme.textSecondary)
                }
            }
            .frame(maxWidth: .infinity)
        }
    }
}

// MARK: - Toggle Row
struct ToggleRow: View {
    @Environment(\.themePalette) private var theme
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
                    .foregroundColor(theme.textPrimary)
                    .font(.qasa(16))
                if !subtitle.isEmpty {
                    Text(subtitle)
                        .foregroundColor(theme.textSecondary)
                        .font(.qasa(14))
                }
            }
            Spacer()
            Toggle("", isOn: $isOn)
                .labelsHidden()
                .tint(theme.accent)
        }
        .padding(.vertical, 8)
    }
}

// MARK: - Nav Chevron Row
struct NavChevronRow: View {
    @Environment(\.themePalette) private var theme
    let icon: String
    let title: String
    var subtitle: String? = nil
    var trailingText: String? = nil
    var destructive: Bool = false
    var iconColor: Color? = nil

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .foregroundColor(destructive ? theme.danger : (iconColor ?? theme.textPrimary))
                .frame(width: 28)
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .foregroundColor(destructive ? theme.danger : theme.textPrimary)
                    .font(.qasa(15, weight: .medium))
                if let subtitle {
                    Text(subtitle)
                        .foregroundColor(theme.textSecondary)
                        .font(.qasa(12))
                }
            }
            Spacer()
            if let trailingText {
                Text(trailingText)
                    .font(.qasa(12))
                    .foregroundColor(theme.textSecondary)
            }
            if !destructive {
                Image(systemName: "chevron.forward")
                    .font(.qasa(12))
                    .foregroundColor(theme.textTertiary)
            }
        }
        .padding(.vertical, 10)
    }
}

// MARK: - Pill badge
struct PillBadge: View {
    @Environment(\.themePalette) private var theme
    let text: String
    let color: Color
    var solid: Bool = false

    var body: some View {
        Text(text)
            .font(.qasaLabel(12))
            .padding(.horizontal, 8)
            .padding(.vertical, 2)
            .background(solid ? color : color.opacity(0.1))
            .foregroundColor(solid ? theme.onAccentChip : color)
            .overlay(
                RoundedRectangle(cornerRadius: 4)
                    .stroke(solid ? Color.clear : color.opacity(0.2), lineWidth: 1)
            )
            .cornerRadius(4)
    }
}

// MARK: - Check Toggle Style (Generator)
struct CheckToggleStyle: ToggleStyle {
    @Environment(\.themePalette) private var theme

    func makeBody(configuration: Configuration) -> some View {
        Button {
            configuration.isOn.toggle()
        } label: {
            ZStack {
                Capsule()
                    .fill(configuration.isOn ? theme.accent : theme.stroke)
                    .frame(width: 48, height: 24)

                Circle()
                    .fill(configuration.isOn ? Color(hex: "2563EB") : theme.stroke)
                    .frame(width: 24, height: 24)
                    .overlay(
                        Image(systemName: "checkmark")
                            .font(.qasa(11, weight: .bold))
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

// MARK: - Localized toggle row (replaces bilingual dual-line)
struct LocalizedToggleRow: View {
    @Environment(\.themePalette) private var theme
    let title: String
    @Binding var isOn: Bool
    var disabled: Bool = false

    var body: some View {
        HStack {
            Text(title)
                .font(.qasa(14))
                .foregroundColor(disabled ? theme.textTertiary : theme.textPrimary)
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

/// Backward-compatible wrapper — shows a single title (EN) localized via catalog when callers migrate.
struct BilingualToggleRow: View {
    let titleEN: String
    let titleCKB: String
    @Binding var isOn: Bool
    var disabled: Bool = false

    var body: some View {
        LocalizedToggleRow(title: titleEN, isOn: $isOn, disabled: disabled)
    }
}

// MARK: - Filter chip
struct FilterChip: View {
    @Environment(\.themePalette) private var theme
    @Environment(\.colorScheme) private var colorScheme
    let title: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 14, weight: isSelected ? .semibold : .regular))
                .padding(.horizontal, 17)
                .padding(.vertical, 7)
                .background(selectedBackground)
                .foregroundColor(selectedForeground)
                .overlay(
                    Capsule().stroke(isSelected ? theme.accent : theme.stroke, lineWidth: 1)
                )
                .clipShape(Capsule())
        }
    }

    private var selectedBackground: Color {
        guard isSelected else { return theme.cardBackground }
        // Light mode: soft mint fill so chips sit lightly on pale backgrounds.
        if colorScheme == .light {
            return theme.accent.opacity(0.16)
        }
        return theme.accentDark
    }

    private var selectedForeground: Color {
        guard isSelected else { return theme.textSecondary }
        if colorScheme == .light {
            return theme.accentDark
        }
        return theme.onAccentChip
    }
}

// MARK: - Floating action button
struct MintFAB: View {
    @Environment(\.themePalette) private var theme
    let systemImage: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.qasa(18, weight: .bold))
                .foregroundColor(theme.onAccent)
                .frame(width: 56, height: 56)
                .background(theme.accent)
                .cornerRadius(Theme.cornerRadiusLarge)
                .overlay(
                    RoundedRectangle(cornerRadius: Theme.cornerRadiusLarge)
                        .stroke(theme.accent.opacity(0.5), lineWidth: 1)
                )
                .shadow(color: .black.opacity(0.2), radius: 8, y: 4)
        }
    }
}
