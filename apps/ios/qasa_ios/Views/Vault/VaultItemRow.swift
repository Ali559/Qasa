import SwiftUI
import SwiftData

struct VaultItemRow: View {
    let item: VaultItemRecord
    var onEdit: () -> Void
    var onDeleted: () -> Void

    @Environment(\.modelContext) private var modelContext
    @Environment(\.openURL) private var openURL
    @State private var showDeleteConfirm = false
    @State private var toast: String?

    private var payload: VaultPayload { item.payload }

    var body: some View {
        HStack(spacing: 16) {
            Image(systemName: payload.systemImage)
                .font(.system(size: 18, weight: .medium))
                .foregroundColor(Theme.textPrimary)
                .frame(width: 48, height: 48)
                .background(Theme.chrome)
                .cornerRadius(8)
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(Theme.strokeChrome, lineWidth: 1)
                )

            VStack(alignment: .leading, spacing: 2) {
                Text(item.displayName)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(Theme.textPrimary)
                    .lineLimit(1)
                Text(payload.subtitle)
                    .font(.system(size: 14))
                    .foregroundColor(Theme.textSecondary)
                    .lineLimit(1)
            }

            Spacer(minLength: 8)

            if payload.primaryCopy != nil {
                Button {
                    copyPrimary()
                } label: {
                    Image(systemName: "doc.on.doc")
                        .foregroundColor(Theme.textSecondary)
                        .frame(width: 36, height: 36)
                }
            }

            Menu {
                ForEach(payload.copyActions) { action in
                    Button {
                        copyValue(action.value, label: action.label)
                    } label: {
                        Label(action.label, systemImage: action.systemImage)
                    }
                }

                if let urlString = payload.websiteURL,
                   let url = normalizedURL(urlString) {
                    Button {
                        openURL(url)
                    } label: {
                        Label("Open Website", systemImage: "safari")
                    }
                }

                Button {
                    onEdit()
                } label: {
                    Label("Edit Item", systemImage: "pencil")
                }

                Button(role: .destructive) {
                    showDeleteConfirm = true
                } label: {
                    Label("Delete Item", systemImage: "trash")
                }
            } label: {
                Image(systemName: "ellipsis")
                    .rotationEffect(.degrees(90))
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
        .alert("Delete Item?", isPresented: $showDeleteConfirm) {
            Button("Cancel", role: .cancel) {}
            Button("Delete", role: .destructive) {
                modelContext.delete(item)
                try? modelContext.save()
                onDeleted()
            }
        } message: {
            Text("This permanently removes “\(item.displayName)” from your vault.")
        }
        .overlay(alignment: .bottom) {
            if let toast {
                Text(toast)
                    .font(.caption.weight(.semibold))
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(Theme.mint)
                    .foregroundColor(Theme.mintOnPrimary)
                    .cornerRadius(8)
                    .offset(y: 8)
                    .transition(.opacity)
            }
        }
    }

    private func copyPrimary() {
        guard let primary = payload.primaryCopy else { return }
        if payload.isProtectedNote {
            unlockIfNeeded(protected: true) { ok in
                guard ok else { return }
                VaultClipboard.copy(primary.value)
                flash("Copied \(primary.label)")
            }
        } else {
            VaultClipboard.copy(primary.value)
            flash("Copied \(primary.label)")
        }
    }

    private func copyValue(_ value: String, label: String) {
        if payload.isProtectedNote && label.contains("Content") {
            unlockIfNeeded(protected: true) { ok in
                guard ok else { return }
                VaultClipboard.copy(value)
                flash(label.replacingOccurrences(of: "Copy ", with: "Copied "))
            }
        } else {
            VaultClipboard.copy(value)
            flash(label.replacingOccurrences(of: "Copy ", with: "Copied "))
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
