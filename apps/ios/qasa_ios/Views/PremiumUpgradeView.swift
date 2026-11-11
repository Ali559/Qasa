import SwiftUI

struct PremiumUpgradeView: View {
    var onOpenMenu: (() -> Void)? = nil
    var embedded: Bool = false

    @Environment(\.dismiss) private var dismiss
    @Environment(AppSession.self) private var session
    @Environment(AppPreferences.self) private var prefs
    @State private var plans: [PricingPlanDTO] = PricingService.fallbackPlans
    @State private var selectedPlanId: String = "premium_monthly"
    @State private var autoRenew = true
    @State private var selectedGateway: String = "wayl"
    @State private var isPaying = false
    @State private var statusMessage: String?
    @State private var errorMessage: String?
    /// Wallet payments aren't live yet: Pay shows "Coming soon" and sends nothing.
    @State private var showComingSoon = false
    /// The plan page the carousel is showing; kept in step with `selectedPlanId`.
    @State private var visiblePlanId: String?

    private var locale: Locale { prefs.locale }

    /// One option: Wayl's hosted page offers FIB, Qi Card and the other Iraqi wallets itself.
    private var methods: [PaymentMethod] {
        [
            PaymentMethod(
                name: L10n.tr("premium.wayl", locale: locale),
                subtitle: L10n.tr("premium.waylSub", locale: locale),
                badgeText: "Wayl",
                gateway: "wayl"
            )
        ]
    }

    private var selectedMethod: PaymentMethod? {
        methods.first { $0.gateway == selectedGateway } ?? methods.first
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
                AppMenuHeader(title: L10n.tr("nav.upgrade", locale: locale), onMenu: onOpenMenu)
            } else {
                sheetChrome(title: L10n.tr("premium.title", locale: locale))
            }

            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    planCarousel
                    autoRenewRow
                    paymentSection

