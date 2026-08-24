import SwiftUI
import Combine

/// Reusable bottom-sheet password generator (not the full Generator tab page).
/// Dismiss via sheet swipe; no Cancel/title chrome.
struct PasswordGeneratorModal: View {
    var onUsePassword: (String) -> Void

    @StateObject private var model = PasswordGeneratorModel()

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    PasswordGeneratorContent(model: model)
                }
                .padding(.horizontal, Theme.screenPadding)
                .padding(.top, 8)
                .padding(.bottom, 12)
            }

            VStack(spacing: 0) {
                Rectangle().fill(Theme.stroke).frame(height: 1)
                Button {
                    onUsePassword(model.password)
                } label: {
                    Text("Use Password")
                        .font(.system(size: 17, weight: .semibold))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 16)
                        .background(Theme.mint)
                        .foregroundColor(Theme.mintOnChip)
                        .cornerRadius(Theme.cornerRadius)
                }
                .padding(.horizontal, Theme.screenPadding)
                .padding(.top, 12)
                .padding(.bottom, 20)
            }
            .background(Theme.chrome)
        }
        .background(Theme.background.ignoresSafeArea())
        .preferredColorScheme(.dark)
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
        case 0...3: return ("Weak", 1, Theme.danger)
        case 4...5: return ("Fair", 2, Theme.gold)
        case 6...7: return ("Strong", 4, Theme.mint)
        default: return ("Very Strong", 5, Theme.mint)
        }
    }

    func regenerate() {
        var pool = ""
        if useUppercase { pool += uppercaseChars }
        if useLowercase { pool += lowercaseChars }
        if useNumbers { pool += numberChars }
        if useSymbols { pool += symbolChars }
        guard !pool.isEmpty else {
            password = ""
            return
        }
        password = String((0..<Int(length)).compactMap { _ in pool.randomElement() })
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
                colors: [Theme.mint.opacity(0.1), .clear],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )

            VStack(spacing: 0) {
                HStack {
                    SectionLabel(text: "Generated Password")
                    Spacer()
                    Button { model.regenerate() } label: {
                        Image(systemName: "arrow.clockwise")
                            .foregroundColor(Theme.cyan)
                            .padding(8)
                    }
                }
                .padding(.bottom, compact ? 12 : 16)

                Text(model.password)
                    .font(.system(size: compact ? 22 : 28, weight: .bold, design: .monospaced))
                    .tracking(compact ? 1.5 : 2.8)
                    .foregroundColor(.white)
                    .lineLimit(1)
                    .minimumScaleFactor(0.4)
                    .frame(maxWidth: .infinity)
                    .padding(.bottom, compact ? 16 : 24)

                Button {
                    model.copyPassword()
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: model.copied ? "checkmark" : "doc.on.doc")
                        Text(model.copied ? "Copied!" : "Copy Password")
                    }
                    .font(.system(size: 14))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 13)
                    .background(Theme.mintDark.opacity(0.2))
                    .foregroundColor(Theme.mint)
                    .cornerRadius(8)
                    .overlay(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(Theme.mint.opacity(0.3), lineWidth: 1)
                    )
                }
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
                Text("Strength / هێز")
                    .font(.kasoMono(12))
                    .tracking(0.6)
                    .foregroundColor(Theme.textSecondary)
                Spacer()
                Text(model.strengthLabel.text)
                    .font(.system(size: 14))
                    .foregroundColor(Theme.mint)
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
                        Text("Length / درێژی")
                            .font(.system(size: 14))
                            .foregroundColor(Theme.textPrimary)
                        Spacer()
                        Text("\(Int(model.length))")
                            .font(.system(size: 20, weight: .semibold))
                            .foregroundColor(Theme.mint)
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
                        .tint(Theme.mint)
                        .onChange(of: model.length) { _, _ in model.regenerate() }
                }

                Rectangle().fill(Theme.stroke).frame(height: 1)

                VStack(spacing: 4) {
                    BilingualToggleRow(titleEN: "Uppercase (A-Z)", titleCKB: "پیتە گەورەکان", isOn: $model.useUppercase)
                        .onChange(of: model.useUppercase) { _, _ in model.regenerate() }
                    BilingualToggleRow(titleEN: "Lowercase (a-z)", titleCKB: "پیتە بچووکەکان", isOn: $model.useLowercase)
                        .onChange(of: model.useLowercase) { _, _ in model.regenerate() }
                    BilingualToggleRow(titleEN: "Numbers (0-9)", titleCKB: "ژمارەکان", isOn: $model.useNumbers)
                        .onChange(of: model.useNumbers) { _, _ in model.regenerate() }
                    BilingualToggleRow(titleEN: "Symbols (!@#$)", titleCKB: "هێماکان", isOn: $model.useSymbols)
                        .onChange(of: model.useSymbols) { _, _ in model.regenerate() }

                    Rectangle().fill(Theme.stroke).frame(height: 1).padding(.vertical, 4)

                    BilingualToggleRow(
                        titleEN: "Pronounceable",
                        titleCKB: "ئاسان بۆ خوێندنەوە",
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
