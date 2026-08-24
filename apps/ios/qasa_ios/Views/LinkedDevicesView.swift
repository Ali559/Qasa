import SwiftUI

struct LinkedDevicesView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var showPremium = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    Button {
                        showPremium = true
                    } label: {
                        CardContainer {
                            VStack(alignment: .leading, spacing: 12) {
                                HStack {
                                    Image(systemName: "star.fill")
                                        .foregroundColor(Theme.gold)
                                    Text("Upgrade to Premium")
                                        .font(.subheadline.weight(.semibold))
                                        .foregroundColor(Theme.textPrimary)
                                }
                                Text("Unlock up to 5 simultaneous devices across Web, iOS, and Android for only 10,000 IQD/mo.")
                                    .font(.caption)
                                    .foregroundColor(Theme.textSecondary)
                                PrimaryButton(title: "Upgrade Now", color: Theme.gold) {
                                    showPremium = true
                                }
                            }
                        }
                    }
                    .buttonStyle(.plain)

                    SectionLabel(text: "Active Devices")

                    CardContainer {
                        HStack(spacing: 12) {
                            Rectangle().fill(Theme.mint).frame(width: 3)
                            Image(systemName: "iphone")
                                .foregroundColor(Theme.textPrimary)
                            VStack(alignment: .leading, spacing: 4) {
                                HStack {
                                    Text("iPhone 15")
                                        .foregroundColor(Theme.textPrimary)
                                        .font(.subheadline.weight(.semibold))
                                    PillBadge(text: "THIS DEVICE", color: Theme.mint)
                                }
                                Text("📍 Baghdad, IQ • Active Now")
                                    .font(.caption)
                                    .foregroundColor(Theme.textSecondary)
                            }
                            Spacer()
                        }
                        Divider().background(Theme.stroke).padding(.vertical, 8)
                        Button {} label: {
                            HStack {
                                Image(systemName: "link.badge.minus")
                                Text("Unlink")
                            }
                            .frame(maxWidth: .infinity)
                            .foregroundColor(Theme.textSecondary)
                            .padding(.vertical, 8)
                        }
                    }

                    CardContainer {
                        VStack(spacing: 12) {
                            Image(systemName: "plus.square.dashed")
                                .font(.title2)
                                .foregroundColor(Theme.textSecondary)
                            Text("Device Limit Reached")
                                .font(.subheadline.weight(.semibold))
                                .foregroundColor(Theme.textPrimary)
                            Text("You are currently on the Free Tier which allows 1 device. Upgrade to link your Mac or Windows PC.")
                                .font(.caption)
                                .foregroundColor(Theme.textSecondary)
                                .multilineTextAlignment(.center)
                        }
                        .frame(maxWidth: .infinity)
                    }

                    HStack(alignment: .top, spacing: 8) {
                        Image(systemName: "info.circle")
                            .foregroundColor(Theme.textSecondary)
                        Text("Unlinking a device will immediately sign it out and permanently purge local vault data from that hardware. Ensure your Master Password is safely stored.")
                            .font(.caption2)
                            .foregroundColor(Theme.textSecondary)
                    }
                }
                .padding(20)
            }
            .background(Theme.background.ignoresSafeArea())
            .navigationTitle("Linked Devices")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button { dismiss() } label: {
                        Image(systemName: "chevron.left")
                    }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    PillBadge(text: "Free Tier: 1/1 Used", color: Theme.textSecondary)
                }
            }
            .sheet(isPresented: $showPremium) {
                PremiumUpgradeView()
            }
        }
    }
}

#Preview {
    LinkedDevicesView()
}
