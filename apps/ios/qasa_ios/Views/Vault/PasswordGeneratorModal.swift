import SwiftUI
import Combine

/// Reusable bottom-sheet password generator (not the full Generator tab page).
/// Dismiss via sheet swipe; no Cancel/title chrome.
struct PasswordGeneratorModal: View {
    var onUsePassword: (String) -> Void

    @StateObject private var model = PasswordGeneratorModel()

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                PasswordGeneratorContent(model: model)
            }
            .padding(.horizontal, Theme.screenPadding)
            .padding(.top, 8)
            .padding(.bottom, 12)
        }
        .background(Theme.background.ignoresSafeArea())
        .sheetHeader(L10n.tr("gen.title"))
        .bottomActionBar {
            Button(L10n.tr("gen.usePassword")) {
                onUsePassword(model.password)
            }
            .buttonStyle(.qasaPrimary)
            .disabled(model.password.isEmpty)
        }
        .appLocalization()
        .onAppear { model.regenerate() }
    }
}

// MARK: - Shared model

final class PasswordGeneratorModel: ObservableObject {
    @Published var password: String = ""
    @Published var length: Double = 16
    @Published var useUppercase = true
    @Published var useLowercase = true
    @Published var useNumbers = true
    @Published var useSymbols = true
    @Published var pronounceable = false
    @Published var copied = false

    private let uppercaseChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private let lowercaseChars = "abcdefghijklmnopqrstuvwxyz"
    private let numberChars = "0123456789"
    private let symbolChars = "!@#$%^&*()_+-="

    var strengthLabel: (text: String, filled: Int, color: Color) {
        let charsetCount = [useUppercase, useLowercase, useNumbers, useSymbols].filter { $0 }.count
        let score = Int(length / 4) + charsetCount
        switch score {
        case 0...3: return (L10n.tr("gen.weak"), 1, Theme.danger)
        case 4...5: return (L10n.tr("gen.fair"), 2, Theme.gold)
        case 6...7: return (L10n.tr("gen.strong"), 4, Theme.accent)
        default: return (L10n.tr("gen.veryStrong"), 5, Theme.accent)
        }
    }

    func regenerate() {
        var classes: [String] = []
        if useUppercase { classes.append(uppercaseChars) }
        if useLowercase { classes.append(lowercaseChars) }
        if useNumbers { classes.append(numberChars) }
        if useSymbols { classes.append(symbolChars) }
        let pool = classes.joined()
        guard !pool.isEmpty else {
            password = ""
            return
        }
        // One character from every enabled class first, so "Symbols on" always yields a
        // symbol (Android `PasswordGenerator` parity), then fill and shuffle so the
        // guaranteed characters don't sit in a predictable prefix.
        var chars = classes.compactMap { $0.randomElement() }
        while chars.count < Int(length) {
            if let c = pool.randomElement() { chars.append(c) }
        }
        password = String(chars.prefix(max(Int(length), classes.count)).shuffled())
    }

    func copyPassword() {
        UIPasteboard.general.string = password
        withAnimation { copied = true }
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) { [weak self] in
            withAnimation { self?.copied = false }
        }
    }
}

// MARK: - Shared content (modal + full page)

struct PasswordGeneratorContent: View {
    @ObservedObject var model: PasswordGeneratorModel
    var compact: Bool = true

    var body: some View {
        VStack(alignment: .leading, spacing: compact ? 16 : 24) {
            generatedPasswordCard
            strengthCard
            settingsCard
        }
    }

