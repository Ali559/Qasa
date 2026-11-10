import SwiftUI
import SwiftData
import UniformTypeIdentifiers

/// Settings → Backups: back up now, where the backup goes, automatic backups and restore.
/// Free on every plan. One file per device; every backup replaces the previous one.
struct BackupsView: View {
    @Environment(AppPreferences.self) private var prefs
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var modelContext
    @Bindable private var backup = BackupPreferences.shared
    private var service = BackupService.shared

    @State private var showFolderPicker = false
    /// Back up now with no location yet: pick one, then back up.
    @State private var backUpAfterPick = false
    @State private var statusKey: String?
    @State private var showRestore = false

    private var locale: Locale { prefs.locale }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    Text(L10n.tr("backup.intro", locale: locale))
                        .font(.qasa(14))
                        .foregroundColor(Theme.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)

                    backUpCard
                    locationSection
                    automaticSection
                    restoreSection
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(Theme.screenPadding)
                .padding(.bottom, 24)
            }
            .background(Theme.background.ignoresSafeArea())
            .navigationTitle(L10n.tr("backup.title", locale: locale))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(L10n.tr("common.close", locale: locale)) { dismiss() }
                }
            }
        }
        .fileImporter(isPresented: $showFolderPicker, allowedContentTypes: [.folder], allowsMultipleSelection: false) { result in
            let thenBackUp = backUpAfterPick
            backUpAfterPick = false
            guard case .success(let urls) = result, let url = urls.first else { return }
            do {
                let picked = try BackupFileIO.bookmark(forPickedFolder: url)
                backup.setLocation(bookmark: picked.bookmark, name: picked.name)
                if thenBackUp { backUpNow() } else { statusKey = nil }
            } catch {
                statusKey = nil
                backup.recordFailure()
            }
        }
        .sheet(isPresented: $showRestore) {
            BackupRestoreView(mode: .merge)
                .appLocalization()
        }
    }

    private var backUpCard: some View {
        CardContainer {
            VStack(alignment: .leading, spacing: 12) {
                PrimaryButton(
                    title: L10n.tr(service.isBackingUp ? "backup.backingUp" : "backup.backUpNow", locale: locale),
                    icon: service.isBackingUp ? nil : "externaldrive.badge.checkmark",
                    cornerRadius: 8
                ) {
                    backUpNow()
                }
                .disabled(service.isBackingUp)
                .opacity(service.isBackingUp ? 0.7 : 1)

                Text(BackupService.lastBackupText(ms: backup.lastBackupMs, locale: locale))
                    .font(.qasa(13))
                    .foregroundColor(Theme.textSecondary)

                if backup.lastBackupFailed, !service.isBackingUp {
                    Text(L10n.tr("backup.failed", locale: locale))
                        .font(.qasa(13, weight: .medium))
                        .foregroundColor(Theme.danger)
                        .fixedSize(horizontal: false, vertical: true)
                } else if let statusKey, !service.isBackingUp {
                    Text(L10n.tr(statusKey, locale: locale))
                        .font(.qasa(13, weight: .medium))
                        .foregroundColor(Theme.accent)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
    }

    private var locationSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            SectionLabel(text: L10n.tr("backup.locationTitle", locale: locale))
            CardContainer {
                Button {
                    backUpAfterPick = false
                    showFolderPicker = true
                } label: {
                    NavChevronRow(
                        icon: "folder",
                        title: backup.folderName ?? L10n.tr("backup.locationNone", locale: locale),
                        trailingText: L10n.tr(backup.hasLocation ? "backup.changeLocation" : "backup.chooseLocation", locale: locale)
                    )
                }
                .buttonStyle(.plain)
            }
            helpText("backup.locationHelp")
        }
    }

    private var automaticSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            CardContainer {
                Menu {
                    ForEach(BackupFrequency.allCases) { frequency in
                        Button(L10n.tr(frequency.labelKey, locale: locale)) {
                            backup.frequency = frequency
                            BackupService.shared.runIfDue(container: modelContext.container)
                        }
                    }
                } label: {
                    NavChevronRow(
                        icon: "clock.arrow.circlepath",
                        title: L10n.tr("backup.auto", locale: locale),
                        trailingText: L10n.tr(backup.frequency.labelKey, locale: locale)
                    )
                }
            }
            helpText("backup.autoHelp")
        }
    }

    private var restoreSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            CardContainer {
                Button { showRestore = true } label: {
                    NavChevronRow(
                        icon: "arrow.counterclockwise",
                        title: L10n.tr("backup.restore", locale: locale)
                    )
                }
                .buttonStyle(.plain)
            }
            helpText("backup.restoreHelp")
        }
    }

    private func helpText(_ key: String) -> some View {
        Text(L10n.tr(key, locale: locale))
            .font(.qasa(12))
            .foregroundColor(Theme.textSecondary)
            .fixedSize(horizontal: false, vertical: true)
    }

    private func backUpNow() {
        guard backup.hasLocation else {
            statusKey = "backup.chooseLocationFirst"
            backUpAfterPick = true
            showFolderPicker = true
            return
        }
        statusKey = nil
        let container = modelContext.container
        Task {
            if await service.backUp(container: container) == .saved {
                statusKey = "backup.done"
            }
        }
    }
}
