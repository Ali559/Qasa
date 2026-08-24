import SwiftUI

struct PremiumUpgradeView: View {
    var onOpenMenu: (() -> Void)? = nil
    var embedded: Bool = false

    @Environment(\.dismiss) private var dismiss
    @Environment(AppSession.self) private var session
    @State private var selectedMethodID: UUID?

    let methods: [PaymentMethod] = [
        PaymentMethod(name: "First Iraqi Bank (FIB)", subtitle: "Instant Transfer", badgeText: "FIB", badgeColorHex: "FFFFFF"),
        PaymentMethod(name: "FastPay", subtitle: "Mobile Wallet", badgeText: "FastPay", badgeColorHex: "E31837"),
        PaymentMethod(name: "SuperQi", subtitle: "Qi Card integration", badgeText: "SuperQi", badgeColorHex: "2563EB")
    ]

    private var selectedMethod: PaymentMethod? {
        methods.first { $0.id == selectedMethodID } ?? methods.first
    }

    var body: some View {
        VStack(spacing: 0) {
            if embedded, let onOpenMenu {
                AppMenuHeader(title: "Upgrade", onMenu: onOpenMenu)
            } else {
                HStack {
                    Button { dismiss() } label: {
                        Image(systemName: "xmark")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(Theme.textPrimary)
                            .frame(width: 32, height: 32)
                    }
                    Spacer()
                    Text("Kaso Premium")
                        .font(.system(size: 20, weight: .bold))
                        .foregroundColor(Theme.textOnChrome)
                    Spacer()
                    Color.clear.frame(width: 32, height: 32)
                }
                .padding(.horizontal, 16)
                .frame(height: 64)
                .background(Theme.chrome)
                .overlay(alignment: .bottom) {
                    Rectangle().fill(Theme.strokeChrome).frame(height: 1)
                }
            }

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    heroCard
                    paymentSection
                        .padding(.top, 4)
                }
                .padding(.horizontal, Theme.screenPadding)
                .padding(.top, 20)
                .padding(.bottom, 140)
            }

            VStack(spacing: 12) {
                PrimaryButton(
                    title: "Pay 10,000 IQD via \(selectedMethod?.badgeText ?? "FIB")",
                    icon: "lock.fill",
                    cornerRadius: 8
                ) {
                    session.setPremium(true)
                    if embedded {
                        // Stay on page; menu will now hide Upgrade.
                    } else {
                        dismiss()
                    }
                }
                Text("Local instant payment. Cancel anytime\nfrom device settings.")
                    .font(.kasoMono(12))
                    .tracking(0.6)
                    .foregroundColor(Theme.textSecondary)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
            }
            .padding(.horizontal, Theme.screenPadding)
            .padding(.top, 21)
            .padding(.bottom, 20)
            .background(Theme.cardMuted.opacity(0.95))
            .overlay(alignment: .top) {
                Rectangle().fill(Theme.stroke).frame(height: 1)
            }
        }
        .background(Theme.chrome.ignoresSafeArea())
        .onAppear { selectedMethodID = methods.first?.id }
    }

    private var heroCard: some View {
        ZStack(alignment: .topTrailing) {
            Circle()
                .fill(Theme.gold.opacity(0.1))
                .frame(width: 128, height: 128)
                .blur(radius: 20)
                .offset(x: 40, y: -40)

            VStack(spacing: 24) {
                VStack(spacing: 8) {
                    Text("UPGRADE TO PRO")
                        .font(.kasoMono(12))
                        .tracking(1.2)
                        .foregroundColor(Theme.gold)
                    HStack(alignment: .lastTextBaseline, spacing: 4) {
                        Text("10,000")
                            .font(.system(size: 28, weight: .heavy))
                            .foregroundColor(Theme.textPrimary)
                        Text("IQD / mo")
                            .font(.system(size: 14))
                            .foregroundColor(Theme.textSecondary)
                            .padding(.bottom, 4)
                    }
                }

                VStack(alignment: .leading, spacing: 12) {
                    FeatureRow(text: "Protect up to 5 Devices")
                    FeatureRow(text: "24/7 Dark Web Breach Monitor")
                    FeatureRow(text: "Unlimited High-Speed VPN")
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(25)
        }
        .background(Theme.cardBackgroundLight)
        .cornerRadius(Theme.cornerRadius)
        .overlay(
            RoundedRectangle(cornerRadius: Theme.cornerRadius)
                .stroke(Theme.stroke, lineWidth: 1)
        )
        .clipped()
    }

    private var paymentSection: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Select Payment Method")
                .font(.system(size: 14))
                .foregroundColor(Theme.textSecondary)

            VStack(spacing: 12) {
                ForEach(methods) { method in
                    paymentRow(method)
                }
            }
        }
        .padding(.top, 16)
    }

    private func paymentRow(_ method: PaymentMethod) -> some View {
        let selected = selectedMethodID == method.id
        return Button {
            selectedMethodID = method.id
        } label: {
            HStack(spacing: 0) {
                paymentBadge(method)
                    .padding(.trailing, 16)

                VStack(alignment: .leading, spacing: 2) {
                    Text(method.name)
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(Theme.textPrimary)
                    Text(method.subtitle)
                        .font(.kasoMono(12))
                        .tracking(0.6)
                        .foregroundColor(Theme.textSecondary)
                }
                Spacer()
                Image(systemName: selected ? "checkmark.circle.fill" : "circle")
                    .foregroundColor(selected ? Theme.mint : Theme.stroke)
                    .font(.system(size: 20))
            }
            .padding(17)
            .background(selected ? Theme.mint.opacity(0.05) : Theme.chrome)
            .cornerRadius(8)
            .overlay(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(selected ? Theme.mint : Theme.stroke, lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }

    private func paymentBadge(_ method: PaymentMethod) -> some View {
        let isFIB = method.badgeText == "FIB"
        return Text(isFIB ? "First\nIraqi\nBank" : method.badgeText)
            .font(.system(size: 9, weight: .bold))
            .multilineTextAlignment(.center)
            .foregroundColor(isFIB ? .black : .white)
            .frame(width: 48, height: 32)
            .background(isFIB ? Color.white : Color(hex: method.badgeColorHex))
            .cornerRadius(4)
    }
}

struct FeatureRow: View {
    let text: String
    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "checkmark.circle.fill")
                .foregroundColor(Theme.mint)
                .font(.system(size: 18))
            Text(text)
                .font(.system(size: 14))
                .foregroundColor(Theme.textPrimary)
        }
    }
}

#Preview {
    PremiumUpgradeView()
        .environment(AppSession())
}
