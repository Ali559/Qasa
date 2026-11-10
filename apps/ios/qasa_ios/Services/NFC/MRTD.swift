import CommonCrypto
import CryptoKit
import Foundation
import Security

// Reading the chip of a passport or ID card (an eMRTD, ICAO Doc 9303): Basic Access Control,
// Secure Messaging and DG1 (the MRZ). Pure Swift, no Core NFC: the chip is reached through a
// `transmit` closure, so the whole protocol runs in unit tests against vectors and a fake chip.
// `MRTDNFCSession` plugs it into Core NFC.

/// Why reading a document chip failed.
nonisolated enum MRTDError: Error, Equatable {
    /// The chip has no ICAO applet, or only speaks PACE (no BAC).
    case unsupported
    /// The chip refused the access key: the number or dates don't match the document.
    case accessDenied
    /// The document moved away, or the reply was cut or failed its check.
    case lost
    /// The chip's data could not be read as an MRZ.
    case malformed
}

// MARK: - MRZ

nonisolated enum MRZ {
    /// ICAO 9303 Part 3 check digit: weights 7-3-1, digits as is, A–Z as 10–35, `<` as 0.
    static func checkDigit(_ field: String) -> Character {
        let weights = [7, 3, 1]
        var sum = 0
        for (i, ch) in field.uppercased().enumerated() {
            let value: Int
            if let d = ch.wholeNumberValue, ch.isASCII {
                value = d
            } else if let ascii = ch.asciiValue, ascii >= 65, ascii <= 90 {
                value = Int(ascii) - 55
            } else {
                value = 0
            }
            sum += value * weights[i % 3]
        }
        return Character(String(sum % 10))
    }

    /// A document number as the MRZ writes it: upper case, letters and digits only.
    static func normalizedDocumentNumber(_ raw: String) -> String {
        String(raw.uppercased().filter { $0.isASCII && ($0.isLetter || $0.isNumber) })
    }

    /// The BAC key material ("MRZ information"): document number padded with `<` to 9 and its
    /// check digit, birth date YYMMDD and its check digit, expiry YYMMDD and its check digit.
    static func accessKey(documentNumber: String, birthDate: String, expiryDate: String) -> String {
        var number = normalizedDocumentNumber(documentNumber)
        if number.count < 9 { number += String(repeating: "<", count: 9 - number.count) }
        return number + String(checkDigit(number))
            + birthDate + String(checkDigit(birthDate))
            + expiryDate + String(checkDigit(expiryDate))
    }

    /// `YYYY-MM-DD` → `YYMMDD`, or `nil` when it is not such a date.
    static func yymmdd(fromISO iso: String) -> String? {
        let parts = iso.split(separator: "-")
        guard parts.count == 3, parts[0].count == 4, parts[1].count == 2, parts[2].count == 2,
              parts.allSatisfy({ $0.allSatisfy(\.isNumber) }) else { return nil }
        return String(parts[0].suffix(2)) + parts[1] + parts[2]
    }
}

