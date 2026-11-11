import SwiftUI

/// White disc on the page wash, with the status glyph in a raised inner circle.
struct HomeStatusHero: View {
    let status: HomeStatus
    var onAddFirst: () -> Void

    @Environment(\.themePalette) private var theme
    @Environment(AppPreferences.self) private var prefs

    var body: some View {
        VStack(spacing: 6) {
            ZStack {
                Circle().fill(theme.cardBackground.opacity(0.55)).frame(width: 168, height: 168)
                Circle().fill(theme.cardBackground).frame(width: 104, height: 104)
                if status == .loading {
                    ProgressView().tint(theme.accent)
                } else {
                    QasaIconImage(icon, size: 40)
                        .foregroundColor(status == .allGood ? theme.success : theme.accent)
                }
            }
            .padding(.bottom, 6)
            Text(title)
                .font(.qasa(22, weight: .bold))
                .foregroundColor(theme.textPrimary)
                .multilineTextAlignment(.center)
            if let body = bodyText {
                Text(body)
                    .font(.qasa(14))
                    .foregroundColor(theme.textSecondary)
                    .multilineTextAlignment(.center)
            }
            if status == .empty {
                PrimaryButton(title: L10n.tr("home.addfirst", locale: prefs.locale), action: onAddFirst)
                    .padding(.top, 10)
            }
        }
        .frame(maxWidth: .infinity)
    }

    private var icon: QasaIcon {
        switch status {
        case .allGood: return .success
        case .needsAttention: return .tip
        case .empty, .loading: return .login
        }
    }

    private var title: String {
        let l = prefs.locale
        switch status {
        case .allGood: return L10n.tr("home.allgoodTitle", locale: l)
        case .needsAttention: return L10n.tr("home.attentionTitle", locale: l)
        case .empty: return L10n.tr("home.emptyTitle", locale: l)
        case .loading: return L10n.tr("home.checking", locale: l)
        }
    }

    private var bodyText: String? {
        let l = prefs.locale
        switch status {
        case .allGood: return L10n.tr("home.allgoodBody", locale: l)
        case .needsAttention: return L10n.tr("home.attentionHint", locale: l)
        case .empty: return L10n.tr("home.emptyBody", locale: l)
        case .loading: return nil
        }
    }
}

/// Three-colour bar: fine, reused and easy-to-guess passwords as shares of the total.
struct HomeHealthCard: View {
    let report: PasswordHealthReport?
    var onTap: () -> Void

    @Environment(\.themePalette) private var theme
    @Environment(AppPreferences.self) private var prefs

    var body: some View {
        let total = report?.totalPasswords ?? 0
        let reused = report?.reusedCount ?? 0
        let weak = report?.weakCount ?? 0
        let breached = report?.breachedCount ?? 0
        // A login can be both reused and weak; count it once.
        let toFix = report.map { Set(($0.reusedItems + $0.weakItems + $0.breachedItems).map(\.id)).count } ?? 0
        let fine = max(total - toFix, 0)
        return HomeCard(onTap: onTap) {
            HStack {
                Text(L10n.tr("home.healthTitle", locale: prefs.locale))
                    .font(.qasa(16, weight: .semibold))
                    .foregroundColor(theme.textPrimary)
                Spacer()
                if toFix > 0 {
                    HomePill(text: String(format: L10n.tr("home.tofix", locale: prefs.locale), toFix),
                             background: theme.dangerBackground, color: theme.dangerText)
                } else {
                    HomePill(text: L10n.tr("home.allfine", locale: prefs.locale),
                             background: theme.success.opacity(0.14), color: theme.success)
                }
            }
            GeometryReader { geo in
                let sum = CGFloat(max(fine + reused + weak, 1))
                HStack(spacing: 0) {
                    Rectangle().fill(theme.success).frame(width: geo.size.width * CGFloat(fine) / sum)
                    Rectangle().fill(theme.gold).frame(width: geo.size.width * CGFloat(reused) / sum)
                    Rectangle().fill(theme.danger).frame(width: geo.size.width * CGFloat(weak) / sum)
                }
            }
            .frame(height: 8)
            .background(theme.stroke)
            .clipShape(Capsule())
            .padding(.vertical, 4)
            Text(toFix == 0
                 ? String(format: L10n.tr("home.healthAllgood", locale: prefs.locale), total)
                 : breached > 0
                 ? String(format: L10n.tr("home.attentionBreachedBody", locale: prefs.locale), reused, weak, breached)
                 : String(format: L10n.tr("home.attentionBody", locale: prefs.locale), reused, weak))
                .font(.qasa(13))
                .foregroundColor(theme.textSecondary)
        }
    }
}

/// VPN state as it really is. Premium users can connect from here; the "tunnel coming
/// soon" note stays until the WireGuard tunnel ships, so Home never overstates protection.
struct HomeVPNCard: View {
    let isPremium: Bool
    var onTap: () -> Void

    @Environment(\.themePalette) private var theme
    @Environment(AppPreferences.self) private var prefs
    private var vpn = VPNService.shared

    init(isPremium: Bool, onTap: @escaping () -> Void) {
        self.isPremium = isPremium
        self.onTap = onTap
    }

