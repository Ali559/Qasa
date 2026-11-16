//! Decodes a downloaded icon and normalises it (docs/site-icons.md steps 5–6): PNG, ICO
//! (its largest image), JPEG, GIF (first frame) or WebP, at least 16 × 16, fitted into
//! 64 × 64 (aspect kept, centred, transparent padding) and encoded as PNG.

use std::io::Cursor;

use image::imageops::FilterType;
use image::{DynamicImage, ImageFormat, ImageReader, Limits, RgbaImage};

/// The side of every cached icon, in pixels.
pub const SIDE: u32 = 64;
/// Smaller images are rejected (a 1 × 1 tracking pixel, a broken favicon).
pub const MIN_SIDE: u32 = 16;

/// The normalised 64 × 64 PNG, or None when `bytes` isn't a usable image.
pub fn normalise(bytes: &[u8]) -> Option<Vec<u8>> {
    let mut reader = ImageReader::new(Cursor::new(bytes)).with_guessed_format().ok()?;
    if !matches!(reader.format()?, ImageFormat::Png | ImageFormat::Ico | ImageFormat::Jpeg | ImageFormat::Gif | ImageFormat::WebP) {
        return None;
    }
    // A 256 KB file can still claim a huge canvas: cap what decoding may allocate.
    let mut limits = Limits::default();
    limits.max_image_width = Some(4096);
    limits.max_image_height = Some(4096);
    limits.max_alloc = Some(64 * 1024 * 1024);
    reader.limits(limits);
    let image = reader.decode().ok()?;
    if image.width() < MIN_SIDE || image.height() < MIN_SIDE {
        return None;
    }
    let fitted = image.resize(SIDE, SIDE, FilterType::Lanczos3).to_rgba8();
    let mut canvas = RgbaImage::new(SIDE, SIDE);
    let x = (SIDE - fitted.width()) / 2;
    let y = (SIDE - fitted.height()) / 2;
    image::imageops::overlay(&mut canvas, &fitted, i64::from(x), i64::from(y));
    let mut out = Vec::new();
    DynamicImage::ImageRgba8(canvas).write_to(&mut Cursor::new(&mut out), ImageFormat::Png).ok()?;
    Some(out)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn png(w: u32, h: u32) -> Vec<u8> {
        let img = RgbaImage::from_pixel(w, h, image::Rgba([200, 10, 10, 255]));
        let mut out = Vec::new();
        DynamicImage::ImageRgba8(img).write_to(&mut Cursor::new(&mut out), ImageFormat::Png).unwrap();
        out
    }

    #[test]
    fn fits_into_64_keeping_the_aspect() {
        let out = image::load_from_memory(&normalise(&png(128, 64)).unwrap()).unwrap().to_rgba8();
        assert_eq!(out.dimensions(), (64, 64));
        // 64 × 32, centred: transparent above and below.
        assert_eq!(out.get_pixel(32, 4)[3], 0);
        assert_eq!(out.get_pixel(32, 32)[3], 255);
        assert_eq!(out.get_pixel(32, 60)[3], 0);
    }

    #[test]
    fn rejects_tiny_images_and_junk() {
        assert!(normalise(&png(15, 32)).is_none());
        assert!(normalise(&png(16, 16)).is_some());
        assert!(normalise(b"<html>not an image</html>").is_none());
    }
}
