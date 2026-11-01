import SwiftUI
import SwiftData

struct VaultItemDetailView: View {
    let itemId: UUID
    var onEdit: () -> Void
    var onDeleted: () -> Void

    @Environment(\.dismiss) private var dismiss
    @Query(sort: \VaultCollection.name) private var collections: [VaultCollection]
    @Bindable private var cache = VaultRepository.shared.cache

    @State private var payload: VaultPayload = .login(LoginFields())
    @State private var showDeleteConfirm = false
    @State private var revealedSecrets: Set<String> = []
    @State private var unlockedProtected = false

    private var row: VaultListRow? {
        cache.row(for: itemId)
    }

    private var collectionName: String? {
        let id = payload.organizationCollectionId ?? row?.collectionId
        guard let id else { return nil }
        return collections.first { $0.id == id }?.name
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                heroIcon

                Text(row?.displayName ?? L10n.tr("vault.untitled"))
                    .font(.system(size: 22, weight: .bold))
                    .foregroundColor(Theme.textPrimary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, Theme.screenPadding)

                detailsSection
                organizationSection
                historySection
            }
            .padding(.top, 8)
            .padding(.bottom, 40)
        }
        .background(Theme.background.ignoresSafeArea())
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.visible, for: .navigationBar)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                HStack(spacing: 16) {
                    Button {
                        onEdit()
                    } label: {
                        Image(systemName: "pencil")
                            .foregroundColor(Theme.mint)
                    }
                    .accessibilityLabel(L10n.tr("common.edit"))

                    Button(role: .destructive) {
                        showDeleteConfirm = true
                    } label: {
                        Image(systemName: "trash")
                            .foregroundColor(Theme.danger)
                    }
                    .accessibilityLabel(L10n.tr("common.delete"))
                }
            }
        }
        .alert(L10n.tr("vault.deleteItemTitle"), isPresented: $showDeleteConfirm) {
            Button(L10n.tr("common.cancel"), role: .cancel) {}
            Button(L10n.tr("common.delete"), role: .destructive) {
                try? VaultRepository.shared.delete(id: itemId)
                onDeleted()
                dismiss()
            }
        } message: {
            Text(String(
                format: L10n.tr("vault.deleteItemMessage"),
                row?.displayName ?? L10n.tr("vault.thisItem")
            ))
        }
        .onAppear {
            loadItem()
            try? VaultRepository.shared.markUsed(id: itemId)
            unlockProtectedIfNeeded()
        }
        .onChange(of: row?.updatedAt) { _, _ in
            loadItem()
            unlockProtectedIfNeeded()
        }
    }

    private func loadItem() {
        if let loaded = try? VaultRepository.shared.payload(for: itemId) {
            payload = loaded
        }
    }

    private func unlockProtectedIfNeeded() {
        if payload.isProtectedNote {
            unlockIfNeeded(protected: true) { ok in
                unlockedProtected = ok
            }
        } else {
            unlockedProtected = true
        }
    }

    private var heroIcon: some View {
        Image(systemName: payload.systemImage)
            .font(.system(size: 36, weight: .medium))
            .foregroundColor(Theme.textPrimary)
            .frame(width: 88, height: 88)
            .background(Theme.chrome)
            .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .stroke(Theme.strokeChrome, lineWidth: 1)
            )
            .padding(.top, 12)
    }

    private var detailsSection: some View {
        detailCard(title: L10n.tr("vault.details")) {
            let fields = payload.detailFields
            if fields.isEmpty {
                Text(L10n.tr("vault.noDetails"))
                    .foregroundColor(Theme.textSecondary)
            } else {
                VStack(spacing: 0) {
                    ForEach(Array(fields.enumerated()), id: \.element.id) { index, field in
                        detailRow(field)
                        if index < fields.count - 1 {
                            Divider().background(Theme.stroke)
                        }
                    }
                }
            }
        }
    }

    private var organizationSection: some View {
        detailCard(title: L10n.tr("vault.organization")) {
            VStack(spacing: 0) {
                organizationColorRow
                Divider().background(Theme.stroke)
                organizationCollectionRow
            }
        }
    }

    private var organizationColorRow: some View {
        HStack {
            Text(L10n.tr("vault.color"))
                .font(.system(size: 14))
                .foregroundColor(Theme.textSecondary)
            Spacer()
            if let noteColor = payload.organizationColor {
                HStack(spacing: 8) {
                    Circle()
                        .fill(noteColor.color)
                        .frame(width: 14, height: 14)
                    Text(noteColor.displayName)
                        .font(.system(size: 15, weight: .medium))
                        .foregroundColor(Theme.textPrimary)
                }
            } else {
                Text(L10n.tr("common.none"))
                    .font(.system(size: 15, weight: .medium))
                    .foregroundColor(Theme.textSecondary)
            }
        }
        .padding(.vertical, 12)
    }

    private var organizationCollectionRow: some View {
        HStack {
            Text(L10n.tr("vault.collection"))
                .font(.system(size: 14))
                .foregroundColor(Theme.textSecondary)
            Spacer()
            Text(collectionName ?? L10n.tr("common.none"))
                .font(.system(size: 15, weight: .medium))
                .foregroundColor(collectionName == nil ? Theme.textSecondary : Theme.textPrimary)
        }
        .padding(.vertical, 12)
    }

    private var historySection: some View {
        detailCard(title: L10n.tr("vault.history")) {
            VStack(spacing: 0) {
                historyRow(label: L10n.tr("vault.created"), date: row?.createdAt)
                Divider().background(Theme.stroke)
                historyRow(label: L10n.tr("vault.lastUpdated"), date: row?.updatedAt)
                Divider().background(Theme.stroke)
                historyRow(label: L10n.tr("vault.lastUsed"), date: row?.lastUsedAt)
            }
        }
    }

    private func historyRow(label: String, date: Date?) -> some View {
        HStack(alignment: .firstTextBaseline) {
            Text(label)
                .font(.system(size: 14))
                .foregroundColor(Theme.textSecondary)
            Spacer()
            Text(date.map { VaultDateFormat.display($0) } ?? L10n.tr("vault.never"))
                .font(.system(size: 15, weight: .medium))
                .foregroundColor(Theme.textPrimary)
                .multilineTextAlignment(.trailing)
        }
        .padding(.vertical, 12)
    }

    private func detailCard<Content: View>(title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title)
                .font(.system(size: 14, weight: .semibold))
                .foregroundColor(Theme.textSecondary)
                .textCase(.uppercase)
                .tracking(0.6)

            content()
                .padding(.horizontal, 16)
                .padding(.vertical, 4)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Theme.cardBackground)
                .cornerRadius(Theme.cornerRadius)
                .overlay(
                    RoundedRectangle(cornerRadius: Theme.cornerRadius)
                        .stroke(Theme.stroke, lineWidth: 1)
                )
        }
        .padding(.horizontal, Theme.screenPadding)
    }

    @ViewBuilder
    private func detailRow(_ field: DetailField) -> some View {
        let locked = field.isSecret && payload.isProtectedNote && !unlockedProtected
        let hidden = field.isSecret && !revealedSecrets.contains(field.id) && !locked

        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(field.label)
                    .font(.system(size: 13))
                    .foregroundColor(Theme.textSecondary)
                Spacer()
                if field.isSecret && !locked {
                    Button {
                        if revealedSecrets.contains(field.id) {
                            revealedSecrets.remove(field.id)
                        } else {
                            revealedSecrets.insert(field.id)
                        }
                    } label: {
                        Image(systemName: hidden ? "eye" : "eye.slash")
                            .font(.system(size: 14))
                            .foregroundColor(Theme.mint)
                    }
                    .buttonStyle(.plain)
                }
            }

            if locked {
                Text(L10n.tr("vault.lockedAuthenticate"))
                    .font(.system(size: 15, weight: .medium))
                    .foregroundColor(Theme.textSecondary)
            } else if hidden {
                Text(String(repeating: "•", count: min(max(field.value.count, 8), 16)))
                    .font(.kasoMono(15))
                    .foregroundColor(Theme.textPrimary)
            } else {
                Text(field.value)
                    .font(field.isSecret ? .kasoMono(15) : .system(size: 15, weight: .medium))
                    .foregroundColor(Theme.textPrimary)
                    .textSelection(.enabled)
            }
        }
        .padding(.vertical, 12)
        .contentShape(Rectangle())
        .onTapGesture {
            guard !locked, !field.value.isEmpty else { return }
            let value = field.value
            let copyAction = {
                if PaymentClipboard.copy(
                    payload: payload,
                    preferredFieldId: PaymentClipboard.preferredFieldId(forLabel: field.label)
                ) {
                    try? VaultRepository.shared.markUsed(id: itemId)
                    return
                }
                VaultClipboard.copy(value)
                try? VaultRepository.shared.markUsed(id: itemId)
            }
            if field.isSecret && payload.isProtectedNote {
                unlockIfNeeded(protected: true) { ok in
                    guard ok else { return }
                    copyAction()
                }
            } else {
                copyAction()
            }
        }
    }
}

