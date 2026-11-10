import AVFoundation
import SwiftUI
import Vision
import VisionKit

/// Login editor row for the 2FA code. Free on every plan. The secret itself is never shown:
/// once set, the row shows the issuer and the live code, with a way to remove it.
struct TOTPEditorField: View {
    @Binding var totp: String?

    @State private var showScanner = false
    @State private var showKeyEntry = false
    @State private var keyInput = ""
    @State private var error: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            FormFieldLabel(text: L10n.tr("field.totp"))

            if (totp ?? "").isEmpty {
                addMenu
                Text(L10n.tr("totp.hint"))
                    .font(.qasa(12))
                    .foregroundColor(Theme.textSecondary)
            } else {
                setRow(TOTP(stored: totp))
            }

            if let error {
                Text(error)
                    .font(.qasa(12))
                    .foregroundColor(Theme.danger)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .textPromptSheet(
            isPresented: $showKeyEntry,
            title: L10n.tr("totp.enterKey"),
            message: L10n.tr("totp.hint"),
            placeholder: L10n.tr("totp.keyPlaceholder"),
            text: $keyInput,
            confirmTitle: L10n.tr("totp.add"),
            cancelTitle: L10n.tr("common.cancel"),
            plainText: true
        ) {
            applyKey(keyInput)
        }
        .sheet(isPresented: $showScanner) {
            TOTPScannerSheet(
                onScanned: { uri in
                    totp = uri
                    error = nil
                    showScanner = false
                },
                onEnterKey: {
                    showScanner = false
                    // The alert can't present while the sheet is still dismissing.
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { openKeyEntry() }
                }
            )
            .appLocalization()
        }
    }

    private var addMenu: some View {
        Menu {
            Button {
                error = nil
                showScanner = true
            } label: {
                Label(L10n.tr("totp.scan"), systemImage: "qrcode.viewfinder")
            }
            Button {
                openKeyEntry()
            } label: {
                Label(L10n.tr("totp.enterKey"), systemImage: "keyboard")
            }
        } label: {
            QasaButtonLabel(title: L10n.tr("totp.add"), icon: "plus")
                .font(.qasa(16, weight: .semibold))
                .foregroundColor(Theme.accent)
                .frame(maxWidth: .infinity)
                .frame(height: Theme.buttonHeight)
                .background(Theme.cardBackground)
                .clipShape(RoundedRectangle(cornerRadius: Theme.controlCornerRadius, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: Theme.controlCornerRadius, style: .continuous)
                        .stroke(Theme.accent.opacity(0.4), lineWidth: 1)
                )
        }
    }

    private func setRow(_ config: TOTP?) -> some View {
        HStack(spacing: 12) {
            Image(systemName: "checkmark.shield.fill")
                .font(.qasa(18, weight: .medium))
                .foregroundColor(Theme.accent)

            VStack(alignment: .leading, spacing: 2) {
                Text(config?.issuer ?? config?.accountName ?? L10n.tr("field.totp"))
                    .font(.qasa(15, weight: .medium))
                    .foregroundColor(Theme.textPrimary)
                    .lineLimit(1)
                if let config {
                    TimelineView(.periodic(from: .now, by: 1)) { context in
                        Text(TOTP.grouped(config.code(at: context.date)))
                            .font(.kasoMono(15))
                            .foregroundColor(Theme.textSecondary)
                    }
                }
            }

            Spacer(minLength: 8)

            Button(role: .destructive) {
                totp = nil
                error = nil
            } label: {
                Text(L10n.tr("totp.remove"))
                    .font(.qasa(14, weight: .semibold))
                    .foregroundColor(Theme.danger)
            }
            .buttonStyle(.plain)
        }
        .padding(14)
        .background(Theme.cardBackground)
        .cornerRadius(Theme.cornerRadius)
        .overlay(
            RoundedRectangle(cornerRadius: Theme.cornerRadius)
                .stroke(Theme.stroke, lineWidth: 1)
        )
    }

    private func openKeyEntry() {
        keyInput = ""
        error = nil
        showKeyEntry = true
    }

    private func applyKey(_ input: String) {
        do {
            totp = try TOTP.parse(input).uri
            error = nil
        } catch {
            self.error = L10n.tr("totp.invalid")
        }
    }
}

// MARK: - QR scanning

/// Camera sheet for the site's 2FA QR code. VisionKit's data scanner, QR only.
/// A denied (or missing) camera falls back to typing the setup key.
struct TOTPScannerSheet: View {
    var onScanned: (String) -> Void
    var onEnterKey: () -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var access: CameraAccess = .checking
    @State private var message: String?