    var body: some View {
        let l = prefs.locale
        HomeCard(onTap: onTap) {
            HStack(spacing: 12) {
                HomeIconTile(icon: .vpn)
                VStack(alignment: .leading, spacing: 2) {
                    Text(L10n.tr("nav.vpn", locale: l))
                        .font(.qasa(16, weight: .semibold))
                        .foregroundColor(theme.textPrimary)
                    Text(!isPremium
                         ? L10n.tr("home.vpnPremium", locale: l)
                         : vpn.connected
                            ? String(format: L10n.tr("home.vpnOn", locale: l), vpn.city ?? "—")
                            : L10n.tr("home.vpnOff", locale: l))
                        .font(.qasa(13))
                        .foregroundColor(theme.textSecondary)
                }
                Spacer()
                if isPremium {
                    Button {
                        Task { vpn.connected ? await vpn.disconnect() : await vpn.connect() }
                    } label: {
                        HomePill(text: L10n.tr(vpn.connected ? "home.vpnDisconnect" : "home.vpnConnect", locale: l),
                                 background: theme.cardMuted, color: theme.onAccentChip)
                    }
                    .disabled(vpn.isBusy)
                }
            }
            if isPremium {
                Text(L10n.tr("vpn.sessionOnly", locale: l))
                    .font(.qasa(12))
                    .foregroundColor(theme.textTertiary)
            }
        }
    }
}

/// Breached-password check (Premium, on device). Replaced the email leak alerts card, which is
/// parked (todo email-leak-alerts in docs/server-followups-plan.md). On Free it shows the
/// locked copy and taps through to the upgrade page, like `HomeVPNCard`.
struct HomeBreachedCard: View {
    let isPremium: Bool
    var onTap: () -> Void

    @Environment(\.themePalette) private var theme
    @Environment(AppPreferences.self) private var prefs

    var body: some View {
        let l = prefs.locale
        HomeCard(onTap: onTap) {
            HStack(spacing: 12) {
                HomeIconTile(icon: isPremium ? .breach : .shieldLock)
                VStack(alignment: .leading, spacing: 2) {
                    Text(L10n.tr(isPremium ? "nav.breachedPasswords" : "breached.lockedTitle", locale: l))
                        .font(.qasa(16, weight: .semibold))
                        .foregroundColor(theme.textPrimary)
                    Text(L10n.tr(isPremium ? "home.breachedBody" : "breached.lockedBody", locale: l))
                        .font(.qasa(13))
                        .foregroundColor(theme.textSecondary)
                }
                Spacer(minLength: 0)
            }
            if !isPremium {
                HomePill(text: L10n.tr("breached.unlock", locale: l),
                         background: theme.cardMuted, color: theme.onAccentChip)
            }
        }
    }
}

/// Nudge to set up backups while the vault lives only on this phone (core
/// `backupShouldRemind`). Not a whole-card button: it carries its own action and dismiss.
struct HomeBackupReminderCard: View {
    var onSetUp: () -> Void
    var onDismiss: () -> Void

    @Environment(\.themePalette) private var theme
    @Environment(AppPreferences.self) private var prefs

    var body: some View {
        let l = prefs.locale
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top, spacing: 12) {
                HomeIconTile(icon: .backupProblem)
                VStack(alignment: .leading, spacing: 2) {
                    Text(L10n.tr("backup.reminder.title", locale: l))
                        .font(.qasa(16, weight: .semibold))
                        .foregroundColor(theme.textPrimary)
                    Text(L10n.tr("backup.reminder.body", locale: l))
                        .font(.qasa(13))
                        .foregroundColor(theme.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 0)
            }
            HStack(spacing: 12) {
                Button(action: onSetUp) {
                    HomePill(text: L10n.tr("backup.reminder.action", locale: l),
                             background: theme.cardMuted, color: theme.onAccentChip)
                }
                .buttonStyle(.plain)
                Button(action: onDismiss) {
                    Text(L10n.tr("backup.reminder.dismiss", locale: l))
                        .font(.qasa(13, weight: .medium))
                        .foregroundColor(theme.textSecondary)
                }
                .buttonStyle(.plain)
                Spacer(minLength: 0)
            }
        }
        .padding(17)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(theme.cardBackground)
        .clipShape(RoundedRectangle(cornerRadius: Theme.cornerRadius))
    }
}

private struct HomeCard<Content: View>: View {
    var onTap: () -> Void
    @ViewBuilder var content: Content
    @Environment(\.themePalette) private var theme

    var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: 8) { content }
                .padding(17)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(theme.cardBackground)
                .clipShape(RoundedRectangle(cornerRadius: Theme.cornerRadius))
        }
        .buttonStyle(.plain)
    }
}

private struct HomeIconTile: View {
    let icon: QasaIcon
    @Environment(\.themePalette) private var theme

    var body: some View {
        QasaIconImage(icon, size: 18)
            .foregroundColor(theme.accent)
            .frame(width: 40, height: 40)
            .background(theme.cardMuted)
            .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}

private struct HomePill: View {
    let text: String
    let background: Color
    let color: Color

    var body: some View {
        Text(text)
            .font(.qasa(13, weight: .medium))
            .foregroundColor(color)
            .padding(.horizontal, 12)
            .padding(.vertical, 4)
            .background(background)
            .clipShape(Capsule())
    }
}
