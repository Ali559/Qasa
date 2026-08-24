import SwiftUI

struct LinkedDevicesView: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 16) {
                Button { dismiss() } label: {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(Theme.textPrimary)
                        .frame(width: 32, height: 32)
                }
                Text("Linked Devices")
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundColor(Theme.textOnChrome)
                Spacer()
                StatusPill(
                    text: "Free Tier: 1/1 Used",
                    dotColor: Theme.goldMuted,
                    textColor: Theme.textSecondary,
                    background: Color(hex: "2A2A2B")
                )
            }
            .padding(.horizontal, 16)
            .frame(height: 64)
            .background(Theme.chrome)
            .overlay(alignment: .bottom) {
                Rectangle().fill(Theme.strokeChrome).frame(height: 1)
            }

            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    upgradeHintBanner

                    VStack(alignment: .leading, spacing: 16) {
                        Text("Active Devices")
                            .font(.system(size: 20, weight: .semibold))
                            .foregroundColor(Theme.textPrimary)

                        currentDeviceCard
                        limitReachedCard
                    }

                    infoNote
                }
                .padding(.horizontal, Theme.screenPadding)
                .padding(.vertical, 24)
            }
        }
        .background(Theme.chrome.ignoresSafeArea())
    }

    private var upgradeHintBanner: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top, spacing: 16) {
                ZStack {
                    Circle()
                        .fill(Theme.gold.opacity(0.2))
                        .frame(width: 44, height: 44)
                    Image(systemName: "star.fill")
                        .foregroundColor(Theme.gold)
                }
                VStack(alignment: .leading, spacing: 4) {
                    Text("Need more devices?")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(Theme.gold)
                    Text("Unlock up to 5 simultaneous devices across Web, iOS, and Android. Upgrade in Settings.")
                        .font(.system(size: 14))
                        .foregroundColor(Theme.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
        .padding(21)
        .background(
            LinearGradient(
                colors: [Theme.gold.opacity(0.1), Theme.gold.opacity(0.05)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        )
        .cornerRadius(Theme.cornerRadius)
        .overlay(
            RoundedRectangle(cornerRadius: Theme.cornerRadius)
                .stroke(Theme.gold.opacity(0.2), lineWidth: 1)
        )
    }

    private var currentDeviceCard: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(spacing: 16) {
                Image(systemName: "iphone")
                    .font(.system(size: 28))
                    .foregroundColor(Theme.mint)
                    .frame(width: 40)

                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 8) {
                        Text("iPhone 15")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(Theme.textPrimary)
                        PillBadge(text: "THIS DEVICE", color: Theme.mintDark, solid: true)
                    }
                    HStack(spacing: 4) {
                        Image(systemName: "mappin")
                            .font(.system(size: 11))
                        Text("Baghdad, IQ • Active Now")
                            .font(.system(size: 14))
                    }
                    .foregroundColor(Theme.textSecondary)
                }
            }
            .padding(.leading, 8)

            Button {} label: {
                HStack(spacing: 8) {
                    Image(systemName: "link.badge.minus")
                    Text("Unlink")
                }
                .font(.system(size: 14))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 9)
                .foregroundColor(Theme.textSecondary)
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(Theme.strokeChrome, lineWidth: 1)
                )
                .opacity(0.5)
            }
            .disabled(true)
        }
        .padding(21)
        .background(Theme.cardBackground)
        .cornerRadius(Theme.cornerRadius)
        .overlay(
            RoundedRectangle(cornerRadius: Theme.cornerRadius)
                .stroke(Theme.stroke, lineWidth: 1)
        )
        .overlay(alignment: .leading) {
            UnevenRoundedRectangle(
                topLeadingRadius: Theme.cornerRadius,
                bottomLeadingRadius: Theme.cornerRadius,
                bottomTrailingRadius: 0,
                topTrailingRadius: 0
            )
            .fill(Theme.mint)
            .frame(width: 4)
        }
        .clipShape(RoundedRectangle(cornerRadius: Theme.cornerRadius))
    }

    private var limitReachedCard: some View {
        VStack(spacing: 12) {
            Image(systemName: "desktopcomputer")
                .font(.system(size: 36))
                .foregroundColor(Theme.textSecondary)
            Text("Device Limit Reached")
                .font(.system(size: 16, weight: .bold))
                .foregroundColor(Theme.textPrimary)
            Text("You are currently on the Free Tier which allows 1 device. Upgrade in Settings to link your Mac or Windows PC.")
                .font(.system(size: 14))
                .foregroundColor(Theme.textSecondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(25)
        .background(Theme.cardMuted.opacity(0.5))
        .cornerRadius(Theme.cornerRadius)
        .overlay(
            RoundedRectangle(cornerRadius: Theme.cornerRadius)
                .strokeBorder(style: StrokeStyle(lineWidth: 1, dash: [6, 4]))
                .foregroundColor(Theme.stroke)
        )
        .opacity(0.9)
    }

    private var infoNote: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: "info.circle.fill")
                .foregroundColor(Theme.info)
                .font(.system(size: 18))
            Text("Unlinking a device will immediately sign it out and permanently purge local vault data from that hardware. Ensure your Master Password is safely stored.")
                .font(.system(size: 14))
                .foregroundColor(Theme.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(17)
        .background(Theme.cardMuted)
        .cornerRadius(8)
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(Theme.strokeChrome, lineWidth: 1)
        )
        .padding(.top, 8)
    }
}

#Preview {
    LinkedDevicesView()
}
