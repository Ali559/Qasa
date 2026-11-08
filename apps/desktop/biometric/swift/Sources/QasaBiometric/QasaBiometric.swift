import CryptoKit
import Foundation
import LocalAuthentication

// Status codes shared with src/macos.rs.
private let ok: Int32 = 0
private let cancelled: Int32 = 1
private let invalidated: Int32 = 2
private let failed: Int32 = 3
private let tooSmall: Int32 = 4
private let unavailable: Int32 = 5

/// 1 when Touch ID is enrolled and usable, 0 otherwise.
@_cdecl("qasa_bio_available")
public func qasaBioAvailable() -> Int32 {
    var error: NSError?
    let ok = LAContext().canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error)
    return ok && SecureEnclave.isAvailable ? 1 : 0
}

private func copy(_ data: Data, _ out: UnsafeMutablePointer<UInt8>, _ cap: Int, _ len: UnsafeMutablePointer<Int>) -> Bool {
    guard data.count <= cap else { return false }
    data.copyBytes(to: out, count: data.count)
    len.pointee = data.count
    return true
}

/// A new Secure Enclave key-agreement key usable only with the currently enrolled
/// fingerprints (`.biometryCurrentSet`): adding or removing a finger invalidates it. Writes
/// the key's encrypted blob (only this Mac's Secure Enclave can use it) and its public key.
/// Creating the key needs no Touch ID; using it does.
@_cdecl("qasa_se_create")
public func qasaSeCreate(
    _ keyOut: UnsafeMutablePointer<UInt8>, _ keyCap: Int, _ keyLen: UnsafeMutablePointer<Int>,
    _ pubOut: UnsafeMutablePointer<UInt8>, _ pubCap: Int, _ pubLen: UnsafeMutablePointer<Int>
) -> Int32 {
    guard SecureEnclave.isAvailable else { return unavailable }
    var acError: Unmanaged<CFError>?
    guard let access = SecAccessControlCreateWithFlags(
        nil, kSecAttrAccessibleWhenUnlockedThisDeviceOnly, [.privateKeyUsage, .biometryCurrentSet], &acError
    ) else { return failed }
    do {
        let key = try SecureEnclave.P256.KeyAgreement.PrivateKey(accessControl: access)
        guard copy(key.dataRepresentation, keyOut, keyCap, keyLen),
              copy(key.publicKey.x963Representation, pubOut, pubCap, pubLen) else { return tooSmall }
        return ok
    } catch {
        return failed
    }
}

/// ECDH between the Secure Enclave key and `peer` (an x9.63 P-256 public key). Shows the
/// Touch ID prompt with `reason`. Writes the 32-byte shared secret.
@_cdecl("qasa_se_agree")
public func qasaSeAgree(
    _ key: UnsafePointer<UInt8>, _ keyLen: Int,
    _ peer: UnsafePointer<UInt8>, _ peerLen: Int,
    _ reason: UnsafePointer<CChar>,
    _ out: UnsafeMutablePointer<UInt8>, _ outCap: Int, _ outLen: UnsafeMutablePointer<Int>
) -> Int32 {
    let context = LAContext()
    context.localizedReason = String(cString: reason)
    // No "Enter Password" button: the main password is the fallback, on Qasa's own screen.
    context.localizedFallbackTitle = ""
    let privateKey: SecureEnclave.P256.KeyAgreement.PrivateKey
    do {
        privateKey = try SecureEnclave.P256.KeyAgreement.PrivateKey(
            dataRepresentation: Data(bytes: key, count: keyLen), authenticationContext: context
        )
    } catch {
        // The blob no longer opens: the fingerprints changed or the key is gone.
        return invalidated
    }
    do {
        let peerKey = try P256.KeyAgreement.PublicKey(x963Representation: Data(bytes: peer, count: peerLen))
        let shared = try privateKey.sharedSecretFromKeyAgreement(with: peerKey)
        let bytes = shared.withUnsafeBytes { Data($0) }
        return copy(bytes, out, outCap, outLen) ? ok : tooSmall
    } catch let error as LAError {
        switch error.code {
        case .userCancel, .appCancel, .systemCancel, .userFallback: return cancelled
        case .biometryNotAvailable, .biometryNotEnrolled, .biometryLockout: return unavailable
        default: return failed
        }
    } catch {
        let ns = error as NSError
        // CryptoKit wraps LAError -2 (user cancel) etc. in its own error.
        if let la = ns.userInfo[NSUnderlyingErrorKey] as? NSError, la.domain == LAErrorDomain {
            return la.code == LAError.userCancel.rawValue || la.code == LAError.systemCancel.rawValue
                || la.code == LAError.appCancel.rawValue ? cancelled : failed
        }
        return failed
    }
}
