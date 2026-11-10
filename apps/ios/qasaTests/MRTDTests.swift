import Foundation
import Testing
@testable import qasa

private func hex(_ s: String) -> Data {
    let clean = s.filter { !$0.isWhitespace }
    var out = Data()
    var i = clean.startIndex
    while i < clean.endIndex {
        let j = clean.index(i, offsetBy: 2)
        out.append(UInt8(clean[i..<j], radix: 16)!)
        i = j
    }
    return out
}

private extension Data {
    var hexString: String { map { String(format: "%02X", $0) }.joined() }
}

/// ICAO Doc 9303 eMRTD reading: MRZ, BAC (Part 11 Appendix D worked example), Secure
/// Messaging and DG1 parsing.
struct MRTDTests {
    // MARK: MRZ

    @Test func checkDigits() {
        #expect(MRZ.checkDigit("L898902C<") == "3")
        #expect(MRZ.checkDigit("690806") == "1")
        #expect(MRZ.checkDigit("940623") == "6")
        #expect(MRZ.checkDigit("D23145890") == "7")
        #expect(MRZ.checkDigit("740812") == "2")
        #expect(MRZ.checkDigit("120415") == "9")
        #expect(MRZ.checkDigit("L898902C3") == "6")
        #expect(MRZ.checkDigit("ZE184226B<<<<<") == "1")
    }