/// What the chip's DG1 says about the holder and the document.
nonisolated struct MRZDocument: Equatable {
    enum Format: Equatable { case td1, td2, td3 }

    var format: Format
    var documentCode: String
    /// ICAO three-letter code (`D` for Germany), `<` removed.
    var issuingState: String
    var documentNumber: String
    var surname: String
    var givenNames: String
    var nationality: String
    /// YYMMDD as in the MRZ.
    var birthDate: String
    /// YYMMDD as in the MRZ.
    var expiryDate: String

    /// "Given Surname", in title case.
    var fullName: String {
        let posix = Locale(identifier: "en_US_POSIX")
        return [givenNames, surname]
            .filter { !$0.isEmpty }
            .map { $0.lowercased(with: posix).capitalized(with: posix) }
            .joined(separator: " ")
    }

    /// Expiry as `YYYY-MM-DD` (documents expire in this century).
    var expiryISO: String? { Self.iso(expiryDate, century: { _ in 2000 }) }

    /// Birth as `YYYY-MM-DD`: a year after `currentYear`'s last two digits is in the 1900s.
    func birthISO(currentYear: Int = Calendar(identifier: .gregorian).component(.year, from: Date())) -> String? {
        Self.iso(birthDate, century: { yy in yy > currentYear % 100 ? 1900 : 2000 })
    }

    /// The issuing state as an ISO 3166 two-letter code, for the countries the app lists.
    var issuingCountryISO2: String? { MRZCountry.iso2(fromICAO: issuingState) }

    private static func iso(_ yymmdd: String, century: (Int) -> Int) -> String? {
        guard yymmdd.count == 6, yymmdd.allSatisfy(\.isNumber),
              let yy = Int(yymmdd.prefix(2)),
              let mm = Int(yymmdd.dropFirst(2).prefix(2)),
              let dd = Int(yymmdd.suffix(2)),
              (1...12).contains(mm), (1...31).contains(dd) else { return nil }
        return String(format: "%04d-%02d-%02d", century(yy) + yy, mm, dd)
    }

    /// Reads a TD1 (3×30), TD2 (2×36) or TD3 (2×44) MRZ; line breaks are ignored.
    static func parse(mrz raw: String) -> MRZDocument? {
        let mrz = String(raw.uppercased().filter { !$0.isNewline && $0 != " " })
        let chars = Array(mrz)
        func field(_ from: Int, _ length: Int) -> String {
            guard from >= 0, from + length <= chars.count else { return "" }
            return String(chars[from..<(from + length)])
        }
        func clean(_ s: String) -> String { s.replacingOccurrences(of: "<", with: "") }
        func names(_ s: String) -> (surname: String, given: String) {
            let trimmed = s.trimmingCharacters(in: CharacterSet(charactersIn: "<"))
            let parts = trimmed.components(separatedBy: "<<")
            let surname = parts[0].replacingOccurrences(of: "<", with: " ")
            let given = parts.dropFirst().joined(separator: " ")
                .replacingOccurrences(of: "<", with: " ")
                .split(separator: " ").joined(separator: " ")
            return (surname.trimmingCharacters(in: .whitespaces), given)
        }

        switch chars.count {
        case 90: // TD1: ID cards
            var number = field(5, 9)
            // A number longer than 9 runs on into the optional data, ended by its check digit.
            if field(14, 1) == "<" {
                let optional = clean(field(15, 15).components(separatedBy: "<").first ?? "")
                if optional.count > 1 { number += String(optional.dropLast()) }
            }
            let n = names(field(60, 30))
            return MRZDocument(
                format: .td1, documentCode: clean(field(0, 2)), issuingState: clean(field(2, 3)),
                documentNumber: clean(number), surname: n.surname, givenNames: n.given,
                nationality: clean(field(45, 3)), birthDate: field(30, 6), expiryDate: field(38, 6)
            )
        case 72, 88: // TD2, TD3 (passports): same layout, line length 36 or 44
            let line = chars.count / 2
            let n = names(field(5, line - 5))
            return MRZDocument(
                format: chars.count == 88 ? .td3 : .td2, documentCode: clean(field(0, 2)),
                issuingState: clean(field(2, 3)), documentNumber: clean(field(line, 9)),
                surname: n.surname, givenNames: n.given, nationality: clean(field(line + 10, 3)),
                birthDate: field(line + 13, 6), expiryDate: field(line + 21, 6)
            )
        default:
            return nil
        }
    }

    /// Reads EF.DG1: tag 61 holding 5F1F, the MRZ.
    static func parse(dg1: Data) throws -> MRZDocument {
        guard let outer = TLV.first(in: [UInt8](dg1)), outer.tag == 0x61,
              let inner = TLV.all(in: outer.value).first(where: { $0.tag == 0x5F1F }),
              let text = String(bytes: inner.value, encoding: .ascii),
              let document = parse(mrz: text) else { throw MRTDError.malformed }
        return document
    }
}

