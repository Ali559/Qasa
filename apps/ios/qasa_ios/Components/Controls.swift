import SwiftUI

// Shared controls: one field height, one button height, one bottom action bar, one settings
// row, one confirmation sheet. Screens compose these instead of sizing controls themselves.

// MARK: - Field chrome

/// The box every single-line input sits in: `Theme.fieldHeight` tall, card fill, 1pt stroke
/// (danger when `isError`). Apply to the `TextField` / `SecureField` / picker row itself; any
/// vertical padding on the content would be clipped by the fixed height, so add none.
struct QasaFieldChrome: ViewModifier {
    @Environment(\.themePalette) private var theme
    var isError: Bool = false
    var horizontalPadding: CGFloat = Theme.fieldHorizontalPadding

    func body(content: Content) -> some View {
        content
            .font(.qasa(16))
            .foregroundColor(theme.textPrimary)
            .tint(theme.accent)
            .padding(.horizontal, horizontalPadding)
            .frame(maxWidth: .infinity, minHeight: Theme.fieldHeight, maxHeight: Theme.fieldHeight, alignment: .leading)
            .background(theme.cardBackground)
            .clipShape(RoundedRectangle(cornerRadius: Theme.controlCornerRadius, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Theme.controlCornerRadius, style: .continuous)
                    .stroke(isError ? theme.danger : theme.stroke, lineWidth: 1)
            )
    }
}

extension View {
    /// Fixed-height (`Theme.fieldHeight`) input box.
    func qasaField(isError: Bool = false, horizontalPadding: CGFloat = Theme.fieldHorizontalPadding) -> some View {
        modifier(QasaFieldChrome(isError: isError, horizontalPadding: horizontalPadding))
    }
}

/// A square icon button that sits inside a field (reveal, generate, copy…).
struct FieldIconButton: View {
    @Environment(\.themePalette) private var theme
    let icon: QasaIcon
    var tint: Color? = nil
    let accessibilityLabel: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            QasaIconImage(icon, size: 16)
                .foregroundColor(tint ?? theme.textSecondary)
                .frame(width: 32, height: 32)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(accessibilityLabel)
    }
}

// MARK: - Action buttons

enum QasaButtonKind {
    /// Accent fill: the one main action of a page.
    case primary
    /// Card fill with a stroke: the alternative action.
    case secondary
    /// Danger fill: an irreversible action.
    case destructive
    /// Quiet text-only action (still `Theme.buttonHeight` tall for a full-width tap target).
    case plain
}

/// Full-width action button: `Theme.buttonHeight`, `Theme.controlCornerRadius`, pressed and
/// disabled states. Every primary / secondary / destructive action uses this.
struct QasaButtonStyle: ButtonStyle {
    var kind: QasaButtonKind = .primary
    var fullWidth: Bool = true

    func makeBody(configuration: Configuration) -> some View {
        QasaButtonBody(configuration: configuration, kind: kind, fullWidth: fullWidth)
    }
}

private struct QasaButtonBody: View {
    @Environment(\.themePalette) private var theme
    @Environment(\.isEnabled) private var isEnabled
    let configuration: ButtonStyle.Configuration
    let kind: QasaButtonKind
    let fullWidth: Bool

    var body: some View {
        configuration.label
            .font(.qasa(16, weight: .semibold))
            .lineLimit(1)
            .minimumScaleFactor(0.8)
            .foregroundColor(foreground)
            .padding(.horizontal, 20)
            .frame(maxWidth: fullWidth ? .infinity : nil)
            .frame(height: Theme.buttonHeight)
            .background(
                RoundedRectangle(cornerRadius: Theme.controlCornerRadius, style: .continuous)
                    .fill(background)
            )
            .overlay(
                RoundedRectangle(cornerRadius: Theme.controlCornerRadius, style: .continuous)
                    .stroke(kind == .secondary ? theme.stroke : .clear, lineWidth: 1)
            )
            .contentShape(RoundedRectangle(cornerRadius: Theme.controlCornerRadius, style: .continuous))
            .opacity(isEnabled ? (configuration.isPressed ? 0.85 : 1) : 0.45)
            .scaleEffect(configuration.isPressed && isEnabled ? 0.98 : 1)
            .animation(.easeOut(duration: 0.12), value: configuration.isPressed)
    }