    private enum CameraAccess { case checking, granted, denied }

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 16) {
                Text(L10n.tr("totp.scanHint"))
                    .font(.qasa(14))
                    .foregroundColor(Theme.textSecondary)

                switch access {
                case .checking:
                    ProgressView()
                        .tint(Theme.accent)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                case .granted:
                    TOTPQRScanner(onPayload: handle)
                        .clipShape(RoundedRectangle(cornerRadius: Theme.cornerRadius))
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                case .denied:
                    VStack(alignment: .leading, spacing: 16) {
                        Label(L10n.tr("totp.cameraDenied"), systemImage: "camera.fill")
                            .font(.qasa(14))
                            .foregroundColor(Theme.textPrimary)
                        PrimaryButton(title: L10n.tr("totp.enterKey"), icon: "keyboard", action: onEnterKey)
                    }
                    Spacer(minLength: 0)
                }

                if let message {
                    Text(message)
                        .font(.qasa(13, weight: .medium))
                        .foregroundColor(Theme.danger)
                }
            }
            .padding(Theme.screenPadding)
            .background(Theme.background.ignoresSafeArea())
            .navigationTitle(L10n.tr("totp.scanTitle"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(L10n.tr("common.cancel")) { dismiss() }
                }
            }
        }
        .task { access = await Self.requestAccess() }
    }

    private func handle(_ payload: String) {
        do {
            onScanned(try TOTP.parseScanned(payload).uri)
        } catch TOTP.ParseError.notOTPAuth {
            message = L10n.tr("totp.notQr")
        } catch {
            message = L10n.tr("totp.invalid")
        }
    }

    private static func requestAccess() async -> CameraAccess {
        // No camera (simulator, some iPads) is handled like a denied one: type the key.
        guard DataScannerViewController.isSupported else { return .denied }
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            return DataScannerViewController.isAvailable ? .granted : .denied
        case .notDetermined:
            let granted = await AVCaptureDevice.requestAccess(for: .video)
            return granted && DataScannerViewController.isAvailable ? .granted : .denied
        default:
            return .denied
        }
    }
}

private struct TOTPQRScanner: UIViewControllerRepresentable {
    var onPayload: (String) -> Void

    func makeUIViewController(context: Context) -> DataScannerViewController {
        let scanner = DataScannerViewController(
            recognizedDataTypes: [.barcode(symbologies: [.qr])],
            qualityLevel: .balanced,
            recognizesMultipleItems: false,
            isHighFrameRateTrackingEnabled: false,
            isHighlightingEnabled: true
        )
        scanner.delegate = context.coordinator
        return scanner
    }

    func updateUIViewController(_ scanner: DataScannerViewController, context: Context) {
        context.coordinator.onPayload = onPayload
        if !scanner.isScanning { try? scanner.startScanning() }
    }

    static func dismantleUIViewController(_ scanner: DataScannerViewController, coordinator: Coordinator) {
        scanner.stopScanning()
    }

    func makeCoordinator() -> Coordinator { Coordinator(onPayload: onPayload) }

    final class Coordinator: NSObject, DataScannerViewControllerDelegate {
        var onPayload: (String) -> Void
        /// The scanner reports the same code again as it moves; only react to a new one.
        private var last: String?

        init(onPayload: @escaping (String) -> Void) {
            self.onPayload = onPayload
        }

        func dataScanner(_ dataScanner: DataScannerViewController, didAdd addedItems: [RecognizedItem], allItems: [RecognizedItem]) {
            for item in addedItems {
                guard case .barcode(let barcode) = item,
                      let payload = barcode.payloadStringValue,
                      payload != last else { continue }
                last = payload
                onPayload(payload)
                return
            }
        }
    }
}

// MARK: - Login detail

/// The current code, grouped for reading, with the seconds left in its period. Tapping the
/// row or the copy button copies the code the moment it's tapped.
struct TOTPCodeRow: View {
    let totp: TOTP
    var onCopy: (String) -> Void

    var body: some View {
        TimelineView(.periodic(from: .now, by: 1)) { context in
            let left = totp.secondsRemaining(at: context.date)
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Text(L10n.tr("field.totp"))
                        .font(.qasa(13))
                        .foregroundColor(Theme.textSecondary)
                    Spacer()
                    Button {
                        onCopy(totp.code())
                    } label: {
                        Image(systemName: "doc.on.doc")
                            .font(.qasa(14))
                            .foregroundColor(Theme.accent)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(L10n.tr("copy.totp"))
                }

                HStack(spacing: 12) {
                    Text(TOTP.grouped(totp.code(at: context.date)))
                        .font(.kasoMono(22, weight: .semibold))
                        .foregroundColor(Theme.textPrimary)
                        .contentTransition(.numericText())
                    Spacer()
                    ZStack {
                        Circle()
                            .stroke(Theme.stroke, lineWidth: 3)
                        Circle()
                            .trim(from: 0, to: CGFloat(left) / CGFloat(totp.period))
                            .stroke(left <= 5 ? Theme.danger : Theme.accent,
                                    style: StrokeStyle(lineWidth: 3, lineCap: .round))
                            .rotationEffect(.degrees(-90))
                            .animation(.linear(duration: 1), value: left)
                        Text("\(left)")
                            .font(.qasa(11, weight: .semibold))
                            .foregroundColor(left <= 5 ? Theme.danger : Theme.textSecondary)
                    }
                    .frame(width: 28, height: 28)
                    .accessibilityHidden(true)
                }
            }
        }
        .padding(.vertical, 12)
        .contentShape(Rectangle())
        .onTapGesture { onCopy(totp.code()) }
    }
}