/// ICAO 9303 three-letter state codes for the countries in `Country.all`.
nonisolated enum MRZCountry {
    static let icaoToISO2: [String: String] = [
        "IRQ": "IQ", "USA": "US", "GBR": "GB", "GBD": "GB", "GBN": "GB", "GBO": "GB", "GBP": "GB",
        "GBS": "GB", "TUR": "TR", "D": "DE", "DEU": "DE", "FRA": "FR", "ARE": "AE", "SAU": "SA",
        "JOR": "JO", "KWT": "KW", "QAT": "QA", "BHR": "BH", "EGY": "EG", "IRN": "IR", "CAN": "CA",
        "AUS": "AU", "NLD": "NL", "SWE": "SE", "NOR": "NO", "CHE": "CH", "IND": "IN", "PAK": "PK",
        "CHN": "CN", "JPN": "JP", "KOR": "KR", "BRA": "BR", "MEX": "MX", "ESP": "ES", "ITA": "IT",
        "POL": "PL",
    ]

    static func iso2(fromICAO code: String) -> String? {
        icaoToISO2[code.replacingOccurrences(of: "<", with: "").uppercased()]
    }
}

// MARK: - BER-TLV (the subset the chip uses)

nonisolated struct TLV {
    let tag: UInt32
    let value: [UInt8]
    /// The whole object: tag, length and value.
    let raw: [UInt8]

    /// The first object at the start of `bytes`, or `nil` when it is cut short.
    static func first(in bytes: [UInt8]) -> TLV? {
        parse(bytes, at: 0)?.tlv
    }

    static func all(in bytes: [UInt8]) -> [TLV] {
        var out: [TLV] = []
        var i = 0
        while i < bytes.count, let (tlv, next) = parse(bytes, at: i) {
            out.append(tlv)
            i = next
        }
        return out
    }

    /// Length of the tag and length bytes, when `bytes` starts an object.
    static func headerAndValueLength(_ bytes: [UInt8]) -> (header: Int, value: Int)? {
        guard let (tagLength, _) = tag(bytes, at: 0),
              let (lengthLength, value) = length(bytes, at: tagLength) else { return nil }
        return (tagLength + lengthLength, value)
    }

    private static func parse(_ b: [UInt8], at start: Int) -> (tlv: TLV, next: Int)? {
        guard let (tagLength, tag) = tag(b, at: start),
              let (lengthLength, length) = length(b, at: start + tagLength) else { return nil }
        let valueStart = start + tagLength + lengthLength
        guard valueStart + length <= b.count else { return nil }
        let end = valueStart + length
        return (TLV(tag: tag, value: Array(b[valueStart..<end]), raw: Array(b[start..<end])), end)
    }

    private static func tag(_ b: [UInt8], at i: Int) -> (Int, UInt32)? {
        guard i < b.count else { return nil }
        var tag = UInt32(b[i])
        var n = 1
        if b[i] & 0x1F == 0x1F {
            repeat {
                guard i + n < b.count else { return nil }
                tag = (tag << 8) | UInt32(b[i + n])
                n += 1
            } while b[i + n - 1] & 0x80 != 0
        }
        return (n, tag)
    }

    private static func length(_ b: [UInt8], at i: Int) -> (Int, Int)? {
        guard i < b.count else { return nil }
        let first = Int(b[i])
        if first < 0x80 { return (1, first) }
        let count = first & 0x7F
        guard (1...3).contains(count), i + count < b.count else { return nil }
        var value = 0
        for k in 1...count { value = (value << 8) | Int(b[i + k]) }
        return (1 + count, value)
    }

    static func encodeLength(_ n: Int) -> [UInt8] {
        if n < 0x80 { return [UInt8(n)] }
        if n <= 0xFF { return [0x81, UInt8(n)] }
        return [0x82, UInt8(n >> 8), UInt8(n & 0xFF)]
    }
}

// MARK: - Crypto (DES / 3DES via CommonCrypto, ISO 9797-1 MAC algorithm 3)

