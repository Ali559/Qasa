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

    private var plan: SubscriptionPlan {
        SubscriptionPlan.from(planId: subscription?.plan_id ?? "")
    }

    private var isCancelled: Bool {
        (subscription?.status ?? "").lowercased() == "cancelled"
    }

    var body: some View {
        VStack(spacing: 0) {
            if embedded, let onOpenMenu {
                AppMenuHeader(title: "Subscription", onMenu: onOpenMenu)
            } else {
                HStack {
                    Button { dismiss() } label: {
                        Image(systemName: "xmark")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(Theme.textPrimary)
                            .frame(width: 32, height: 32)
                    }
                    Spacer()
                    Text("Manage Subscription")
                        .font(.system(size: 18, weight: .bold))
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
                            .tint(Theme.mint)
                            .frame(maxWidth: .infinity)
                            .padding(.top, 40)
                    } else if let subscription {
                        detailsCard(subscription)
                        autoRenewCard
                        if !isCancelled {
                            cancelButton
                        }
                    } else {
                        Text("No active subscription found.")
                            .font(.system(size: 14))
                            .foregroundColor(Theme.textSecondary)
                            .frame(maxWidth: .infinity)
                            .padding(.top, 40)
                    }

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
                .padding(.bottom, 40)
            }
        }
        .background(Theme.chrome.ignoresSafeArea())
        .task { await load() }
        .alert("Cancel subscription?", isPresented: $showCancelConfirm) {
            Button("Keep Premium", role: .cancel) {}
            Button("Continue", role: .destructive) {
                cancelReason = ""
                showCancelReason = true
            }
        } message: {
            Text("You'll keep Premium until \(formattedDate(subscription?.expires_at)). Auto-renew will be turned off.")
        }
        .sheet(isPresented: $showCancelReason) {
            cancelReasonSheet
        }
    }

    private func detailsCard(_ sub: SubscriptionDTO) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                Text(plan.displayName)
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(Theme.textPrimary)
                Spacer()
                Text(isCancelled ? "Cancelling" : "Active")
                    .font(.kasoMono(11))
                    .tracking(0.6)
                    .foregroundColor(isCancelled ? Theme.gold : Theme.mint)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .overlay(
                        Capsule().stroke(isCancelled ? Theme.gold : Theme.mint, lineWidth: 1)
                    )
            }

            detailRow(label: "Amount", value: "\(sub.amount_iqd.formatted(.number.grouping(.automatic))) IQD")
            detailRow(label: "Started", value: formattedDate(sub.started_at))
            detailRow(label: "Renews / expires", value: formattedDate(sub.expires_at))
            if isCancelled, let cancelledAt = sub.cancelled_at {
                detailRow(label: "Cancelled", value: formattedDate(cancelledAt))
            }

            Text(isCancelled
                 ? "Access continues until the end of the current period."
                 : "Premium includes 5 devices, breach monitoring, and VPN.")
                .font(.system(size: 13))
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
                .font(.system(size: 13))
                .foregroundColor(Theme.textSecondary)
            Spacer()
            Text(value)
                .font(.system(size: 13, weight: .medium))
                .foregroundColor(Theme.textPrimary)
        }
    }

    private var autoRenewCard: some View {
        HStack(spacing: 12) {
            Image(systemName: "arrow.triangle.2.circlepath")
                .foregroundColor(Theme.mint)
                .font(.system(size: 18))
            VStack(alignment: .leading, spacing: 2) {
                Text("Auto-renew")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundColor(Theme.textPrimary)
                Text(isCancelled
                      ? "Turned off after cancellation."
                      : "Renews automatically at period end.")
                    .font(.system(size: 12))
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
            .tint(Theme.mint)
            .disabled(isCancelled || isSaving || subscription == nil)
        }
        .padding(16)
        .background(Theme.chrome)
        .cornerRadius(8)
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(Theme.stroke, lineWidth: 1)
        )
    }

    private var cancelButton: some View {
        Button {
            showCancelConfirm = true
        } label: {
            Text("Cancel Subscription")
                .font(.system(size: 15, weight: .semibold))
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
                Text("Help us improve — why are you cancelling?")
                    .font(.system(size: 15))
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

                Text("Optional. You can skip and still cancel.")
                    .font(.kasoMono(11))
                    .foregroundColor(Theme.textSecondary)

                Spacer()

                PrimaryButton(
                    title: isCancelling ? "Cancelling…" : "Confirm Cancel",
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
            .navigationTitle("Cancellation reason")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Skip") {
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
            statusMessage = enabled ? "Auto-renew enabled." : "Auto-renew disabled."
        } catch {
            autoRenew = subscription?.auto_renew ?? true
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
            statusMessage = "Subscription cancelled. Premium stays active until \(formattedDate(sub.expires_at))."
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
        return date.formatted(date: .abbreviated, time: .omitted)
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
