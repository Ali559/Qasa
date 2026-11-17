import CoreGraphics
import CryptoKit
import Foundation
import ImageIO
import Testing
@testable import qasa

/// Step 7 "Shape" (`docs/site-icons.md`): every image in `docs/site-icon-shapes/` gets the
/// kind (and edge colour, within 6 per channel) listed under "shapes" in
/// `docs/site-icons-vectors.json`, which `reference.py` and the other apps check too. Plus the
/// cache's migration of entries written before the step existed.
@MainActor
struct SiteIconShapeTests {
    struct ShapeCase: Decodable { let file: String; let kind: String; let edge: String? }
    struct Vectors: Decodable { let shapes: [ShapeCase] }

    // qasaTests/ → apps/ios → apps → repository root; read in place so it stays the one source.
    static let docs = URL(fileURLWithPath: #filePath)
        .deletingLastPathComponent().deletingLastPathComponent()
        .deletingLastPathComponent().deletingLastPathComponent()
        .appendingPathComponent("docs")

    static let cases: [ShapeCase] = {
        let data = try! Data(contentsOf: docs.appendingPathComponent("site-icons-vectors.json"))
        return try! JSONDecoder().decode(Vectors.self, from: data).shapes
    }()

    static func image(_ file: String) throws -> Data {
        try Data(contentsOf: docs.appendingPathComponent("site-icon-shapes").appendingPathComponent(file))
    }

    static func rgb(hex: String) -> SiteIconImage.RGB {
        let v = UInt32(hex.dropFirst(), radix: 16)!
        return SiteIconImage.RGB(r: UInt8(v >> 16 & 0xFF), g: UInt8(v >> 8 & 0xFF), b: UInt8(v & 0xFF))
    }

    static func close(_ a: SiteIconImage.RGB, _ b: SiteIconImage.RGB, within: Int = 6) -> Bool {
        abs(Int(a.r) - Int(b.r)) <= within && abs(Int(a.g) - Int(b.g)) <= within && abs(Int(a.b) - Int(b.b)) <= within
    }

    /// Steps 5–7 the way a download goes through them.
    static func classify(_ data: Data) throws -> SiteIconImage.Classification {
        let decoded = try #require(SiteIconImage.decode(data))
        let fitted = try #require(SiteIconImage.fit(decoded))
        return SiteIconImage.shape(fitted).classification
    }

    static func decodePNG(_ png: Data) throws -> CGImage {
        let source = try #require(CGImageSourceCreateWithData(png as CFData, nil))
        return try #require(CGImageSourceCreateImageAtIndex(source, 0, nil))
    }

    @Test func shapeVectors() throws {
        #expect(Self.cases.count >= 10)
        for c in Self.cases {
            let result = try Self.classify(try Self.image(c.file))
            #expect(result.kind.rawValue == c.kind, "\(c.file): got \(result.kind)")
            if let hex = c.edge {
                let edge = try #require(result.edge, "\(c.file): no edge colour")
                #expect(Self.close(edge, Self.rgb(hex: hex)), "\(c.file): edge \(edge) vs \(hex)")
            } else {
                #expect(result.edge == nil, "\(c.file)")
            }
        }
    }

    /// The saved PNG is 64 × 64 and already shaped: shaping it again keeps its kind (a shaped
    /// edge or bleed icon is solid in its circle, so it is a bleed from then on).
    @Test func normalizedPNGIsShaped() throws {
        for c in Self.cases {
            let png = try #require(SiteIconImage.normalizedPNG(from: try Self.image(c.file)), "\(c.file)")
            let image = try Self.decodePNG(png)
            #expect(image.width == 64 && image.height == 64)
            let again: SiteIconImage.Classification = SiteIconImage.shape(image).classification
            switch c.kind {
            case "bleed", "edge": #expect(again.kind == SiteIconImage.ShapeKind.bleed, "\(c.file): \(again.kind.rawValue)")
            default: #expect(again.kind == SiteIconImage.ShapeKind.glyph, "\(c.file): \(again.kind.rawValue)")
            }
            if c.kind == "edge", let hex = c.edge {
                // The corner of an edge icon is its edge colour.
                let rgba = try #require(SiteIconImage.straightRGBA(image))
                let corner = SiteIconImage.RGB(r: rgba[0], g: rgba[1], b: rgba[2])
                #expect(rgba[3] == 255)
                #expect(Self.close(corner, Self.rgb(hex: hex)), "\(c.file): corner \(corner)")
            }
        }
    }

    /// The pure step on raw bytes: an empty canvas is an empty glyph, a full one a bleed.
    @Test func classifyOnBytes() {
        let side = SiteIconImage.side
        let empty = [UInt8](repeating: 0, count: side * side * 4)
        #expect(SiteIconImage.classify(rgba: empty) == .init(kind: .glyph, edge: nil, box: nil))
        let full = [UInt8](repeating: 255, count: side * side * 4)
        #expect(SiteIconImage.classify(rgba: full).kind == .bleed)
        // A 40 × 20 solid bar, red: an edge with box (12, 22)–(52, 42).
        var bar = empty
        for y in 22..<42 { for x in 12..<52 { let i = (y * side + x) * 4; bar[i] = 200; bar[i + 3] = 255 } }
        #expect(SiteIconImage.classify(rgba: bar) == .init(
            kind: .edge, edge: .init(r: 200, g: 0, b: 0), box: .init(x0: 12, y0: 22, x1: 52, y1: 42)
        ))
    }

    /// The tile's ring, told from the saved PNG alone: exactly the glyph kinds get one.
    @Test func ringOnlyForGlyphs() throws {
        for c in Self.cases {
            let png = try #require(SiteIconImage.normalizedPNG(from: try Self.image(c.file)), "\(c.file)")
            let shaped = try Self.decodePNG(png)
            #expect(SiteIconImage.needsRing(shaped) == (c.kind == "glyph"), "\(c.file)")
            let icon = try #require(SiteIconStore.Icon(png: png), "\(c.file)")
            #expect(icon.ring == (c.kind == "glyph"), "\(c.file)")
        }
    }

    // MARK: - Cache

    @Test func unshapedEntryIsShapedOnReadAndWrittenBack() throws {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent("site-icons-\(UUID().uuidString)")
        let cache = SiteIconCache(directory: dir)
        defer { cache.clear() }
        let key = SymmetricKey(size: .bits256)

        // What Phase 7 saved: fitted to 64 × 64 but not shaped, and no `shape` field.
        let decoded = try #require(SiteIconImage.decode(try Self.image("wide-solid.png")))
        let fitted = try #require(SiteIconImage.fit(decoded))
        let unshapedPNG = try #require(Self.encodePNG(fitted))
        #expect(SiteIconImage.shape(fitted).classification.kind == .edge)
        let checkedAt = Date(timeIntervalSince1970: 1_700_000_000)
        try cache.write(SiteIconCacheEntry(host: "wide.com", kind: .icon, png: unshapedPNG, checkedAt: checkedAt, shape: nil), key: key)

        // The JSON really lacks the field (an old entry).
        let file = try #require(try FileManager.default.contentsOfDirectory(atPath: dir.path).first)
        let plain = try VaultCrypto.decrypt(try Data(contentsOf: dir.appendingPathComponent(file)), key: key)
        #expect(String(decoding: plain, as: UTF8.self).contains("\"shape\"") == false)

        let read = try #require(cache.entry(host: "wide.com", key: key))
        #expect(read.shape == 1)
        #expect(read.kind == .icon)
        #expect(read.checkedAt == checkedAt, "shaping isn't a refresh")
        let shaped = try Self.decodePNG(try #require(read.png))
        #expect(SiteIconImage.shape(shaped).classification.kind == .bleed)

        // Written back: a second read gets the same shaped entry straight from disk.
        let plainAfter = try VaultCrypto.decrypt(try Data(contentsOf: dir.appendingPathComponent(file)), key: key)
        let onDisk = try JSONDecoder().decode(SiteIconCacheEntry.self, from: plainAfter)
        #expect(onDisk == read)
        #expect(cache.entry(host: "wide.com", key: key) == read)
    }

    @Test func newEntriesRecordShape() {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent("site-icons-\(UUID().uuidString)")
        let cache = SiteIconCache(directory: dir)
        defer { cache.clear() }
        let key = SymmetricKey(size: .bits256)
        #expect(cache.store(host: "a.com", result: .icon(Data([1])), key: key)?.shape == 1)
        #expect(cache.store(host: "b.com", result: .none, key: key)?.shape == 1)
        // Already shaped: read back untouched.
        #expect(cache.entry(host: "a.com", key: key)?.png == Data([1]))
    }

    static func encodePNG(_ image: CGImage) -> Data? {
        let out = NSMutableData()
        guard let dest = CGImageDestinationCreateWithData(out, "public.png" as CFString, 1, nil) else { return nil }
        CGImageDestinationAddImage(dest, image, nil)
        return CGImageDestinationFinalize(dest) ? out as Data : nil
    }
}
