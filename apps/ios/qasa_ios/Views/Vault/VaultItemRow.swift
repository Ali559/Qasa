import SwiftUI

/// One vault item as a row inside a `VaultSectionCard` (design/README.md rule 6e): the 44
/// tile, title over subtitle, then the copy and ⋯ buttons; 64 high, no card of its own.
struct VaultItemRow: View {
    let row: VaultListRow
    var onEdit: () -> Void
    var onDeleted: () -> Void

    @Environment(\.openURL) private var openURL
    @State private var showDeleteConfirm = false
    @State private var showActions = false
    @State private var payload: VaultPayload?

    /// Logins show their site's own icon when one is cached, else the calm letter
    /// (`LoginSiteTile`); other kinds keep their symbol.
    @ViewBuilder
    private var tile: some View {
        if row.kind == .login {
            if case .login(let login) = payload {
                LoginSiteTile(name: row.displayName, website: login.website)
            } else {
                LoginSiteTile(name: row.displayName, website: nil)
            }
        } else {
            // A note or card shows the colour picked for it; until it is decrypted, the default.
            let itemColor = payload?.organizationColor
            QasaIconImage((payload ?? fallbackPayload).icon, size: QasaTokens.Size.vaultTileGlyph)
                .foregroundColor(itemColor?.color ?? Theme.accent)
                .frame(width: QasaTokens.Size.vaultTile, height: QasaTokens.Size.vaultTile)
                .background(itemColor?.tile ?? Theme.cardMuted)
                .clipShape(Circle())
        }
    }

    /// The ⋯ sheet: copy actions, the 2FA code, open website, edit, then delete.
    private var itemActions: [SheetAction] {
        let current = payload ?? fallbackPayload
        var actions = current.copyActions.map { action in
            SheetAction(icon: action.icon, title: action.label) { copyValue(action.value, label: action.label) }
        }
        // Not a `CopyAction`: the code changes every period, so it's generated on tap.
        if case .login(let login) = payload, let totp = TOTP(stored: login.totp) {
            actions.append(SheetAction(icon: .copy, title: L10n.tr("copy.totp")) {
                VaultClipboard.copy(totp.code())
                try? VaultRepository.shared.markUsed(id: row.id)
                ToastCenter.shared.show(L10n.tr("totp.copied"))
            })
        }
        if let urlString = current.websiteURL, let url = normalizedURL(urlString) {
            actions.append(SheetAction(icon: .website, title: L10n.tr("vault.openWebsite")) { openURL(url) })
        }
        actions.append(SheetAction(icon: .edit, title: L10n.tr("vault.editItem")) { onEdit() })
        actions.append(SheetAction(icon: .delete, title: L10n.tr("vault.deleteItem"), destructive: true) {
            showDeleteConfirm = true
        })
        return actions
    }

