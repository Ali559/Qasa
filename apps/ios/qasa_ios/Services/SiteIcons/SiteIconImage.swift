import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers

/// Decoding a downloaded icon and normalising it to the cached form (`docs/site-icons.md`
/// steps 5–7): PNG, ICO (its largest image), JPEG, GIF (first frame) or WebP; anything under
/// 16 × 16 is rejected; the result is fitted into 64 × 64 (aspect kept, centred, transparent
/// padding), shaped for its circle (bleed / edge / glyph, `docs/site-icon-shapes/reference.py`)
/// and encoded as PNG.
nonisolated enum SiteIconImage {
    static let side = 64
    static let minimumSide = 16

    private static let acceptedTypes: Set<String> = [
        UTType.png.identifier, UTType.ico.identifier, UTType.jpeg.identifier,
        UTType.gif.identifier, UTType.webP.identifier,
    ]

    /// The normalised 64 × 64 PNG, or nil when `data` isn't a usable icon.
    static func normalizedPNG(from data: Data) -> Data? {
        guard let image = decode(data) else { return nil }
        guard let fitted = fit(image) else { return nil }
        return encodePNG(shape(fitted).image)
    }

    /// Shapes a PNG cached before step 7 existed (already fitted to 64 × 64), or nil when it
    /// can't be decoded.
    static func shapedPNG(fromUnshaped png: Data) -> Data? {
        guard let source = CGImageSourceCreateWithData(png as CFData, nil),
              let image = CGImageSourceCreateImageAtIndex(source, 0, nil),
              let fitted = fit(image) else { return nil }
        return encodePNG(shape(fitted).image)
    }

    /// The image to use: for a multi-image file (ICO) the largest, ties to the first (a GIF's
    /// frames are all one size, so that is its first frame).
    static func decode(_ data: Data) -> CGImage? {
        let options = [kCGImageSourceShouldCache: false] as CFDictionary
        guard let source = CGImageSourceCreateWithData(data as CFData, options),
              let type = CGImageSourceGetType(source) as String?,
              acceptedTypes.contains(type) else { return nil }
        let count = CGImageSourceGetCount(source)
        guard count > 0 else { return nil }
        var best: (index: Int, area: Int)?
        for index in 0..<count {
            guard let props = CGImageSourceCopyPropertiesAtIndex(source, index, nil) as? [CFString: Any],
                  let w = props[kCGImagePropertyPixelWidth] as? Int,
                  let h = props[kCGImagePropertyPixelHeight] as? Int else { continue }
            if best == nil || w * h > best!.area { best = (index, w * h) }
        }
        guard let image = CGImageSourceCreateImageAtIndex(source, best?.index ?? 0, nil),
              image.width >= minimumSide, image.height >= minimumSide else { return nil }
        return image
    }

    /// Fits `image` into a `box` × `box` transparent square: scaled to the smaller ratio,
    /// rounded to whole pixels and centred (offsets rounded down), as `reference.py` `fit`.
    static func fit(_ image: CGImage, box: Int = side) -> CGImage? {
        guard let context = makeContext(side: box) else { return nil }
        draw(image, into: context, box: box, originX: 0, originTop: 0)
        return context.makeImage()
    }

    // MARK: - Step 7: shape

    enum ShapeKind: String, Sendable { case bleed, edge, glyph }

    struct RGB: Equatable, Sendable { let r: UInt8, g: UInt8, b: UInt8 }

    /// A bounding box in pixel coordinates (top-left origin), `x1`/`y1` exclusive.
    struct Box: Equatable, Sendable { let x0: Int, y0: Int, x1: Int, y1: Int }

    struct Classification: Equatable, Sendable {
        let kind: ShapeKind
        /// The edge colour, for `.edge` only.
        let edge: RGB?
        /// The opaque pixels' bounding box; nil for `.bleed` and for an empty glyph.
        let box: Box?
    }

    static let circleRadius = 30.0
    static let solidShare = 0.95
    static let opaqueAlpha: UInt8 = 128
    static let edgeBox = 48
    static let glyphBox = 38
    static let glyphRadius = 8.0
    static let edgeTolerance = 24
    static let edgeShare = 0.90

    /// Classifies a fitted 64 × 64 icon. `rgba` is 64 × 64 × 4 bytes, rows top to bottom,
    /// straight (not premultiplied) alpha. Pure: thresholds and geometry as `reference.py`.
    static func classify(rgba: [UInt8]) -> Classification {
        precondition(rgba.count == side * side * 4)
        func alpha(_ x: Int, _ y: Int) -> UInt8 { rgba[(y * side + x) * 4 + 3] }
        func opaque(_ x: Int, _ y: Int) -> Bool { alpha(x, y) >= opaqueAlpha }

        var inside = 0, insideOpaque = 0
        var opaqueCount = 0
        var x0 = side, y0 = side, x1 = -1, y1 = -1
        for y in 0..<side {
            for x in 0..<side {
                let isOpaque = opaque(x, y)
                let dx = Double(x) + 0.5 - 32, dy = Double(y) + 0.5 - 32
                if dx * dx + dy * dy <= circleRadius * circleRadius {
                    inside += 1
                    if isOpaque { insideOpaque += 1 }
                }
                if isOpaque {
                    opaqueCount += 1
                    x0 = min(x0, x); x1 = max(x1, x); y0 = min(y0, y); y1 = max(y1, y)
                }
            }
        }
        if Double(insideOpaque) >= solidShare * Double(inside) {
            return Classification(kind: .bleed, edge: nil, box: nil)
        }
        guard opaqueCount > 0 else { return Classification(kind: .glyph, edge: nil, box: nil) }
        let box = Box(x0: x0, y0: y0, x1: x1 + 1, y1: y1 + 1)
        let w = x1 - x0 + 1, h = y1 - y0 + 1
        if w >= 16, h >= 16, Double(opaqueCount) >= solidShare * Double(w * h) {
            var ringCount = 0
            var reds: [Int] = [], greens: [Int] = [], blues: [Int] = []
            for y in y0...y1 {
                for x in x0...x1 {
                    let inset = min(x - x0, x1 - x, y - y0, y1 - y)
                    guard inset >= 3, inset <= 4 else { continue }
                    ringCount += 1
                    guard opaque(x, y) else { continue }
                    let i = (y * side + x) * 4
                    reds.append(Int(rgba[i])); greens.append(Int(rgba[i + 1])); blues.append(Int(rgba[i + 2]))
                }
            }
            let n = reds.count
            if n > 0, Double(n) >= edgeShare * Double(ringCount) {
                // Per channel, the value at index n / 2 of the sorted list.
                let mid = [reds.sorted()[n / 2], greens.sorted()[n / 2], blues.sorted()[n / 2]]
                var close = 0
                for k in 0..<n where max(abs(reds[k] - mid[0]), abs(greens[k] - mid[1]), abs(blues[k] - mid[2])) <= edgeTolerance {
                    close += 1
                }
                if Double(close) >= edgeShare * Double(n) {
                    let edge = RGB(r: UInt8(mid[0]), g: UInt8(mid[1]), b: UInt8(mid[2]))
                    return Classification(kind: .edge, edge: edge, box: box)
                }
            }
        }
        return Classification(kind: .glyph, edge: nil, box: box)
    }

    /// Step 7 on a fitted 64 × 64 icon: its kind, edge colour and the shaped image.
    static func shape(_ fitted: CGImage) -> (classification: Classification, image: CGImage) {
        guard let rgba = straightRGBA(fitted) else {
            return (Classification(kind: .bleed, edge: nil, box: nil), fitted)
        }
        let c = classify(rgba: rgba)
        if c.kind == .bleed { return (c, fitted) }
        guard let context = makeContext(side: side) else { return (c, fitted) }
        if let edge = c.edge {
            context.setFillColor(CGColor(
                srgbRed: CGFloat(edge.r) / 255, green: CGFloat(edge.g) / 255, blue: CGFloat(edge.b) / 255, alpha: 1
            ))
            context.fill(CGRect(x: 0, y: 0, width: side, height: side))
        }
        if let b = c.box,
           let crop = fitted.cropping(to: CGRect(x: b.x0, y: b.y0, width: b.x1 - b.x0, height: b.y1 - b.y0)) {
            let box = c.kind == .edge ? edgeBox : glyphBox
            let origin = (side - box) / 2
            context.saveGState()
            if c.kind == .glyph {
                // Corners cut to a radius of 8 so a hard square edge never shows.
                let rect = CGRect(x: origin, y: side - origin - box, width: box, height: box)
                context.addPath(CGPath(roundedRect: rect, cornerWidth: glyphRadius, cornerHeight: glyphRadius, transform: nil))
                context.clip()
            }
            draw(crop, into: context, box: box, originX: origin, originTop: origin)
            context.restoreGState()
        }
        return (c, context.makeImage() ?? fitted)
    }

    /// Whether a tile draws its 1pt `stroke` ring round this shaped 64 × 64 icon: only a
    /// glyph, told from the PNG alone by pixel (11, 11) being see-through (alpha < 128).
    /// Bleed and edge icons fill the circle there and get no ring (`docs/site-icons.md`).
    static func needsRing(_ shaped: CGImage) -> Bool {
        guard let rgba = straightRGBA(shaped) else { return true }
        return rgba[(11 * side + 11) * 4 + 3] < opaqueAlpha
    }

    /// `image` drawn into 64 × 64 as straight-alpha RGBA bytes, rows top to bottom.
    static func straightRGBA(_ image: CGImage) -> [UInt8]? {
        var bytes = [UInt8](repeating: 0, count: side * side * 4)
        let ok = bytes.withUnsafeMutableBytes { buffer -> Bool in
            guard let context = CGContext(
                data: buffer.baseAddress, width: side, height: side, bitsPerComponent: 8, bytesPerRow: side * 4,
                space: CGColorSpace(name: CGColorSpace.sRGB)!,
                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
            ) else { return false }
            context.draw(image, in: CGRect(x: 0, y: 0, width: side, height: side))
            return true
        }
        guard ok else { return nil }
        for i in stride(from: 0, to: bytes.count, by: 4) {
            let a = Int(bytes[i + 3])
            guard a > 0, a < 255 else { continue }
            for k in 0..<3 { bytes[i + k] = UInt8(min(255, (Int(bytes[i + k]) * 255 + a / 2) / a)) }
        }
        return bytes
    }

    private static func makeContext(side: Int) -> CGContext? {
        let context = CGContext(
            data: nil, width: side, height: side, bitsPerComponent: 8, bytesPerRow: 0,
            space: CGColorSpace(name: CGColorSpace.sRGB)!,
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        )
        context?.interpolationQuality = .high
        return context
    }

    /// Draws `image` fitted into the `box` square whose top-left corner is (`originX`,
    /// `originTop`) in top-left pixel coordinates.
    private static func draw(_ image: CGImage, into context: CGContext, box: Int, originX: Int, originTop: Int) {
        let scale = min(Double(box) / Double(image.width), Double(box) / Double(image.height))
        let w = max(1, Int((Double(image.width) * scale).rounded()))
        let h = max(1, Int((Double(image.height) * scale).rounded()))
        let left = originX + (box - w) / 2
        let top = originTop + (box - h) / 2
        context.draw(image, in: CGRect(x: left, y: context.height - top - h, width: w, height: h))
    }

    private static func encodePNG(_ image: CGImage?) -> Data? {
        guard let image else { return nil }
        let out = NSMutableData()
        guard let dest = CGImageDestinationCreateWithData(out, UTType.png.identifier as CFString, 1, nil) else { return nil }
        CGImageDestinationAddImage(dest, image, nil)
        return CGImageDestinationFinalize(dest) ? out as Data : nil
    }
}
