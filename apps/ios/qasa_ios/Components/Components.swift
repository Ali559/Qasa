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
                QasaMark(size: 22)
                    .foregroundColor(theme.accent)
                Text(L10n.tr("brand.name", locale: prefs.locale))
                    .font(.qasa(titleSize, weight: .bold))
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
/// Full-width main action (`QasaButtonStyle`, `Theme.buttonHeight`). `color` swaps the fill
/// for the rare tinted call to action; `cornerRadius` is kept for source compatibility.
struct PrimaryButton: View {
    @Environment(\.themePalette) private var theme
    let title: String
    var icon: String? = nil
    var color: Color? = nil
    var textColor: Color? = nil
    var cornerRadius: CGFloat = Theme.controlCornerRadius
    var kind: QasaButtonKind = .primary
    var action: () -> Void

    var body: some View {
        if let color {
            Button(action: action) {
                QasaButtonLabel(title: title, icon: icon)
                    .font(.qasa(16, weight: .semibold))
                    .foregroundColor(textColor ?? theme.onAccent)
                    .frame(maxWidth: .infinity)
                    .frame(height: Theme.buttonHeight)
                    .background(color)
                    .clipShape(RoundedRectangle(cornerRadius: Theme.controlCornerRadius, style: .continuous))
            }
            .buttonStyle(PressableStyle())
        } else {
            Button(action: action) {
                QasaButtonLabel(title: title, icon: icon)
            }
            .buttonStyle(QasaButtonStyle(kind: kind))
        }
    }
}

/// Pressed / disabled feedback for custom-drawn buttons.
struct PressableStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        PressableBody(configuration: configuration)
    }

    private struct PressableBody: View {
        @Environment(\.isEnabled) private var isEnabled
        let configuration: ButtonStyle.Configuration
        var body: some View {
            configuration.label
                .opacity(isEnabled ? (configuration.isPressed ? 0.85 : 1) : 0.45)
                .scaleEffect(configuration.isPressed && isEnabled ? 0.98 : 1)
                .animation(.easeOut(duration: 0.12), value: configuration.isPressed)
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
            .font(.qasa(12, weight: .semibold))
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
    /// A password, even while revealed: no autocorrect, auto-capitalisation or spell check,
    /// which would otherwise change what was typed (e.g. "qasa…" becoming "Qasa…").
    var isPassword: Bool = false
    var trailing: AnyView? = nil
    /// Return key action (for example "unlock").
    var onSubmit: (() -> Void)? = nil

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
            .textInputAutocapitalization(isPassword ? .never : nil)
            .autocorrectionDisabled(isPassword)
            .onSubmit { onSubmit?() }
            .multilineTextAlignment(.leading)
            if let trailing { trailing }
        }
        .qasaField()
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
/// A `SettingsRow` with a switch.
struct ToggleRow: View {
    @Environment(\.themePalette) private var theme
    let icon: String
    let iconColor: Color
    let title: String
    let subtitle: String
    @Binding var isOn: Bool
    var iconBackground: Color? = nil

    var body: some View {
        SettingsRow(
            icon: icon,
            title: title,
            subtitle: subtitle,
            iconColor: iconColor == theme.textPrimary ? nil : iconColor,
            iconBackground: iconBackground
        ) {
            Toggle("", isOn: $isOn)
                .labelsHidden()
                .tint(theme.accent)
        }
    }
}

// MARK: - Nav Chevron Row
/// A `SettingsRow` that navigates: optional trailing value, chevron (none when destructive).
struct NavChevronRow: View {
    @Environment(\.themePalette) private var theme
    let icon: String
    let title: String
    var subtitle: String? = nil
    var trailingText: String? = nil
    var destructive: Bool = false
    var iconColor: Color? = nil

    var body: some View {
        SettingsRow(
            icon: icon,
            title: title,
            subtitle: subtitle,
            iconColor: iconColor,
            destructive: destructive
        ) {
            HStack(spacing: 8) {
                if let trailingText { RowValue(text: trailingText) }
                if !destructive { RowChevron() }
            }
        }
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
                    .fill(configuration.isOn ? theme.cardBackground : theme.textTertiary)
                    .frame(width: 24, height: 24)
                    .overlay(
                        Image(systemName: "checkmark")
                            .font(.qasa(11, weight: .bold))
                            .foregroundColor(theme.accent)
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
                .font(.qasa(14, weight: isSelected ? .semibold : .regular))
                .padding(.horizontal, 17)
                .padding(.vertical, 7)
                .background(selectedBackground)
                .foregroundColor(selectedForeground)
                .overlay(
                    Capsule().stroke(isSelected ? theme.accent : theme.stroke, lineWidth: 1)
                )
                .clipShape(Capsule())
        }
        .buttonStyle(PressableStyle())
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

/// The Qasa brand mark, the Qaf Keyhole (`Assets.xcassets/QasaMark`, generated by
/// `assets/brand/generate.py`). A template image: colour it with `.foregroundColor`.
struct QasaMark: View {
    var size: CGFloat

    var body: some View {
        Image("QasaMark")
            .renderingMode(.template)
            .resizable()
            .scaledToFit()
            .frame(width: size, height: size)
            .accessibilityHidden(true)
    }
}
