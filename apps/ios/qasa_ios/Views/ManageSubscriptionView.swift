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
                SheetHeader(title: L10n.tr("sub.manageTitle", locale: locale))
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
        .confirmSheet(
            isPresented: $showCancelConfirm,
            title: L10n.tr("sub.cancelConfirmTitle", locale: locale),
            message: String(
                format: L10n.tr("sub.cancelConfirmMessage", locale: locale),
                formattedDate(subscription?.expires_at)
            ),
            icon: "xmark.circle",
            confirmTitle: L10n.tr("sub.continue", locale: locale),
            cancelTitle: L10n.tr("sub.keepPremium", locale: locale),
            destructive: true
        ) {
            cancelReason = ""
            // Let the confirmation sheet finish dismissing before the next one presents.
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) {
                showCancelReason = true
            }
        }
        .confirmSheet(
            isPresented: $showRemovePaymentConfirm,
            title: L10n.tr("sub.removePaymentMethod", locale: locale),
            message: L10n.tr("sub.removePaymentMethodConfirm", locale: locale),
            icon: "creditcard",
            confirmTitle: L10n.tr("sub.removePaymentMethod", locale: locale),
            cancelTitle: L10n.tr("common.cancel", locale: locale),
            destructive: true
        ) {
            Task { await removePaymentMethod() }
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
                    .font(.qasa(11, weight: .semibold))
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
        case "wayl": return L10n.tr("premium.wayl", locale: locale)
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
                .buttonStyle(PressableStyle())
                .disabled(isRemovingPaymentMethod)
            }
        }
        .padding(16)
        .background(Theme.cardBackground)
        .clipShape(RoundedRectangle(cornerRadius: Theme.cornerRadius, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: Theme.cornerRadius, style: .continuous)
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
                icon: "lock.fill"
            ) {
                Task { await payRenewal(link) }
            }
            .disabled(isPayingRenewal)
        }
        .padding(16)
        .background(Theme.cardBackground)
        .clipShape(RoundedRectangle(cornerRadius: Theme.cornerRadius, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: Theme.cornerRadius, style: .continuous)
                .stroke(Theme.accent, lineWidth: 1)
        )
    }

    private var cancelButton: some View {
        Button {
            showCancelConfirm = true
        } label: {
            Text(L10n.tr("sub.cancelSubscription", locale: locale))
                .foregroundColor(Theme.danger)
        }
        .buttonStyle(.qasaSecondary)
        .disabled(isCancelling)
    }

    private var cancelReasonSheet: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 16) {
                Text(L10n.tr("sub.cancelReasonPrompt", locale: locale))
                    .font(.qasa(15))
                    .foregroundColor(Theme.textSecondary)

                TextEditor(text: $cancelReason)
                    .qasaNotesField()

                Text(L10n.tr("sub.cancelReasonOptional", locale: locale))
                    .font(.qasa(11))
                    .foregroundColor(Theme.textSecondary)

                Spacer()
            }
            .padding(Theme.screenPadding)
            .background(Theme.chrome.ignoresSafeArea())
            .bottomActionBar {
                Button {
                    Task { await confirmCancel() }
                } label: {
                    QasaButtonLabel(
                        title: isCancelling
                            ? L10n.tr("sub.cancellingProgress", locale: locale)
                            : L10n.tr("sub.confirmCancel", locale: locale),
                        icon: "xmark.circle"
                    )
                }
                .buttonStyle(.qasaDestructive)
                .disabled(isCancelling)
            }
            // The bottom button already confirms without a reason, so no Skip in the header.
            .sheetHeader(L10n.tr("sub.cancelReasonTitle", locale: locale))
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
            errorMessage = L10n.message(for: error)
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
            errorMessage = L10n.message(for: error)
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
            errorMessage = L10n.message(for: error)
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
            errorMessage = L10n.message(for: error)
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
            errorMessage = L10n.message(for: error)
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
