import SwiftUI

/// The first-launch tour, direction B "Live demo" (`design/README.md` rule 6d): four pages
/// about free features, each playing a tiny version of the app. Story bars at the top move on
/// by themselves after `Motion.tourDwell`; tapping the leading or trailing half, or swiping,
/// goes back or forward. With reduce motion on, pages fade, the demos show their end state
/// and nothing moves on by itself.
struct OnboardingTourView: View {
    @Environment(AppPreferences.self) private var prefs
    @Environment(\.themePalette) private var theme
    @Environment(\.layoutDirection) private var layoutDirection
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var onSkip: () -> Void
    var onDone: () -> Void

    static let pageCount = 4
    @State private var page = 0
    /// When the current page appeared; its story bar fills from this moment.
    @State private var pageStart = Date()

    private var locale: Locale { prefs.locale }
    private var isLast: Bool { page == Self.pageCount - 1 }

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 12) {
                storyBars
                Button(L10n.tr("common.skip", locale: locale), action: onSkip)
                    .font(.qasa(15, weight: .semibold))
                    .foregroundColor(theme.accent)
                    .frame(minHeight: 44)
            }
            .padding(.horizontal, Theme.screenPadding)
            .padding(.top, 8)

            ZStack {
                TourPage(index: page)
                    .id(page)
                    .transition(.opacity)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .contentShape(Rectangle())
            .gesture(DragGesture(minimumDistance: 0).onEnded(handleGesture))
            .accessibilityElement(children: .contain)
            .accessibilityAction(named: L10n.tr("onboarding.next", locale: locale)) { move(1) }
        }
        .background(theme.background.ignoresSafeArea())
        .bottomActionBar {
            PrimaryButton(title: L10n.tr(isLast ? "common.continue" : "onboarding.next", locale: locale)) { move(1) }
        }
        .task(id: page) { await runStoryBar() }
    }

    /// Drawn from the clock, not an animation, so a page change never inherits the last fill.
    private var storyBars: some View {
        TimelineView(.animation(paused: reduceMotion)) { context in
            let current = reduceMotion ? 1 : min(1, context.date.timeIntervalSince(pageStart) / QasaTokens.Motion.tourDwell)
            HStack(spacing: 4) {
                ForEach(0..<Self.pageCount, id: \.self) { i in
                    GeometryReader { geo in
                        Capsule().fill(theme.strokeChrome)
                            .overlay(alignment: .leading) {
                                Capsule().fill(theme.accent)
                                    .frame(width: geo.size.width * (i < page ? 1 : i > page ? 0 : current))
                            }
                    }
                    .frame(height: 3)
                }
            }
        }
        .accessibilityHidden(true)
    }

    /// Moves on after `tourDwell`. Reduce motion: nothing moves on by itself.
    private func runStoryBar() async {
        pageStart = Date()
        guard !reduceMotion else { return }
        try? await Task.sleep(for: .seconds(QasaTokens.Motion.tourDwell))
        guard !Task.isCancelled else { return }
        move(1)
    }

    private func handleGesture(_ value: DragGesture.Value) {
        let dx = value.translation.width * (layoutDirection == .rightToLeft ? -1 : 1)
        if dx < -40 { move(1); return }
        if dx > 40 { move(-1); return }
        guard abs(value.translation.height) < 20 else { return }
        // A tap: the trailing half goes forward, the leading half back.
        let width = UIScreen.main.bounds.width
        var trailing = value.location.x > width / 2
        if layoutDirection == .rightToLeft { trailing.toggle() }
        move(trailing ? 1 : -1)
    }

    private func move(_ delta: Int) {
        let next = page + delta
        if next >= Self.pageCount { onDone(); return }
        guard next >= 0 else { return }
        withAnimation(QasaTokens.Motion.fade) { page = next }
    }
}

// MARK: - One page

private struct TourPage: View {
    @Environment(AppPreferences.self) private var prefs
    @Environment(\.themePalette) private var theme
    let index: Int

