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
            .bottomActionBar {
                PrimaryButton(
                    title: L10n.tr(service.isBackingUp ? "backup.backingUp" : "backup.backUpNow", locale: locale),
                    icon: service.isBackingUp ? nil : .backupDone
                ) {
                    backUpNow()
                }
                .disabled(service.isBackingUp)
            }
            .sheetHeader(L10n.tr("backup.title", locale: locale))
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
            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 14) {
                    IconBadge(icon: .backupDone)
                    Text(BackupService.lastBackupText(ms: backup.lastBackupMs, locale: locale))
                        .font(.qasa(14, weight: .medium))
                        .foregroundColor(Theme.textPrimary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    if service.isBackingUp { ProgressView() }
                }

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
            SettingsSection(title: L10n.tr("backup.locationTitle", locale: locale)) {
                Button {
                    backUpAfterPick = false
                    showFolderPicker = true
                } label: {
                    NavChevronRow(
                        icon: .folder,
                        title: backup.folderName ?? L10n.tr("backup.locationNone", locale: locale),
                        trailingText: L10n.tr(backup.hasLocation ? "backup.changeLocation" : "backup.chooseLocation", locale: locale)
                    )
                }
                .buttonStyle(.qasaRow)
            }
            // Where the file is: Qasa never uploads it, so say whether it left the phone.
            if let inICloud = backup.isInICloudDrive {
                if inICloud {
                    Text(String(format: L10n.tr("backup.where.cloudFmt", locale: locale), "iCloud Drive"))
                        .font(.qasa(13, weight: .medium))
                        .foregroundColor(Theme.success)
                } else {
                    VStack(alignment: .leading, spacing: 8) {
                        Text(L10n.tr("backup.where.notCloud", locale: locale))
                            .font(.qasa(13))
                            .foregroundColor(Theme.textSecondary)
                            .fixedSize(horizontal: false, vertical: true)
                        Button {
                            backUpAfterPick = false
                            showFolderPicker = true
                        } label: {
                            Text(L10n.tr("backup.where.pick", locale: locale))
                                .font(.qasa(14, weight: .semibold))
                                .foregroundColor(Theme.accent)
                        }
                        .buttonStyle(PressableStyle())
                    }
                    .padding(14)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Theme.cardBackground)
                    .clipShape(RoundedRectangle(cornerRadius: Theme.cornerRadius, style: .continuous))
                    .overlay(
                        RoundedRectangle(cornerRadius: Theme.cornerRadius, style: .continuous)
                            .stroke(Theme.stroke, lineWidth: 1)
                    )
                }
            }
            helpText("backup.locationHelp")
        }
    }

    private var automaticSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            SettingsSection {
                Menu {
                    ForEach(BackupFrequency.allCases) { frequency in
                        Button(L10n.tr(frequency.labelKey, locale: locale)) {
                            backup.frequency = frequency
                            BackupService.shared.runIfDue(container: modelContext.container)
                        }
                    }
                } label: {
                    NavChevronRow(
                        icon: .history,
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
            SettingsSection {
                Button { showRestore = true } label: {
                    NavChevronRow(
                        icon: .history,
                        title: L10n.tr("backup.restore", locale: locale)
                    )
                }
                .buttonStyle(.qasaRow)
            }
            helpText("backup.restoreHelp")
        }
    }

    private func helpText(_ key: String) -> some View {
        Text(L10n.tr(key, locale: locale))
            .font(.qasa(12))
            .foregroundColor(Theme.textSecondary)
            .fixedSize(horizontal: false, vertical: true)
            .padding(.horizontal, 4)
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