                    if let statusMessage {
                        Text(statusMessage)
                            .font(.qasa(13))
                            .foregroundColor(Theme.accent)
                    }
                    if let errorMessage {
                        Text(errorMessage)
                            .font(.qasa(13))
                            .foregroundColor(Theme.danger)
                    }
                }
                .padding(.horizontal, Theme.screenPadding)
                .padding(.top, 20)
                .padding(.bottom, 24)
            }
        }
        .bottomActionBar {
            PrimaryButton(
                title: isPaying
                    ? L10n.tr("premium.processing", locale: locale)
                    : String(
                        format: L10n.tr("premium.payVia", locale: locale),
                        formattedAmount,
                        selectedMethod?.badgeText ?? "Wayl"
                    ),
                icon: "lock.fill"
            ) {
                // Payments are not open yet: no request goes to the server.
                // `startPayment()` stays for when they are.
                showComingSoon = true
            }
            .disabled(isPaying || session.isPremium)

            if session.isPremium {
                Text(L10n.tr("premium.alreadyPremium", locale: locale))
                    .font(.qasaLabel(12))
                    .foregroundColor(Theme.success)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
            } else {
                Text(L10n.tr("premium.opensWallet", locale: locale))
                    .font(.qasaLabel(12))
                    .foregroundColor(Theme.textSecondary)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
            }
        }
        .background(Theme.chrome.ignoresSafeArea())
        .appLocalization()
        .confirmSheet(
            isPresented: $showComingSoon,
            title: L10n.tr("premium.comingSoonTitle", locale: locale),
            message: L10n.tr("premium.comingSoonBody", locale: locale),
            icon: "clock",
            confirmTitle: L10n.tr("common.ok", locale: locale),
            onConfirm: {}
        )
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

    /// Shown as a sheet: the shared sheet header (closes by dragging down).
    private func sheetChrome(title: String) -> some View {
        SheetHeader(title: title)
    }

    /// Paged horizontal scroll rather than a `.page` TabView: inside this screen's vertical
    /// ScrollView the TabView never paged forward. This also mirrors in RTL and takes the
    /// height of the tallest card without measuring.
    private var planCarousel: some View {
        VStack(spacing: 12) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 0) {
                    ForEach(plans) { plan in
                        planCard(plan)
                            .frame(maxHeight: .infinity, alignment: .top)
                            // Room for the 1pt border at the page edges.
                            .padding(2)
                            .containerRelativeFrame(.horizontal)
                            .id(plan.id)
                    }
                }
                // Equal-height cards: every page stretches to the tallest one.
                .fixedSize(horizontal: false, vertical: true)
                .scrollTargetLayout()
            }
            .scrollTargetBehavior(.paging)
            .scrollPosition(id: $visiblePlanId)
            .onChange(of: visiblePlanId) { _, id in
                if let id, id != selectedPlanId { selectedPlanId = id }
            }
            .onChange(of: selectedPlanId) { _, id in
                if visiblePlanId != id { visiblePlanId = id }
            }
            .onAppear { visiblePlanId = selectedPlanId }

            HStack(spacing: 8) {
                ForEach(plans) { plan in
                    Circle()
                        .fill(plan.id == selectedPlanId ? Theme.accent : Theme.stroke)
                        .frame(width: 7, height: 7)
                        .animation(.easeInOut(duration: 0.2), value: selectedPlanId)
                }
            }
            .frame(maxWidth: .infinity)
            .accessibilityHidden(true)
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
                    Text(plan.interval == "yearly"
                          ? L10n.tr("premium.bestValue", locale: locale)
                          : L10n.tr("premium.upgradeToPro", locale: locale))
                        .font(.qasaLabel(12))
                        .tracking(1.2)
                        .foregroundColor(Theme.gold)
                    HStack(alignment: .lastTextBaseline, spacing: 4) {
                        Text(plan.displayAmount)
                            .font(.qasa(28, weight: .bold))
                            .foregroundColor(Theme.textPrimary)
                        Text(plan.periodLabel)
                            .font(.qasa(14))
                            .foregroundColor(Theme.textSecondary)
                            .padding(.bottom, 4)
                    }
                    Text(plan.localizedTitle)
                        .font(.qasa(11, weight: .semibold))
                        .foregroundColor(Theme.accent)
                }

                VStack(alignment: .leading, spacing: 12) {
                    ForEach(plan.localizedFeatures, id: \.self) { feature in
                        FeatureRow(text: feature)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(25)
        }
        .frame(maxWidth: .infinity, alignment: .top)
        .background(Theme.cardBackgroundLight)
        // Clip the glow to the card shape, then draw the border on top so it stays whole.
        .clipShape(RoundedRectangle(cornerRadius: Theme.cornerRadius, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: Theme.cornerRadius, style: .continuous)
                .strokeBorder(plan.id == selectedPlanId ? Theme.accent : Theme.stroke, lineWidth: 1)
        )
    }

    /// Only wallets the server can charge get the "we'll charge it" wording; unknown means pay-by-link.
    private var autoRenewDetail: String {
        PricingService.shared.autoRenewModes[selectedGateway]?.lowercased() == "charge"
            ? L10n.tr("premium.autoRenewDetailCharge", locale: locale)
            : L10n.tr("premium.autoRenewDetail", locale: locale)
    }

    private var autoRenewRow: some View {
        ToggleRow(
            icon: "arrow.triangle.2.circlepath",
            iconColor: Theme.accent,
            title: L10n.tr("premium.autoRenew", locale: locale),
            subtitle: autoRenewDetail,
            isOn: $autoRenew
        )
        .padding(.horizontal, 14)
        .background(Theme.cardBackground)
        .clipShape(RoundedRectangle(cornerRadius: Theme.cornerRadius, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: Theme.cornerRadius, style: .continuous)
                .stroke(Theme.stroke, lineWidth: 1)
        )
    }

    private var paymentSection: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text(L10n.tr("premium.selectPayment", locale: locale))
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
        let selected = selectedGateway == method.gateway
        return Button {
            selectedGateway = method.gateway
        } label: {
            HStack(spacing: 0) {
                paymentBadge(method)
                    .padding(.trailing, 14)

                VStack(alignment: .leading, spacing: 2) {
                    Text(method.name)
                        .font(.qasa(14, weight: .semibold))
                        .foregroundColor(Theme.textPrimary)
                    Text(method.subtitle)
                        .font(.qasaLabel(12))
                        .foregroundColor(Theme.textSecondary)
                }
                Spacer(minLength: 12)
                Image(systemName: selected ? "checkmark.circle.fill" : "circle")
                    .foregroundColor(selected ? Theme.accent : Theme.textTertiary)
                    .font(.system(size: 22))
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .frame(minHeight: Theme.rowMinHeight)
            .background(selected ? Theme.accent.opacity(0.06) : Theme.cardBackground)
            .clipShape(RoundedRectangle(cornerRadius: Theme.cornerRadius, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Theme.cornerRadius, style: .continuous)
                    .stroke(selected ? Theme.accent : Theme.stroke, lineWidth: 1)
            )
            .contentShape(Rectangle())
        }
        .buttonStyle(PressableStyle())
    }

    /// No wallet logo for Wayl: a text badge, the method's initial on an accent tint.
    private func paymentBadge(_ method: PaymentMethod) -> some View {
        Text(String(method.badgeText.prefix(1)))
            .font(.qasa(16, weight: .bold))
            .foregroundColor(Theme.accent)
            .frame(width: Theme.iconBadgeSize, height: Theme.iconBadgeSize)
            .background(Theme.accent.opacity(0.12))
            .clipShape(RoundedRectangle(cornerRadius: Theme.iconBadgeCornerRadius, style: .continuous))
            .accessibilityHidden(true)
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
                statusMessage = String(
                    format: L10n.tr("premium.completeInWallet", locale: locale),
                    method.badgeText
                )
            } else {
                #if DEBUG
                statusMessage = String(
                    format: L10n.tr("premium.simulatingPayment", locale: locale),
                    method.badgeText
                )
                // The server may route to another gateway (e.g. Wayl); its webhook is the one to hit.
                try await PaymentService.shared.simulateStubPayment(
                    gateway: intent.gateway,
                    gatewayOrderId: intent.gateway_order_id
                )
                #else
                errorMessage = String(
                    format: L10n.tr("premium.installWalletApp", locale: locale),
                    method.badgeText
                )
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
                    statusMessage = L10n.tr("premium.activated", locale: locale)
                    if !embedded { dismiss() }
                } else {
                    errorMessage = LicenseManager.shared.lastError
                        ?? L10n.tr("premium.licenseRefreshFailed", locale: locale)
                }
            } else {
                errorMessage = PaymentService.shared.lastError
                    ?? L10n.tr("premium.paymentFailed", locale: locale)
            }
        } catch {
            errorMessage = L10n.message(for: error)
        }
    }
}

struct FeatureRow: View {
    let text: String
    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "checkmark.circle.fill")
                .foregroundColor(Theme.accent)
                .font(.system(size: 18))
            Text(text)
                .font(.qasa(14))
                .foregroundColor(Theme.textPrimary)
        }
    }
}

#Preview {
    PremiumUpgradeView()
        .environment(AppSession())
        .environment(AppPreferences.shared)
}