    var body: some View {
        HStack(spacing: QasaTokens.Space.rowIconGap) {
            tile

            VStack(alignment: .leading, spacing: 0) {
                Text(row.displayName)
                    .font(.qasa(QasaTokens.Typography.vaultRowTitle.size, weight: QasaTokens.Typography.vaultRowTitle.weight))
                    .foregroundColor(Theme.textPrimary)
                    .lineLimit(1)
                Text(row.subtitle)
                    .font(.qasa(QasaTokens.Typography.vaultRowSubtitle.size, weight: QasaTokens.Typography.vaultRowSubtitle.weight))
                    .foregroundColor(Theme.textSecondary)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            // The 36 buttons overlap the gaps by 4 each side, as in the preview, so their
            // glyphs line up with the row's padding.
            HStack(spacing: QasaTokens.Space.rowIconGap - 8) {
                if (payload ?? fallbackPayload).primaryCopy != nil {
                    Button {
                        copyPrimary()
                    } label: {
                        QasaIconImage(.copy, size: QasaTokens.Size.rowIconButtonGlyph)
                            .foregroundColor(Theme.textSecondary)
                            .frame(width: QasaTokens.Size.rowIconButton, height: QasaTokens.Size.rowIconButton)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }

                Button { showActions = true } label: {
                    QasaIconImage(.moreVertical, size: QasaTokens.Size.rowIconButtonGlyph)
                        .foregroundColor(Theme.textSecondary)
                        .frame(width: QasaTokens.Size.rowIconButton, height: QasaTokens.Size.rowIconButton)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(L10n.tr("a11y.moreActions"))
            }
            .padding(.horizontal, -4)
        }
        .padding(.vertical, QasaTokens.Space.vaultRowVertical)
        .padding(.horizontal, QasaTokens.Space.vaultRowHorizontal)
        .frame(maxWidth: .infinity, alignment: .leading)
        .contentShape(Rectangle())
        .onAppear(perform: loadPayload)
        .onChange(of: row.updatedAt) { _, _ in
            loadPayload()
        }
        .qasaActionSheet(isPresented: $showActions, title: row.displayName) { itemActions }
        .confirmSheet(
            isPresented: $showDeleteConfirm,
            title: L10n.tr("vault.deleteItemTitle"),
            message: String(format: L10n.tr("vault.deleteItemMessage"), row.displayName),
            icon: .delete,
            confirmTitle: L10n.tr("common.delete"),
            cancelTitle: L10n.tr("common.cancel"),
            destructive: true
        ) {
            try? VaultRepository.shared.delete(id: row.id)
            onDeleted()
        }
    }

    private var fallbackPayload: VaultPayload {
        .login(LoginFields(itemName: row.displayName))
    }

    private func loadPayload() {
        payload = try? VaultRepository.shared.payload(for: row.id)
    }

    private func copyPrimary() {
        guard let payload = payload ?? Optional(fallbackPayload).flatMap({ _ in try? VaultRepository.shared.payload(for: row.id) }),
              let primary = payload.primaryCopy else { return }
        let doCopy = {
            if PaymentClipboard.copy(
                payload: payload,
                preferredFieldId: PaymentClipboard.preferredFieldId(forLabel: primary.label)
            ) {
                try? VaultRepository.shared.markUsed(id: row.id)
                return
            }
            VaultClipboard.copy(primary.value)
            try? VaultRepository.shared.markUsed(id: row.id)
            ToastCenter.shared.show(Self.copiedMessage(primaryLabel: primary.label))
        }
        if payload.isProtectedNote {
            unlockIfNeeded(protected: true) { ok in
                guard ok else { return }
                doCopy()
            }
        } else {
            doCopy()
        }
    }

    private func copyValue(_ value: String, label: String) {
        let payload = self.payload ?? (try? VaultRepository.shared.payload(for: row.id)) ?? fallbackPayload
        let doCopy = {
            if PaymentClipboard.copy(
                payload: payload,
                preferredFieldId: PaymentClipboard.preferredFieldId(forLabel: label)
            ) {
                try? VaultRepository.shared.markUsed(id: row.id)
                return
            }
            VaultClipboard.copy(value)
            try? VaultRepository.shared.markUsed(id: row.id)
            ToastCenter.shared.show(Self.copiedMessage(actionLabel: label))
        }
        if payload.isProtectedNote && label.contains("Content") {
            unlockIfNeeded(protected: true) { ok in
                guard ok else { return }
                doCopy()
            }
        } else {
            doCopy()
        }
    }

    /// "Copied Password" in the app-wide toast at the bottom of the screen (like Android),
    /// with the field's name in the app's language.
    private static func copiedMessage(fieldKey: String?, fallback: String) -> String {
        String(format: L10n.tr("vault.copiedFmt"), fieldKey.map { L10n.tr($0) } ?? fallback)
    }

    /// `primaryCopy` labels are fixed English keys (they also pick the payment clipboard field).
    private static let primaryFieldKeys: [String: String] = [
        "Password": "field.password", "Title": "field.title", "Number": "field.number",
        "Account Number": "field.accountNumber", "Name": "field.name", "Email": "field.email",
        "Phone Number": "field.phoneNumber", "Address": "field.address",
        "Company": "field.company", "Website": "field.website",
    ]

    private static func copiedMessage(primaryLabel: String) -> String {
        copiedMessage(fieldKey: primaryFieldKeys[primaryLabel], fallback: primaryLabel)
    }

    /// The ⋯ sheet's actions carry their translated "Copy …" title; find its field by key.
    private static let actionFieldKeys: [(copy: String, field: String)] = [
        ("copy.email", "field.email"), ("copy.username", "field.username"),
        ("copy.password", "field.password"), ("copy.website", "field.website"),
        ("copy.content", "field.content"), ("copy.number", "field.number"),
        ("copy.accountHolder", "field.accountHolder"), ("copy.accountNumber", "field.accountNumber"),
        ("copy.routingNumber", "field.routingNumber"), ("copy.securityCode", "field.securityCode"),
        ("copy.expiry", "field.expiryDate"), ("copy.name", "field.name"),
        ("copy.phone", "field.phoneNumber"), ("copy.address", "field.address"),
        ("copy.city", "field.city"), ("copy.zip", "field.zipCode"),
        ("copy.company", "field.company"), ("copy.jobTitle", "field.jobTitle"),
    ]

    private static func copiedMessage(actionLabel: String) -> String {
        let field = actionFieldKeys.first { L10n.tr($0.copy) == actionLabel }?.field
        return copiedMessage(fieldKey: field, fallback: actionLabel)
    }

    private func normalizedURL(_ string: String) -> URL? {
        if let url = URL(string: string), url.scheme != nil { return url }
        return URL(string: "https://\(string)")
    }
}
