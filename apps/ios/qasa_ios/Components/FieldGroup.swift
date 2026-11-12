import SwiftUI

// Labelled fields are rows in a grouped card (design/README.md, "Labelled fields"): the label
// sits inside each row, rows share one card with inset dividers, errors stripe the row and
// are listed under the card. Android (`QasaFieldGroup`) and the desktop (`FieldGroup.vue`)
// draw the same thing from the same tokens.

// MARK: - Group

/// One card of field rows. Put rows directly inside (no wrapping stack), so each becomes a
/// row with a divider above it; `if` and `ForEach` are fine.
struct FieldGroup<Content: View>: View {
    @Environment(\.themePalette) private var theme
    @ViewBuilder var content: Content

    @State private var errors: [FieldRowError] = []
    @State private var rowCount = 0

    private var shape: RoundedRectangle {
        RoundedRectangle(cornerRadius: QasaTokens.Radius.group, style: .continuous)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: QasaTokens.Space.fieldErrorGap) {
            _VariadicView.Tree(DividedRows()) { content }
                .background(theme.cardBackground)
                .clipShape(shape)
                .overlay(shape.stroke(theme.stroke, lineWidth: QasaTokens.Size.stroke))
            ForEach(errors) { item in
                Text(rowCount > 1 ? "\(item.label): \(item.message)" : item.message)
                    .font(.qasa(QasaTokens.Typography.fieldError.size, weight: QasaTokens.Typography.fieldError.weight))
                    .foregroundColor(theme.danger)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .multilineTextAlignment(.leading)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .onPreferenceChange(FieldRowErrorKey.self) { errors = $0 }
        .onPreferenceChange(FieldRowCountKey.self) { rowCount = $0 }
    }
}

/// Rows stacked with a 1pt divider between them, inset on the leading side.
private struct DividedRows: _VariadicView_MultiViewRoot {
    func body(children: _VariadicView.Children) -> some View {
        let firstId = children.first?.id
        VStack(spacing: 0) {
            ForEach(children) { child in
                if child.id != firstId {
                    RowSeparator()
                }
                child
            }
        }
    }
}

private struct RowSeparator: View {
    @Environment(\.themePalette) private var theme
    var body: some View {
        Rectangle()
            .fill(theme.stroke)
            .frame(height: QasaTokens.Size.stroke)
            .padding(.leading, QasaTokens.Space.groupDividerInset)
    }
}

struct FieldRowError: Equatable, Identifiable {
    let id: String
    let label: String
    let message: String
}

private struct FieldRowErrorKey: PreferenceKey {
    static let defaultValue: [FieldRowError] = []
    static func reduce(value: inout [FieldRowError], nextValue: () -> [FieldRowError]) {
        value.append(contentsOf: nextValue())
    }
}

private struct FieldRowCountKey: PreferenceKey {
    static let defaultValue = 0
    static func reduce(value: inout Int, nextValue: () -> Int) { value += nextValue() }
}

// MARK: - Row

/// One row: the label on top, `value` under it, `trailing` controls centered on the trailing
/// edge. `isFocused` tints the label; `error` turns it red, stripes the leading edge and
/// lists the message under the group.
struct FieldRow<Value: View, Trailing: View>: View {
    @Environment(\.themePalette) private var theme
    let label: String
    var error: String? = nil
    var isFocused: Bool = false
    var onTap: (() -> Void)? = nil
    @ViewBuilder var value: Value
    @ViewBuilder var trailing: Trailing

    private var labelColor: Color {
        if error != nil { return theme.danger }
        return isFocused ? theme.accent : theme.textSecondary
    }

