import SwiftUI
import UIKit

/// The document an NFC scan reads. Driving licences have no ICAO chip, so they are not here.
enum NFCDocumentKind {
    case idCard
    case passport
}

/// "Scan with NFC" for an ID card or passport: the access details (step 1), a picture of the
/// phone moving onto the document, and the scan itself through the system NFC sheet.
struct NFCDocumentScanSheet: View {
    let kind: NFCDocumentKind
    /// Called on the main actor with what the chip said, just before the sheet closes.
    var onRead: (MRZDocument) -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var documentNumber: String
    @State private var birthDate: String
    @State private var expiryDate: String
    @State private var status: Status = .ready
    @State private var session: MRTDNFCSession?

    enum Status: Equatable {
        case ready
        case reading
        case done
        case failed(MRTDScanFailure)
    }

    init(kind: NFCDocumentKind, documentNumber: String, expiryDate: String, onRead: @escaping (MRZDocument) -> Void) {
        self.kind = kind
        self.onRead = onRead
        _documentNumber = State(initialValue: MRZ.normalizedDocumentNumber(documentNumber))
        _birthDate = State(initialValue: "")
        _expiryDate = State(initialValue: Self.isoDate(expiryDate) ?? "")
    }

    private var locale: Locale { AppLanguage.current.locale }
    private func tr(_ key: String) -> String { L10n.tr(key, locale: locale) }

    /// A typed or stored date as `YYYY-MM-DD`, when it reads as one.
    private static func isoDate(_ raw: String) -> String? {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, let date = VaultValidation.date(fromStored: trimmed) else { return nil }
        return VaultValidation.isoDate(from: date)
    }

    /// The BAC key from the three inputs, or `nil` while one is missing or not a date.
    private var accessKey: String? {
        let number = MRZ.normalizedDocumentNumber(documentNumber)
        guard !number.isEmpty,
              let birth = Self.isoDate(birthDate).flatMap(MRZ.yymmdd(fromISO:)),
              let expiry = Self.isoDate(expiryDate).flatMap(MRZ.yymmdd(fromISO:)) else { return nil }
        return MRZ.accessKey(documentNumber: number, birthDate: birth, expiryDate: expiry)
    }

    /// The step to do now: the details first, then holding the document, then keeping still.
    private var currentStep: Int {
        if status == .reading { return 3 }
        return accessKey == nil ? 1 : 2
    }

