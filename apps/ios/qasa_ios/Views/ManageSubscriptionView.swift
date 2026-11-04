import SwiftUI

struct ManageSubscriptionView: View {
    var embedded: Bool = false
    var onOpenMenu: (() -> Void)? = nil

    @Environment(\.dismiss) private var dismiss
    @Environment(AppSession.self) private var session

    @State private var subscription: SubscriptionDTO?
    @State private var autoRenew = true
    @State private var isLoading = true
    @State private var isSaving = false
    @State private var errorMessage: String?
    @State private var statusMessage: String?

    @State private var showCancelConfirm = false
    @State private var showCancelReason = false
    @State private var cancelReason = ""
    @State private var isCancelling = false

    @State private var showRemovePaymentConfirm = false
    @State private var isRemovingPaymentMethod = false

    @State private var isPayingRenewal = false

    private var locale: Locale { AppLanguage.current.locale }

    private var plan: SubscriptionPlan {
        SubscriptionPlan.from(planId: subscription?.plan_id ?? "")
    }

    private var isCancelled: Bool {
        (subscription?.status ?? "").lowercased() == "cancelled"
    }

    var body: some View {
        VStack(spacing: 0) {
            if embedded, let onOpenMenu {
                AppMenuHeader(title: L10n.tr("settings.subscription", locale: locale), onMenu: onOpenMenu)
            } else {
                HStack {
                    Button { dismiss() } label: {
                        Image(systemName: "xmark")
                            .font(.qasa(14, weight: .semibold))
                            .foregroundColor(Theme.textPrimary)
                            .frame(width: 32, height: 32)
                    }
                    Spacer()
                    Text(L10n.tr("sub.manageTitle", locale: locale))
                        .font(.qasa(18, weight: .bold))
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
                VStack(alignment: .leading, spacing: 16) {
                    if isLoading {
                        ProgressView()
                            .tint(Theme.accent)
                            .frame(maxWidth: .infinity)
                            .padding(.top, 40)
                    } else if let subscription {
                        detailsCard(subscription)
                        if let link = subscription.renewal?.link {
                            renewalLinkCard(link)
                        }
                        autoRenewCard
                        if !isCancelled {
                            cancelButton
                        }
                    } else {
                        Text(L10n.tr("sub.noneActive", locale: locale))
                            .font(.qasa(14))
                            .foregroundColor(Theme.textSecondary)
                            .frame(maxWidth: .infinity)
                            .padding(.top, 40)
                    }

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
                .padding(.bottom, 40)
            }
        }
        .background(Theme.chrome.ignoresSafeArea())
        .appLocalization()
        .task { await load() }
        .alert(L10n.tr("sub.cancelConfirmTitle", locale: locale), isPresented: $showCancelConfirm) {
            Button(L10n.tr("sub.keepPremium", locale: locale), role: .cancel) {}
            Button(L10n.tr("sub.continue", locale: locale), role: .destructive) {
                cancelReason = ""
                showCancelReason = true
            }
        } message: {
            Text(String(
                format: L10n.tr("sub.cancelConfirmMessage", locale: locale),
                formattedDate(subscription?.expires_at)
            ))
        }
        .alert(L10n.tr("sub.removePaymentMethod", locale: locale), isPresented: $showRemovePaymentConfirm) {
            Button(L10n.tr("common.cancel", locale: locale), role: .cancel) {}
            Button(L10n.tr("sub.removePaymentMethod", locale: locale), role: .destructive) {
                Task { await removePaymentMethod() }
            }
        } message: {
            Text(L10n.tr("sub.removePaymentMethodConfirm", locale: locale))
        }
        .sheet(isPresented: $showCancelReason) {
            cancelReasonSheet
                .appLocalization()
        }
    }

    private func detailsCard(_ sub: SubscriptionDTO) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                Text(plan.displayName)
                    .font(.qasa(18, weight: .bold))
                    .foregroundColor(Theme.textPrimary)
                Spacer()
                Text(isCancelled ? L10n.tr("sub.cancelling", locale: locale) : L10n.tr("sub.active", locale: locale))
                    .font(AppTypography.usesRudaw ? .qasa(11, weight: .semibold) : .qasaLabel(11))
                    .foregroundColor(isCancelled ? Theme.gold : Theme.accent)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .overlay(
                        Capsule().stroke(isCancelled ? Theme.gold : Theme.accent, lineWidth: 1)
                    )
            }

            detailRow(
                label: L10n.tr("sub.amount", locale: locale),
                value: "\(sub.amount_iqd.formatted(.number.grouping(.automatic).locale(locale))) \(L10n.tr("sub.iqd", locale: locale))"
            )
            detailRow(label: L10n.tr("sub.started", locale: locale), value: formattedDate(sub.started_at))
            detailRow(label: L10n.tr("sub.renewsExpires", locale: locale), value: formattedDate(sub.expires_at))
            if isCancelled, let cancelledAt = sub.cancelled_at {
                detailRow(label: L10n.tr("sub.cancelled", locale: locale), value: formattedDate(cancelledAt))
            }

            Text(isCancelled
                 ? L10n.tr("sub.accessContinues", locale: locale)
                 : L10n.tr("sub.premiumIncludes", locale: locale))
                .font(.qasa(13))
                .foregroundColor(Theme.textSecondary)
        }
        .padding(20)
        .background(Theme.cardBackgroundLight)
        .cornerRadius(Theme.cornerRadius)
        .overlay(
            RoundedRectangle(cornerRadius: Theme.cornerRadius)
                .stroke(Theme.stroke, lineWidth: 1)
        )
    }

    private func detailRow(label: String, value: String) -> some View {
        HStack {
            Text(label)
                .font(.qasa(13))
                .foregroundColor(Theme.textSecondary)
            Spacer()
            Text(value)
                .font(.qasa(13, weight: .medium))
                .foregroundColor(Theme.textPrimary)
        }
    }

    /// Cancelled / off / older servers keep the original wording.
    private var autoRenewSubtitle: String {
        if isCancelled {
            return L10n.tr("sub.autoRenewOffCancelled", locale: locale)
        }
        guard let sub = subscription, let renewal = sub.renewal, sub.auto_renew else {
            return L10n.tr("sub.autoRenewOn", locale: locale)
        }
        let amount = renewal.amount_iqd ?? sub.amount_iqd
        if renewal.mode?.lowercased() == "link" {
            return String(
                format: L10n.tr("sub.renewsByLink", locale: locale),
                formattedDate(sub.expires_at),
                formattedIQD(amount)
            )
        }
        guard renewal.chargesSavedWallet else {
            return L10n.tr("sub.renewalNotSetUp", locale: locale)
        }
        return String(
            format: L10n.tr("sub.renewsVia", locale: locale),
            formattedDate(sub.expires_at),
            formattedIQD(amount),
            gatewayName(renewal.gateway)
        )
    }

    private func formattedIQD(_ amount: Int) -> String {
        amount.formatted(.number.grouping(.automatic).locale(locale))
    }

    private func gatewayName(_ gateway: String?) -> String {
        switch gateway?.lowercased() {
        case "fib": return L10n.tr("premium.fib", locale: locale)
        case "fastpay": return L10n.tr("premium.fastpay", locale: locale)
        case "superqi": return L10n.tr("premium.superqi", locale: locale)
        case "wayl": return "Wayl"
        default: return gateway ?? "—"
        }
    }

    private var autoRenewCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 12) {
                Image(systemName: "arrow.triangle.2.circlepath")
                    .foregroundColor(Theme.accent)
                    .font(.qasa(18))
                VStack(alignment: .leading, spacing: 2) {
                    Text(L10n.tr("sub.autoRenew", locale: locale))
                        .font(.qasa(15, weight: .semibold))
                        .foregroundColor(Theme.textPrimary)
                    Text(autoRenewSubtitle)
                        .font(.qasa(12))
                        .foregroundColor(Theme.textSecondary)
                }
                Spacer()
                Toggle("", isOn: Binding(
                    get: { autoRenew },
                    set: { newValue in
                        guard !isCancelled, !isSaving, subscription != nil else { return }
                        autoRenew = newValue
                        Task { await updateAutoRenew(newValue) }
                    }
                ))
                .labelsHidden()
                .tint(Theme.accent)
                .disabled(isCancelled || isSaving || subscription == nil)
            }

            if subscription?.renewal?.chargesSavedWallet == true {
                Button {
                    showRemovePaymentConfirm = true
                } label: {
                    Text(L10n.tr("sub.removePaymentMethod", locale: locale))
                        .font(.qasa(13, weight: .semibold))
                        .foregroundColor(Theme.danger)
                }
                .buttonStyle(.plain)
                .disabled(isRemovingPaymentMethod)
            }
        }
        .padding(16)
        .background(Theme.chrome)
        .cornerRadius(8)
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(Theme.stroke, lineWidth: 1)
        )
    }

    /// A renewal link is out (the hosted checkout can't charge a saved wallet): let them pay it here.
    private func renewalLinkCard(_ link: RenewalLinkDTO) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(String(
                format: L10n.tr("sub.renewalLinkReady", locale: locale),
                formattedIQD(link.amount_iqd),
                formattedDate(link.pay_by)
            ))
                .font(.qasa(13))
                .foregroundColor(Theme.textPrimary)

            PrimaryButton(
                title: isPayingRenewal
                    ? L10n.tr("premium.processing", locale: locale)
                    : L10n.tr("sub.payRenewal", locale: locale),
                icon: "lock.fill",
                cornerRadius: 8
            ) {
                Task { await payRenewal(link) }
            }
            .opacity(isPayingRenewal ? 0.6 : 1)
            .disabled(isPayingRenewal)
        }
        .padding(16)
        .background(Theme.chrome)
        .cornerRadius(8)
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(Theme.accent, lineWidth: 1)
        )
    }

    private var cancelButton: some View {
        Button {
            showCancelConfirm = true
        } label: {
            Text(L10n.tr("sub.cancelSubscription", locale: locale))
                .font(.qasa(15, weight: .semibold))
                .foregroundColor(Theme.danger)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .background(Theme.danger.opacity(0.08))
                .cornerRadius(8)
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(Theme.danger.opacity(0.35), lineWidth: 1)
                )
        }
        .buttonStyle(.plain)
        .disabled(isCancelling)
    }

    private var cancelReasonSheet: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 16) {
                Text(L10n.tr("sub.cancelReasonPrompt", locale: locale))
                    .font(.qasa(15))
                    .foregroundColor(Theme.textSecondary)

                TextEditor(text: $cancelReason)
                    .scrollContentBackground(.hidden)
                    .padding(12)
                    .frame(minHeight: 140)
                    .background(Theme.cardBackgroundLight)
                    .cornerRadius(8)
                    .overlay(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(Theme.stroke, lineWidth: 1)
                    )
                    .foregroundColor(Theme.textPrimary)
                    .font(.qasa(15))

                Text(L10n.tr("sub.cancelReasonOptional", locale: locale))
                    .font(AppTypography.usesRudaw ? .qasa(11) : .qasaLabel(11))
                    .foregroundColor(Theme.textSecondary)

                Spacer()

                PrimaryButton(
                    title: isCancelling
                        ? L10n.tr("sub.cancellingProgress", locale: locale)
                        : L10n.tr("sub.confirmCancel", locale: locale),
                    icon: "xmark.circle",
                    cornerRadius: 8
                ) {
                    Task { await confirmCancel() }
                }
                .disabled(isCancelling)
                .opacity(isCancelling ? 0.6 : 1)
            }
            .padding(Theme.screenPadding)
            .background(Theme.chrome.ignoresSafeArea())
            .navigationTitle(L10n.tr("sub.cancelReasonTitle", locale: locale))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(L10n.tr("sub.skip", locale: locale)) {
                        Task { await confirmCancel() }
                    }
                    .disabled(isCancelling)
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    private func load() async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }
        do {
            let sub = try await SubscriptionService.shared.fetchCurrent()
            subscription = sub
            autoRenew = sub?.auto_renew ?? true
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func updateAutoRenew(_ enabled: Bool) async {
        isSaving = true
        errorMessage = nil
        statusMessage = nil
        defer { isSaving = false }
        do {
            let sub = try await SubscriptionService.shared.setAutoRenew(enabled)
            subscription = sub
            autoRenew = sub.auto_renew
            statusMessage = enabled
                ? L10n.tr("sub.autoRenewEnabled", locale: locale)
                : L10n.tr("sub.autoRenewDisabled", locale: locale)
        } catch {
            autoRenew = subscription?.auto_renew ?? true
            errorMessage = error.localizedDescription
        }
    }

    private func removePaymentMethod() async {
        isRemovingPaymentMethod = true
        errorMessage = nil
        statusMessage = nil
        defer { isRemovingPaymentMethod = false }
        do {
            try await SubscriptionService.shared.removePaymentMethod()
            let sub = try await SubscriptionService.shared.fetchCurrent()
            subscription = sub
            autoRenew = sub?.auto_renew ?? true
            statusMessage = L10n.tr("sub.paymentMethodRemoved", locale: locale)
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    /// Same confirmation as checkout: open the hosted page, poll the payment, then reload.
    private func payRenewal(_ link: RenewalLinkDTO) async {
        isPayingRenewal = true
        errorMessage = nil
        statusMessage = nil
        defer { isPayingRenewal = false }

        guard await PaymentService.shared.openDeepLink(link.url) else {
            errorMessage = L10n.tr("premium.paymentFailed", locale: locale)
            return
        }
        let success = await PaymentService.shared.pollUntilComplete(paymentId: link.payment_id)
        if success {
            await MainActor.run {
                session.syncFromLicense()
            }
        }
        do {
            let sub = try await SubscriptionService.shared.fetchCurrent()
            subscription = sub
            autoRenew = sub?.auto_renew ?? true
            // Still open means they came back without paying (or it hasn't landed yet),
            // not that the payment failed.
            if !success {
                if sub?.renewal?.link != nil {
                    statusMessage = L10n.tr("sub.renewalNotPaidYet", locale: locale)
                } else {
                    errorMessage = PaymentService.shared.lastError
                        ?? L10n.tr("premium.paymentFailed", locale: locale)
                }
            }
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func confirmCancel() async {
        isCancelling = true
        errorMessage = nil
        statusMessage = nil
        defer { isCancelling = false }
        do {
            let sub = try await SubscriptionService.shared.cancel(reason: cancelReason)
            subscription = sub
            autoRenew = sub.auto_renew
            showCancelReason = false
            statusMessage = String(
                format: L10n.tr("sub.cancelledUntil", locale: locale),
                formattedDate(sub.expires_at)
            )
            await MainActor.run {
                session.syncFromLicense()
            }
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func formattedDate(_ iso: String?) -> String {
        guard let iso, let date = ISO8601DateFormatter.qasa.date(from: iso)
                ?? ISO8601DateFormatter.qasaFractional.date(from: iso) else {
            return iso ?? "—"
        }
        return date.formatted(Date.FormatStyle(date: .abbreviated, time: .omitted).locale(locale))
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

#Preview {
    ManageSubscriptionView()
        .environment(AppSession())
}