nonisolated enum MRTDCrypto {
    static let zeroIV = Data(count: 8)

    /// SHA-1 of the MRZ information, first 16 bytes.
    static func keySeed(mrzInfo: String) -> Data {
        Data(Insecure.SHA1.hash(data: Data(mrzInfo.utf8))).prefix(16)
    }

    /// ICAO 9303 Part 11 §9.7.1: SHA-1(seed ‖ counter)[0..<16], DES parity set.
    /// Counter 1 gives the encryption key, 2 the MAC key.
    static func deriveKey(seed: Data, counter: UInt32) -> Data {
        var input = Data(seed)
        withUnsafeBytes(of: counter.bigEndian) { input.append(contentsOf: $0) }
        return adjustParity(Data(Data(Insecure.SHA1.hash(data: input)).prefix(16)))
    }

    /// Sets each byte's lowest bit so the byte has odd parity.
    static func adjustParity(_ key: Data) -> Data {
        Data(key.map { byte in
            let high = byte & 0xFE
            return high.nonzeroBitCount % 2 == 0 ? high | 1 : high
        })
    }

    /// ISO 9797-1 padding method 2: 0x80 then zeros up to a multiple of 8.
    static func pad(_ data: Data) -> Data {
        var out = Data(data)
        out.append(0x80)
        while out.count % 8 != 0 { out.append(0) }
        return out
    }

    static func unpad(_ data: Data) -> Data? {
        guard let index = data.lastIndex(where: { $0 != 0 }), data[index] == 0x80 else { return nil }
        return Data(data[data.startIndex..<index])
    }

    /// Two-key 3DES in CBC mode, zero IV unless given, no padding.
    static func tdes(_ encrypt: Bool, key: Data, data: Data, iv: Data = zeroIV) -> Data {
        let k = Data(key.prefix(16))
        return crypt(encrypt, algorithm: CCAlgorithm(kCCAlgorithm3DES), key: k + k.prefix(8), data: data, iv: iv)
    }

    /// Single DES in CBC mode, no padding.
    static func des(_ encrypt: Bool, key: Data, data: Data, iv: Data = zeroIV) -> Data {
        crypt(encrypt, algorithm: CCAlgorithm(kCCAlgorithmDES), key: Data(key.prefix(8)), data: data, iv: iv)
    }

    /// ISO 9797-1 MAC algorithm 3 ("retail MAC") with padding method 2: DES-CBC under the
    /// key's first half, then the last block decrypted with the second half and encrypted
    /// again with the first.
    static func retailMAC(key: Data, message: Data) -> Data {
        let ka = Data(key.prefix(8))
        let kb = Data(key.dropFirst(8).prefix(8))
        let chained = des(true, key: ka, data: pad(message))
        let last = Data(chained.suffix(8))
        return des(true, key: ka, data: des(false, key: kb, data: last))
    }

    private static func crypt(_ encrypt: Bool, algorithm: CCAlgorithm, key: Data, data: Data, iv: Data) -> Data {
        precondition(data.count % 8 == 0, "DES input must be whole blocks")
        var out = Data(count: data.count)
        var moved = 0
        let status = out.withUnsafeMutableBytes { outBuf in
            data.withUnsafeBytes { inBuf in
                key.withUnsafeBytes { keyBuf in
                    iv.withUnsafeBytes { ivBuf in
                        CCCrypt(
                            CCOperation(encrypt ? kCCEncrypt : kCCDecrypt), algorithm, 0,
                            keyBuf.baseAddress, key.count, ivBuf.baseAddress,
                            inBuf.baseAddress, data.count, outBuf.baseAddress, data.count, &moved
                        )
                    }
                }
            }
        }
        precondition(status == CCCryptorStatus(kCCSuccess), "CCCrypt failed: \(status)")
        return out
    }

    static func xor(_ a: Data, _ b: Data) -> Data {
        Data(zip(a, b).map { $0 ^ $1 })
    }

    static func random(_ count: Int) -> Data {
        var bytes = [UInt8](repeating: 0, count: count)
        _ = SecRandomCopyBytes(kSecRandomDefault, count, &bytes)
        return Data(bytes)
    }
}

