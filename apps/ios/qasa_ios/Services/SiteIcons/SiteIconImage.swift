import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers

/// Decoding a downloaded icon and normalising it to the cached form (`docs/site-icons.md`
/// steps 5–6): PNG, ICO (its largest image), JPEG, GIF (first frame) or WebP; anything under
/// 16 × 16 is rejected; the result is fitted into 64 × 64 (aspect kept, centred, transparent
/// padding) and encoded as PNG.
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
        return encodePNG(fit(image))
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

    private static func fit(_ image: CGImage) -> CGImage? {
        guard let context = CGContext(
            data: nil, width: side, height: side, bitsPerComponent: 8, bytesPerRow: 0,
            space: CGColorSpace(name: CGColorSpace.sRGB)!,
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        ) else { return nil }
        context.interpolationQuality = .high
        let scale = min(Double(side) / Double(image.width), Double(side) / Double(image.height))
        let w = Double(image.width) * scale
        let h = Double(image.height) * scale
        context.draw(image, in: CGRect(x: (Double(side) - w) / 2, y: (Double(side) - h) / 2, width: w, height: h))
        return context.makeImage()
    }

    private static func encodePNG(_ image: CGImage?) -> Data? {
        guard let image else { return nil }
        let out = NSMutableData()
        guard let dest = CGImageDestinationCreateWithData(out, UTType.png.identifier as CFString, 1, nil) else { return nil }
        CGImageDestinationAddImage(dest, image, nil)
        return CGImageDestinationFinalize(dest) ? out as Data : nil
    }
}
