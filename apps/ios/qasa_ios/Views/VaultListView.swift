import SwiftUI

struct VaultListView: View {
    @State private var selectedType: VaultItemType? = nil
    @State private var searchText = ""
    @State private var showLinkedDevices = false

    let items: [VaultItem] = [
        VaultItem(title: "GitHub", subtitle: "dev@kaso.io", type: .login, iconName: "chevron.left.forwardslash.chevron.right"),
        VaultItem(title: "FIB Corporate Card", subtitle: "**** 4921", type: .card, iconName: "creditcard.fill"),
        VaultItem(title: "ProtonMail Main", subtitle: "admin@kaso.io", type: .login, iconName: "envelope.fill"),
        VaultItem(title: "AWS Root Account", subtitle: "ops@kaso.io", type: .login, iconName: "cloud.fill", flagged: true)
    ]

    var filteredItems: [VaultItem] {
        items.filter { selectedType == nil || $0.type == selectedType }
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                HStack {
                    Image(systemName: "magnifyingglass")
                        .foregroundColor(Theme.textSecondary)
                    TextField("Search items...", text: $searchText)
                        .foregroundColor(Theme.textPrimary)
                }
                .padding()
                .background(Theme.cardBackground)
                .cornerRadius(Theme.cornerRadius)

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        FilterChip(title: "All", isSelected: selectedType == nil) { selectedType = nil }
                        ForEach(VaultItemType.allCases, id: \.self) { type in
                            FilterChip(title: type.rawValue, isSelected: selectedType == type) {
                                selectedType = type
                            }
                        }
                    }
                }

                ScrollView {
                    VStack(spacing: 10) {
                        ForEach(filteredItems) { item in
                            VaultItemRow(item: item)
                        }
                    }
                }
            }
            .padding(16)
            .background(Theme.background.ignoresSafeArea())
            .navigationTitle("Kaso")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button {
                        showLinkedDevices = true
                    } label: {
                        Image(systemName: "plus.circle.fill")
                            .foregroundColor(Theme.mint)
                    }
                }
            }
            .sheet(isPresented: $showLinkedDevices) {
                LinkedDevicesView()
            }
        }
    }
}

struct FilterChip: View {
    let title: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.caption.weight(.medium))
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(isSelected ? Theme.mint : Theme.cardBackground)
                .foregroundColor(isSelected ? .black : Theme.textPrimary)
                .cornerRadius(20)
        }
    }
}

struct VaultItemRow: View {
    let item: VaultItem

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: item.iconName)
                .foregroundColor(Theme.textPrimary)
                .frame(width: 36, height: 36)
                .background(Theme.cardBackgroundLight)
                .cornerRadius(8)

            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    Text(item.title)
                        .font(.subheadline.weight(.semibold))
                        .foregroundColor(Theme.textPrimary)
                    if item.flagged {
                        Circle().fill(Theme.gold).frame(width: 6, height: 6)
                    }
                }
                Text(item.subtitle)
                    .font(.caption)
                    .foregroundColor(Theme.textSecondary)
            }
            Spacer()
            Image(systemName: "ellipsis")
                .foregroundColor(Theme.textSecondary)
        }
        .padding(12)
        .background(Theme.cardBackground)
        .cornerRadius(Theme.cornerRadius)
    }
}

#Preview {
    VaultListView()
}