// MARK: - APDUs

nonisolated struct CommandAPDU: Equatable {
    var cla: UInt8 = 0x00
    var ins: UInt8
    var p1: UInt8
    var p2: UInt8
    var data = Data()
    /// Expected response length, 1…256 (256 is sent as 00); `nil` for none.
    var le: Int?

    /// The short-form bytes.
    var bytes: Data {
        var out = Data([cla, ins, p1, p2])
        if !data.isEmpty {
            out.append(UInt8(data.count))
            out.append(data)
        }
        if let le { out.append(UInt8(le & 0xFF)) }
        return out
    }

    static func selectApplication(_ aid: Data) -> CommandAPDU {
        CommandAPDU(ins: 0xA4, p1: 0x04, p2: 0x0C, data: aid)
    }

    static func selectFile(_ id: UInt16) -> CommandAPDU {
        CommandAPDU(ins: 0xA4, p1: 0x02, p2: 0x0C, data: Data([UInt8(id >> 8), UInt8(id & 0xFF)]))
    }

    static func readBinary(offset: Int, length: Int) -> CommandAPDU {
        CommandAPDU(ins: 0xB0, p1: UInt8((offset >> 8) & 0x7F), p2: UInt8(offset & 0xFF), le: length)
    }

    static let getChallenge = CommandAPDU(ins: 0x84, p1: 0, p2: 0, le: 8)

    static func externalAuthenticate(_ data: Data) -> CommandAPDU {
        CommandAPDU(ins: 0x82, p1: 0, p2: 0, data: data, le: 40)
    }
}

nonisolated struct ResponseAPDU: Equatable {
    var data: Data
    var sw: UInt16
    var isSuccess: Bool { sw == 0x9000 }

    /// Splits the chip's raw reply (data followed by SW1 SW2).
    init(raw: Data) throws {
        guard raw.count >= 2 else { throw MRTDError.lost }
        let bytes = [UInt8](raw)
        data = Data(bytes.dropLast(2))
        sw = UInt16(bytes[bytes.count - 2]) << 8 | UInt16(bytes[bytes.count - 1])
    }

    init(data: Data, sw: UInt16) {
        self.data = data
        self.sw = sw
    }
}

// MARK: - BAC

nonisolated enum BAC {
    /// The ICAO applet (LDS1 eMRTD application).
    static let applicationID = Data([0xA0, 0x00, 0x00, 0x02, 0x47, 0x10, 0x01])

    struct Keys: Equatable {
        let enc: Data
        let mac: Data

        init(enc: Data, mac: Data) {
            self.enc = enc
            self.mac = mac
        }

        init(mrzInfo: String) {
            let seed = MRTDCrypto.keySeed(mrzInfo: mrzInfo)
            enc = MRTDCrypto.deriveKey(seed: seed, counter: 1)
            mac = MRTDCrypto.deriveKey(seed: seed, counter: 2)
        }
    }

    /// EXTERNAL AUTHENTICATE data: E.IFD = 3DES(Kenc, RND.IFD ‖ RND.IC ‖ K.IFD), then its MAC.
    static func authenticationData(keys: Keys, rndIC: Data, rndIFD: Data, kIFD: Data) -> Data {
        let encrypted = MRTDCrypto.tdes(true, key: keys.enc, data: rndIFD + rndIC + kIFD)
        return encrypted + MRTDCrypto.retailMAC(key: keys.mac, message: encrypted)
    }

    /// Checks the chip's answer and derives the session keys and send sequence counter.
    static func secureMessaging(
        response: Data, keys: Keys, rndIC: Data, rndIFD: Data, kIFD: Data
    ) throws -> SecureMessaging {
        let r = [UInt8](response)
        guard r.count == 40 else { throw MRTDError.accessDenied }
        let encrypted = Data(r[0..<32])
        guard MRTDCrypto.retailMAC(key: keys.mac, message: encrypted) == Data(r[32..<40]) else {
            throw MRTDError.accessDenied
        }
        let plain = [UInt8](MRTDCrypto.tdes(false, key: keys.enc, data: encrypted))
        guard Data(plain[0..<8]) == rndIC, Data(plain[8..<16]) == rndIFD else { throw MRTDError.accessDenied }
        let kIC = Data(plain[16..<32])
        let seed = MRTDCrypto.xor(kIFD, kIC)
        let ssc = [UInt8](rndIC.suffix(4)) + [UInt8](rndIFD.suffix(4))
        return SecureMessaging(
            encKey: MRTDCrypto.deriveKey(seed: seed, counter: 1),
            macKey: MRTDCrypto.deriveKey(seed: seed, counter: 2),
            ssc: ssc.reduce(UInt64(0)) { $0 << 8 | UInt64($1) }
        )
    }
}