    var body: some View {
        HStack(alignment: .center, spacing: QasaTokens.Space.groupRowTrailingGap) {
            VStack(alignment: .leading, spacing: 0) {
                Text(label)
                    .font(.qasa(QasaTokens.Typography.groupLabel.size, weight: QasaTokens.Typography.groupLabel.weight))
                    .foregroundColor(labelColor)
                    .lineLimit(1)
                    .accessibilityHidden(true)
                value
                    .font(.qasa(QasaTokens.Typography.groupValue.size, weight: QasaTokens.Typography.groupValue.weight))
                    .foregroundColor(theme.textPrimary)
                    .tint(theme.accent)
                    .accessibilityLabel(label)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            trailing
        }
        .padding(.vertical, QasaTokens.Space.groupRowVertical)
        .padding(.horizontal, QasaTokens.Space.groupRowHorizontal)
        .frame(maxWidth: .infinity, minHeight: QasaTokens.Size.groupRowMinHeight, alignment: .leading)
        .overlay(alignment: .leading) {
            if error != nil {
                Rectangle().fill(theme.danger).frame(width: QasaTokens.Size.groupErrorStripe)
            }
        }
        .contentShape(Rectangle())
        .onTapGesture { onTap?() }
        .animation(QasaTokens.Motion.fast, value: isFocused)
        .preference(key: FieldRowCountKey.self, value: 1)
        .preference(
            key: FieldRowErrorKey.self,
            value: error.map { [FieldRowError(id: label, label: label, message: $0)] } ?? []
        )
    }
}

extension FieldRow where Trailing == EmptyView {
    init(label: String, error: String? = nil, isFocused: Bool = false, onTap: (() -> Void)? = nil, @ViewBuilder value: () -> Value) {
        self.init(label: label, error: error, isFocused: isFocused, onTap: onTap, value: value, trailing: { EmptyView() })
    }
}

// MARK: - Text row

/// A single-line text input as a row. `hint` is an example ("name@example.com"), never the
/// label again; `prefix` is fixed text before the input (a dial code).
struct TextFieldRow<Trailing: View>: View {
    @Environment(\.themePalette) private var theme
    let label: String
    @Binding var text: String
    var hint: String? = nil
    var isSecure: Bool = false
    /// Passwords and codes: no autocorrect, capitalisation or spell check.
    var isPlain: Bool = false
    var monospaced: Bool = false
    var keyboard: UIKeyboardType = .default
    var contentType: UITextContentType? = nil
    var prefix: String? = nil
    var error: String? = nil
    var onSubmit: (() -> Void)? = nil
    @ViewBuilder var trailing: Trailing

    @FocusState private var focused: Bool

    var body: some View {
        FieldRow(label: label, error: error, isFocused: focused, onTap: { focused = true }) {
            HStack(spacing: 6) {
                if let prefix {
                    Text(prefix)
                        .font(.qasa(QasaTokens.Typography.groupValue.size, weight: .semibold))
                        .foregroundColor(theme.accent)
                        .accessibilityHidden(true)
                }
                input
                    .focused($focused)
                    .keyboardType(keyboard)
                    .textContentType(contentType)
                    .textInputAutocapitalization(isPlain ? .never : nil)
                    .autocorrectionDisabled(isPlain)
                    .multilineTextAlignment(.leading)
                    .onSubmit { onSubmit?() }
                    .font(monospaced
                        ? .kasoMono(QasaTokens.Typography.groupValue.size, weight: .regular)
                        : .qasa(QasaTokens.Typography.groupValue.size, weight: QasaTokens.Typography.groupValue.weight))
            }
        } trailing: {
            trailing
        }
    }

    @ViewBuilder private var input: some View {
        let prompt = hint.map { Text($0).foregroundColor(theme.textPlaceholder) }
        if isSecure {
            SecureField("", text: $text, prompt: prompt)
        } else {
            TextField("", text: $text, prompt: prompt)
        }
    }
}

extension TextFieldRow where Trailing == EmptyView {
    init(
        label: String,
        text: Binding<String>,
        hint: String? = nil,
        isSecure: Bool = false,
        isPlain: Bool = false,
        keyboard: UIKeyboardType = .default,
        contentType: UITextContentType? = nil,
        prefix: String? = nil,
        error: String? = nil,
        onSubmit: (() -> Void)? = nil
    ) {
        self.init(
            label: label, text: text, hint: hint, isSecure: isSecure, isPlain: isPlain,
            keyboard: keyboard, contentType: contentType, prefix: prefix, error: error,
            onSubmit: onSubmit, trailing: { EmptyView() }
        )
    }
}

/// A password row: secure until revealed, the reveal button trailing (plus any `extra`
/// button before it, such as generate).
struct PasswordFieldRow<Extra: View>: View {
    let label: String
    @Binding var text: String
    var hint: String? = nil
    var contentType: UITextContentType? = .password
    var error: String? = nil
    var onSubmit: (() -> Void)? = nil
    @ViewBuilder var extra: Extra

    @State private var revealed = false

