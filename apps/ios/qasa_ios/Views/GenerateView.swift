import SwiftUI

/// Full-page Generator tab (brand chrome + save). For sheets, use `PasswordGeneratorModal`.
struct GeneratorView: View {
    @StateObject private var model = PasswordGeneratorModel()

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                HStack(spacing: 12) {
                    Image(systemName: "shield.fill")
                        .foregroundColor(Theme.mint)
                    VStack(alignment: .leading, spacing: 0) {
                        Text("KASO")
                            .font(.system(size: 20, weight: .black))
                            .tracking(-1)
                            .foregroundColor(Theme.mint)
                        Text("Generator / دروستکەری تێپەڕەوشە")
                            .font(.system(size: 12))
                            .tracking(0.6)
                            .foregroundColor(Theme.textSecondary)
                    }
                }
                Spacer()
                Image(systemName: "person")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(Theme.mint)
                    .frame(width: 32, height: 32)
                    .background(Theme.cardBackground)
                    .clipShape(Circle())
                    .overlay(Circle().stroke(Theme.stroke, lineWidth: 1))
            }
            .padding(.horizontal, 16)
            .frame(height: 64)
            .background(Theme.chrome)
            .overlay(alignment: .bottom) {
                Rectangle().fill(Theme.stroke).frame(height: 1)
            }

            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    PasswordGeneratorContent(model: model, compact: false)
                    saveButton
                }
                .padding(.horizontal, Theme.screenPadding)
                .padding(.top, 24)
                .padding(.bottom, 40)
            }
        }
        .background(Theme.chrome.ignoresSafeArea())
        .onAppear { model.regenerate() }
    }

    private var saveButton: some View {
        Button {} label: {
            VStack(spacing: 4) {
                Text("Save to Vault")
                    .font(.system(size: 16))
                Text("پاشەکەوتکردن لە خەزێنە")
                    .font(.system(size: 12))
                    .tracking(0.6)
                    .opacity(0.8)
                    .environment(\.layoutDirection, .rightToLeft)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
            .background(Theme.mint)
            .foregroundColor(Theme.mintOnChip)
            .cornerRadius(Theme.cornerRadius)
            .shadow(color: Theme.mint.opacity(0.3), radius: 8)
        }
        .padding(.top, 16)
    }
}

#Preview {
    GeneratorView()
}