    private var background: Color {
        switch kind {
        case .primary: return theme.accent
        case .secondary: return theme.cardBackground
        case .destructive: return theme.danger
        case .plain: return .clear
        }
    }

    private var foreground: Color {
        switch kind {
        case .primary: return theme.onAccent
        case .secondary: return theme.textPrimary
        case .destructive: return .white
        case .plain: return theme.accent
        }
    }
}

extension ButtonStyle where Self == QasaButtonStyle {
    static var qasaPrimary: QasaButtonStyle { QasaButtonStyle(kind: .primary) }
    static var qasaSecondary: QasaButtonStyle { QasaButtonStyle(kind: .secondary) }
    static var qasaDestructive: QasaButtonStyle { QasaButtonStyle(kind: .destructive) }
    static var qasaPlain: QasaButtonStyle { QasaButtonStyle(kind: .plain) }
}

/// Title + optional leading icon, for `QasaButtonStyle` buttons.
struct QasaButtonLabel: View {
    let title: String
    var icon: QasaIcon? = nil

    var body: some View {
        HStack(spacing: 8) {
            if let icon {
                QasaIconImage(icon, size: 15)
                    // A forward arrow points the reading direction.
                    .flipsForRightToLeftLayoutDirection(icon == .forward)
            }
            Text(title)
        }
    }
}

// MARK: - Bottom action bar

/// The page's main action(s), pinned to the bottom above the home indicator: chrome fill,
/// top hairline, screen padding. Use via `.bottomActionBar { … }`.
struct BottomActionBar<Content: View>: View {
    @Environment(\.themePalette) private var theme
    @Environment(\.displayScale) private var displayScale
    @ViewBuilder var content: Content

    var body: some View {
        VStack(spacing: 10) {
            content
        }
        .padding(.horizontal, Theme.screenPadding)
        .padding(.top, 12)
        .padding(.bottom, 12)
        .frame(maxWidth: .infinity)
        .background(
            theme.chrome
                .overlay(alignment: .top) {
                    Rectangle().fill(theme.stroke).frame(height: 1 / displayScale)
                }
                .ignoresSafeArea(edges: .bottom)
        )
    }
}

extension View {
    /// Pins `content` (usually one `QasaButtonStyle` button) to the bottom of the page; the
    /// scroll content above gets inset so nothing hides behind it.
    func bottomActionBar<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        safeAreaInset(edge: .bottom, spacing: 0) {
            BottomActionBar(content: content)
        }
    }
}

// MARK: - Settings rows

/// The rounded-square icon tile every settings / tools row leads with.
struct IconBadge: View {
    @Environment(\.themePalette) private var theme
    let icon: QasaIcon
    var tint: Color? = nil
    var background: Color? = nil

    var body: some View {
        let color = tint ?? theme.accent
        QasaIconImage(icon, size: Theme.iconBadgeGlyphSize)
            .foregroundColor(color)
            .frame(width: Theme.iconBadgeSize, height: Theme.iconBadgeSize)
            .background(
                RoundedRectangle(cornerRadius: Theme.iconBadgeCornerRadius, style: .continuous)
                    .fill(background ?? color.opacity(0.12))
            )
            .accessibilityHidden(true)
    }
}

/// One settings row: icon badge, title, optional subtitle, trailing control; vertically
/// centred, at least `Theme.rowMinHeight` tall, leading-aligned (mirrors in RTL).
struct SettingsRow<Trailing: View>: View {
    @Environment(\.themePalette) private var theme
    let icon: QasaIcon
    let title: String
    var subtitle: String? = nil
    var iconColor: Color? = nil
    var iconBackground: Color? = nil
    var destructive: Bool = false
    @ViewBuilder var trailing: Trailing

