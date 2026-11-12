import SwiftUI

struct VaultItemRow: View {
    let row: VaultListRow
    var onEdit: () -> Void
    var onDeleted: () -> Void

    @Environment(\.openURL) private var openURL
    @State private var showDeleteConfirm = false
    @State private var toast: String?
    @State private var payload: VaultPayload?

    /// Logins show a bundled site icon when their website has one (`SiteIcon`), else the
    /// site's letter on its brand colour; other kinds keep their symbol.
    @ViewBuilder
    private var tile: some View {
        if case .login(let login) = payload, let asset = SiteIcon.assetName(forWebsite: login.website) {
            Image(asset)
                .renderingMode(.template)
                .resizable()
                .scaledToFit()
                .frame(width: 24, height: 24)
                .foregroundColor(Theme.textPrimary)
                .frame(width: 48, height: 48)
                .background(Theme.cardMuted)
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .accessibilityHidden(true)
        } else if row.kind == .login {
            let logo = SiteLogo(name: row.displayName)
            Text(logo.letter)
                .font(.qasa(18, weight: .bold))
                .foregroundColor(.white)
                .frame(width: 48, height: 48)
                .background(logo.color)
                .clipShape(RoundedRectangle(cornerRadius: 12))
        } else {
            // A note or card shows the colour picked for it; until it is decrypted, the default.
            let itemColor = payload?.organizationColor
            QasaIconImage((payload ?? fallbackPayload).icon, size: 18)
                .foregroundColor(itemColor?.color ?? Theme.accent)
                .frame(width: 48, height: 48)
                .background(itemColor?.tile ?? Theme.cardMuted)
                .clipShape(RoundedRectangle(cornerRadius: 12))
        }
    }

    var body: some View {
        HStack(spacing: 16) {
            tile

            VStack(alignment: .leading, spacing: 2) {
                Text(row.displayName)
                    .font(.qasa(16, weight: .semibold))
                    .foregroundColor(Theme.textPrimary)
                    .lineLimit(1)
                Text(row.subtitle)
                    .font(.qasa(14))
                    .foregroundColor(Theme.textSecondary)
                    .lineLimit(1)
            }

            Spacer(minLength: 8)

            if (payload ?? fallbackPayload).primaryCopy != nil {
                Button {
                    copyPrimary()
                } label: {
                    QasaIconImage(.copy, size: 16)
                        .foregroundColor(Theme.textSecondary)
                        .frame(width: 36, height: 36)
                }
            }

            Menu {
                ForEach((payload ?? fallbackPayload).copyActions) { action in
                    Button {
                        copyValue(action.value, label: action.label)
                    } label: {
                        Label { Text(action.label) } icon: { QasaIconImage(action.icon, size: 17) }
                    }
                }

                // Not a `CopyAction`: the code changes every period, so it's generated on tap.
                if case .login(let login) = payload, let totp = TOTP(stored: login.totp) {
                    Button {
                        VaultClipboard.copy(totp.code())
                        try? VaultRepository.shared.markUsed(id: row.id)
                        flash(L10n.tr("totp.copied"))
                    } label: {
                        Label { Text(L10n.tr("copy.totp")) } icon: { QasaIconImage(.copy, size: 17) }
                    }
                }

                if let urlString = (payload ?? fallbackPayload).websiteURL,
                   let url = normalizedURL(urlString) {
                    Button {
                        openURL(url)
                    } label: {
                        Label { Text(L10n.tr("vault.openWebsite")) } icon: { QasaIconImage(.website, size: 17) }
                    }
                }

                Button {
                    onEdit()
                } label: {
                    Label { Text(L10n.tr("vault.editItem")) } icon: { QasaIconImage(.edit, size: 17) }
                }

                Button(role: .destructive) {
                    showDeleteConfirm = true
                } label: {
                    Label { Text(L10n.tr("vault.deleteItem")) } icon: { QasaIconImage(.delete, size: 17) }
                }
            } label: {
                QasaIconImage(.moreVertical, size: 16)
                    .foregroundColor(Theme.textSecondary)
                    .frame(width: 36, height: 36)
            }
        }
        .padding(17)
        .background(Theme.cardBackground)
        .cornerRadius(Theme.cornerRadius)
        .overlay(
            RoundedRectangle(cornerRadius: Theme.cornerRadius)
                .stroke(Theme.stroke, lineWidth: 1)
        )
        .onAppear(perform: loadPayload)
        .onChange(of: row.updatedAt) { _, _ in
            loadPayload()
        }
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
        .overlay(alignment: .bottom) {
            if let toast {
                Text(toast)
                    .font(.qasa(12, weight: .semibold))
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(Theme.accent)
                    .foregroundColor(Theme.onAccent)
                    .cornerRadius(8)
                    .offset(y: 8)
                    .transition(.opacity)
            }
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
            flash("Copied \(primary.label)")
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
            flash(label.replacingOccurrences(of: "Copy ", with: "Copied "))
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

    private func flash(_ message: String) {
        withAnimation { toast = message }
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) {
            withAnimation { toast = nil }
        }
    }

    private func normalizedURL(_ string: String) -> URL? {
        if let url = URL(string: string), url.scheme != nil { return url }
        return URL(string: "https://\(string)")
    }
}
