import SwiftUI
import SwiftData

struct NotificationsView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var modelContext
    @Query(sort: \AppNotification.createdAt, order: .reverse) private var notifications: [AppNotification]

    var body: some View {
        NavigationStack {
            Group {
                if notifications.isEmpty {
                    ContentUnavailableView(
                        "No Notifications",
                        systemImage: "bell.slash",
                        description: Text("You’re all caught up.")
                    )
                } else {
                    List {
                        ForEach(notifications, id: \.id) { note in
                            Button {
                                note.isRead = true
                                try? modelContext.save()
                            } label: {
                                HStack(alignment: .top, spacing: 12) {
                                    Circle()
                                        .fill(note.isRead ? Theme.stroke : Theme.mint)
                                        .frame(width: 8, height: 8)
                                        .padding(.top, 6)
                                    VStack(alignment: .leading, spacing: 4) {
                                        Text(note.title)
                                            .font(.subheadline.weight(.semibold))
                                            .foregroundColor(Theme.textPrimary)
                                        Text(note.body)
                                            .font(.caption)
                                            .foregroundColor(Theme.textSecondary)
                                        Text(note.createdAt.formatted(date: .abbreviated, time: .shortened))
                                            .font(.caption2)
                                            .foregroundColor(Theme.textTertiary)
                                    }
                                }
                            }
                            .listRowBackground(Theme.cardBackground)
                        }
                        .onDelete { indexSet in
                            for index in indexSet {
                                modelContext.delete(notifications[index])
                            }
                            try? modelContext.save()
                        }
                    }
                    .scrollContentBackground(.hidden)
                }
            }
            .background(Theme.background.ignoresSafeArea())
            .navigationTitle("Notifications")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") { dismiss() }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    if notifications.contains(where: { !$0.isRead }) {
                        Button("Mark All Read") {
                            for n in notifications where !n.isRead {
                                n.isRead = true
                            }
                            try? modelContext.save()
                        }
                        .font(.caption)
                    }
                }
            }
        }
        .preferredColorScheme(.dark)
        .tint(Theme.mint)
    }
}
