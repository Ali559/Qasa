import SwiftUI
import SwiftData

struct NotificationsView: View {
    var onOpenMenu: (() -> Void)? = nil
    var deepLinkId: String? = nil

    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var modelContext
    @Query(sort: \AppNotification.createdAt, order: .reverse) private var localNotes: [AppNotification]

    @State private var remote: [AppNotificationDTO] = []
    @State private var selected: AppNotificationDTO?
    @State private var errorMessage: String?

    private var isEmbedded: Bool { onOpenMenu != nil }
    private var locale: String { AppPreferences.shared.language.rawValue }

    var body: some View {
        VStack(spacing: 0) {
            if let onOpenMenu {
                AppMenuHeader(
                    title: "Notifications",
                    onMenu: onOpenMenu,
                    trailing: AnyView(markAllButton)
                )
            }

            Group {
                if remote.isEmpty && localNotes.isEmpty {
                    ContentUnavailableView(
                        "No Notifications",
                        systemImage: "bell.slash",
                        description: Text("You’re all caught up.")
                    )
                } else {
                    List {
                        if !remote.isEmpty {
                            Section("From Qasa") {
                                ForEach(remote) { note in
                                    Button {
                                        Task { await openRemote(note) }
                                    } label: {
                                        notificationRow(
                                            title: note.title(locale: locale),
                                            body: note.body(locale: locale),
                                            date: formatNotificationDate(note.created_at),
                                            unread: note.read_at == nil
                                        )
                                    }
                                    .listRowBackground(Theme.cardBackground)
                                }
                            }
                        }
                        if !localNotes.isEmpty {
                            Section("On this device") {
                                ForEach(localNotes, id: \.id) { note in
                                    Button {
                                        note.isRead = true
                                        try? modelContext.save()
                                    } label: {
                                        notificationRow(
                                            title: note.title,
                                            body: note.body,
                                            date: note.createdAt.formatted(date: .abbreviated, time: .shortened),
                                            unread: !note.isRead
                                        )
                                    }
                                    .listRowBackground(Theme.cardBackground)
                                }
                                .onDelete { indexSet in
                                    for index in indexSet {
                                        modelContext.delete(localNotes[index])
                                    }
                                    try? modelContext.save()
                                }
                            }
                        }
                    }
                    .scrollContentBackground(.hidden)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)

            if let errorMessage {
                Text(errorMessage)
                    .font(.caption)
                    .foregroundColor(Theme.danger)
                    .padding(.bottom, 8)
            }
        }
        .background(Theme.background.ignoresSafeArea())
        .navigationTitle(isEmbedded ? "" : "Notifications")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if !isEmbedded {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") { dismiss() }
                }
                ToolbarItem(placement: .topBarTrailing) { markAllButton }
            }
        }
        .task {
            remote = await NotificationService.shared.fetchList()
            if let deepLinkId {
                if let detail = try? await NotificationService.shared.fetchDetail(id: deepLinkId) {
                    selected = detail
                }
            }
        }
        .sheet(item: $selected) { note in
            NavigationStack {
                ScrollView {
                    VStack(alignment: .leading, spacing: 12) {
                        Text(note.title(locale: locale))
                            .font(.title2.bold())
                            .foregroundColor(Theme.textPrimary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        Text(formatNotificationDate(note.created_at))
                            .font(.caption)
                            .foregroundColor(Theme.textTertiary)
                        Text(note.body(locale: locale))
                            .font(.body)
                            .foregroundColor(Theme.textSecondary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .padding(Theme.screenPadding)
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Theme.background.ignoresSafeArea())
                .navigationTitle("Notification")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Close") { selected = nil }
                    }
                }
            }
            .presentationDetents([.medium, .large])
            .presentationDragIndicator(.visible)
        }
        .tint(Theme.mint)
    }

    private func notificationRow(title: String, body: String, date: String, unread: Bool) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Circle()
                .fill(unread ? Theme.mint : Theme.stroke)
                .frame(width: 8, height: 8)
                .padding(.top, 6)
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.subheadline.weight(.semibold))
                    .foregroundColor(Theme.textPrimary)
                Text(body)
                    .font(.caption)
                    .foregroundColor(Theme.textSecondary)
                Text(date)
                    .font(.caption2)
                    .foregroundColor(Theme.textTertiary)
            }
        }
    }

    @ViewBuilder
    private var markAllButton: some View {
        if localNotes.contains(where: { !$0.isRead }) {
            Button("Mark All Read") {
                for n in localNotes where !n.isRead { n.isRead = true }
                try? modelContext.save()
            }
            .font(.caption.weight(.semibold))
            .foregroundColor(Theme.mint)
        }
    }

    private func openRemote(_ note: AppNotificationDTO) async {
        do {
            selected = try await NotificationService.shared.fetchDetail(id: note.id)
            remote = NotificationService.shared.items
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func formatNotificationDate(_ iso: String) -> String {
        guard let date = ISO8601DateFormatter.qasa.date(from: iso)
            ?? ISO8601DateFormatter.qasaFractional.date(from: iso)
        else {
            return iso
        }
        return date.formatted(date: .abbreviated, time: .shortened)
    }
}

private extension ISO8601DateFormatter {
    static let qasa: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime]
        return f
    }()

    static let qasaFractional: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return f
    }()
}