enum VaultDateFormat {
    /// Recent: minutes / hours. Otherwise absolute like `Mar 4, 2025, 6:03PM`.
    static func display(_ date: Date, now: Date = Date()) -> String {
        let locale = AppLanguage.current.locale
        let seconds = now.timeIntervalSince(date)
        guard seconds >= 0 else { return absolute(date, locale: locale) }

        if seconds < 60 {
            return L10n.tr("date.justNow", locale: locale)
        }
        if seconds < 3600 {
            let minutes = Int(seconds / 60)
            if minutes == 1 {
                return L10n.tr("date.minuteAgo", locale: locale)
            }
            return String(
                format: L10n.tr("date.minutesAgo", locale: locale),
                locale: locale,
                arguments: [minutes]
            )
        }
        if seconds < 24 * 3600 {
            let hours = Int(seconds / 3600)
            if hours == 1 {
                return L10n.tr("date.hourAgo", locale: locale)
            }
            return String(
                format: L10n.tr("date.hoursAgo", locale: locale),
                locale: locale,
                arguments: [hours]
            )
        }
        return absolute(date, locale: locale)
    }

    private static func absolute(_ date: Date, locale: Locale) -> String {
        date.formatted(
            Date.FormatStyle(date: .abbreviated, time: .shortened)
                .locale(locale)
        )
    }
}
