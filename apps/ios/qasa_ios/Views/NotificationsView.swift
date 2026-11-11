import SwiftUI
import SwiftData

struct NotificationsView: View {
    var onOpenMenu: (() -> Void)? = nil
    var deepLinkId: String? = nil

    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var modelContext
    @Environment(AppPreferences.self) private var prefs
    @Query(sort: \AppNotification.createdAt, order: .reverse) private var localNotes: [AppNotification]

    @State private var selected: AppNotificationDTO?
    private let service = NotificationService.shared
    private var remote: [AppNotificationDTO] { service.items }
    private var remoteHasUnread: Bool {
        service.unreadCount > 0 || remote.contains { $0.read_at == nil }
    }
    @State private var errorMessage: String?

    private var isEmbedded: Bool { onOpenMenu != nil }
    private var localeCode: String { prefs.language.rawValue }
    private var locale: Locale { prefs.locale }

    var body: some View {
        VStack(spacing: 0) {
            if let onOpenMenu {
                AppMenuHeader(
                    title: L10n.tr("notifications.title", locale: locale),
                    onMenu: onOpenMenu,
                    trailing: AnyView(markAllButton)
                )
            }

            Group {
                if remote.isEmpty && localNotes.isEmpty {
                    ContentUnavailableView(
                        L10n.tr("notifications.empty", locale: locale),
                        systemImage: "bell.slash",
                        description: Text(L10n.tr("notifications.emptyDesc", locale: locale))
                    )
                } else {
                    List {
                        if !remote.isEmpty {
                            Section(L10n.tr("notifications.fromQasa", locale: locale)) {
                                ForEach(remote) { note in
                                    Button {
                                        Task { await openRemote(note) }
                                    } label: {
                                        notificationRow(
                                            title: note.title(locale: localeCode),
                                            body: note.body(locale: localeCode),
                                            date: formatNotificationDate(note.created_at),
                                            unread: note.read_at == nil
                                        )
                                    }
                                    .listRowBackground(Theme.cardBackground)
                                    .onAppear {
                                        // End of the loaded pages scrolled into view.
                                        if note.id == remote.last?.id {
                                            Task { await service.loadMore() }
                                        }
                                    }
                                }
                                .onDelete { indexSet in
                                    let ids = indexSet.map { remote[$0].id }
                                    for id in ids { deleteRemote(id) }
                                }
                                if service.hasMore {
                                    ProgressView()
                                        .frame(maxWidth: .infinity)
                                        .listRowBackground(Theme.cardBackground)
                                }
                            }
                        }
                        if !localNotes.isEmpty {
                            Section(L10n.tr("notifications.onDevice", locale: locale)) {
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
                    .font(.qasa(12))
                    .foregroundColor(Theme.danger)
                    .padding(.bottom, 8)
            }
        }
        .background(Theme.background.ignoresSafeArea())
        .modifier(NotificationsSheetHeader(
            isEmbedded: isEmbedded,
            title: L10n.tr("notifications.title", locale: locale),
            markAll: AnyView(markAllButton)
        ))
        .task {
            await service.fetchList()
            if let deepLinkId {
                if let detail = try? await service.fetchDetail(id: deepLinkId) {
                    selected = detail
                    await service.markRead(id: detail.id)
                }
            }
        }
        .sheet(item: $selected) { note in
            NavigationStack {
                ScrollView {
                    VStack(alignment: .leading, spacing: 12) {
                        Text(note.title(locale: localeCode))
                            .font(.qasa(22, weight: .bold))
                            .foregroundColor(Theme.textPrimary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        Text(formatNotificationDate(note.created_at))
                            .font(.qasa(12))
                            .foregroundColor(Theme.textTertiary)
                        Text(note.body(locale: localeCode))
                            .font(.qasa(17))
                            .foregroundColor(Theme.textSecondary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .padding(Theme.screenPadding)
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Theme.background.ignoresSafeArea())
                .sheetHeader(L10n.tr("notifications.detailTitle", locale: locale))
            }
            .presentationDetents([.medium, .large])
            .qasaSheetPresentation()
            .appLocalization()
        }
        .tint(Theme.accent)
    }

    private func notificationRow(title: String, body: String, date: String, unread: Bool) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Circle()
                .fill(unread ? Theme.accent : Theme.stroke)
                .frame(width: 8, height: 8)
                .padding(.top, 6)
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.qasa(15, weight: .semibold))
                    .foregroundColor(Theme.textPrimary)
                Text(body)
                    .font(.qasa(12))
                    .foregroundColor(Theme.textSecondary)
                Text(date)
                    .font(.qasa(11))
                    .foregroundColor(Theme.textTertiary)
            }
        }
    }

    /// Both sections: local rows in SwiftData, server rows through `read-all`.
    @ViewBuilder
    private var markAllButton: some View {
        if remoteHasUnread || localNotes.contains(where: { !$0.isRead }) {
            Button(L10n.tr("notifications.markAllRead", locale: AppPreferences.shared.locale)) {
                for n in localNotes where !n.isRead { n.isRead = true }
                try? modelContext.save()
                if remoteHasUnread {
                    Task {
                        if await service.markAllRead() {
                            errorMessage = nil
                        } else {
                            errorMessage = L10n.tr("notifications.actionFailed", locale: locale)
                        }
                    }
                }
            }
            .font(.qasa(12, weight: .semibold))
            .foregroundColor(Theme.accent)
        }
    }

    /// Show the row we already have and mark it read on the server.
    private func openRemote(_ note: AppNotificationDTO) async {
        selected = note
        await service.markRead(id: note.id)
    }

    /// Optimistic per-user delete; the row comes back if the server call fails.
    private func deleteRemote(_ id: String) {
        if selected?.id == id { selected = nil }
        service.delete(id: id) { ok in
            errorMessage = ok ? nil : L10n.tr("notifications.actionFailed", locale: locale)
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

/// As a sheet (not embedded in the shell): the shared sheet header, Mark all read trailing.
private struct NotificationsSheetHeader: ViewModifier {
    let isEmbedded: Bool
    let title: String
    let markAll: AnyView

    func body(content: Content) -> some View {
        if isEmbedded {
            content.toolbar(.hidden, for: .navigationBar)
        } else {
            content.sheetHeader(title) { markAll }
        }
    }
}
