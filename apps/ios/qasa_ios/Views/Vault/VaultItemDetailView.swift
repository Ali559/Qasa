import SwiftUI
import SwiftData

struct VaultItemDetailView: View {
    let itemId: UUID
    var onEdit: () -> Void
    var onDeleted: () -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var scenePhase
    @Query(filter: VaultCollection.live) private var collections: [VaultCollection]
    @Bindable private var cache = VaultRepository.shared.cache

    @State private var payload: VaultPayload = .login(LoginFields())
    @State private var showDeleteConfirm = false
    @State private var revealedSecrets: Set<String> = []
    /// Rule 6b: what a protected note may show. `loaded` is false until the payload is read,
    /// so nothing (not even the placeholder) is drawn before the gate knows the item.
    @State private var gate = ProtectedItemGate(isProtected: false)
    @State private var loaded = false
    @State private var toast: String?
    /// The stored payload fails a shared field rule (imported, or saved before the rules).
    @State private var needsAttention = false

    private var row: VaultListRow? {
        cache.row(for: itemId)
    }

    private var collectionName: String? {
        let id = payload.organizationCollectionId ?? row?.collectionId
        guard let id else { return nil }
        return collections.first { $0.id == id }.flatMap(VaultRepository.collectionName)
    }

    var body: some View {
        Group {
            if loaded && gate.showsItem {
                content
            } else {
                ProtectedItemCover()
            }
        }
        .sheet(isPresented: Binding(
            get: { loaded && !gate.showsItem },
            set: { shown in
                // Dragged down while still locked: back to the list.
                if !shown && !gate.showsItem { dismiss() }
            }
        )) {
            SelfSizingSheet {
                ProtectedUnlockSheet { gate.unlock() }
            }
        }
        .onAppear {
            loadItem()
            try? VaultRepository.shared.markUsed(id: itemId)
        }
        .onDisappear { gate.leave() }
        .onChange(of: row?.updatedAt) { _, _ in loadItem() }
        .onChange(of: scenePhase) { _, phase in
            if phase == .background { gate.lock() }
        }
    }

