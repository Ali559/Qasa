import SwiftUI

// The app's own menus (`design/README.md`, rule 6a): every ⋯ menu is an action sheet and every
// dropdown a picker sheet, never a system `Menu`. Both use the shared sheet header and close by
// dragging down; neither has a Cancel.

// MARK: - Title

/// The shared sheet title for a self-sized sheet: `type.sheetTitle`, centered, one line.
/// `SelfSizingSheet` / `OwnSheetFrame` already pad `sheetContentTop` (= `sheetTitleTop`) above it.
struct OwnSheetTitle: View {
    @Environment(\.themePalette) private var theme
    let title: String

    var body: some View {
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
    }
}

// MARK: - Action sheet

/// One row of an action sheet.
struct SheetAction: Identifiable {
    let id = UUID()
    let icon: QasaIcon
    let title: String
    var destructive: Bool = false
    let action: () -> Void
}

/// Settings-style rows (icon badge, label, divider between), destructive last in `danger`.
struct ActionSheetContent: View {
    let title: String
    let actions: [SheetAction]
    let onPick: (SheetAction) -> Void

    var body: some View {
        VStack(spacing: 0) {
            OwnSheetTitle(title: title)
            SettingsSection {
                ForEach(Array(actions.enumerated()), id: \.element.id) { index, action in
                    if index > 0 { RowDivider() }
                    Button { onPick(action) } label: {
                        SettingsRow(icon: action.icon, title: action.title, destructive: action.destructive) {
                            EmptyView()
                        }
                    }
                    .buttonStyle(.qasaRow)
                }
            }
        }
    }
}

private struct ActionSheetModifier: ViewModifier {
    @Binding var isPresented: Bool
    let title: String
    let actions: () -> [SheetAction]
    /// Runs once the sheet is gone, so an action may present the next sheet.
    @State private var pending: (() -> Void)?

    func body(content: Content) -> some View {
        content.sheet(isPresented: $isPresented, onDismiss: {
            let run = pending
            pending = nil
            run?()
        }) {
            SelfSizingSheet {
                ActionSheetContent(title: title, actions: actions()) { picked in
                    pending = picked.action
                    isPresented = false
                }
            }
        }
    }
}

extension View {
    /// An item's or folder's ⋯ menu as the app's own sheet. `actions` is read when it opens.
    func qasaActionSheet(isPresented: Binding<Bool>, title: String, actions: @escaping () -> [SheetAction]) -> some View {
        modifier(ActionSheetModifier(isPresented: isPresented, title: title, actions: actions))
    }
}

// MARK: - Picker sheet

/// One choice in a picker sheet. `search` adds text that matches without being shown
/// (a country's dial code).
struct PickerOption<Value: Hashable>: Identifiable {
    let value: Value
    let label: String
    var search: String = ""
    var id: Value { value }
}

/// More options than this get a search field and open at full height.
let pickerSheetSearchThreshold = 8

/// The field's label as title, then the options in one card, a check on the current one.
struct PickerSheetContent<Value: Hashable>: View {
    @Environment(\.themePalette) private var theme
    @Environment(\.dismiss) private var dismiss
    let title: String
    let options: [PickerOption<Value>]
    @Binding var selection: Value
    @State private var query = ""

    private var isLong: Bool { options.count > pickerSheetSearchThreshold }

    private var shown: [PickerOption<Value>] {
        let q = query.trimmingCharacters(in: .whitespaces)
        guard !q.isEmpty else { return options }
        return options.filter {
            $0.label.localizedCaseInsensitiveContains(q) || $0.search.localizedCaseInsensitiveContains(q)
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            OwnSheetTitle(title: title)
            if isLong {
                TextField(L10n.tr("common.search", locale: AppLanguage.current.locale), text: $query)
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.never)
                    .submitLabel(.search)
                    .qasaField()
                    .padding(.bottom, QasaTokens.Space.formGap)
                ScrollView { list }
                    .scrollDismissesKeyboard(.interactively)
            } else {
                list
            }
        }
    }

    @ViewBuilder
    private var list: some View {
        if shown.isEmpty {
            Text(L10n.tr("common.noMatches", locale: AppLanguage.current.locale))
                .font(.qasa(15))
                .foregroundColor(theme.textSecondary)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 24)
        } else {
            CardContainer(padding: 0) {
                LazyVStack(spacing: 0) {
                    ForEach(Array(shown.enumerated()), id: \.element.id) { index, option in
                        if index > 0 {
                            Rectangle().fill(theme.stroke).frame(height: 1).padding(.leading, 14)
                        }
                        row(option)
                    }
                }
            }
        }
    }

    private func row(_ option: PickerOption<Value>) -> some View {
        let isSelected = option.value == selection
        return Button {
            selection = option.value
            dismiss()
        } label: {
            HStack(spacing: 12) {
                Text(option.label)
                    .font(.qasa(15, weight: .medium))
                    .foregroundColor(theme.textPrimary)
                    .multilineTextAlignment(.leading)
                    .frame(maxWidth: .infinity, alignment: .leading)
                if isSelected {
                    QasaIconImage(.check, size: 16).foregroundColor(theme.textPrimary)
                }
            }
            .padding(.horizontal, 14)
            .frame(minHeight: Theme.rowMinHeight)
            .contentShape(Rectangle())
        }
        .buttonStyle(.qasaRow)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}

/// A long list fills the sheet (`.large`) and scrolls; a short one sizes to fit.
private struct PickerSheetModifier<Value: Hashable>: ViewModifier {
    @Binding var isPresented: Bool
    let title: String
    let options: () -> [PickerOption<Value>]
    @Binding var selection: Value

    func body(content: Content) -> some View {
        content.sheet(isPresented: $isPresented) {
            let opts = options()
            if opts.count > pickerSheetSearchThreshold {
                OwnSheetFrame {
                    PickerSheetContent(title: title, options: opts, selection: $selection)
                }
            } else {
                SelfSizingSheet {
                    PickerSheetContent(title: title, options: opts, selection: $selection)
                }
            }
        }
    }
}

/// A full-height sheet with the same padding, corners and background as `SelfSizingSheet`.
struct OwnSheetFrame<Content: View>: View {
    @Environment(\.colorScheme) private var colorScheme
    @ViewBuilder var content: Content

    private var theme: ThemePalette { .resolved(for: colorScheme) }

    var body: some View {
        content
            .environment(\.themePalette, theme)
            .padding(.horizontal, Theme.screenPadding)
            .padding(.top, QasaTokens.Space.sheetContentTop)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            .presentationDetents([.large])
            .presentationDragIndicator(.visible)
            .presentationCornerRadius(QasaTokens.Radius.sheet)
            .presentationBackground(theme.background)
            .appLocalization()
    }
}

extension View {
    /// A dropdown as the app's own sheet. `options` is read when it opens.
    func qasaPickerSheet<Value: Hashable>(
        isPresented: Binding<Bool>,
        title: String,
        selection: Binding<Value>,
        options: @escaping () -> [PickerOption<Value>]
    ) -> some View {
        modifier(PickerSheetModifier(isPresented: isPresented, title: title, options: options, selection: selection))
    }
}
