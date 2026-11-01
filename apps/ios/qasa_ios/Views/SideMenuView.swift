import SwiftUI

struct SideMenuView: View {
    @Environment(AppSession.self) private var session
    @Environment(AppPreferences.self) private var prefs
    @Environment(\.themePalette) private var theme
    @Binding var isPresented: Bool
    var currentPage: AppPage
    var onSelect: (AppPage) -> Void
    var onUpgrade: () -> Void

    var body: some View {
        ZStack(alignment: .leading) {
            Color.black.opacity(0.45)
                .ignoresSafeArea()
                .onTapGesture { close() }

            VStack(alignment: .leading, spacing: 0) {
                header
                Divider().background(theme.strokeChrome)

                ScrollView {
                    VStack(alignment: .leading, spacing: 0) {
                        primarySection
                        sectionDivider
                        toolsSection
                        sectionDivider
                        accountSection
                    }
                    .padding(.vertical, 8)
                }

                Spacer(minLength: 0)
            }
            .frame(maxWidth: 320)
            .frame(maxHeight: .infinity)
            .background(theme.chrome)
            .overlay(alignment: .trailing) {
                Rectangle().fill(theme.strokeChrome).frame(width: 1)
            }
        }
        // `.leading` mirrors with layoutDirection (RTL → visual right).
        .transition(.move(edge: .leading).combined(with: .opacity))
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top) {
                HStack(spacing: 10) {
                    Image(systemName: "shield.fill")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundColor(theme.mint)
                    Text(L10n.tr("brand.name", locale: prefs.locale))
                        .font(.system(size: 22, weight: .heavy))
                        .foregroundColor(theme.mint)
                }

                Spacer()

                if !session.isPremium {
                    Button(action: onUpgrade) {
                        Text(L10n.tr("nav.upgrade", locale: prefs.locale))
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(theme.mintOnPrimary)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 7)
                            .background(theme.mint)
                            .clipShape(Capsule())
                    }
                }
            }

            VStack(alignment: .leading, spacing: 4) {
                Text(session.displayName)
                    .font(.qasa(17, weight: .semibold))
                    .foregroundColor(theme.textPrimary)
                Text(session.planSubtitle)
                    .font(.qasa(13))
                    .foregroundColor(theme.textSecondary)
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, 16)
        .padding(.bottom, 18)
    }

    private var primarySection: some View {
        VStack(spacing: 2) {
            menuRow(.vault)
            menuRow(.notifications)
            menuRow(.generator)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
    }

    private var toolsSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            sectionLabel(L10n.tr("nav.tools", locale: prefs.locale))
            VStack(spacing: 2) {
                menuRow(.passwordHealth)
                menuRow(.breachMonitoring)
                menuRow(.vpn)
                menuRow(.collections)
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
    }

    private var accountSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            sectionLabel(L10n.tr("nav.myAccount", locale: prefs.locale))
            VStack(spacing: 2) {
                menuRow(.settings)
                menuRow(.terms)
                menuRow(.privacy)
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
    }

    private func sectionLabel(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 12, weight: .semibold))
            .foregroundColor(theme.textTertiary)
            .textCase(.uppercase)
            .tracking(0.8)
            .padding(.horizontal, 12)
            .padding(.top, 4)
    }

    private var sectionDivider: some View {
        Rectangle()
            .fill(theme.strokeChrome)
            .frame(height: 1)
            .padding(.vertical, 4)
    }

    private func menuRow(_ page: AppPage) -> some View {
        let selected = currentPage == page
        return Button {
            onSelect(page)
        } label: {
            HStack(spacing: 14) {
                Image(systemName: page.systemImage)
                    .font(.system(size: 16, weight: .medium))
                    .frame(width: 22)
                    .foregroundColor(selected ? theme.mint : theme.textSecondary)
                Text(L10n.tr(page.localizationKey, locale: prefs.locale))
                    .font(.qasa(16, weight: selected ? .semibold : .regular))
                    .foregroundColor(selected ? theme.textPrimary : theme.textSecondary)
                Spacer()
                if page.requiresPremium && !session.isPremium {
                    Image(systemName: "lock.fill")
                        .font(.system(size: 11))
                        .foregroundColor(theme.gold)
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 12)
            .background(selected ? theme.cardBackground : Color.clear)
            .cornerRadius(10)
        }
        .buttonStyle(.plain)
    }

    private func close() {
        withAnimation(.easeOut(duration: 0.2)) {
            isPresented = false
        }
    }
}