    var body: some View {
        let locale = prefs.locale
        VStack(alignment: .leading, spacing: 0) {
            Spacer(minLength: 12)
            Group {
                switch index {
                case 0: VaultDemo()
                case 1: SignUpDemo()
                case 2: CodeDemo()
                default: HealthDemo()
                }
            }
            .frame(maxWidth: .infinity)
            Spacer(minLength: 20)
            TourCopy(
                title: L10n.tr("onboarding.tour\(index + 1)Title", locale: locale),
                text: L10n.tr("onboarding.tour\(index + 1)Body", locale: locale)
            )
        }
        .padding(.horizontal, Theme.screenPadding)
        .padding(.bottom, 16)
    }
}

private struct TourCopy: View {
    @Environment(\.themePalette) private var theme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    let title: String
    let text: String
    @State private var shown = false

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.qasa(24, weight: .bold))
                .foregroundColor(theme.textPrimary)
                .fixedSize(horizontal: false, vertical: true)
                .rise(shown, reduceMotion)
            Text(text)
                .font(.qasa(15))
                .foregroundColor(theme.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
                .rise(shown, reduceMotion, delay: 0.08)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .onAppear { shown = true }
    }
}

private extension View {
    /// Fades and rises 14pt into place (`motion.standard`-like ease-out); still under reduce motion.
    func rise(_ shown: Bool, _ reduceMotion: Bool, delay: Double = 0) -> some View {
        opacity(shown || reduceMotion ? 1 : 0)
            .offset(y: shown || reduceMotion ? 0 : 14)
            .animation(reduceMotion ? nil : .easeOut(duration: 0.4).delay(delay), value: shown)
    }
}

/// The card every demo sits in: the app's card surface and a soft shadow.
private struct DemoCard<Content: View>: View {
    @Environment(\.themePalette) private var theme
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 0) { content }
            .background(theme.cardBackground)
            .clipShape(RoundedRectangle(cornerRadius: QasaTokens.Radius.card, style: .continuous))
            .shadow(color: Color.black.opacity(0.08), radius: 14, y: 6)
    }
}

/// Rows of a demo rise in on the `tourEnter` spring, `tourStagger` apart.
private struct Staggered: ViewModifier {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    let order: Int
    let shown: Int

    func body(content: Content) -> some View {
        let visible = reduceMotion || shown > order
        content
            .opacity(visible ? 1 : 0)
            .offset(y: visible ? 0 : 18)
            .scaleEffect(visible ? 1 : 0.96)
    }
}

private func stagger(count: Int, into shown: Binding<Int>) async {
    for i in 0..<count {
        try? await Task.sleep(for: .seconds(i == 0 ? 0.05 : QasaTokens.Motion.tourStagger))
        guard !Task.isCancelled else { return }
        withAnimation(QasaTokens.Motion.tourEnter) { shown.wrappedValue = i + 1 }
    }
}

private struct DemoRow: View {
    @Environment(\.themePalette) private var theme
    let icon: QasaIcon
    let color: NoteColor
    let title: String
    let subtitle: String

