import CoreNFC
import Foundation

/// Why a scan ended without a document, as the scan sheet tells it.
nonisolated enum MRTDScanFailure: Hashable, Sendable {
    /// The person closed the system sheet: nothing to report.
    case cancelled
    case lost
    case unsupported
    case accessDenied
    case off
    case noNFC

    var messageKey: String {
        switch self {
        case .cancelled: return "nfc.status.ready"
        case .lost: return "nfc.error.lost"
        case .unsupported: return "nfc.error.unsupported"
        case .accessDenied: return "nfc.error.access"
        case .off: return "nfc.error.off"
        case .noNFC: return "nfc.error.none"
        }
    }
}

/// One Core NFC reading of a passport or ID card chip: opens the system NFC sheet, waits for
/// an ISO 7816 tag and runs `MRTDReader` on it.
nonisolated final class MRTDNFCSession: NSObject, NFCTagReaderSessionDelegate, @unchecked Sendable {
    /// The texts the system NFC sheet shows, resolved on the main actor by the caller.
    struct Messages: Sendable {
        var hold: String
        var reading: String
        var done: String
        var failure: @Sendable (MRTDScanFailure) -> String
    }

    static var isAvailable: Bool { NFCTagReaderSession.readingAvailable }

    private let mrzInfo: String
    private let messages: Messages
    private let queue = DispatchQueue(label: "qasa.nfc.mrtd")
    // Touched on `queue` only.
    private var session: NFCTagReaderSession?
    private var continuation: CheckedContinuation<MRZDocument, Error>?

    init(mrzInfo: String, messages: Messages) {
        self.mrzInfo = mrzInfo
        self.messages = messages
    }

    /// Scans until the chip is read, the person cancels or something fails
    /// (thrown as `MRTDScanFailure` wrapped in `ScanError`).
    func read() async throws -> MRZDocument {
        try await withCheckedThrowingContinuation { continuation in
            queue.async {
                guard NFCTagReaderSession.readingAvailable,
                      let session = NFCTagReaderSession(pollingOption: .iso14443, delegate: self, queue: self.queue)
                else {
                    continuation.resume(throwing: ScanError(failure: .noNFC))
                    return
                }
                self.continuation = continuation
                self.session = session
                session.alertMessage = self.messages.hold
                session.begin()
            }
        }
    }

    func cancel() {
        queue.async { self.session?.invalidate() }
    }

    struct ScanError: Error, Equatable {
        let failure: MRTDScanFailure
    }

    // MARK: NFCTagReaderSessionDelegate (called on `queue`)

    func tagReaderSessionDidBecomeActive(_ session: NFCTagReaderSession) {}

    func tagReaderSession(_ session: NFCTagReaderSession, didInvalidateWithError error: Error) {
        finish(.failure(ScanError(failure: Self.failure(for: error))))
    }

    func tagReaderSession(_ session: NFCTagReaderSession, didDetect tags: [NFCTag]) {
        guard tags.count == 1, let tag = tags.first else {
            session.restartPolling()
            return
        }
        guard case .iso7816(let chip) = tag else {
            session.invalidate(errorMessage: messages.failure(.unsupported))
            finish(.failure(ScanError(failure: .unsupported)))
            return
        }
        let mrzInfo = self.mrzInfo
        let messages = self.messages
        Task {
            do {
                try await session.connect(to: tag)
                session.alertMessage = messages.reading
                let reader = MRTDReader(transmit: { apdu in
                    guard let command = NFCISO7816APDU(data: apdu) else { throw MRTDError.malformed }
                    let (data, sw1, sw2) = try await chip.sendCommand(apdu: command)
                    return data + Data([sw1, sw2])
                })
                let document = try await reader.readDocument(mrzInfo: mrzInfo)
                session.alertMessage = messages.done
                self.finish(.success(document))
                session.invalidate()
            } catch {
                let failure = Self.failure(for: error)
                session.invalidate(errorMessage: messages.failure(failure))
                self.finish(.failure(ScanError(failure: failure)))
            }
        }
    }

    // MARK: -

    private func finish(_ result: Result<MRZDocument, Error>) {
        queue.async {
            guard let continuation = self.continuation else { return }
            self.continuation = nil
            self.session = nil
            continuation.resume(with: result)
        }
    }

    static func failure(for error: Error) -> MRTDScanFailure {
        if let scan = error as? ScanError { return scan.failure }
        if let mrtd = error as? MRTDError {
            switch mrtd {
            case .unsupported, .malformed: return .unsupported
            case .accessDenied: return .accessDenied
            case .lost: return .lost
            }
        }
        if let nfc = error as? NFCReaderError {
            switch nfc.code {
            case .readerSessionInvalidationErrorUserCanceled,
                 .readerSessionInvalidationErrorFirstNDEFTagRead:
                return .cancelled
            case .readerErrorUnsupportedFeature:
                return .noNFC
            case .readerErrorSecurityViolation, .readerErrorInvalidParameter,
                 .readerErrorInvalidParameterLength, .readerErrorParameterOutOfBound:
                return .unsupported
            case .readerErrorRadioDisabled:
                return .off
            default:
                return .lost
            }
        }
        return .lost
    }
}
