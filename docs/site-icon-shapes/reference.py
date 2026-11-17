#!/usr/bin/env python3
"""Reference for docs/site-icons.md steps 6-7 (fit, then shape). Apps don't run this; it
checks the expected kinds in docs/site-icons-vectors.json and writes out/<name>.png so the
results can be looked at. Usage: python3 -I reference.py"""
import json
from pathlib import Path
from PIL import Image

HERE = Path(__file__).resolve().parent
SIDE, CIRCLE_R, SOLID, ALPHA = 64, 30, 0.95, 128
EDGE_BOX, GLYPH_BOX, GLYPH_RADIUS, EDGE_TOLERANCE, EDGE_SHARE = 48, 38, 8, 24, 0.90

def fit(src, box=SIDE):
    src = src.convert("RGBA")
    s = min(box / src.width, box / src.height)
    w, h = max(1, round(src.width * s)), max(1, round(src.height * s))
    out = Image.new("RGBA", (box, box), (0, 0, 0, 0))
    out.paste(src.resize((w, h), Image.LANCZOS), ((box - w) // 2, (box - h) // 2))
    return out

def classify(im):
    px = im.load()
    inside = [(x, y) for y in range(SIDE) for x in range(SIDE) if (x + .5 - 32) ** 2 + (y + .5 - 32) ** 2 <= CIRCLE_R ** 2]
    if sum(px[p][3] >= ALPHA for p in inside) >= SOLID * len(inside):
        return "bleed", None, None
    opaque = [(x, y) for y in range(SIDE) for x in range(SIDE) if px[x, y][3] >= ALPHA]
    if not opaque:
        return "glyph", None, None
    x0, x1 = min(p[0] for p in opaque), max(p[0] for p in opaque)
    y0, y1 = min(p[1] for p in opaque), max(p[1] for p in opaque)
    bbox = (x0, y0, x1 + 1, y1 + 1)
    area = (x1 - x0 + 1) * (y1 - y0 + 1)
    if x1 - x0 + 1 >= 16 and y1 - y0 + 1 >= 16 and len(opaque) >= SOLID * area:
        ring = [(x, y) for y in range(y0, y1 + 1) for x in range(x0, x1 + 1)
                if 3 <= min(x - x0, x1 - x, y - y0, y1 - y) <= 4]
        rgb = [px[p][:3] for p in ring if px[p][3] >= ALPHA]
        if len(rgb) >= EDGE_SHARE * len(ring):
            # Per channel, the value at index n / 2 of the sorted list (anti-aliased rims don't move it).
            mean = tuple(sorted(c[i] for c in rgb)[len(rgb) // 2] for i in range(3))
            close = sum(max(abs(c[i] - mean[i]) for i in range(3)) <= EDGE_TOLERANCE for c in rgb)
            if close >= EDGE_SHARE * len(rgb):
                return "edge", mean, bbox
    return "glyph", None, bbox

def shape(im):
    kind, edge, bbox = classify(im)
    if kind == "bleed":
        return kind, edge, im
    out = Image.new("RGBA", (SIDE, SIDE), (*edge, 255) if kind == "edge" else (0, 0, 0, 0))
    if bbox is None:
        return kind, edge, out
    box = EDGE_BOX if kind == "edge" else GLYPH_BOX
    content = fit(im.crop(bbox), box)
    if kind == "glyph":
        mask = Image.new("L", (box, box), 0)
        from PIL import ImageDraw
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, box - 1, box - 1], radius=GLYPH_RADIUS, fill=255)
        content.putalpha(Image.composite(content.getchannel("A"), Image.new("L", (box, box), 0), mask))
    out.alpha_composite(content, ((SIDE - box) // 2, (SIDE - box) // 2))
    return kind, edge, out

if __name__ == "__main__":
    vectors = json.loads((HERE.parent / "site-icons-vectors.json").read_text()).get("shapes", [])
    want = {v["file"]: v for v in vectors}
    (HERE / "out").mkdir(exist_ok=True)
    bad = 0
    for path in sorted(HERE.glob("*.png")):
        kind, edge, out = shape(fit(Image.open(path)))
        out.save(HERE / "out" / path.name)
        hexed = "#%02X%02X%02X" % edge if edge else None
        v = want.get(path.name)
        ok = v is None or (v["kind"] == kind and v.get("edge") == hexed)
        bad += not ok
        print(("ok  " if ok else "BAD ") + f"{path.name}: {kind} {hexed or ''}")
    raise SystemExit(bad)
