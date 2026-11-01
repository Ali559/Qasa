import SwiftUI

struct AutoLockSettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(AppPreferences.self) private var prefs

    private var locale: Locale { prefs.locale }

    private let presets = [1, 2, 5, 10, 15, 30, 45, 60]

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Stepper(value: Binding(
                        get: { prefs.autoLockMinutes },
                        set: { prefs.autoLockMinutes = min(60, max(1, $0)) }
                    ), in: 1...60) {
                        Text(
                            String(
                                format: L10n.tr("settings.autoLockMinutesFmt", locale: locale),
                                prefs.autoLockMinutes
                            )
                        )
                        .font(.qasa(16))
                    }
                } footer: {
                    Text(L10n.tr("settings.autoLockHelp", locale: locale))
                        .font(.qasa(13))
                }

                Section(L10n.tr("settings.autoLockPresets", locale: locale)) {
                    ForEach(presets, id: \.self) { minutes in
                        Button {
                            prefs.autoLockMinutes = minutes
                        } label: {
                            HStack {
                                Text(
                                    String(
                                        format: L10n.tr("settings.autoLockMinutesFmt", locale: locale),
                                        minutes
                                    )
                                )
                                .foregroundColor(Theme.textPrimary)
                                Spacer()
                                if prefs.autoLockMinutes == minutes {
                                    Image(systemName: "checkmark")
                                        .foregroundColor(Theme.accent)
                                }
                            }
                        }
                    }
                }
            }
            .navigationTitle(L10n.tr("settings.autoLock", locale: locale))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(L10n.tr("settings.done", locale: locale)) { dismiss() }
                }
            }
        }
    }
}
