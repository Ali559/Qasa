import SwiftUI

struct SettingsView: View {
    var onOpenMenu: () -> Void = {}
    var onLogout: (() -> Void)? = nil

    @Environment(AppSession.self) private var session
    @State private var biometricUnlock = true
    @State private var showClearDataAlert = false
    @State private var showLinkedDevices = false
    @State private var showPremium = false
    @State private var showLogoutConfirm = false

    var body: some View {
        VStack(spacing: 0) {
            AppMenuHeader(title: "Settings", onMenu: onOpenMenu)

            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    profileCard

                    settingsSection(title: "Security") {
                        NavChevronRow(icon: "ellipsis", title: "Change Master Password")
                        Divider().background(Theme.stroke)
                        ToggleRow(
                            icon: "touchid",
                            iconColor: Theme.textPrimary,
                            title: "Biometric Unlock",
                            subtitle: "",
                            isOn: $biometricUnlock
                        )
                        Divider().background(Theme.stroke)
                        NavChevronRow(icon: "clock", title: "Auto-lock", trailingText: "5 mins")
                    }

                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            SectionLabel(text: "Linked Devices")
                            Spacer()
                            Text("3 Active")
                                .font(.kasoMono(12))
                                .foregroundColor(Theme.mint)
                        }
                        CardContainer {
                            Button { showLinkedDevices = true } label: {
                                NavChevronRow(
                                    icon: "iphone",
                                    title: "iPhone 15",
                                    subtitle: "(This Device)",
                                    iconColor: Theme.mint
                                )
                            }
                            .buttonStyle(.plain)
                            Divider().background(Theme.stroke)
                            NavChevronRow(icon: "laptopcomputer", title: "MacBook Pro", subtitle: "Last active: 2h ago")
                            Divider().background(Theme.stroke)
                            NavChevronRow(icon: "puzzlepiece.extension", title: "Chrome Extension", subtitle: "Last active: 1d ago")
                        }
                    }

                    settingsSection(title: "Appearance") {
                        NavChevronRow(icon: "globe", title: "Language", trailingText: "English")
                        Divider().background(Theme.stroke)
                        NavChevronRow(icon: "moon.fill", title: "Theme", trailingText: "Dark")
                    }

                    settingsSection(title: "Data & Account") {
                        NavChevronRow(icon: "square.and.arrow.down", title: "Import Data")
                        Divider().background(Theme.stroke)
                        NavChevronRow(icon: "square.and.arrow.up", title: "Export Vault")
                        Divider().background(Theme.stroke)
                        Button { showClearDataAlert = true } label: {
                            NavChevronRow(icon: "xmark.bin", title: "Clear Local Data", destructive: true)
                        }
                        .buttonStyle(.plain)
                        Divider().background(Theme.stroke)
                        Button { showLogoutConfirm = true } label: {
                            NavChevronRow(icon: "rectangle.portrait.and.arrow.right", title: "Logout")
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(Theme.screenPadding)
                .padding(.bottom, 40)
            }
        }
        .background(Theme.background.ignoresSafeArea())
        .alert("Clear Local Data?", isPresented: $showClearDataAlert) {
            Button("Cancel", role: .cancel) {}
            Button("Clear", role: .destructive) {}
        } message: {
            Text("This permanently deletes your local vault. This cannot be undone.")
        }
        .alert("Lock Vault?", isPresented: $showLogoutConfirm) {
            Button("Cancel", role: .cancel) {}
            Button("Logout", role: .destructive) {
                onLogout?()
            }
        } message: {
            Text("You’ll need your master password to unlock again.")
        }
        .sheet(isPresented: $showLinkedDevices) { LinkedDevicesView() }
        .sheet(isPresented: $showPremium) {
            PremiumUpgradeView(embedded: false)
                .environment(session)
        }
    }

    private var profileCard: some View {
        CardContainer {
            HStack(spacing: 12) {
                Circle()
                    .stroke(Theme.mint, lineWidth: 2)
                    .frame(width: 56, height: 56)
                    .overlay(
                        Image(systemName: "person.fill")
                            .font(.title2)
                            .foregroundColor(Theme.textSecondary)
                    )
                VStack(alignment: .leading, spacing: 4) {
                    Text(session.username)
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(Theme.textPrimary)
                    Text(session.email)
                        .font(.system(size: 14))
                        .foregroundColor(Theme.textSecondary)
                    HStack(spacing: 4) {
                        Image(systemName: session.isPremium ? "trophy.fill" : "person")
                            .font(.system(size: 10))
                        Text(session.isPremium ? "Premium Account" : "Free Account")
                            .font(.kasoMono(12))
                    }
                    .foregroundColor(session.isPremium ? Theme.mint : Theme.textSecondary)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .overlay(Capsule().stroke(session.isPremium ? Theme.mint : Theme.stroke, lineWidth: 1))
                }
                Spacer()
            }

            Button { showPremium = true } label: {
                Text(session.isPremium ? "Manage Subscription" : "Upgrade to Premium")
                    .font(.system(size: 14, weight: .medium))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
                    .background(Theme.cardBackgroundLight)
                    .foregroundColor(Theme.textPrimary)
                    .cornerRadius(8)
            }
            .padding(.top, 12)
        }
    }

    private func settingsSection(title: String, @ViewBuilder content: () -> some View) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            SectionLabel(text: title)
            CardContainer { content() }
        }
    }
}

#Preview {
    SettingsView(onOpenMenu: {})
        .environment(AppSession())
}