    private var canStart: Bool {
        guard accessKey != nil else { return false }
        switch status {
        case .ready: return true
        case .failed(let failure): return failure != .noNFC
        case .reading, .done: return false
        }
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 18) {
                header
                NFCScanIllustration(kind: kind, animated: !reduceMotion)
                    .frame(maxWidth: .infinity)
                    .accessibilityHidden(true)
                steps
            }
            .padding(.horizontal, Theme.screenPadding)
            .padding(.top, 28)
            .padding(.bottom, 12)
        }
        .scrollDismissesKeyboard(.interactively)
        .safeAreaInset(edge: .bottom) { footer }
        .background(Theme.background.ignoresSafeArea())
        .onAppear {
            if !MRTDNFCSession.isAvailable { status = .failed(.noNFC) }
        }
        .onDisappear { session?.cancel() }
        .onChange(of: status) { _, new in
            AccessibilityNotification.Announcement(statusText(new)).post()
        }
    }

    // MARK: - Parts

    private var header: some View {
        VStack(spacing: 6) {
            Text(tr(kind == .passport ? "nfc.passport.title" : "nfc.id.title"))
                .font(.qasa(19, weight: .bold))
                .foregroundColor(Theme.textPrimary)
                .accessibilityAddTraits(.isHeader)
            Text(tr("nfc.id.subtitle"))
                .font(.qasa(14))
                .foregroundColor(Theme.textSecondary)
        }
        .multilineTextAlignment(.center)
        .fixedSize(horizontal: false, vertical: true)
        .frame(maxWidth: .infinity)
    }

    private var steps: some View {
        VStack(alignment: .leading, spacing: 14) {
            NFCStepRow(number: 1, text: tr("nfc.id.step1"), isCurrent: currentStep == 1) {
                VStack(alignment: .leading, spacing: 12) {
                    FormTextField(title: tr("nfc.field.documentNumber"), text: $documentNumber)
                        .textInputAutocapitalization(.characters)
                        .autocorrectionDisabled()
                    ValidatedDateField(title: tr("nfc.field.birthDate"), text: $birthDate)
                    ValidatedDateField(title: tr("field.expiryDate"), text: $expiryDate)
                }
                .disabled(status == .reading)
                .padding(.top, 4)
            }
            NFCStepRow(
                number: 2,
                text: tr(kind == .passport ? "nfc.passport.step2" : "nfc.id.step2"),
                isCurrent: currentStep == 2
            ) { EmptyView() }
            NFCStepRow(number: 3, text: tr("nfc.id.step3"), isCurrent: currentStep == 3) { EmptyView() }
        }
    }

    private var footer: some View {
        VStack(spacing: 12) {
            statusLine
            ViewThatFits(in: .horizontal) {
                HStack(spacing: 10) { cancelButton; startButton }
                VStack(spacing: 10) { startButton; cancelButton }
            }
        }
        .padding(.horizontal, Theme.screenPadding)
        .padding(.top, 12)
        .padding(.bottom, 8)
        .background(Theme.background.ignoresSafeArea(edges: .bottom))
    }

    private var statusColor: Color {
        switch status {
        case .ready, .reading: return Theme.accent
        case .done: return Theme.success
        case .failed: return Theme.danger
        }
    }

    private func statusText(_ status: Status) -> String {
        switch status {
        case .ready: return tr("nfc.status.ready")
        case .reading: return tr("nfc.status.reading")
        case .done: return tr("nfc.status.done")
        case .failed(let failure): return tr(failure.messageKey)
        }
    }

    private var statusLine: some View {
        HStack(alignment: .firstTextBaseline, spacing: 8) {
            Group {
                if status == .reading && !reduceMotion {
                    statusDot.phaseAnimator([1.0, 0.3]) { dot, opacity in
                        dot.opacity(opacity)
                    } animation: { _ in .easeInOut(duration: 0.6) }
                } else {
                    statusDot
                }
            }
            .alignmentGuide(.firstTextBaseline) { $0[VerticalAlignment.center] + 4 }
            Text(statusText(status))
                .font(.qasa(14, weight: status == .done ? .medium : .regular))
                .foregroundColor(status == .ready || status == .reading ? Theme.textSecondary : statusColor)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.updatesFrequently)
    }

    private var statusDot: some View {
        Circle()
            .fill(statusColor)
            .frame(width: 8, height: 8)
            .accessibilityHidden(true)
    }

    private var cancelButton: some View {
        Button {
            session?.cancel()
            dismiss()
        } label: {
            Text(tr("common.cancel"))
                .font(.qasa(15, weight: .semibold))
                .foregroundColor(Theme.textPrimary)
                .padding(.vertical, 13)
                .padding(.horizontal, 12)
                .frame(maxWidth: .infinity)
                .background(Theme.cardBackground)
                .clipShape(RoundedRectangle(cornerRadius: 14))
                .overlay(RoundedRectangle(cornerRadius: 14).stroke(Theme.stroke, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }

    private var startButton: some View {
        let label: String
        if case .failed(let failure) = status, failure != .noNFC {
            label = tr("nfc.tryAgain")
        } else {
            label = tr("nfc.start")
        }
        return Button(action: start) {
            Text(label)
                .font(.qasa(15, weight: .semibold))
                .foregroundColor(Theme.onAccent)
                .padding(.vertical, 13)
                .padding(.horizontal, 12)
                .frame(maxWidth: .infinity)
                .background(Theme.accent.opacity(canStart ? 1 : 0.45))
                .clipShape(RoundedRectangle(cornerRadius: 14))
        }
        .buttonStyle(.plain)
        .disabled(!canStart)
    }

    // MARK: - Scan

    private func start() {
        guard let key = accessKey else { return }
        let failureTexts = Dictionary(uniqueKeysWithValues: [
            MRTDScanFailure.cancelled, .lost, .unsupported, .accessDenied, .off, .noNFC,
        ].map { ($0, tr($0.messageKey)) })
        let messages = MRTDNFCSession.Messages(
            hold: tr(kind == .passport ? "nfc.passport.title" : "nfc.id.title"),
            reading: tr("nfc.status.reading"),
            done: tr("nfc.status.done"),
            failure: { failureTexts[$0] ?? "" }
        )
        let scan = MRTDNFCSession(mrzInfo: key, messages: messages)
        session = scan
        status = .reading
        Task {
            do {
                let document = try await scan.read()
                UINotificationFeedbackGenerator().notificationOccurred(.success)
                status = .done
                onRead(document)
                try? await Task.sleep(for: .milliseconds(700))
                dismiss()
            } catch {
                let failure = MRTDNFCSession.failure(for: error)
                if failure != .cancelled {
                    UINotificationFeedbackGenerator().notificationOccurred(.error)
                }
                status = failure == .cancelled ? .ready : .failed(failure)
            }
            session = nil
        }
    }
}

/// One numbered step; the current one has a filled number.
private struct NFCStepRow<Extra: View>: View {
    let number: Int
    let text: String
    let isCurrent: Bool
    @ViewBuilder var extra: Extra

    @ScaledMetric(relativeTo: .body) private var badge: CGFloat = 28

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Text(number, format: .number)
                .font(.qasa(13, weight: .semibold))
                .foregroundColor(isCurrent ? Theme.onAccent : Theme.accent)
                .frame(width: badge, height: badge)
                .background(Circle().fill(isCurrent ? Theme.accent : Theme.cardMuted))
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 8) {
                Text(text)
                    .font(.qasa(14.5))
                    .foregroundColor(Theme.textPrimary)
                    .multilineTextAlignment(.leading)
                    .fixedSize(horizontal: false, vertical: true)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.top, 4)
                    .accessibilityLabel("\(number). \(text)")
                extra
            }
        }
    }
}