    private var generatedPasswordCard: some View {
        ZStack {
            LinearGradient(
                colors: [Theme.accent.opacity(0.1), .clear],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )

            VStack(spacing: 0) {
                HStack {
                    SectionLabel(text: L10n.tr("gen.generatedPassword"))
                    Spacer()
                    FieldIconButton(
                        icon: .refresh,
                        tint: Theme.accent,
                        accessibilityLabel: L10n.tr("gen.generatedPassword")
                    ) {
                        model.regenerate()
                    }
                }
                .padding(.bottom, compact ? 12 : 16)

                Text(model.password)
                    .font(.kasoMono(compact ? 22 : 28, weight: .bold))
                    .tracking(compact ? 1.5 : 2.8)
                    .foregroundColor(Theme.textPrimary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.4)
                    .frame(maxWidth: .infinity)
                    .padding(.bottom, compact ? 16 : 24)

                Button {
                    model.copyPassword()
                } label: {
                    QasaButtonLabel(
                        title: model.copied ? L10n.tr("gen.copied") : L10n.tr("gen.copyPassword"),
                        icon: model.copied ? .check : .copy
                    )
                }
                .buttonStyle(.qasaSecondary)
                .disabled(model.password.isEmpty)
            }
            .padding(compact ? 18 : 25)
        }
        .background(Theme.cardBackground)
        .cornerRadius(Theme.cornerRadiusLarge)
        .overlay(
            RoundedRectangle(cornerRadius: Theme.cornerRadiusLarge)
                .stroke(Theme.stroke, lineWidth: 1)
        )
    }

    private var strengthCard: some View {
        CardContainer(padding: compact ? 14 : 17, cornerRadius: Theme.cornerRadiusLarge) {
            HStack {
                Text(L10n.tr("gen.strength"))
                    .font(.qasa(12, weight: .semibold))
                    .foregroundColor(Theme.textSecondary)
                Spacer()
                Text(model.strengthLabel.text)
                    .font(.qasa(14))
                    .foregroundColor(Theme.accent)
            }
            StrengthBar(
                segments: 5,
                filled: model.strengthLabel.filled,
                color: model.strengthLabel.color,
                height: 8
            )
            .padding(.top, 8)
        }
    }

    private var settingsCard: some View {
        CardContainer(padding: compact ? 16 : 21, cornerRadius: Theme.cornerRadiusLarge) {
            VStack(alignment: .leading, spacing: compact ? 16 : 24) {
                VStack(spacing: 12) {
                    HStack {
                        Text(L10n.tr("gen.length"))
                            .font(.qasa(14))
                            .foregroundColor(Theme.textPrimary)
                        Spacer()
                        Text("\(Int(model.length))")
                            .font(.qasa(20, weight: .semibold))
                            .foregroundColor(Theme.accent)
                            .padding(.horizontal, 13)
                            .padding(.vertical, 5)
                            .background(Theme.cardMuted)
                            .cornerRadius(8)
                            .overlay(
                                RoundedRectangle(cornerRadius: 8)
                                    .stroke(Theme.stroke, lineWidth: 1)
                            )
                    }
                    Slider(value: $model.length, in: 8...32, step: 1)
                        .tint(Theme.accent)
                        .onChange(of: model.length) { _, _ in model.regenerate() }
                }

                Rectangle().fill(Theme.stroke).frame(height: 1)

                VStack(spacing: 4) {
                    LocalizedToggleRow(
                        title: L10n.tr("gen.uppercase", locale: AppPreferences.shared.locale),
                        isOn: $model.useUppercase
                    )
                        .onChange(of: model.useUppercase) { _, _ in model.regenerate() }
                    LocalizedToggleRow(
                        title: L10n.tr("gen.lowercase", locale: AppPreferences.shared.locale),
                        isOn: $model.useLowercase
                    )
                        .onChange(of: model.useLowercase) { _, _ in model.regenerate() }
                    LocalizedToggleRow(
                        title: L10n.tr("gen.numbers", locale: AppPreferences.shared.locale),
                        isOn: $model.useNumbers
                    )
                        .onChange(of: model.useNumbers) { _, _ in model.regenerate() }
                    LocalizedToggleRow(
                        title: L10n.tr("gen.symbols", locale: AppPreferences.shared.locale),
                        isOn: $model.useSymbols
                    )
                        .onChange(of: model.useSymbols) { _, _ in model.regenerate() }

                    Rectangle().fill(Theme.stroke).frame(height: 1).padding(.vertical, 4)

                    LocalizedToggleRow(
                        title: L10n.tr("gen.pronounceable", locale: AppPreferences.shared.locale),
                        isOn: $model.pronounceable,
                        disabled: true
                    )
                }
            }
        }
    }
}

#Preview {
    Color.black.ignoresSafeArea()
        .sheet(isPresented: .constant(true)) {
            PasswordGeneratorModal(onUsePassword: { _ in })
                .presentationDetents([.fraction(0.92), .large])
                .presentationDragIndicator(.visible)
        }
}
