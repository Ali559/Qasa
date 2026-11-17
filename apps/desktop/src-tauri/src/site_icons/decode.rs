//! Decodes a downloaded icon and normalises it (docs/site-icons.md steps 5–7): PNG, ICO
//! (its largest image), JPEG, GIF (first frame) or WebP, at least 16 × 16, fitted into
//! 64 × 64 (aspect kept, centred, transparent padding), shaped for its circle (`shape`)
//! and encoded as PNG.

use std::io::Cursor;

use image::{DynamicImage, ImageFormat, ImageReader, Limits, RgbaImage};

pub use super::shape::SIDE;
use super::shape::{fit, shape};

/// Smaller images are rejected (a 1 × 1 tracking pixel, a broken favicon).
pub const MIN_SIDE: u32 = 16;

/// The normalised, shaped 64 × 64 PNG, or None when `bytes` isn't a usable image.
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
    encode(&shape(&fit(&image.to_rgba8(), SIDE)).2)
}

/// Shapes a cached PNG written before step 7 existed (docs/site-icons.md "Cache"). No
/// network: the PNG is already fitted (it is fitted again if it isn't 64 × 64).
pub fn reshape(png: &[u8]) -> Option<Vec<u8>> {
    let image = image::load_from_memory_with_format(png, ImageFormat::Png).ok()?.to_rgba8();
    let fitted = if image.dimensions() == (SIDE, SIDE) { image } else { fit(&image, SIDE) };
    encode(&shape(&fitted).2)
}

fn encode(image: &RgbaImage) -> Option<Vec<u8>> {
    let mut out = Vec::new();
    DynamicImage::ImageRgba8(image.clone()).write_to(&mut Cursor::new(&mut out), ImageFormat::Png).ok()?;
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
        let src = image::load_from_memory(&png(128, 64)).unwrap().to_rgba8();
        let out = fit(&src, SIDE);
        assert_eq!(out.dimensions(), (64, 64));
        // 64 × 32, centred: transparent above and below.
        assert_eq!(out.get_pixel(32, 4)[3], 0);
        assert_eq!(out.get_pixel(32, 32)[3], 255);
        assert_eq!(out.get_pixel(32, 60)[3], 0);
    }

    #[test]
    fn a_wide_solid_logo_is_saved_on_its_edge_colour() {
        let out = image::load_from_memory(&normalise(&png(128, 64)).unwrap()).unwrap().to_rgba8();
        assert_eq!(out.dimensions(), (64, 64));
        assert_eq!(*out.get_pixel(32, 2), image::Rgba([200, 10, 10, 255]));
        assert_eq!(*out.get_pixel(32, 32), image::Rgba([200, 10, 10, 255]));
    }

    #[test]
    fn rejects_tiny_images_and_junk() {
        assert!(normalise(&png(15, 32)).is_none());
        assert!(normalise(&png(16, 16)).is_some());
        assert!(normalise(b"<html>not an image</html>").is_none());
    }
}