// MARK: - Illustration

/// A phone moving onto the document on a 3.2 s loop: it comes in (0–35 %), holds while
/// three waves spread and a tick shows (to 78 %), then moves back. Drawn in a 300×220 space
/// scaled to the width; only offsets, rotation, scale and opacity change per frame.
/// With Reduce Motion the phone rests on the document with one wave showing.
struct NFCScanIllustration: View {
    let kind: NFCDocumentKind
    let animated: Bool

    static let size = CGSize(width: 300, height: 220)
    static let loop: TimeInterval = 3.2

    var body: some View {
        Color.clear
            .aspectRatio(Self.size.width / Self.size.height, contentMode: .fit)
            .frame(maxWidth: Self.size.width)
            .overlay {
                GeometryReader { geo in
                    TimelineView(.animation(paused: !animated)) { context in
                        let frame = animated
                            ? Frame(phase: context.date.timeIntervalSinceReferenceDate
                                .truncatingRemainder(dividingBy: Self.loop) / Self.loop)
                            : .still
                        scene(frame)
                            .frame(width: Self.size.width, height: Self.size.height)
                            .scaleEffect(geo.size.width / Self.size.width, anchor: .topLeading)
                    }
                }
            }
            // A picture, not text: the same way round in right-to-left languages.
            .environment(\.layoutDirection, .leftToRight)
    }

    // MARK: Colours (theme)

    private var docFill: Color { Theme.cardBackgroundLight }
    private var docLine: Color { Theme.accent.opacity(0.32) }
    private var phoneBody: Color { Color(light: ThemePalette.light.textPrimary, dark: ThemePalette.dark.onAccent) }
    private var phoneScreen: Color { ThemePalette.dark.strokeChrome }
    private var tickColor: Color { ThemePalette.light.onAccent }

    // MARK: Scene

    private func scene(_ f: Frame) -> some View {
        ZStack {
            document(glow: f.glow)
            phone(f)
                .rotationEffect(.degrees(f.phoneRotation))
                .offset(x: f.phoneOffset.width, y: f.phoneOffset.height)
                .position(x: 151, y: 110)
            // Over the phone: the waves only show while it rests on the document, where
            // underneath they would be hidden by it.
            ForEach(Self.waves.indices, id: \.self) { i in
                let wave = Self.waves[i]
                wave.path
                    .stroke(Theme.accent, style: StrokeStyle(lineWidth: 3, lineCap: .round))
                    .scaleEffect(f.waveScale[i], anchor: wave.anchor)
                    .opacity(f.waveOpacity[i])
            }
        }
    }