    var body: some View {
        TextFieldRow(
            label: label, text: $text, hint: hint, isSecure: !revealed, isPlain: true,
            monospaced: revealed, contentType: contentType, error: error, onSubmit: onSubmit
        ) {
            extra
            FieldIconButton(
                icon: revealed ? .hide : .show,
                accessibilityLabel: L10n.tr(revealed ? "vault.hidePassword" : "vault.showPassword")
            ) {
                revealed.toggle()
            }
        }
    }
}

extension PasswordFieldRow where Extra == EmptyView {
    init(
        label: String,
        text: Binding<String>,
        hint: String? = nil,
        contentType: UITextContentType? = .password,
        error: String? = nil,
        onSubmit: (() -> Void)? = nil
    ) {
        self.init(label: label, text: text, hint: hint, contentType: contentType, error: error, onSubmit: onSubmit) {
            EmptyView()
        }
    }
}

// MARK: - Picker row

/// A dropdown as a row: the label, the chosen value under it, a chevron trailing. Tapping it
/// opens the app's own picker sheet (`qasaPickerSheet`), titled with the label.
struct PickerFieldRow<Value: Hashable, Extra: View>: View {
    @Environment(\.themePalette) private var theme
    let label: String
    @Binding var selection: Value
    let options: () -> [PickerOption<Value>]
    var error: String? = nil
    /// A button beside the chevron (new collection).
    @ViewBuilder var extra: Extra
    @State private var isOpen = false

    private var value: String {
        options().first { $0.value == selection }?.label
            ?? L10n.tr("common.none", locale: AppLanguage.current.locale)
    }

    var body: some View {
        FieldRow(label: label, error: error) {
            Button { isOpen = true } label: {
                HStack(spacing: 6) {
                    Text(value)
                        .foregroundColor(theme.textPrimary)
                        .lineLimit(1)
                    Spacer(minLength: 0)
                    RowChevron()
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(label)
            .accessibilityValue(value)
        } trailing: {
            extra
        }
        .qasaPickerSheet(isPresented: $isOpen, title: label, selection: $selection, options: options)
    }
}

extension PickerFieldRow where Extra == EmptyView {
    init(label: String, selection: Binding<Value>, error: String? = nil, options: @escaping () -> [PickerOption<Value>]) {
        self.init(label: label, selection: selection, options: options, error: error, extra: { EmptyView() })
    }
}

// MARK: - Notes

/// A multi-line field in its own card under the group: the label inside at the top, the text
/// under it, `Theme.notesMinHeight` tall and growing.
struct NotesFieldCard: View {
    @Environment(\.themePalette) private var theme
    let label: String
    @Binding var text: String
    var hint: String? = nil
    var error: String? = nil
    @FocusState private var focused: Bool

    private var shape: RoundedRectangle {
        RoundedRectangle(cornerRadius: Theme.controlCornerRadius, style: .continuous)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: QasaTokens.Space.fieldErrorGap) {
            VStack(alignment: .leading, spacing: 2) {
                Text(label)
                    .font(.qasa(QasaTokens.Typography.groupLabel.size, weight: QasaTokens.Typography.groupLabel.weight))
                    .foregroundColor(error != nil ? theme.danger : (focused ? theme.accent : theme.textSecondary))
                    .accessibilityHidden(true)
                TextField("", text: $text, prompt: hint.map { Text($0).foregroundColor(theme.textPlaceholder) }, axis: .vertical)
                    .lineLimit(4...12)
                    .focused($focused)
                    .font(.qasa(QasaTokens.Typography.groupValue.size))
                    .foregroundColor(theme.textPrimary)
                    .tint(theme.accent)
                    .multilineTextAlignment(.leading)
                    .accessibilityLabel(label)
            }
            .padding(.top, QasaTokens.Space.groupNotesLabelTop)
            .padding(.bottom, 10)
            .padding(.horizontal, QasaTokens.Space.groupRowHorizontal)
            .frame(maxWidth: .infinity, minHeight: Theme.notesMinHeight, alignment: .topLeading)
            .background(theme.cardBackground)
            .clipShape(shape)
            .overlay(shape.stroke(error != nil ? theme.danger : theme.stroke, lineWidth: QasaTokens.Size.stroke))
            .contentShape(shape)
            .onTapGesture { focused = true }
            .animation(QasaTokens.Motion.fast, value: focused)
            if let error {
                Text(error)
                    .font(.qasa(QasaTokens.Typography.fieldError.size))
                    .foregroundColor(theme.danger)
            }
        }
    }
}
