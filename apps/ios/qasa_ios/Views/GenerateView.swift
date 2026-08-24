import SwiftUI

struct GeneratorView: View {
    @State private var password: String = "xK9#v2!PzL5@"
    @State private var length: Double = 16
    @State private var useUppercase = true
    @State private var useLowercase = true
    @State private var useNumbers = true
    @State private var useSymbols = true
    @State private var pronounceable = false
    @State private var copied = false

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

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    header
                    generatedPasswordCard
                    strengthCard
                    settingsCard
                    saveButton
                }
                .padding(20)
            }
            .background(Theme.background.ignoresSafeArea())
        }
        .onAppear { regenerate() }
    }

    // MARK: - Header
    private var header: some View {
        HStack {
            HStack(spacing: 8) {
                Image(systemName: "shield.fill")
                    .foregroundColor(Theme.mint)
                VStack(alignment: .leading, spacing: 1) {
                    Text("KASO")
                        .font(.title3.bold())
                        .foregroundColor(Theme.mint)
                    HStack(spacing: 4) {
                        Text("Generator /")
                            .font(.caption2)
                            .foregroundColor(Theme.textSecondary)
                        Text("دروستکەری تێپەڕەوشە")
                            .font(.caption2)
                            .foregroundColor(Theme.textSecondary)
                            .environment(\.layoutDirection, .rightToLeft)
                    }
                }
            }
            Spacer()
            Circle()
                .fill(Theme.cardBackgroundLight)
                .frame(width: 40, height: 40)
                .overlay(
                    Image(systemName: "person.fill")
                        .foregroundColor(Theme.textSecondary)
                )
        }
    }

    // MARK: - Generated Password Card
    private var generatedPasswordCard: some View {
        CardContainer(padding: 20) {
            HStack {
                SectionLabel(text: "Generated Password")
                Spacer()
                Button {
                    regenerate()
                } label: {
                    Image(systemName: "arrow.clockwise")
                        .foregroundColor(Color(hex: "3B82F6"))
                }
            }

            Text(password)
                .font(.system(size: 28, weight: .bold, design: .monospaced))
                .foregroundColor(Theme.textPrimary)
                .lineLimit(1)
                .minimumScaleFactor(0.5)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 20)
                .multilineTextAlignment(.center)

            Button {
                UIPasteboard.general.string = password
                withAnimation { copied = true }
                DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                    withAnimation { copied = false }
                }
            } label: {
                HStack(spacing: 8) {
                    Image(systemName: copied ? "checkmark" : "doc.on.doc")
                    Text(copied ? "Copied!" : "Copy Password")
                        .fontWeight(.semibold)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .background(Theme.mint.opacity(0.12))
                .foregroundColor(Theme.mint)
                .cornerRadius(Theme.cornerRadius)
                .overlay(
                    RoundedRectangle(cornerRadius: Theme.cornerRadius)
                        .stroke(Theme.mint.opacity(0.4), lineWidth: 1)
                )
            }
        }
    }

    // MARK: - Strength Card
    private var strengthCard: some View {
        CardContainer {
            HStack {
                HStack(spacing: 6) {
                    Image(systemName: "bolt.fill")
                        .foregroundColor(Theme.textSecondary)
                    Text("Strength /")
                        .font(.subheadline)
                        .foregroundColor(Theme.textPrimary)
                }
                Spacer()
                Text(strengthLabel.text)
                    .font(.subheadline.weight(.semibold))
                    .foregroundColor(Theme.mint)
            }
            HStack(spacing: 6) {
                ForEach(0..<5, id: \.self) { i in
                    Capsule()
                        .fill(i < strengthLabel.filled ? Theme.mint : Theme.cardBackgroundLight)
                        .frame(height: 6)
                }
            }
            .padding(.top, 10)
        }
    }

    // MARK: - Settings Card
    private var settingsCard: some View {
        CardContainer {
            HStack {
                HStack(spacing: 6) {
                    Image(systemName: "ruler.fill")
                        .foregroundColor(Theme.textSecondary)
                    Text("Length /")
                        .font(.subheadline)
                        .foregroundColor(Theme.textPrimary)
                }
                Spacer()
                Text("\(Int(length))")
                    .font(.subheadline.weight(.semibold))
                    .foregroundColor(Theme.textPrimary)
                    .frame(width: 40, height: 30)
                    .background(Color.black.opacity(0.4))
                    .cornerRadius(8)
            }

            Slider(value: $length, in: 8...32, step: 1)
                .tint(Theme.mint)
                .padding(.vertical, 6)
                .onChange(of: length) { _, _ in regenerate() }

            Divider().background(Theme.stroke).padding(.vertical, 6)

            VStack(spacing: 0) {
                BilingualToggleRow(titleEN: "Uppercase (A-Z)", titleCKB: "پیتە گەورەکان", isOn: $useUppercase)
                    .onChange(of: useUppercase) { _, _ in regenerate() }

                Divider().background(Theme.stroke)

                BilingualToggleRow(titleEN: "Lowercase (a-z)", titleCKB: "پیتە بچووکەکان", isOn: $useLowercase)
                    .onChange(of: useLowercase) { _, _ in regenerate() }

                Divider().background(Theme.stroke)

                BilingualToggleRow(titleEN: "Numbers (0-9)", titleCKB: "ژمارەکان", isOn: $useNumbers)
                    .onChange(of: useNumbers) { _, _ in regenerate() }

                Divider().background(Theme.stroke)

                BilingualToggleRow(titleEN: "Symbols (!@#$)", titleCKB: "هێماکان", isOn: $useSymbols)
                    .onChange(of: useSymbols) { _, _ in regenerate() }
            }

            Divider().background(Theme.stroke).padding(.vertical, 6)

            BilingualToggleRow(titleEN: "Pronounceable", titleCKB: "ئاسان بۆ خوێندنەوە", isOn: $pronounceable, disabled: true)
        }
    }

    // MARK: - Save Button
    private var saveButton: some View {
        Button {
            // save to vault action
        } label: {
            VStack(spacing: 3) {
                Text("Save to Vault")
                    .fontWeight(.semibold)
                Text("پاشەکەوتکردن لە خەزێنە")
                    .font(.caption2)
                    .environment(\.layoutDirection, .rightToLeft)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(Theme.mint)
            .foregroundColor(.black)
            .cornerRadius(Theme.cornerRadius)
        }
    }

    // MARK: - Password Generation
    private func regenerate() {
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
}

#Preview {
    GeneratorView()
}
