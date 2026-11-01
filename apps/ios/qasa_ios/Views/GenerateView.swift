import SwiftUI

/// Full-page Generator (brand chrome + save). For sheets, use `PasswordGeneratorModal`.
struct GeneratorView: View {
    var onOpenMenu: (() -> Void)? = nil

    @Environment(AppPreferences.self) private var prefs
    @StateObject private var model = PasswordGeneratorModel()

    var body: some View {
        VStack(spacing: 0) {
            AppMenuHeader(title: L10n.tr("nav.generator", locale: prefs.locale), onMenu: onOpenMenu)

            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    PasswordGeneratorContent(model: model, compact: false)
                    saveButton
                }
                .padding(.horizontal, Theme.screenPadding)
                .padding(.top, 24)
                .padding(.bottom, 40)
            }
        }
        .background(Theme.chrome.ignoresSafeArea())
        .onAppear { model.regenerate() }
    }

    private var saveButton: some View {
        Button {} label: {
            Text(L10n.tr("gen.saveToVault", locale: prefs.locale))
                .font(.qasa(16, weight: .semibold))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 16)
                .background(Theme.accent)
                .foregroundColor(Theme.onAccentChip)
                .cornerRadius(Theme.cornerRadius)
                .shadow(color: Theme.accent.opacity(0.3), radius: 8)
        }
        .padding(.top, 16)
    }
}

#Preview {
    GeneratorView(onOpenMenu: {})
}
