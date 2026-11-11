import SwiftUI

struct AutoLockSettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(AppPreferences.self) private var prefs

    private var locale: Locale { prefs.locale }

    private let presets = [1, 2, 5, 10, 15, 30, 45, 60]

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    VStack(alignment: .leading, spacing: 8) {
                        SettingsSection {
                            SettingsRow(
                                icon: .autoLock,
                                title: String(
                                    format: L10n.tr("settings.autoLockMinutesFmt", locale: locale),
                                    prefs.autoLockMinutes
                                )
                            ) {
                                Stepper(
                                    "",
                                    value: Binding(
                                        get: { prefs.autoLockMinutes },
                                        set: { prefs.autoLockMinutes = min(60, max(1, $0)) }
                                    ),
                                    in: 1...60
                                )
                                .labelsHidden()
                            }
                        }
                        Text(L10n.tr("settings.autoLockHelp", locale: locale))
                            .font(.qasa(12))
                            .foregroundColor(Theme.textSecondary)
                            .fixedSize(horizontal: false, vertical: true)
                            .padding(.horizontal, 4)
                    }

                    SettingsSection(title: L10n.tr("settings.autoLockPresets", locale: locale)) {
                        ForEach(Array(presets.enumerated()), id: \.element) { index, minutes in
                            if index > 0 { RowDivider() }
                            Button {
                                prefs.autoLockMinutes = minutes
                            } label: {
                                SettingsRow(
                                    icon: .timer,
                                    title: String(
                                        format: L10n.tr("settings.autoLockMinutesFmt", locale: locale),
                                        minutes
                                    ),
                                    iconColor: prefs.autoLockMinutes == minutes ? Theme.accent : Theme.textSecondary
                                ) {
                                    QasaIconImage(.check, size: 15)
                                        .foregroundColor(Theme.accent)
                                        .opacity(prefs.autoLockMinutes == minutes ? 1 : 0)
                                }
                            }
                            .buttonStyle(.qasaRow)
                            .accessibilityAddTraits(prefs.autoLockMinutes == minutes ? .isSelected : [])
                        }
                    }
                }
                .padding(Theme.screenPadding)
            }
            .background(Theme.background.ignoresSafeArea())
            // A pick applies at once; the sheet closes by dragging it down.
            .sheetHeader(L10n.tr("settings.autoLock", locale: locale))
        }
    }
}