    @Test func accessKeyFromInputs() {
        #expect(MRZ.accessKey(documentNumber: "l898902c", birthDate: "690806", expiryDate: "940623")
            == "L898902C<369080619406236")
        #expect(MRZ.yymmdd(fromISO: "1969-08-06") == "690806")
        #expect(MRZ.yymmdd(fromISO: "1969-8-6") == nil)
        #expect(MRZ.normalizedDocumentNumber(" ab-12 3 ") == "AB123")
    }

    // MARK: BAC keys (Appendix D.2)

    static let mrzInfo = "L898902C<369080619406236"

    @Test func keyDerivation() {
        let seed = MRTDCrypto.keySeed(mrzInfo: Self.mrzInfo)
        #expect(seed.hexString == "239AB9CB282DAF66231DC5A4DF6BFBAE")
        let keys = BAC.Keys(mrzInfo: Self.mrzInfo)
        #expect(keys.enc.hexString == "AB94FDECF2674FDFB9B391F85D7F76F2")
        #expect(keys.mac.hexString == "7962D9ECE03D1ACD4C76089DCE131543")
    }

    // MARK: Mutual authentication (Appendix D.3)

    static let rndIC = hex("4608F91988702212")
    static let rndIFD = hex("781723860C06C226")
    static let kIFD = hex("0B795240CB7049B01C19B33E32804F0B")
    static let authCommand = hex("72C29C2371CC9BDB65B779B8E8D37B29ECC154AA56A8799FAE2F498F76ED92F25F1448EEA8AD90A7")
    static let authResponse = hex("46B9342A41396CD7386BF5803104D7CEDC122B9132139BAF2EEDC94EE178534F2F2D235D074D7449")

    @Test func externalAuthenticateData() {
        let data = BAC.authenticationData(
            keys: BAC.Keys(mrzInfo: Self.mrzInfo), rndIC: Self.rndIC, rndIFD: Self.rndIFD, kIFD: Self.kIFD
        )
        #expect(data == Self.authCommand)
    }

    @Test func sessionKeys() throws {
        let sm = try BAC.secureMessaging(
            response: Self.authResponse, keys: BAC.Keys(mrzInfo: Self.mrzInfo),
            rndIC: Self.rndIC, rndIFD: Self.rndIFD, kIFD: Self.kIFD
        )
        #expect(sm.encKey.hexString == "979EC13B1CBFE9DCD01AB0FED307EAE5")
        #expect(sm.macKey.hexString == "F1CB1F1FB5ADF208806B89DC579DC1F8")
        #expect(sm.sscBytes.hexString == "887022120C06C226")
    }

    @Test func wrongKeyIsRefused() {
        #expect(throws: MRTDError.accessDenied) {
            try BAC.secureMessaging(
                response: Self.authResponse, keys: BAC.Keys(mrzInfo: "L898902C<369080619406237"),
                rndIC: Self.rndIC, rndIFD: Self.rndIFD, kIFD: Self.kIFD
            )
        }
    }

    // MARK: Secure Messaging (Appendix D.4)

    static func appendixSession() -> SecureMessaging {
        SecureMessaging(
            encKey: hex("979EC13B1CBFE9DCD01AB0FED307EAE5"),
            macKey: hex("F1CB1F1FB5ADF208806B89DC579DC1F8"),
            ssc: 0x8870_2212_0C06_C226
        )
    }

    @Test func protectedSelectAndReadBinary() throws {
        var sm = Self.appendixSession()

        // SELECT EF.COM
        #expect(sm.protect(.selectFile(0x011E)).hexString
            == "0CA4020C158709016375432908C044F68E08BF8B92D635FF24F800")
        let select = try sm.unprotect(hex("990290008E08FA855A5D4C50A8ED9000"))
        #expect(select.isSuccess && select.data.isEmpty)

        // READ BINARY, first 4 bytes
        #expect(sm.protect(.readBinary(offset: 0, length: 4)).hexString
            == "0CB000000D9701048E08ED6705417E96BA5500")
        let head = try sm.unprotect(hex("8709019FF0EC34F9922651990290008E08AD55CC17140B2DED9000"))
        #expect(head.data.hexString == "60145F01")

        // READ BINARY, the remaining 18 bytes
        #expect(sm.protect(.readBinary(offset: 4, length: 18)).hexString
            == "0CB000040D9701128E082EA28A70F3C7B53500")
        let rest = try sm.unprotect(hex(
            "871901FB9235F4E4037F2327DCC8964F1F9B8C30F42C8E2FFF224A990290008E08C8B2787EAEA07D749000"
        ))
        #expect(rest.data.hexString == "04303130365F36063034303030305C026175")
        #expect(sm.sscBytes.hexString == "887022120C06C22C")
    }

    @Test func tamperedResponseFailsMAC() {
        var sm = Self.appendixSession()
        _ = sm.protect(.selectFile(0x011E))
        #expect(throws: MRTDError.lost) {
            try sm.unprotect(hex("990290008E08FA855A5D4C50A8EE9000"))
        }
    }

    @Test func retailMACAndPadding() {
        #expect(MRTDCrypto.pad(hex("011E")).hexString == "011E800000000000")
        #expect(MRTDCrypto.pad(Data(count: 8)).count == 16)
        #expect(MRTDCrypto.unpad(hex("0102800000000000")) == hex("0102"))
        // The MAC of E.IFD from Appendix D.3.
        #expect(MRTDCrypto.retailMAC(
            key: hex("7962D9ECE03D1ACD4C76089DCE131543"), message: Self.authCommand.prefix(32)
        ).hexString == "5F1448EEA8AD90A7")
    }

    // MARK: MRZ parsing (ICAO 9303 Parts 4 and 5 specimens)

    static let td3 = "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<"
        + "L898902C36UTO7408122F1204159ZE184226B<<<<<10"
    static let td1 = "I<UTOD231458907<<<<<<<<<<<<<<<"
        + "7408122F1204159UTO<<<<<<<<<<<6"
        + "ERIKSSON<<ANNA<MARIA<<<<<<<<<<"

    @Test func parsesTD3() throws {
        let doc = try #require(MRZDocument.parse(mrz: Self.td3))
        #expect(doc.format == .td3)
        #expect(doc.documentCode == "P")
        #expect(doc.issuingState == "UTO")
        #expect(doc.documentNumber == "L898902C3")
        #expect(doc.surname == "ERIKSSON")
        #expect(doc.givenNames == "ANNA MARIA")
        #expect(doc.fullName == "Anna Maria Eriksson")
        #expect(doc.nationality == "UTO")
        #expect(doc.birthDate == "740812")
        #expect(doc.birthISO(currentYear: 2026) == "1974-08-12")
        #expect(doc.expiryISO == "2012-04-15")
        #expect(doc.issuingCountryISO2 == nil)
    }

    @Test func parsesTD1() throws {
        let doc = try #require(MRZDocument.parse(mrz: Self.td1))
        #expect(doc.format == .td1)
        #expect(doc.documentCode == "I")
        #expect(doc.documentNumber == "D23145890")
        #expect(doc.fullName == "Anna Maria Eriksson")
        #expect(doc.nationality == "UTO")
        #expect(doc.expiryISO == "2012-04-15")
    }

    @Test func parsesTD1LongDocumentNumber() throws {
        // Number "D23145890123" (12 characters): 9 in the field, "<" for the check digit,
        // the rest and the check digit at the start of the optional data.
        let number = "D23145890123"
        let line1 = "I<UTO" + "D23145890" + "<" + "123" + String(MRZ.checkDigit(number)) + "<<<<<<<<<<<"
        let doc = try #require(MRZDocument.parse(mrz: line1 + Self.td1.dropFirst(30)))
        #expect(doc.documentNumber == number)
    }

    @Test func parsesDG1AndCountries() throws {
        let mrz = Data(Self.td3.utf8)
        let dg1 = Data([0x61, UInt8(mrz.count + 3), 0x5F, 0x1F, UInt8(mrz.count)]) + mrz
        let doc = try MRZDocument.parse(dg1: dg1)
        #expect(doc.documentNumber == "L898902C3")
        #expect(throws: MRTDError.malformed) { try MRZDocument.parse(dg1: hex("6003")) }

        #expect(MRZCountry.iso2(fromICAO: "IRQ") == "IQ")
        #expect(MRZCountry.iso2(fromICAO: "D<<") == "DE")
        #expect(MRZCountry.iso2(fromICAO: "GBR") == "GB")
        // Every mapped code is a country the app lists.
        let listed = Set(Country.all.map(\.code))
        #expect(Set(MRZCountry.icaoToISO2.values).isSubset(of: listed))
        #expect(listed.isSubset(of: Set(MRZCountry.icaoToISO2.values)))
    }

    // MARK: Whole read against a fake chip

    @Test func readsDG1FromFakeChip() async throws {
        let mrz = Data(Self.td1.utf8)
        let dg1 = Data([0x61, UInt8(mrz.count + 3), 0x5F, 0x1F, UInt8(mrz.count)]) + mrz
        let chip = FakeChip(mrzInfo: MRZ.accessKey(documentNumber: "D23145890", birthDate: "740812", expiryDate: "120415"), dg1: dg1)
        var reader = MRTDReader(transmit: { try await chip.handle($0) })
        reader.chunkSize = 32 // several READ BINARYs
        let doc = try await reader.readDocument(
            mrzInfo: MRZ.accessKey(documentNumber: "D23145890", birthDate: "740812", expiryDate: "120415")
        )
        #expect(doc.documentNumber == "D23145890")
        #expect(doc.fullName == "Anna Maria Eriksson")
        #expect(await chip.reads > 2)
    }

    @Test func fakeChipRefusesWrongDates() async throws {
        let chip = FakeChip(mrzInfo: MRZ.accessKey(documentNumber: "D23145890", birthDate: "740812", expiryDate: "120415"), dg1: Data())
        let reader = MRTDReader(transmit: { try await chip.handle($0) })
        await #expect(throws: MRTDError.accessDenied) {
            try await reader.readDocument(
                mrzInfo: MRZ.accessKey(documentNumber: "D23145890", birthDate: "740813", expiryDate: "120415")
            )
        }
    }

    @Test func paceOnlyChipIsUnsupported() async throws {
        // Selects the applet but refuses GET CHALLENGE (no BAC).
        let reader = MRTDReader(transmit: { apdu in
            apdu[1] == 0xA4 ? hex("9000") : hex("6982")
        })
        await #expect(throws: MRTDError.unsupported) {
            try await reader.readDocument(mrzInfo: Self.mrzInfo)
        }
    }

    @Test func appendixChallengeAndAuthentication() async throws {
        // The reader with the appendix's RND.IFD and K.IFD sends exactly its command.
        var sent: [Data] = []
        let log = Log()
        var reader = MRTDReader(transmit: { apdu in
            await log.add(apdu)
            switch apdu[1] {
            case 0xA4: return hex("9000")
            case 0x84: return Self.rndIC + hex("9000")
            case 0x82: return Self.authResponse + hex("9000")
            default: return hex("6A82")
            }
        })
        var randoms = [Self.rndIFD, Self.kIFD]
        reader.random = { _ in randoms.removeFirst() }
        let sm = try await reader.authenticate(mrzInfo: Self.mrzInfo)
        sent = await log.items
        #expect(sent[0].hexString == "00A4040C07A0000002471001")
        #expect(sent[1].hexString == "0084000008")
        #expect(sent[2].hexString == "0082000028" + Self.authCommand.hexString + "28")
        #expect(sm.sscBytes.hexString == "887022120C06C226")
    }
}