    var body: some View {
        HStack(alignment: .center, spacing: 14) {
            IconBadge(
                icon: icon,
                tint: destructive ? theme.danger : iconColor,
                background: iconBackground
            )
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.qasa(15, weight: .medium))
                    .foregroundColor(destructive ? theme.danger : theme.textPrimary)
                    .multilineTextAlignment(.leading)
                if let subtitle, !subtitle.isEmpty {
                    Text(subtitle)
                        .font(.qasa(12))
                        .foregroundColor(theme.textSecondary)
                        .multilineTextAlignment(.leading)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            trailing
        }
        .padding(.vertical, 10)
        .frame(minHeight: Theme.rowMinHeight)
        .contentShape(Rectangle())
    }
}

/// Trailing chevron for navigation rows (mirrors in RTL).
struct RowChevron: View {
    @Environment(\.themePalette) private var theme
    var body: some View {
        QasaIconImage(.chevronForward, size: 13)
            .flipsForRightToLeftLayoutDirection(true)
            .foregroundColor(theme.textTertiary)
            .accessibilityHidden(true)
    }
}

/// Trailing value text (current language, auto-lock minutes…).
struct RowValue: View {
    @Environment(\.themePalette) private var theme
    let text: String
    var body: some View {
        Text(text)
            .font(.qasa(13))
            .foregroundColor(theme.textSecondary)
            .lineLimit(1)
    }
}

/// Divider inset to the row's text, so badges read as one column.
struct RowDivider: View {
    @Environment(\.themePalette) private var theme
    var body: some View {
        Rectangle()
            .fill(theme.stroke)
            .frame(height: 1)
            .padding(.leading, Theme.iconBadgeSize + 14)
    }
}

/// A titled card of `SettingsRow`s. Rows supply their own height; the card only pads sideways.
struct SettingsSection<Content: View>: View {
    var title: String? = nil
    var trailingTitle: AnyView? = nil
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if title != nil || trailingTitle != nil {
                HStack {
                    if let title { SectionLabel(text: title) }
                    Spacer()
                    if let trailingTitle { trailingTitle }
                }
                .padding(.horizontal, 4)
            }
            CardContainer(padding: 0) {
                VStack(spacing: 0) { content }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 4)
            }
        }
    }
}

/// Row press feedback: a soft wash instead of the default dimming.
struct RowButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .background(
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(Theme.textPrimary.opacity(configuration.isPressed ? 0.06 : 0))
                    .padding(.horizontal, -8)
            )
            .animation(.easeOut(duration: 0.12), value: configuration.isPressed)
    }
}

extension ButtonStyle where Self == RowButtonStyle {
    static var qasaRow: RowButtonStyle { RowButtonStyle() }
}

// MARK: - Sheet header

/// The header of every sheet (design/README.md`, "Bottom sheets"): the system grabber above, then the
/// title centered `QasaTokens.Space.sheetTitleTop` from the sheet's top edge. No Cancel: a
/// sheet closes by dragging it down. `trailing` is for a rare extra (Mark all read).
struct SheetHeader<Trailing: View>: View {
    @Environment(\.themePalette) private var theme
    let title: String
    /// Only for a page shown full screen (nothing to drag down): a leading back button.
    var onBack: (() -> Void)? = nil
    @ViewBuilder var trailing: Trailing

    var body: some View {
        Text(title)
            .font(.qasa(QasaTokens.Typography.sheetTitle.size, weight: QasaTokens.Typography.sheetTitle.weight))
            .foregroundColor(theme.textPrimary)
            .lineLimit(1)
            .minimumScaleFactor(0.85)
            .multilineTextAlignment(.center)
            // Room for `trailing` on both sides keeps the title centered on the sheet.
            .padding(.horizontal, Theme.screenPadding + QasaTokens.Space.sheetTitleSideInset)
            .frame(maxWidth: .infinity)
            .overlay(alignment: .trailing) {
                trailing.padding(.trailing, Theme.screenPadding)
            }
            .overlay(alignment: .leading) {
                if let onBack {
                    HeaderBackButton(action: onBack).padding(.leading, Theme.screenPadding)
                }
            }
            .padding(.top, QasaTokens.Space.sheetTitleTop)
            .padding(.bottom, QasaTokens.Space.sheetTitleBottom)
            .background(theme.background)
            .accessibilityAddTraits(.isHeader)
            // Every sheet with this header gets the same grabber and corners.
            .qasaSheetPresentation()
    }
}

