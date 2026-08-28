import SwiftUI

struct PremiumUpgradeView: View {
    var onOpenMenu: (() -> Void)? = nil
    var embedded: Bool = false

    @Environment(\.dismiss) private var dismiss
    @Environment(AppSession.self) private var session
    @State private var plans: [PricingPlanDTO] = PricingService.fallbackPlans
    @State private var selectedPlanId: String = "premium_monthly"
    @State private var autoRenew = true
    @State private var selectedMethodID: UUID?
    @State private var isPaying = false
    @State private var statusMessage: String?
    @State private var errorMessage: String?

    private var methods: [PaymentMethod] {
        [
            PaymentMethod(name: L10n.tr("premium.fib"), subtitle: L10n.tr("premium.fibSub"), badgeText: "FIB", badgeColorHex: "FFFFFF", gateway: "fib"),
            PaymentMethod(name: L10n.tr("premium.fastpay"), subtitle: L10n.tr("premium.fastpaySub"), badgeText: "FastPay", badgeColorHex: "E31837", gateway: "fastpay"),
            PaymentMethod(name: L10n.tr("premium.superqi"), subtitle: L10n.tr("premium.superqiSub"), badgeText: "SuperQi", badgeColorHex: "2563EB", gateway: "superqi")
        ]
    }

    private var selectedMethod: PaymentMethod? {
        methods.first { $0.id == selectedMethodID } ?? methods.first
    }

    private var selectedPlan: PricingPlanDTO {
        plans.first { $0.id == selectedPlanId } ?? plans[0]
    }

    private var formattedAmount: String {
        selectedPlan.displayAmount
    }

    var body: some View {
        VStack(spacing: 0) {
            if embedded, let onOpenMenu {
                AppMenuHeader(title: L10n.tr("nav.upgrade", locale: AppPreferences.shared.locale), onMenu: onOpenMenu)
            } else {
                sheetChrome(title: L10n.tr("premium.title"))
            }

            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    planCarousel
                    autoRenewRow
                    paymentSection

                    if let statusMessage {
                        Text(statusMessage)
                            .font(.system(size: 13))
                            .foregroundColor(Theme.mint)
                    }
                    if let errorMessage {
                        Text(errorMessage)
                            .font(.system(size: 13))
                            .foregroundColor(Theme.danger)
                    }
                }
                .padding(.horizontal, Theme.screenPadding)
                .padding(.top, 20)
                .padding(.bottom, 140)
            }