// MARK: - Secure Messaging (ICAO 9303 Part 11 §9.8, 3DES)

nonisolated struct SecureMessaging: Equatable {
    let encKey: Data
    let macKey: Data
    /// Send sequence counter, incremented before each command and each response.
    private(set) var ssc: UInt64

    init(encKey: Data, macKey: Data, ssc: UInt64) {
        self.encKey = encKey
        self.macKey = macKey
        self.ssc = ssc
    }

    var sscBytes: Data { withUnsafeBytes(of: ssc.bigEndian) { Data($0) } }

    /// The protected form of `command`: DO87 (encrypted data), DO97 (Le) and DO8E (MAC).
    mutating func protect(_ command: CommandAPDU) -> Data {
        let cla = command.cla | 0x0C
        let header = MRTDCrypto.pad(Data([cla, command.ins, command.p1, command.p2]))
        var objects = Data()
        if !command.data.isEmpty {
            let encrypted = MRTDCrypto.tdes(true, key: encKey, data: MRTDCrypto.pad(command.data))
            objects.append(0x87)
            objects.append(contentsOf: TLV.encodeLength(encrypted.count + 1))
            objects.append(0x01)
            objects.append(encrypted)
        }
        if let le = command.le {
            objects.append(contentsOf: [0x97, 0x01, UInt8(le & 0xFF)])
        }
        ssc &+= 1
        let mac = MRTDCrypto.retailMAC(key: macKey, message: sscBytes + header + objects)
        objects.append(contentsOf: [0x8E, 0x08])
        objects.append(mac)

        var out = Data([cla, command.ins, command.p1, command.p2, UInt8(objects.count)])
        out.append(objects)
        out.append(0x00)
        return out
    }

    /// Checks the reply's MAC and decrypts its data. A reply that is only a status word
    /// (the chip refused before Secure Messaging) comes back as is.
    mutating func unprotect(_ raw: Data) throws -> ResponseAPDU {
        let response = try ResponseAPDU(raw: raw)
        if response.data.isEmpty {
            // The chip answers errors without Secure Messaging; the session is over either way.
            if response.isSuccess { throw MRTDError.lost }
            return response
        }
        let objects = TLV.all(in: [UInt8](response.data))
        ssc &+= 1
        guard let mac = objects.first(where: { $0.tag == 0x8E }) else { throw MRTDError.lost }
        var macInput = sscBytes
        for object in objects where object.tag == 0x87 || object.tag == 0x99 || object.tag == 0x85 {
            macInput.append(contentsOf: object.raw)
        }
        guard MRTDCrypto.retailMAC(key: macKey, message: macInput) == Data(mac.value) else {
            throw MRTDError.lost
        }
        var sw = response.sw
        if let status = objects.first(where: { $0.tag == 0x99 }), status.value.count == 2 {
            sw = UInt16(status.value[0]) << 8 | UInt16(status.value[1])
        }
        var data = Data()
        if let encrypted = objects.first(where: { $0.tag == 0x87 }) {
            guard encrypted.value.first == 0x01, (encrypted.value.count - 1) % 8 == 0,
                  let plain = MRTDCrypto.unpad(
                    MRTDCrypto.tdes(false, key: encKey, data: Data(encrypted.value.dropFirst()))
                  ) else { throw MRTDError.lost }
            data = plain
        }
        return ResponseAPDU(data: data, sw: sw)
    }
}

