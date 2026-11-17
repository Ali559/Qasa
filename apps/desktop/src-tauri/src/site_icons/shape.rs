//! Steps 6–7 of docs/site-icons.md: fit into 64 × 64, then shape for the circle. A port of
//! `docs/site-icon-shapes/reference.py` (same thresholds and geometry); pure functions
//! over RGBA images.

use image::imageops::FilterType;
use image::{Rgba, RgbaImage};

/// The side of every cached icon, in pixels.
pub const SIDE: u32 = 64;
const CIRCLE_R: f64 = 30.0;
const SOLID: f64 = 0.95;
const ALPHA: u8 = 128;
const EDGE_BOX: u32 = 48;
const GLYPH_BOX: u32 = 38;
const GLYPH_RADIUS: u32 = 8;
const EDGE_TOLERANCE: i32 = 24;
const EDGE_SHARE: f64 = 0.90;

/// What the shape step made of an icon.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Kind {
    /// Solid inside its circle: kept as it is.
    Bleed,
    /// A solid logo that isn't square: drawn on its own edge colour.
    Edge,
    /// Anything else: a small rounded glyph on a transparent canvas.
    Glyph,
}

/// Fits `src` into a `side` × `side` canvas: aspect kept, centred, transparent padding.
pub fn fit(src: &RgbaImage, side: u32) -> RgbaImage {
    let (sw, sh) = src.dimensions();
    let s = (f64::from(side) / f64::from(sw)).min(f64::from(side) / f64::from(sh));
    // Python's round() rounds halves to even.
    let w = ((f64::from(sw) * s).round_ties_even() as u32).clamp(1, side);
    let h = ((f64::from(sh) * s).round_ties_even() as u32).clamp(1, side);
    let resized = image::imageops::resize(src, w, h, FilterType::Lanczos3);
    let mut out = RgbaImage::new(side, side);
    image::imageops::replace(&mut out, &resized, i64::from((side - w) / 2), i64::from((side - h) / 2));
    out
}

fn opaque(p: &Rgba<u8>) -> bool {
    p[3] >= ALPHA
}

/// The kind, the edge colour (for `Kind::Edge`) and the opaque pixels' bounding box
/// `(x0, y0, x1, y1)`, inclusive (None when no pixel is opaque, or for `Kind::Bleed`).
pub fn classify(im: &RgbaImage) -> (Kind, Option<[u8; 3]>, Option<(u32, u32, u32, u32)>) {
    let centre = f64::from(SIDE) / 2.0;
    let (mut inside, mut solid) = (0usize, 0usize);
    for (x, y, p) in im.enumerate_pixels() {
        let (dx, dy) = (f64::from(x) + 0.5 - centre, f64::from(y) + 0.5 - centre);
        if dx * dx + dy * dy <= CIRCLE_R * CIRCLE_R {
            inside += 1;
            solid += usize::from(opaque(p));
        }
    }
    if solid as f64 >= SOLID * inside as f64 {
        return (Kind::Bleed, None, None);
    }

    let mut count = 0usize;
    let (mut x0, mut y0, mut x1, mut y1) = (u32::MAX, u32::MAX, 0, 0);
    for (x, y, p) in im.enumerate_pixels() {
        if opaque(p) {
            count += 1;
            x0 = x0.min(x);
            y0 = y0.min(y);
            x1 = x1.max(x);
            y1 = y1.max(y);
        }
    }
    if count == 0 {
        return (Kind::Glyph, None, None);
    }
    let bbox = Some((x0, y0, x1, y1));
    let (w, h) = (x1 - x0 + 1, y1 - y0 + 1);
    if w >= 16 && h >= 16 && count as f64 >= SOLID * f64::from(w * h) {
        let mut ring = 0usize;
        let mut rgb: Vec<[u8; 3]> = Vec::new();
        for y in y0..=y1 {
            for x in x0..=x1 {
                let inset = (x - x0).min(x1 - x).min(y - y0).min(y1 - y);
                if (3..=4).contains(&inset) {
                    ring += 1;
                    let p = im.get_pixel(x, y);
                    if opaque(p) {
                        rgb.push([p[0], p[1], p[2]]);
                    }
                }
            }
        }
        if !rgb.is_empty() && rgb.len() as f64 >= EDGE_SHARE * ring as f64 {
            // Per channel, the value at index n / 2 of the sorted list (anti-aliased rims don't move it).
            let mut mean = [0u8; 3];
            for (i, m) in mean.iter_mut().enumerate() {
                let mut channel: Vec<u8> = rgb.iter().map(|c| c[i]).collect();
                channel.sort_unstable();
                *m = channel[channel.len() / 2];
            }
            let close = rgb
                .iter()
                .filter(|c| (0..3).all(|i| (i32::from(c[i]) - i32::from(mean[i])).abs() <= EDGE_TOLERANCE))
                .count();
            if close as f64 >= EDGE_SHARE * rgb.len() as f64 {
                return (Kind::Edge, Some(mean), bbox);
            }
        }
    }
    (Kind::Glyph, None, bbox)
}

