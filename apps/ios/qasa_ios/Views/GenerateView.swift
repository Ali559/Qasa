import SwiftUI

/// Full-page Generator (brand chrome + save). For sheets, use `PasswordGeneratorModal`.
struct GeneratorView: View {
    var onOpenMenu: (() -> Void)? = nil
    /// Opens the new-login editor with this password filled in (the shell switches to
    /// Passwords). Mirrors desktop `nav.pendingPassword`.
    var onSaveToVault: ((String) -> Void)? = nil

    @Environment(AppPreferences.self) private var prefs
    @StateObject private var model = PasswordGeneratorModel()

    var body: some View {
        VStack(spacing: 0) {
            AppMenuHeader(title: L10n.tr("nav.generator", locale: prefs.locale), onMenu: onOpenMenu)

            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    PasswordGeneratorContent(model: model, compact: false)
                }
                .padding(.horizontal, Theme.screenPadding)
                .padding(.top, 24)
                .padding(.bottom, 24)
            }
        }
        .bottomActionBar { saveButton }
        .background(Theme.chrome.ignoresSafeArea())
        .onAppear { model.regenerate() }
    }

    private var saveButton: some View {
        Button {
            onSaveToVault?(model.password)
        } label: {
            QasaButtonLabel(title: L10n.tr("gen.saveToVault", locale: prefs.locale), icon: .login)
        }
        .buttonStyle(.qasaPrimary)
        .disabled(model.password.isEmpty || onSaveToVault == nil)
    }
}

#Preview {
    GeneratorView(onOpenMenu: {})
}