// MARK: - Reader

/// Runs BAC and reads DG1 through `transmit`, which sends one APDU and returns the chip's
/// reply with its status word at the end.
nonisolated struct MRTDReader {
    typealias Transmit = (Data) async throws -> Data

    let transmit: Transmit
    /// Random bytes for RND.IFD and K.IFD; fixed in tests.
    var random: (Int) -> Data = MRTDCrypto.random
    /// Bytes asked for per READ BINARY; small enough that the protected reply fits one APDU.
    var chunkSize = 0xA0

    /// Reads and parses DG1, unlocking the chip with `mrzInfo` (`MRZ.accessKey`).
    func readDocument(mrzInfo: String) async throws -> MRZDocument {
        try MRZDocument.parse(dg1: try await readDG1(mrzInfo: mrzInfo))
    }

    func readDG1(mrzInfo: String) async throws -> Data {
        var sm = try await authenticate(mrzInfo: mrzInfo)
        return try await readFile(0x0101, sm: &sm)
    }

    func authenticate(mrzInfo: String) async throws -> SecureMessaging {
        let select = try await send(.selectApplication(BAC.applicationID))
        guard select.isSuccess else { throw MRTDError.unsupported }

        // A PACE-only chip refuses GET CHALLENGE outside PACE.
        let challenge = try await send(.getChallenge)
        guard challenge.isSuccess, challenge.data.count == 8 else { throw MRTDError.unsupported }

        let keys = BAC.Keys(mrzInfo: mrzInfo)
        let rndIFD = random(8)
        let kIFD = random(16)
        let auth = try await send(.externalAuthenticate(
            BAC.authenticationData(keys: keys, rndIC: challenge.data, rndIFD: rndIFD, kIFD: kIFD)
        ))
        switch auth.sw {
        case 0x9000:
            return try BAC.secureMessaging(
                response: auth.data, keys: keys, rndIC: challenge.data, rndIFD: rndIFD, kIFD: kIFD
            )
        case 0x6D00, 0x6E00:
            throw MRTDError.unsupported
        default:
            // 6300 / 6982 / 6985 …: the key from the number and dates is wrong.
            throw MRTDError.accessDenied
        }
    }

    func readFile(_ id: UInt16, sm: inout SecureMessaging) async throws -> Data {
        let select = try sm.unprotect(try await transmit(sm.protect(.selectFile(id))))
        guard select.isSuccess else { throw MRTDError.unsupported }

        let head = try await readChunk(offset: 0, length: 4, sm: &sm)
        guard let (header, valueLength) = TLV.headerAndValueLength([UInt8](head)) else {
            throw MRTDError.malformed
        }
        let total = header + valueLength
        var file = head.prefix(total)
        while file.count < total {
            let chunk = try await readChunk(
                offset: file.count, length: min(chunkSize, total - file.count), sm: &sm
            )
            guard !chunk.isEmpty else { throw MRTDError.malformed }
            file.append(chunk)
        }
        return Data(file.prefix(total))
    }

    private func readChunk(offset: Int, length: Int, sm: inout SecureMessaging) async throws -> Data {
        let response = try sm.unprotect(try await transmit(sm.protect(.readBinary(offset: offset, length: length))))
        // 6282: end of file reached before `length`, data still valid.
        guard response.isSuccess || response.sw == 0x6282 else { throw MRTDError.lost }
        return response.data
    }

    private func send(_ command: CommandAPDU) async throws -> ResponseAPDU {
        try ResponseAPDU(raw: try await transmit(command.bytes))
    }
}