extension SheetHeader where Trailing == EmptyView {
    init(title: String) {
        self.init(title: title) { EmptyView() }
    }
}

extension View {
    /// Puts `SheetHeader` above this sheet's content and hides the navigation bar (and with it
    /// any Cancel / Close / Done button). Apply to the sheet's root page.
    func sheetHeader(_ title: String, onBack: (() -> Void)? = nil) -> some View {
        sheetHeader(title, onBack: onBack) { EmptyView() }
    }

    func sheetHeader<Trailing: View>(
        _ title: String,
        onBack: (() -> Void)? = nil,
        @ViewBuilder trailing: () -> Trailing
    ) -> some View {
        let header = SheetHeader(title: title, onBack: onBack, trailing: trailing)
        return self
            .toolbar(.hidden, for: .navigationBar)
            .safeAreaInset(edge: .top, spacing: 0) { header }
    }

    /// Presentation every sheet shares: visible grabber, `QasaTokens.Radius.sheet` corners.
    func qasaSheetPresentation() -> some View {
        self
            .presentationDragIndicator(.visible)
            .presentationCornerRadius(QasaTokens.Radius.sheet)
    }
}

// MARK: - Confirmation / message sheets

/// Bottom sheet that sizes itself to its content (replaces alerts and confirmation dialogs).
struct SelfSizingSheet<Content: View>: View {
    @Environment(\.colorScheme) private var colorScheme
    @State private var height: CGFloat = 260
    @ViewBuilder var content: Content

    /// Resolved here: a sheet attached outside the root's palette environment would
    /// otherwise fall back to the dark default.
    private var theme: ThemePalette { .resolved(for: colorScheme) }

    var body: some View {
        content
            .environment(\.themePalette, theme)
            .padding(.horizontal, Theme.screenPadding)
            .padding(.top, QasaTokens.Space.sheetContentTop)
            .padding(.bottom, QasaTokens.Space.sheetContentBottom)
            .frame(maxWidth: .infinity)
            .fixedSize(horizontal: false, vertical: true)
            .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { height = $0 }
            .presentationDetents([.height(height)])
            .presentationDragIndicator(.visible)
            .presentationCornerRadius(QasaTokens.Radius.sheet)
            .presentationBackground(theme.background)
            .appLocalization()
    }
}

/// Title, message, primary and optional secondary action, each `Theme.buttonHeight`.
struct ConfirmSheetContent: View {
    @Environment(\.themePalette) private var theme
    let title: String
    var message: String? = nil
    var icon: QasaIcon? = nil
    let confirmTitle: String
    var cancelTitle: String? = nil
    var destructive: Bool = false
    let onConfirm: () -> Void
    let onCancel: () -> Void

