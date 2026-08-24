import SwiftUI

struct SettingsView: View {
    @State private var biometricUnlock = true
    @State private var showClearDataAlert = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    CardContainer {
                        HStack(spacing: 12) {
                            Circle()
                                .fill(Theme.cardBackgroundLight)
                                .frame(width: 48, height: 48)
                                .overlay(Image(systemName: "person.fill").foregroundColor(Theme.textSecondary))
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Jane Doe")
                                    .font(.subheadline.weight(.semibold))
                                    .foregroundColor(Theme.textPrimary)
                                Text("jane.doe@example.com")
                                    .font(.caption)
                                    .foregroundColor(Theme.textSecondary)
                                PillBadge(text: "Premium Account", color: Theme.mint)
                            }
                            Spacer()
                        }
                        Divider().background(Theme.stroke).padding(.vertical, 10)
                        Button {} label: {
                            Text("Manage Subscription")
                                .font(.caption.weight(.medium))
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 8)
                                .background(Theme.cardBackgroundLight)
                                .foregroundColor(Theme.textPrimary)
                                .cornerRadius(10)
                        }
                    }

                    VStack(alignment: .leading, spacing: 4) {
                        SectionLabel(text: "Security")
                        CardContainer {
                            NavChevronRow(icon: "key.horizontal.fill", title: "Change Master Password")
                            Divider().background(Theme.stroke)
                            ToggleRow(icon: "faceid", iconColor: Theme.textPrimary, title: "Biometric Unlock", subtitle: "", isOn: $biometricUnlock)
                            Divider().background(Theme.stroke)
                            NavChevronRow(icon: "clock.fill", title: "Auto-lock", trailingText: "5 mins")
                        }
                    }

                    VStack(alignment: .leading, spacing: 4) {
                        HStack {
                            SectionLabel(text: "Linked Devices")
                            Spacer()
                            Text("3 Active")
                                .font(.caption2)
                                .foregroundColor(Theme.textSecondary)
                        }
                        CardContainer {
                            NavChevronRow(icon: "iphone", title: "iPhone 15", subtitle: "(This Device)")
                            Divider().background(Theme.stroke)
                            NavChevronRow(icon: "laptopcomputer", title: "MacBook Pro", subtitle: "Last active: 2h ago")
                            Divider().background(Theme.stroke)
                            NavChevronRow(icon: "puzzlepiece.extension.fill", title: "Chrome Extension", subtitle: "Last active: 1d ago")
                        }
                    }

                    VStack(alignment: .leading, spacing: 4) {
                        SectionLabel(text: "Appearance")
                        CardContainer {
                            NavChevronRow(icon: "globe", title: "Language", trailingText: "English")
                            Divider().background(Theme.stroke)
                            NavChevronRow(icon: "moon.fill", title: "Theme", trailingText: "Dark")
                        }
                    }

                    VStack(alignment: .leading, spacing: 4) {
                        SectionLabel(text: "Data & Account")
                        CardContainer {
                            NavChevronRow(icon: "square.and.arrow.down", title: "Import Data")
                            Divider().background(Theme.stroke)
                            NavChevronRow(icon: "square.and.arrow.up", title: "Export Vault")
                            Divider().background(Theme.stroke)
                            Button {
                                showClearDataAlert = true
                            } label: {
                                NavChevronRow(icon: "xmark.bin.fill", title: "Clear Local Data", destructive: true)
                            }
                            Divider().background(Theme.stroke)
                            NavChevronRow(icon: "rectangle.portrait.and.arrow.right", title: "Logout")
                        }
                    }
                }
                .padding(20)
            }
            .background(Theme.background.ignoresSafeArea())
            .navigationTitle("Settings")
            .alert("Clear Local Data?", isPresented: $showClearDataAlert) {
                Button("Cancel", role: .cancel) {}
                Button("Clear", role: .destructive) {}
            } message: {
                Text("This permanently deletes your local vault. This cannot be undone.")
            }
        }
    }
}

#Preview {
    SettingsView()
}