    var body: some View {
        HStack(spacing: 12) {
            IconBadge(icon: icon, tint: color.color, background: color.tile)
            VStack(alignment: .leading, spacing: 1) {
                Text(title)
                    .font(.qasa(15, weight: .semibold))
                    .foregroundColor(theme.textPrimary)
                Text(subtitle)
                    .font(.qasa(13))
                    .foregroundColor(theme.textSecondary)
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
    }
}

// MARK: Page 1, the vault

private struct VaultDemo: View {
    @Environment(AppPreferences.self) private var prefs
    @Environment(\.themePalette) private var theme
    @State private var shown = 0

    var body: some View {
        let locale = prefs.locale
        VStack(spacing: 12) {
            DemoCard {
                DemoRow(icon: .login, color: .purple, title: "Gmail", subtitle: "you@example.com")
                    .modifier(Staggered(order: 0, shown: shown))
                RowDivider()
                DemoRow(icon: .card, color: .gold, title: "Visa", subtitle: "•••• 4821")
                    .modifier(Staggered(order: 1, shown: shown))
                RowDivider()
                DemoRow(icon: .note, color: .mint, title: L10n.tr("onboarding.mockWifi", locale: locale),
                        subtitle: L10n.tr("field.note", locale: locale))
                    .modifier(Staggered(order: 2, shown: shown))
            }
            StatusPill(text: L10n.tr("brand.localOffline", locale: locale), dotColor: theme.success, textColor: theme.success)
                .modifier(Staggered(order: 3, shown: shown))
        }
        .task { await stagger(count: 4, into: $shown) }
    }
}

// MARK: Page 2, sign-up with a strong password

private struct SignUpDemo: View {
    @Environment(AppPreferences.self) private var prefs
    @Environment(\.themePalette) private var theme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private static let email = "you@example.com"
    private static let password = "Kx9#mT2v!qL7&wR4"

    @State private var typed = ""
    @State private var passwordFocused = false
    @State private var suggesting = false
    @State private var tapped = false
    @State private var filled = false

    var body: some View {
        let locale = prefs.locale
        DemoCard {
            Text(L10n.tr("onboarding.mockSignup", locale: locale))
                .font(.qasa(15, weight: .semibold))
                .foregroundColor(theme.textPrimary)
                .padding(.horizontal, 14)
                .padding(.top, 14)
                .padding(.bottom, 8)
            field(L10n.tr("field.email", locale: locale), value: typed, focused: !passwordFocused && !typed.isEmpty)
            field(L10n.tr("field.password", locale: locale),
                  value: filled ? String(repeating: "•", count: 16) : "", focused: passwordFocused)
            HStack(spacing: 10) {
                IconBadge(icon: .generate, tint: theme.accent, background: theme.cardBackground)
                VStack(alignment: .leading, spacing: 1) {
                    Text(L10n.tr("fill.strongPassword", locale: locale))
                        .font(.qasa(13, weight: .semibold))
                        .foregroundColor(theme.textPrimary)
                    Text(Self.password)
                        .font(.kasoMono(12, weight: .regular))
                        .foregroundColor(theme.textSecondary)
                        .environment(\.layoutDirection, .leftToRight)
                }
                Spacer(minLength: 0)
            }
            .padding(10)
            .background(tapped ? theme.notePurpleTile : theme.cardMuted)
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            .scaleEffect(tapped ? 0.97 : 1)
            .opacity(suggesting ? 1 : 0)
            .offset(y: suggesting ? 0 : 8)
            .padding(.horizontal, 14)
            .padding(.bottom, 12)
        }
        .task { await play() }
    }

    private func field(_ label: String, value: String, focused: Bool) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(label)
                .font(.qasa(11, weight: .semibold))
                .foregroundColor(theme.textTertiary)
            Text(value.isEmpty ? " " : value)
                .font(.qasa(14))
                .foregroundColor(theme.textPrimary)
                .environment(\.layoutDirection, .leftToRight)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .overlay(
            RoundedRectangle(cornerRadius: QasaTokens.Radius.control, style: .continuous)
                .stroke(focused ? theme.accent : theme.stroke, lineWidth: 1)
        )
        .padding(.horizontal, 14)
        .padding(.bottom, 8)
    }

    private func play() async {
        if reduceMotion {
            typed = Self.email
            passwordFocused = true
            filled = true
            return
        }
        try? await Task.sleep(for: .seconds(0.5))
        for ch in Self.email {
            guard !Task.isCancelled else { return }
            typed.append(ch)
            try? await Task.sleep(for: .seconds(QasaTokens.Motion.tourTypeStep))
        }
        try? await Task.sleep(for: .seconds(0.25))
        withAnimation(QasaTokens.Motion.tourEnter) {
            passwordFocused = true
            suggesting = true
        }
        try? await Task.sleep(for: .seconds(0.9))
        withAnimation(QasaTokens.Motion.press) { tapped = true }
        try? await Task.sleep(for: .seconds(0.2))
        withAnimation(QasaTokens.Motion.standard) {
            tapped = false
            suggesting = false
            filled = true
        }
    }
}

// MARK: Page 3, a two-step code

private struct CodeDemo: View {
    @Environment(\.themePalette) private var theme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private static let codes = ["482 913", "731 046", "205 588", "964 120"]
    @State private var codeIndex = 0
    /// Time left on the code, 0…1.
    @State private var left: Double = 0.65
    @State private var shown = 0

