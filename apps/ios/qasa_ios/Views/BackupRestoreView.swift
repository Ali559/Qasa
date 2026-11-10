import SwiftUI
import SwiftData
import UniformTypeIdentifiers

/// Restore from a backup file: pick the file, check its header, type the main password the
/// vault had then, restore. Used from Settings → Backups (merge into this vault) and from
/// first run (no vault yet: the device adopts the backup's vault and account).
struct BackupRestoreView: View {
    enum Mode { case merge, setup }

    let mode: Mode
    /// Setup only: called with the password once the vault has been adopted, so the root
    /// unlocks with it exactly as after a Wi‑Fi sync adoption.
    var onAdopted: (String) -> Void = { _ in }

    @Environment(AppPreferences.self) private var prefs
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var modelContext

    @State private var showPicker = false
    @State private var file: Data?
    @State private var info: FfiBackupInfo?
    @State private var password = ""
    @State private var passwordVisible = false
    @State private var isWorking = false
    @State private var errorKey: String?
    @State private var outcome: FfiRestoreOutcome?

    private var locale: Locale { prefs.locale }
    private var canRestore: Bool { file != nil && !password.isEmpty && !isWorking }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text(L10n.tr(mode == .setup ? "backup.restoreSubtitle" : "backup.restoreHelp", locale: locale))
                        .font(.qasa(14))
                        .foregroundColor(Theme.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)

                    if let outcome {
                        doneBody(outcome)
                    } else {
                        if let info {
                            passwordCard(info)
                        } else {
                            PrimaryButton(
                                title: L10n.tr("backup.restore", locale: locale),
                                icon: "doc.badge.arrow.up",
                                cornerRadius: 8
                            ) {
                                showPicker = true
                            }
                        }
                        if let errorKey {
                            Text(L10n.tr(errorKey, locale: locale))
                                .font(.qasa(13, weight: .medium))
                                .foregroundColor(Theme.danger)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(Theme.screenPadding)
            }
            .background(Theme.background.ignoresSafeArea())
            .navigationTitle(L10n.tr("backup.restore.title", locale: locale))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(L10n.tr(outcome == nil ? "common.cancel" : "common.close", locale: locale)) { dismiss() }
                        .disabled(isWorking)
                }
            }
        }
        .interactiveDismissDisabled(isWorking)
        .fileImporter(isPresented: $showPicker, allowedContentTypes: [.item], allowsMultipleSelection: false) { result in
            guard case .success(let urls) = result, let url = urls.first else { return }
            load(url)
        }
    }

    private func passwordCard(_ info: FfiBackupInfo) -> some View {
        CardContainer {
            VStack(alignment: .leading, spacing: 12) {
                Text(String(
                    format: L10n.tr("backup.restore.fromFmt", locale: locale),
                    info.deviceName,
                    BackupService.formattedDate(ms: info.createdAtMs, locale: locale)
                ))
                .font(.qasa(16, weight: .semibold))
                .foregroundColor(Theme.textPrimary)
                .fixedSize(horizontal: false, vertical: true)

                Text(L10n.tr("backup.restore.passwordPrompt", locale: locale))
                    .font(.qasa(14))
                    .foregroundColor(Theme.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)

                SectionLabel(text: L10n.tr("auth.unlock.masterPassword", locale: locale))
                ThemedTextField(
                    icon: "lock.fill",
                    placeholder: L10n.tr("auth.unlock.placeholder", locale: locale),
                    text: $password,
                    isSecure: !passwordVisible,
                    trailing: AnyView(
                        Button {
                            passwordVisible.toggle()
                        } label: {
                            Image(systemName: passwordVisible ? "eye.slash" : "eye")
                                .foregroundColor(Theme.textSecondary)
                        }
                    )
                )
                .textContentType(.password)
                .submitLabel(.go)
                .onSubmit(restore)

                PrimaryButton(
                    title: L10n.tr(isWorking ? "backup.restore.working" : "backup.restore.button", locale: locale),
                    icon: isWorking ? nil : "arrow.counterclockwise",
                    cornerRadius: 8
                ) {
                    restore()
                }
                .disabled(!canRestore)
                .opacity(canRestore ? 1 : 0.5)

                if isWorking {
                    HStack(spacing: 8) {
                        ProgressView()
                        Text(L10n.tr("backup.restore.working", locale: locale))
                            .font(.qasa(14))
                            .foregroundColor(Theme.textSecondary)
                    }
                } else {
                    Button(L10n.tr("backup.restore", locale: locale)) { showPicker = true }
                        .font(.qasa(14, weight: .semibold))
                        .foregroundColor(Theme.accent)
                }
            }
        }
    }

    private func doneBody(_ outcome: FfiRestoreOutcome) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            Text(doneMessage(outcome))
                .font(.qasa(18, weight: .bold))
                .foregroundColor(Theme.accent)
                .fixedSize(horizontal: false, vertical: true)
            PrimaryButton(title: L10n.tr("pair.done", locale: locale), icon: "checkmark", cornerRadius: 8) {
                if mode == .setup, outcome.adopted {
                    onAdopted(password)
                } else {
                    dismiss()
                }
            }
        }
    }

    private func doneMessage(_ outcome: FfiRestoreOutcome) -> String {
        if outcome.adopted {
            return String(format: L10n.tr("backup.restore.doneFmt", locale: locale), Int(outcome.itemsInBackup))
        }
        if outcome.written == 0 {
            return L10n.tr("backup.restore.upToDate", locale: locale)
        }
        return String(format: L10n.tr("backup.restore.mergedFmt", locale: locale), Int(outcome.written))
    }

    private func load(_ url: URL) {
        errorKey = nil
        info = nil
        file = nil
        let data: Data
        do {
            data = try BackupFileIO.read(pickedFile: url)
        } catch {
            errorKey = "backup.error.unreadable"
            return
        }
        switch BackupService.readInfo(data) {
        case .success(let header):
            file = data
            info = header
        case .failure(.message(let key)):
            errorKey = key
        }
    }

    private func restore() {
        guard canRestore, let file else { return }
        errorKey = nil
        isWorking = true
        let typed = password
        let container = modelContext.container
        Task {
            let result = await BackupService.restore(file: file, password: typed, container: container)
            isWorking = false
            switch result {
            case .success(let restored):
                outcome = restored
            case .failure(.message(let key)):
                errorKey = key
            }
        }
    }
}