    private var content: some View {
        ScrollView {
            VStack(spacing: 24) {
                heroIcon

                Text(row?.displayName ?? L10n.tr("vault.untitled"))
                    .font(.qasa(22, weight: .bold))
                    .foregroundColor(Theme.textPrimary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, Theme.screenPadding)

                if needsAttention {
                    attentionBanner
                }
                detailsSection
                organizationSection
                historySection
            }
            .padding(.top, 8)
            .padding(.bottom, 40)
        }
        .overlay(alignment: .bottom) {
            if let toast {
                Text(toast)
                    .font(.qasa(13, weight: .semibold))
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .background(Theme.accent)
                    .foregroundColor(Theme.onAccent)
                    .cornerRadius(8)
                    .padding(.bottom, 24)
                    .transition(.opacity)
            }
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
                        QasaIconImage(.edit, size: 17)
                            .foregroundColor(Theme.accent)
                    }
                    .accessibilityLabel(L10n.tr("common.edit"))

                    Button(role: .destructive) {
                        showDeleteConfirm = true
                    } label: {
                        QasaIconImage(.delete, size: 17)
                            .foregroundColor(Theme.danger)
                    }
                    .accessibilityLabel(L10n.tr("common.delete"))
                }
            }
        }
        .confirmSheet(
            isPresented: $showDeleteConfirm,
            title: L10n.tr("vault.deleteItemTitle"),
            message: String(
                format: L10n.tr("vault.deleteItemMessage"),
                row?.displayName ?? L10n.tr("vault.thisItem")
            ),
            icon: .delete,
            confirmTitle: L10n.tr("common.delete"),
            cancelTitle: L10n.tr("common.cancel"),
            destructive: true
        ) {
            try? VaultRepository.shared.delete(id: itemId)
            onDeleted()
            dismiss()
        }
    }

    private func loadItem() {
        guard let item = try? VaultRepository.shared.payload(for: itemId) else { return }
        payload = item
        needsAttention = !VaultValidation.issues(item).isEmpty
        // A first read, or the note's protection changed in the editor: decide again.
        if !loaded || gate.isProtected != item.isProtectedNote {
            gate = ProtectedItemGate(isProtected: item.isProtectedNote)
        }
        loaded = true
    }

    private var attentionBanner: some View {
        HStack(alignment: .top, spacing: 10) {
            QasaIconImage(.warning, size: 16)
                .foregroundColor(Theme.danger)
                .accessibilityHidden(true)
            Text(L10n.tr("validation.needsAttention"))
                .font(.qasa(13, weight: .medium))
                .foregroundColor(Theme.textPrimary)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(14)
        .background(Theme.danger.opacity(0.12))
        .cornerRadius(Theme.cornerRadius)
        .overlay(
            RoundedRectangle(cornerRadius: Theme.cornerRadius)
                .stroke(Theme.danger.opacity(0.4), lineWidth: 1)
        )
        .padding(.horizontal, Theme.screenPadding)
    }

    /// Same clipboard behaviour as copying the password.
    private func copyTOTP(_ code: String) {
        VaultClipboard.copy(code)
        try? VaultRepository.shared.markUsed(id: itemId)
        withAnimation { toast = L10n.tr("totp.copied") }
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) {
            withAnimation { toast = nil }
        }
    }

    /// The subtype symbol, or the login's bundled site icon (`SiteIcon`) when it has one.
    @ViewBuilder
    private var heroGlyph: some View {
        if case .login(let login) = payload, let asset = SiteIcon.assetName(forWebsite: login.website) {
            Image(asset)
                .renderingMode(.template)
                .resizable()
                .scaledToFit()
                .frame(width: 36, height: 36)
                .accessibilityHidden(true)
        } else {
            QasaIconImage(payload.icon, size: 36)
        }
    }

    /// A note or card's hero takes its colour: the glyph in the colour on its tile fill.
    private var heroIcon: some View {
        let itemColor = payload.organizationColor
        return heroGlyph
            .foregroundColor(itemColor?.color ?? Theme.textPrimary)
            .frame(width: 88, height: 88)
            .background(itemColor?.tile ?? Theme.chrome)
            .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .stroke(itemColor == nil ? Theme.strokeChrome : Color.clear, lineWidth: 1)
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
                    // Only codes are shown, never the 2FA secret.
                    if case .login(let login) = payload, let totp = TOTP(stored: login.totp) {
                        Divider().background(Theme.stroke)
                        TOTPCodeRow(totp: totp, onCopy: copyTOTP)
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
                .font(.qasa(14))
                .foregroundColor(Theme.textSecondary)
            Spacer()
            if let noteColor = payload.organizationColor {
                HStack(spacing: 8) {
                    Circle()
                        .fill(noteColor.color)
                        .frame(width: 14, height: 14)
                    Text(noteColor.displayName)
                        .font(.qasa(15, weight: .medium))
                        .foregroundColor(Theme.textPrimary)
                }
            } else {
                Text(L10n.tr("common.none"))
                    .font(.qasa(15, weight: .medium))
                    .foregroundColor(Theme.textSecondary)
            }
        }
        .padding(.vertical, 12)
    }

    private var organizationCollectionRow: some View {
        HStack {
            Text(L10n.tr("vault.collection"))
                .font(.qasa(14))
                .foregroundColor(Theme.textSecondary)
            Spacer()
            Text(collectionName ?? L10n.tr("common.none"))
                .font(.qasa(15, weight: .medium))
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
                .font(.qasa(14))
                .foregroundColor(Theme.textSecondary)
            Spacer()
            Text(date.map { VaultDateFormat.display($0) } ?? L10n.tr("vault.never"))
                .font(.qasa(15, weight: .medium))
                .foregroundColor(Theme.textPrimary)
                .multilineTextAlignment(.trailing)
        }
        .padding(.vertical, 12)
    }

    private func detailCard<Content: View>(title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title)
                .font(.qasa(14, weight: .semibold))
                .foregroundColor(Theme.textSecondary)
                .textCase(.uppercase)

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
        // A protected note hides every value (rule 6b); other items hide their secrets.
        let guarded = gate.isProtected && field.hiddenWhenProtected
        let maskable = guarded || field.isSecret
        let hidden = guarded ? gate.isHidden(field.id) : (field.isSecret && !revealedSecrets.contains(field.id))

        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(field.label)
                    .font(.qasa(13))
                    .foregroundColor(Theme.textSecondary)
                Spacer()
                if maskable {
                    Button {
                        if guarded {
                            gate.toggle(field.id)
                        } else if revealedSecrets.contains(field.id) {
                            revealedSecrets.remove(field.id)
                        } else {
                            revealedSecrets.insert(field.id)
                        }
                    } label: {
                        QasaIconImage(hidden ? .show : .hide, size: 14)
                            .foregroundColor(Theme.accent)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(L10n.tr(hidden ? "vault.showPassword" : "vault.hidePassword"))
                }
            }

            if hidden {
                Text(String(repeating: "•", count: min(max(field.value.count, 8), 16)))
                    .font(.kasoMono(15))
                    .foregroundColor(Theme.textPrimary)
                    .accessibilityLabel(L10n.tr("vault.hidePassword"))
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
            // Copying works without revealing; the item is already unlocked to be drawn.
            guard !field.value.isEmpty else { return }
            if PaymentClipboard.copy(
                payload: payload,
                preferredFieldId: PaymentClipboard.preferredFieldId(forLabel: field.label)
            ) {
                try? VaultRepository.shared.markUsed(id: itemId)
                return
            }
            VaultClipboard.copy(field.value)
            try? VaultRepository.shared.markUsed(id: itemId)
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