            VStack(spacing: 12) {
                PrimaryButton(
                    title: isPaying
                        ? L10n.tr("premium.processing")
                        : String(format: L10n.tr("premium.payVia"), formattedAmount, selectedMethod?.badgeText ?? "FIB"),
                    icon: "lock.fill",
                    cornerRadius: 8
                ) {
                    Task { await startPayment() }
                }
                .opacity(isPaying ? 0.6 : 1)
                .disabled(isPaying || session.isPremium)

                if session.isPremium {
                    Text(L10n.tr("premium.alreadyPremium"))
                        .font(.kasoMono(12))
                        .tracking(0.6)
                        .foregroundColor(Theme.success)
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity)
                } else {
                    Text(L10n.tr("premium.opensWallet"))
                        .font(.kasoMono(12))
                        .tracking(0.6)
                        .foregroundColor(Theme.textSecondary)
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity)
                }
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
        .appLocalization()
        .onAppear { selectedMethodID = methods.first?.id }
        .task {
            let fetched = await PricingService.shared.fetchPlans()
            if !fetched.isEmpty {
                plans = fetched
                if plans.allSatisfy({ $0.id != selectedPlanId }) {
                    selectedPlanId = plans[0].id
                }
            }
        }
    }

    private func sheetChrome(title: String) -> some View {
        HStack {
            Button { dismiss() } label: {
                Image(systemName: "xmark")
                    .font(.qasa(14, weight: .semibold))
                    .foregroundColor(Theme.textPrimary)
                    .frame(width: 32, height: 32)
            }
            Spacer()
            Text(title)
                .font(.qasa(20, weight: .bold))
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

    private var planCarousel: some View {
        VStack(spacing: 12) {
            TabView(selection: $selectedPlanId) {
                ForEach(plans) { plan in
                    planCard(plan)
                        .tag(plan.id)
                        .padding(.horizontal, 2)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .never))
            .frame(height: 240)

            HStack(spacing: 8) {
                ForEach(plans) { plan in
                    Circle()
                        .fill(plan.id == selectedPlanId ? Theme.mint : Theme.stroke)
                        .frame(width: 7, height: 7)
                        .animation(.easeInOut(duration: 0.2), value: selectedPlanId)
                }
            }
            .frame(maxWidth: .infinity)
        }
    }

    private func planCard(_ plan: PricingPlanDTO) -> some View {
        ZStack(alignment: .topTrailing) {
            Circle()
                .fill(Theme.gold.opacity(0.1))
                .frame(width: 128, height: 128)
                .blur(radius: 20)
                .offset(x: 40, y: -40)

            VStack(spacing: 20) {
                VStack(spacing: 8) {
                    Text(plan.interval == "yearly" ? L10n.tr("premium.bestValue") : L10n.tr("premium.upgradeToPro"))
                        .font(.kasoMono(12))
                        .tracking(1.2)
                        .foregroundColor(Theme.gold)
                    HStack(alignment: .lastTextBaseline, spacing: 4) {
                        Text(plan.displayAmount)
                            .font(.system(size: 28, weight: .heavy))
                            .foregroundColor(Theme.textPrimary)
                        Text(plan.periodLabel)
                            .font(.system(size: 14))
                            .foregroundColor(Theme.textSecondary)
                            .padding(.bottom, 4)
                    }
                    Text(plan.localizedTitle)
                        .font(AppTypography.usesRudaw ? .qasa(11, weight: .semibold) : .kasoMono(11))
                        .foregroundColor(Theme.mint)
                }

                VStack(alignment: .leading, spacing: 12) {
                    ForEach(plan.featureList.isEmpty ? [L10n.tr("premium.featureDevices"), L10n.tr("premium.featureBreach"), L10n.tr("premium.featureVPN")] : plan.featureList, id: \.self) { feature in
                        FeatureRow(text: feature)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(25)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(Theme.cardBackgroundLight)
        .cornerRadius(Theme.cornerRadius)
        .overlay(
            RoundedRectangle(cornerRadius: Theme.cornerRadius)
                .stroke(plan.id == selectedPlanId ? Theme.mint : Theme.stroke, lineWidth: 1)
        )
        .clipped()
    }

    private var autoRenewRow: some View {
        HStack(spacing: 12) {
            Image(systemName: "arrow.triangle.2.circlepath")
                .foregroundColor(Theme.mint)
                .font(.system(size: 18))
            VStack(alignment: .leading, spacing: 2) {
                Text(L10n.tr("premium.autoRenew"))
                    .font(.qasa(15, weight: .semibold))
                    .foregroundColor(Theme.textPrimary)
                Text(L10n.tr("premium.autoRenewDetail"))
                    .font(.qasa(12))
                    .foregroundColor(Theme.textSecondary)
            }
            Spacer()
            Toggle("", isOn: $autoRenew)
                .labelsHidden()
                .tint(Theme.mint)
        }
        .padding(16)
        .background(Theme.chrome)
        .cornerRadius(8)
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(Theme.stroke, lineWidth: 1)
        )
    }

    private var paymentSection: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text(L10n.tr("premium.selectPayment"))
                .font(.qasa(14))
                .foregroundColor(Theme.textSecondary)

            VStack(spacing: 12) {
                ForEach(methods) { method in
                    paymentRow(method)
                }
            }
        }
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
                        .font(.qasa(14, weight: .semibold))
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

    private func startPayment() async {
        guard let method = selectedMethod else { return }
        let gateway = method.gateway
        let plan = selectedPlan
        let renew = autoRenew
        isPaying = true
        errorMessage = nil
        statusMessage = nil
        defer { isPaying = false }

        do {
            let intent = try await PaymentService.shared.createIntent(
                gateway: gateway,
                planId: plan.id,
                amountIQD: plan.amount_iqd,
                autoRenew: renew
            )
            let opened = await PaymentService.shared.openDeepLink(intent.deep_link)

            if opened {
                statusMessage = "Complete payment in \(method.badgeText)…"
            } else {
                #if DEBUG
                statusMessage = "\(method.badgeText) app not available — simulating payment…"
                try await PaymentService.shared.simulateStubPayment(
                    gateway: gateway,
                    gatewayOrderId: intent.gateway_order_id
                )
                #else
                errorMessage = "Install the \(method.badgeText) app to complete payment, then try again."
                return
                #endif
            }

            let success = await PaymentService.shared.pollUntilComplete(
                paymentId: intent.payment_id,
                maxAttempts: opened ? 30 : 10,
                intervalSeconds: opened ? 2 : 1
            )
            if success {
                if !LicenseManager.shared.isPremium {
                    _ = await LicenseManager.shared.refresh()
                }
                await MainActor.run {
                    session.syncFromLicense()
                }
                if session.isPremium {
                    statusMessage = L10n.tr("premium.activated")
                    if !embedded { dismiss() }
                } else {
                    errorMessage = LicenseManager.shared.lastError
                        ?? L10n.tr("premium.licenseRefreshFailed")
                }
            } else {
                errorMessage = PaymentService.shared.lastError
            }
        } catch {
            errorMessage = error.localizedDescription
        }
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
