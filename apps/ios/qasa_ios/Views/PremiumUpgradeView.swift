import SwiftUI

struct PremiumUpgradeView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var selectedMethodID: UUID?

    let methods: [PaymentMethod] = [
        PaymentMethod(name: "First Iraqi Bank (FIB)", subtitle: "Instant Transfer", badgeText: "FIB", badgeColorHex: "FFFFFF"),
        PaymentMethod(name: "FastPay", subtitle: "Mobile Wallet", badgeText: "FastPay", badgeColorHex: "EF4444"),
        PaymentMethod(name: "SuperQi", subtitle: "Qi Card integration", badgeText: "SuperQi", badgeColorHex: "3B82F6")
    ]

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    CardContainer {
                        VStack(spacing: 10) {
                            Text("UPGRADE TO PRO")
                                .font(.caption.weight(.bold))
                                .foregroundColor(Theme.gold)
                            Text("10,000 IQD / mo")
                                .font(.system(size: 32, weight: .bold))
                                .foregroundColor(Theme.textPrimary)
                                .font(.subheadline)
                                .foregroundColor(Theme.textSecondary)

                            VStack(alignment: .leading, spacing: 10) {
                                FeatureRow(text: "Protect up to 5 Devices")
                                FeatureRow(text: "24/7 Dark Web Breach Monitor")
                                FeatureRow(text: "Unlimited High-Speed VPN")
                            }
                            .padding(.top, 8)
                        }
                        .frame(maxWidth: .infinity)
                    }

                    SectionLabel(text: "Select Payment Method")
                    VStack(spacing: 10) {
                        ForEach(methods) { method in
                            Button {
                                selectedMethodID = method.id
                            } label: {
                                HStack(spacing: 12) {
                                    Text(method.badgeText)
                                        .font(.caption2.weight(.bold))
                                        .padding(8)
                                        .background(Color(hex: method.badgeColorHex).opacity(0.2))
                                        .foregroundColor(Color(hex: method.badgeColorHex))
                                        .cornerRadius(6)
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(method.name)
                                            .foregroundColor(Theme.textPrimary)
                                            .font(.subheadline.weight(.medium))
                                        Text(method.subtitle)
                                            .font(.caption)
                                            .foregroundColor(Theme.textSecondary)
                                    }
                                    Spacer()
                                    Image(systemName: selectedMethodID == method.id ? "checkmark.circle.fill" : "circle")
                                        .foregroundColor(selectedMethodID == method.id ? Theme.mint : Theme.textTertiary)
                                }
                                .padding(12)
                                .background(Theme.cardBackground)
                                .cornerRadius(Theme.cornerRadius)
                            }
                        }
                    }
                    .onAppear { selectedMethodID = methods.first?.id }

                    PrimaryButton(title: "Pay 10,000 IQD via FIB") {
                        dismiss()
                    }
                    Text("Local instant payment. Cancel anytime from device settings.")
                        .font(.caption2)
                        .foregroundColor(Theme.textSecondary)
                        .frame(maxWidth: .infinity, alignment: .center)
                }
                .padding(20)
            }
            .background(Theme.background.ignoresSafeArea())
            .navigationTitle("Kaso Premium")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button { dismiss() } label: {
                        Image(systemName: "xmark")
                    }
                }
            }
        }
    }
}

struct FeatureRow: View {
    let text: String
    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "checkmark.circle.fill")
                .foregroundColor(Theme.mint)
            Text(text)
                .font(.subheadline)
                .foregroundColor(Theme.textPrimary)
        }
    }
}

#Preview {
    PremiumUpgradeView()
}
