import Foundation
import SwiftUI

/// Recorded when the on-disk vault store cannot be opened.
///
/// The store file is deliberately left in place. Destroying it to "recover" turns a
/// transient failure (out of storage, SQLite lock, WAL contention) into permanent,
/// unrecoverable loss of every password the user has.
struct VaultStoreFailure: Identifiable {
    let id = UUID()
    let underlying: String
    let storeURL: URL

    var storePath: String { storeURL.path(percentEncoded: false) }
}

/// Blocking screen shown instead of the vault when the store would not open.
///
/// Deliberately offers no "retry" affordance: `qasaApp.init()` runs once per process
/// launch, so a button here could not re-attempt the open without discarding whatever
/// is in memory. It also offers no "reset" affordance, because that is exactly the
/// destructive action this screen exists to prevent.
struct VaultStoreErrorView: View {
    let failure: VaultStoreFailure
    @Environment(AppPreferences.self) private var prefs

    private var locale: Locale { prefs.locale }

    var body: some View {
        ZStack {
            Theme.background.ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    header
                    remedy
                    diagnostics
                }
                .padding(24)
                .frame(maxWidth: 520, alignment: .leading)
                .frame(maxWidth: .infinity)
            }
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 10) {
            Image(systemName: "lock.trianglebadge.exclamationmark.fill")
                .font(.system(size: 40, weight: .semibold))
                .foregroundColor(Theme.danger)
            Text(L10n.tr("error.vaultStoreTitle", locale: locale))
                .font(.qasa(26, weight: .bold))
                .foregroundColor(Theme.textPrimary)
            Text(L10n.tr("error.vaultStoreBody", locale: locale))
                .font(.qasa(16))
                .foregroundColor(Theme.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    /// Not named `help` — that collides with SwiftUI's `View.help(_:)` modifier.
    private var remedy: some View {
        Text(L10n.tr("error.vaultStoreHelp", locale: locale))
            .font(.qasa(16))
            .foregroundColor(Theme.textPrimary)
            .fixedSize(horizontal: false, vertical: true)
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.cardBackground)
            .clipShape(RoundedRectangle(cornerRadius: Theme.cornerRadius, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Theme.cornerRadius, style: .continuous)
                    .stroke(Theme.stroke, lineWidth: 1)
            )
    }

    private var diagnostics: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(L10n.tr("error.vaultStorePath", locale: locale) + " " + failure.storePath)
            Text(L10n.tr("error.vaultStoreDetail", locale: locale) + " " + failure.underlying)
        }
        .font(.kasoMono(12, weight: .regular))
        .foregroundColor(Theme.textSecondary)
        .textSelection(.enabled)
        .fixedSize(horizontal: false, vertical: true)
    }
}