private actor Log {
    var items: [Data] = []
    func add(_ d: Data) { items.append(d) }
}

/// The chip's side of BAC and Secure Messaging, enough to serve one file.
private actor FakeChip {
    let keys: BAC.Keys
    let dg1: Data
    let rndIC = hex("0102030405060708")
    let kIC = hex("A1A2A3A4A5A6A7A8B1B2B3B4B5B6B7B8")
    var sm: SecureMessaging?
    var selected = false
    var reads = 0

    init(mrzInfo: String, dg1: Data) {
        keys = BAC.Keys(mrzInfo: mrzInfo)
        self.dg1 = dg1
    }

    func handle(_ apdu: Data) throws -> Data {
        let b = [UInt8](apdu)
        if let session = sm { return try secure(b, session) }
        switch b[1] {
        case 0xA4: return hex("9000")
        case 0x84: return rndIC + hex("9000")
        case 0x82:
            let data = Data(b[5..<45])
            let encrypted = data.prefix(32)
            guard MRTDCrypto.retailMAC(key: keys.mac, message: encrypted) == data.suffix(8) else { return hex("6300") }
            let plain = MRTDCrypto.tdes(false, key: keys.enc, data: encrypted)
            // S = RND.IFD ‖ RND.IC ‖ K.IFD
            guard Data(plain[8..<16]) == rndIC else { return hex("6300") }
            let rndIFD = Data(plain[0..<8])
            let kIFD = Data(plain[16..<32])
            let reply = MRTDCrypto.tdes(true, key: keys.enc, data: rndIC + rndIFD + kIC)
            let seed = MRTDCrypto.xor(kIFD, kIC)
            let ssc = [UInt8](rndIC.suffix(4)) + [UInt8](rndIFD.suffix(4))
            sm = SecureMessaging(
                encKey: MRTDCrypto.deriveKey(seed: seed, counter: 1),
                macKey: MRTDCrypto.deriveKey(seed: seed, counter: 2),
                ssc: ssc.reduce(UInt64(0)) { $0 << 8 | UInt64($1) }
            )
            return reply + MRTDCrypto.retailMAC(key: keys.mac, message: reply) + hex("9000")
        default:
            return hex("6D00")
        }
    }

    /// Checks the command's MAC, runs it, and protects the reply, all on the shared counter.
    private func secure(_ b: [UInt8], _ session: SecureMessaging) throws -> Data {
        let objects = TLV.all(in: Array(b[5..<(5 + Int(b[4]))]))
        let ksEnc = session.encKey, ksMac = session.macKey
        var ssc = session.ssc &+ 1
        func sscData(_ v: UInt64) -> Data { withUnsafeBytes(of: v.bigEndian) { Data($0) } }
        var macInput = sscData(ssc) + MRTDCrypto.pad(Data(b[0..<4]))
        for o in objects where o.tag != 0x8E { macInput.append(contentsOf: o.raw) }
        guard let mac = objects.first(where: { $0.tag == 0x8E }),
              MRTDCrypto.retailMAC(key: ksMac, message: macInput) == Data(mac.value) else { return hex("6988") }
        var commandData = Data()
        if let enc = objects.first(where: { $0.tag == 0x87 }) {
            commandData = MRTDCrypto.unpad(MRTDCrypto.tdes(false, key: ksEnc, data: Data(enc.value.dropFirst()))) ?? Data()
        }
        var replyData = Data()
        switch b[1] {
        case 0xA4:
            selected = commandData == hex("0101")
        case 0xB0:
            guard selected else { return hex("6986") }
            let offset = Int(b[2]) << 8 | Int(b[3])
            let le = Int(objects.first(where: { $0.tag == 0x97 })?.value.first ?? 0)
            replyData = dg1.dropFirst(offset).prefix(le == 0 ? 256 : le)
            reads += 1
        default:
            return hex("6D00")
        }
        ssc &+= 1
        var reply = Data()
        if !replyData.isEmpty {
            let enc = MRTDCrypto.tdes(true, key: ksEnc, data: MRTDCrypto.pad(replyData))
            reply.append(0x87)
            reply.append(contentsOf: TLV.encodeLength(enc.count + 1))
            reply.append(0x01)
            reply.append(enc)
        }
        reply.append(contentsOf: [0x99, 0x02, 0x90, 0x00])
        let replyMAC = MRTDCrypto.retailMAC(key: ksMac, message: sscData(ssc) + reply)
        reply.append(contentsOf: [0x8E, 0x08])
        reply.append(replyMAC)
        sm = SecureMessaging(encKey: ksEnc, macKey: ksMac, ssc: ssc)
        return reply + hex("9000")
    }
}