    private func phone(_ f: Frame) -> some View {
        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 14).fill(phoneBody)
                .overlay(RoundedRectangle(cornerRadius: 14).stroke(Theme.strokeChrome.opacity(0.6), lineWidth: 1))
            box(6, 10, 66, 112, r: 8, phoneScreen)
            box(28, 3, 22, 4, r: 2, phoneScreen)
            Path { p in
                p.move(to: CGPoint(x: 27, y: 60))
                p.addLine(to: CGPoint(x: 35, y: 68))
                p.addLine(to: CGPoint(x: 51, y: 50))
            }
            .stroke(tickColor, style: StrokeStyle(lineWidth: 4, lineCap: .round, lineJoin: .round))
            .scaleEffect(f.tickScale, anchor: UnitPoint(x: 39.0 / 78, y: 59.0 / 132))
            .opacity(f.tickOpacity)
        }
        .frame(width: 78, height: 132)
    }

    @ViewBuilder
    private func document(glow: Double) -> some View {
        switch kind {
        case .idCard:
            ZStack(alignment: .topLeading) {
                box(38, 70, 224, 140, r: 16, Theme.accent).opacity(glow)
                outlined(44, 76, 212, 128, r: 14, lineWidth: 2)
                box(62, 96, 54, 66, r: 8, docLine)
                Circle().fill(docFill).frame(width: 24, height: 24).offset(x: 77, y: 108)
                Path { p in
                    p.move(to: CGPoint(x: 71, y: 156))
                    p.addQuadCurve(to: CGPoint(x: 107, y: 156), control: CGPoint(x: 89, y: 136))
                    p.closeSubpath()
                }
                .fill(docFill)
                box(130, 100, 100, 9, r: 4.5, docLine)
                box(130, 118, 74, 9, r: 4.5, docLine)
                box(130, 136, 88, 9, r: 4.5, docLine)
                box(62, 176, 176, 8, r: 4, docLine).opacity(0.7)
            }
            .frame(width: Self.size.width, height: Self.size.height, alignment: .topLeading)
        case .passport:
            ZStack(alignment: .topLeading) {
                box(66, 40, 168, 176, r: 14, Theme.accent).opacity(glow)
                outlined(72, 46, 156, 164, r: 10, lineWidth: 2)
                box(72, 46, 12, 164, r: 4, docLine).opacity(0.6)
                Circle().stroke(docLine, lineWidth: 3).frame(width: 56, height: 56).offset(x: 128, y: 82)
                RoundedRectangle(cornerRadius: 3).stroke(docLine, lineWidth: 2.5)
                    .frame(width: 56, height: 16).offset(x: 128, y: 170)
                box(149, 175, 14, 6, r: 3, docLine)
            }
            .frame(width: Self.size.width, height: Self.size.height, alignment: .topLeading)
        }
    }

    /// A filled rounded rectangle at SVG-style x, y, width, height (inside a top-leading ZStack).
    private func box(_ x: CGFloat, _ y: CGFloat, _ w: CGFloat, _ h: CGFloat, r: CGFloat, _ fill: Color) -> some View {
        RoundedRectangle(cornerRadius: r).fill(fill).frame(width: w, height: h).offset(x: x, y: y)
    }

    private func outlined(_ x: CGFloat, _ y: CGFloat, _ w: CGFloat, _ h: CGFloat, r: CGFloat, lineWidth: CGFloat) -> some View {
        RoundedRectangle(cornerRadius: r).fill(docFill)
            .overlay(RoundedRectangle(cornerRadius: r).stroke(docLine, lineWidth: lineWidth))
            .frame(width: w, height: h)
            .offset(x: x, y: y)
    }

    // MARK: Waves

    struct Wave {
        let path: Path
        /// Centre of the arc's bounding box, for scaling about it.
        let anchor: UnitPoint
    }

    /// Arcs starting at (x, 110 − chord/2), bulging right with the given radius, as in the
    /// approved design (`M150 92 a26 26 0 0 1 0 36` and so on, moved 6 left).
    static let waves: [Wave] = [(144.0, 36.0, 26.0), (156.0, 56.0, 40.0), (168.0, 76.0, 54.0)].map { x, chord, r in
        let half = chord / 2
        let cx = x - (r * r - half * half).squareRoot()
        let spread = asin(half / r)
        var path = Path()
        let steps = 16
        for k in 0...steps {
            let a = -spread + 2 * spread * Double(k) / Double(steps)
            let point = CGPoint(x: cx + r * cos(a), y: 110 + r * sin(a))
            if k == 0 { path.move(to: point) } else { path.addLine(to: point) }
        }
        let midX = (x + cx + r) / 2
        return Wave(path: path, anchor: UnitPoint(x: midX / NFCScanIllustration.size.width, y: 110 / NFCScanIllustration.size.height))
    }

    // MARK: Keyframes

    struct Frame {
        var phoneOffset: CGSize
        var phoneRotation: Double
        var waveScale: [CGFloat]
        var waveOpacity: [Double]
        var tickScale: CGFloat
        var tickOpacity: Double
        var glow: Double

        /// Reduce Motion: the phone on the document, the first wave showing.
        static let still = Frame(
            phoneOffset: .zero, phoneRotation: 0, waveScale: [1, 1, 1], waveOpacity: [0.7, 0, 0],
            tickScale: 1, tickOpacity: 0, glow: 0
        )

        private static let approach = UnitCurve.bezier(
            startControlPoint: UnitPoint(x: 0.45, y: 0.05), endControlPoint: UnitPoint(x: 0.25, y: 1)
        )
        private static let start = (dx: 70.0, dy: -46.0, angle: -8.0)
        /// Each wave starts 0.18 s after the one before.
        private static let waveDelay = 0.18 / NFCScanIllustration.loop

        init(phoneOffset: CGSize, phoneRotation: Double, waveScale: [CGFloat], waveOpacity: [Double],
             tickScale: CGFloat, tickOpacity: Double, glow: Double) {
            self.phoneOffset = phoneOffset
            self.phoneRotation = phoneRotation
            self.waveScale = waveScale
            self.waveOpacity = waveOpacity
            self.tickScale = tickScale
            self.tickOpacity = tickOpacity
            self.glow = glow
        }

        /// The picture at `phase` (0..<1) of the loop.
        init(phase t: Double) {
            let away: Double
            if t < 0.35 {
                away = 1 - Self.approach.value(at: t / 0.35)
            } else if t < 0.78 {
                away = 0
            } else {
                away = Self.approach.value(at: (t - 0.78) / 0.22)
            }
            phoneOffset = CGSize(width: Self.start.dx * away, height: Self.start.dy * away)
            phoneRotation = Self.start.angle * away

            var scales: [CGFloat] = []
            var opacities: [Double] = []
            for i in 0..<3 {
                var local = t - Double(i) * Self.waveDelay
                if local < 0 { local += 1 }
                scales.append(CGFloat(Self.keyframes(local, [(0, 0.6), (0.38, 0.6), (0.70, 1.35), (1, 1.35)])))
                opacities.append(Self.keyframes(local, [(0, 0), (0.38, 0), (0.48, 0.9), (0.70, 0), (1, 0)]))
            }
            waveScale = scales
            waveOpacity = opacities
            tickScale = CGFloat(Self.keyframes(t, [(0, 0.5), (0.68, 0.5), (0.74, 1.08), (0.80, 1), (1, 1)]))
            tickOpacity = Self.keyframes(t, [(0, 0), (0.68, 0), (0.74, 1), (0.90, 1), (1, 0)])
            glow = Self.keyframes(t, [(0, 0), (0.40, 0), (0.55, 0.55), (0.78, 0.55), (1, 0)])
        }

        /// The value at `t` between keyframes, eased out within each segment (as CSS `ease-out`).
        static func keyframes(_ t: Double, _ points: [(Double, Double)]) -> Double {
            guard let first = points.first else { return 0 }
            if t <= first.0 { return first.1 }
            for (a, b) in zip(points, points.dropFirst()) where t <= b.0 {
                let u = b.0 > a.0 ? (t - a.0) / (b.0 - a.0) : 1
                return a.1 + (b.1 - a.1) * UnitCurve.easeOut.value(at: u)
            }
            return points.last?.1 ?? 0
        }
    }
}

#Preview("ID card") {
    NFCDocumentScanSheet(kind: .idCard, documentNumber: "D23145890", expiryDate: "2031-04-15") { _ in }
}

#Preview("Passport") {
    NFCDocumentScanSheet(kind: .passport, documentNumber: "", expiryDate: "") { _ in }
}