    var body: some View {
        VStack(spacing: 20) {
            VStack(spacing: 10) {
                if let icon {
                    IconBadge(icon: icon, tint: destructive ? theme.danger : theme.accent)
                        .scaleEffect(1.2)
                        .padding(.bottom, 4)
                }
                Text(title)
                    .font(.qasa(19, weight: .semibold))
                    .foregroundColor(theme.textPrimary)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                if let message, !message.isEmpty {
                    Text(message)
                        .font(.qasa(15))
                        .foregroundColor(theme.textSecondary)
                        .multilineTextAlignment(.center)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            .frame(maxWidth: .infinity)
            VStack(spacing: 10) {
                Button(confirmTitle, action: onConfirm)
                    .buttonStyle(QasaButtonStyle(kind: destructive ? .destructive : .primary))
                if let cancelTitle {
                    Button(cancelTitle, action: onCancel)
                        .buttonStyle(.qasaSecondary)
                }
            }
        }
    }
}

/// A one-field prompt sheet (new collection, rename, setup key). Same header as every sheet
/// (`SheetHeader`'s title style, `sheetTitleTop` from the top edge, `sheetTitleBottom` to the
/// content) and no Cancel: it closes by dragging down. Its own action is the bottom button.
struct TextPromptSheetContent: View {
    @Environment(\.themePalette) private var theme
    let title: String
    var message: String? = nil
    let placeholder: String
    @Binding var text: String
    let confirmTitle: String
    var plainText: Bool = false
    let onConfirm: () -> Void
    @FocusState private var focused: Bool

    var body: some View {
        VStack(spacing: 0) {
            // `SelfSizingSheet` already pads `sheetContentTop` (= `sheetTitleTop`) above this.
            Text(title)
                .font(.qasa(QasaTokens.Typography.sheetTitle.size, weight: QasaTokens.Typography.sheetTitle.weight))
                .foregroundColor(theme.textPrimary)
                .lineLimit(1)
                .minimumScaleFactor(0.85)
                .multilineTextAlignment(.center)
                .padding(.horizontal, QasaTokens.Space.sheetTitleSideInset)
                .frame(maxWidth: .infinity)
                .accessibilityAddTraits(.isHeader)
                .padding(.bottom, QasaTokens.Space.sheetTitleBottom)
            VStack(spacing: 20) {
                if let message, !message.isEmpty {
                    Text(message)
                        .font(.qasa(15))
                        .foregroundColor(theme.textSecondary)
                        .multilineTextAlignment(.center)
                        .fixedSize(horizontal: false, vertical: true)
                        .frame(maxWidth: .infinity)
                }
                TextField(placeholder, text: $text)
                    .textInputAutocapitalization(plainText ? .never : .sentences)
                    .autocorrectionDisabled(plainText)
                    .focused($focused)
                    .submitLabel(.done)
                    .onSubmit(onConfirm)
                    .qasaField()
                Button(confirmTitle, action: onConfirm)
                    .buttonStyle(.qasaPrimary)
                    .disabled(text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
        }
        .onAppear { focused = true }
    }
}

extension View {
    /// Bottom-sheet confirmation. `cancelTitle: nil` shows a single acknowledge button.
    func confirmSheet(
        isPresented: Binding<Bool>,
        title: String,
        message: String? = nil,
        icon: QasaIcon? = nil,
        confirmTitle: String,
        cancelTitle: String? = nil,
        destructive: Bool = false,
        onCancel: @escaping () -> Void = {},
        onConfirm: @escaping () -> Void
    ) -> some View {
        sheet(isPresented: isPresented) {
            SelfSizingSheet {
                ConfirmSheetContent(
                    title: title,
                    message: message,
                    icon: icon,
                    confirmTitle: confirmTitle,
                    cancelTitle: cancelTitle,
                    destructive: destructive,
                    // Act first: bindings derived from a pending item clear it on dismiss.
                    onConfirm: {
                        onConfirm()
                        isPresented.wrappedValue = false
                    },
                    onCancel: {
                        isPresented.wrappedValue = false
                        onCancel()
                    }
                )
            }
        }
    }

    /// Bottom-sheet single-field prompt (replaces alerts with a `TextField`). No Cancel:
    /// dragging down dismisses it, and `onDismiss` runs however it closed.
    func textPromptSheet(
        isPresented: Binding<Bool>,
        title: String,
        message: String? = nil,
        placeholder: String,
        text: Binding<String>,
        confirmTitle: String,
        plainText: Bool = false,
        onDismiss: @escaping () -> Void = {},
        onConfirm: @escaping () -> Void
    ) -> some View {
        sheet(isPresented: isPresented, onDismiss: onDismiss) {
            SelfSizingSheet {
                TextPromptSheetContent(
                    title: title,
                    message: message,
                    placeholder: placeholder,
                    text: text,
                    confirmTitle: confirmTitle,
                    plainText: plainText,
                    onConfirm: {
                        guard !text.wrappedValue.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
                        onConfirm()
                        isPresented.wrappedValue = false
                    }
                )
            }
        }
    }
}