    var body: some View {
        DemoCard {
            DemoRow(icon: .login, color: .blue, title: "GitHub", subtitle: "you@example.com")
                .padding(.top, 4)
            HStack {
                Text(Self.codes[codeIndex])
                    .font(.kasoMono(28, weight: .semibold))
                    .foregroundColor(theme.textPrimary)
                    .contentTransition(.numericText())
                    .environment(\.layoutDirection, .leftToRight)
                Spacer()
                ZStack {
                    Circle().stroke(theme.stroke, lineWidth: 4)
                    Circle()
                        .trim(from: 0, to: left)
                        .stroke(theme.accent, style: StrokeStyle(lineWidth: 4, lineCap: .round))
                        .rotationEffect(.degrees(-90))
                }
                .frame(width: 30, height: 30)
                .accessibilityHidden(true)
            }
            .padding(.horizontal, 14)
            .padding(.bottom, 16)
        }
        .modifier(Staggered(order: 0, shown: shown))
        .task { await tick() }
    }

    private func tick() async {
        withAnimation(QasaTokens.Motion.tourEnter) { shown = 1 }
        guard !reduceMotion else { return }
        try? await Task.sleep(for: .seconds(0.4))
        while !Task.isCancelled {
            if left - 0.05 <= 0 {
                withAnimation(QasaTokens.Motion.standard) {
                    codeIndex = (codeIndex + 1) % Self.codes.count
                }
                left = 1
            } else {
                withAnimation(.linear(duration: 0.3)) { left -= 0.05 }
            }
            try? await Task.sleep(for: .seconds(0.3))
        }
    }
}

// MARK: Page 4, password health

private struct HealthDemo: View {
    @Environment(AppPreferences.self) private var prefs
    @Environment(\.themePalette) private var theme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private static let target = 82
    @State private var score = 0
    @State private var shown = 0

    var body: some View {
        let locale = prefs.locale
        DemoCard {
            VStack(spacing: 6) {
                Text(L10n.tr("nav.passwordHealth", locale: locale))
                    .font(.qasa(14, weight: .semibold))
                    .foregroundColor(theme.textPrimary)
                ZStack(alignment: .bottom) {
                    Circle().trim(from: 0, to: 0.5)
                        .stroke(theme.stroke, style: StrokeStyle(lineWidth: 12, lineCap: .round))
                        .rotationEffect(.degrees(180))
                    Circle().trim(from: 0, to: 0.5 * Double(score) / 100)
                        .stroke(theme.success, style: StrokeStyle(lineWidth: 12, lineCap: .round))
                        .rotationEffect(.degrees(180))
                    Text("\(score)")
                        .font(.qasa(30, weight: .bold))
                        .foregroundColor(theme.textPrimary)
                        .contentTransition(.numericText(value: Double(score)))
                        .environment(\.layoutDirection, .leftToRight)
                        .padding(.bottom, 74)
                }
                .frame(width: 150, height: 150)
                .frame(height: 86, alignment: .top)
                .environment(\.layoutDirection, .leftToRight)
                .accessibilityHidden(true)
                HStack(spacing: 8) {
                    chip(String(format: L10n.tr("onboarding.mockWeak", locale: locale), 2), color: .gold)
                    chip(String(format: L10n.tr("onboarding.mockReused", locale: locale), 1), color: .purple)
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
        }
        .modifier(Staggered(order: 0, shown: shown))
        .task {
            withAnimation(QasaTokens.Motion.tourEnter) { shown = 1 }
            if reduceMotion { score = Self.target; return }
            try? await Task.sleep(for: .seconds(0.3))
            withAnimation(QasaTokens.Motion.gauge) { score = Self.target }
        }
    }

    private func chip(_ text: String, color: NoteColor) -> some View {
        Text(text)
            .font(.qasa(12, weight: .semibold))
            .foregroundColor(color.color)
            .padding(.horizontal, 10)
            .padding(.vertical, 3)
            .background(color.tile)
            .clipShape(Capsule())
    }
}