/// Whether `(x, y)` of a `side` square is inside its corners cut to `GLYPH_RADIUS` (matches
/// Pillow's `rounded_rectangle([0, 0, side - 1, side - 1], radius=8)` pixel for pixel).
fn in_rounded_square(x: u32, y: u32, side: u32) -> bool {
    let r = i64::from(GLYPH_RADIUS);
    let cx = i64::from(x.min(side - 1 - x));
    let cy = i64::from(y.min(side - 1 - y));
    if cx >= r || cy >= r {
        return true;
    }
    let (dx, dy) = (cx - r, cy - r);
    (dx * dx + dy * dy) as f64 <= 70.56
}

/// Straight-alpha "over": `top` composited onto `bottom` at `(ox, oy)`.
fn composite(bottom: &mut RgbaImage, top: &RgbaImage, ox: u32, oy: u32) {
    for (x, y, t) in top.enumerate_pixels() {
        let b = bottom.get_pixel_mut(ox + x, oy + y);
        let ta = f64::from(t[3]) / 255.0;
        let ba = f64::from(b[3]) / 255.0;
        let oa = ta + ba * (1.0 - ta);
        if oa <= 0.0 {
            *b = Rgba([0, 0, 0, 0]);
            continue;
        }
        let mut px = [0u8; 4];
        for i in 0..3 {
            let c = (f64::from(t[i]) * ta + f64::from(b[i]) * ba * (1.0 - ta)) / oa;
            px[i] = c.round().clamp(0.0, 255.0) as u8;
        }
        px[3] = (oa * 255.0).round().clamp(0.0, 255.0) as u8;
        *b = Rgba(px);
    }
}

/// Shapes a fitted 64 × 64 icon for its circle (step 7).
pub fn shape(im: &RgbaImage) -> (Kind, Option<[u8; 3]>, RgbaImage) {
    let (kind, edge, bbox) = classify(im);
    if kind == Kind::Bleed {
        return (kind, edge, im.clone());
    }
    let fill = match edge {
        Some([r, g, b]) if kind == Kind::Edge => Rgba([r, g, b, 255]),
        _ => Rgba([0, 0, 0, 0]),
    };
    let mut out = RgbaImage::from_pixel(SIDE, SIDE, fill);
    let Some((x0, y0, x1, y1)) = bbox else { return (kind, edge, out) };
    let side = if kind == Kind::Edge { EDGE_BOX } else { GLYPH_BOX };
    let crop = image::imageops::crop_imm(im, x0, y0, x1 - x0 + 1, y1 - y0 + 1).to_image();
    let mut content = fit(&crop, side);
    if kind == Kind::Glyph {
        for (x, y, p) in content.enumerate_pixels_mut() {
            if !in_rounded_square(x, y, side) {
                p[3] = 0;
            }
        }
    }
    let at = (SIDE - side) / 2;
    composite(&mut out, &content, at, at);
    (kind, edge, out)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn an_empty_canvas_is_an_empty_glyph() {
        let (kind, edge, out) = shape(&RgbaImage::new(SIDE, SIDE));
        assert_eq!((kind, edge), (Kind::Glyph, None));
        assert!(out.pixels().all(|p| p[3] == 0));
    }

    #[test]
    fn a_glyph_has_its_corners_cut() {
        let mut im = RgbaImage::new(SIDE, SIDE);
        // A hollow square: solid border, see-through middle.
        for (x, y, p) in im.enumerate_pixels_mut() {
            if (10..54).contains(&x) && (10..54).contains(&y) && !((14..50).contains(&x) && (14..50).contains(&y)) {
                *p = Rgba([200, 0, 0, 255]);
            }
        }
        let (kind, _, out) = shape(&im);
        assert_eq!(kind, Kind::Glyph);
        assert_eq!(out.get_pixel(13, 13)[3], 0, "the corner is cut");
        assert!(out.get_pixel(32, 14)[3] >= 128, "the edge stays");
        assert_eq!(out.get_pixel(5, 32)[3], 0, "outside the 38 box");
    }
}
