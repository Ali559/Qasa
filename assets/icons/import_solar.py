#!/usr/bin/env python3
"""Copy the icons listed in icons.json into svg/, from the Solar icon set.

    npm pack @iconify-json/solar @tabler/icons   # in a scratch folder, then extract both
    python3 assets/icons/import_solar.py <solar icons.json> <tabler icons/outline folder>

Writes svg/<name>.svg (Solar Linear, 24x24) and svg/<name>.fill.svg (Solar Bold) for icons
marked "fill" (the navigation tabs' active state). Custom icons: fingerprint and Wi-Fi come
from Tabler (MIT) redrawn at Solar's 1.5 line; the chevrons are the iOS SF Symbol shapes
measured in their own frames. The SVGs are committed, so generate.py needs no network.
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
OUT = HERE / "svg"

SVG = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="{vb}">{body}</svg>\n'

# Measured from SF Symbols chevron.backward 17 semibold / chevron.forward 13 semibold.
CHEVRONS = {
    "chevron-back": ("0 0 13 18", '<path fill="none" stroke="currentColor" stroke-width="2.32" '
                     'stroke-linecap="round" stroke-linejoin="round" d="M8.79 2.26L2.16 9L8.79 15.74"/>'),
    "chevron-forward": ("0 0 11 14", '<path fill="none" stroke="currentColor" stroke-width="1.81" '
                        'stroke-linecap="round" stroke-linejoin="round" d="M3.11 1.91L8.9 6.975L3.11 12.04"/>'),
    # chevron.down 13: the forward chevron turned a quarter, in its 14x11 frame.
    "chevron-down": ("0 0 14 11", '<path fill="none" stroke="currentColor" stroke-width="1.81" '
                     'stroke-linecap="round" stroke-linejoin="round" d="M1.91 2.1L6.975 7.89L12.04 2.1"/>'),
    # A plain ring (an unselected radio), Solar's 1.5 line on the check-circle's circle.
    "circle": ("0 0 24 24", '<circle cx="12" cy="12" r="10" fill="none" stroke="currentColor" stroke-width="1.5"/>'),
}


def tabler(folder: Path, name: str) -> str:
    """A Tabler outline icon's shapes at Solar's 1.5 stroke, round caps and joins."""
    text = (folder / f"{name}.svg").read_text()
    text = re.sub(r"<!--.*?-->", "", text, flags=re.S)
    shapes = re.findall(r"<(?:path|circle|line|polyline|rect|ellipse)\b[^>]*/>", text)
    shapes = [s for s in shapes if 'stroke="none"' not in s]
    body = "".join(re.sub(r"\s+", " ", s).replace(" />", "/>") for s in shapes)
    return ('<g fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" '
            f'stroke-linejoin="round">{body}</g>')


def simplify(body: str) -> str:
    """Rewrite what Android vectors can't draw into what they can.

    Solar draws QR code's small squares as a thick stroke clipped by a mask of the same
    shape: that is just the shape filled. A rotate() on a circle changes nothing.
    """
    body = re.sub(r"<mask\b.*?</mask>", "", body, flags=re.S)

    def masked(m: re.Match) -> str:
        tag = m.group(0)
        tag = re.sub(r'\s(mask|stroke-width)="[^"]*"', "", tag)
        return tag.replace("<path", '<path fill="currentColor" stroke="none"', 1)

    body = re.sub(r'<path\b[^>]*mask="url\(#[^)]+\)"[^>]*/>', masked, body)
    body = re.sub(r'(<circle\b[^>]*?)\s+transform="rotate\([^)]*\)"', r"\1", body)
    return body


def main() -> int:
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    solar = json.loads(Path(sys.argv[1]).read_text())["icons"]
    tabler_dir = Path(sys.argv[2])
    manifest = json.loads((HERE / "icons.json").read_text())["icons"]
    OUT.mkdir(exist_ok=True)
    for old in OUT.glob("*.svg"):
        old.unlink()
    for name, spec in manifest.items():
        if "solar" in spec:
            key = f"{spec['solar']}-linear"
            if key not in solar:
                sys.exit(f"{name}: no Solar icon {key}")
            (OUT / f"{name}.svg").write_text(SVG.format(vb="0 0 24 24", body=simplify(solar[key]["body"])))
            if spec.get("fill"):
                bold = f"{spec['solar']}-bold"
                if bold not in solar:
                    sys.exit(f"{name}: no Solar icon {bold}")
                (OUT / f"{name}.fill.svg").write_text(SVG.format(vb="0 0 24 24", body=simplify(solar[bold]["body"])))
        elif spec.get("custom") in CHEVRONS:
            vb, body = CHEVRONS[spec["custom"]]
            (OUT / f"{name}.svg").write_text(SVG.format(vb=vb, body=body))
        elif spec.get("custom"):
            (OUT / f"{name}.svg").write_text(SVG.format(vb="0 0 24 24", body=tabler(tabler_dir, spec["custom"])))
        else:
            sys.exit(f"{name}: needs 'solar' or 'custom'")
    print(f"wrote {len(list(OUT.glob('*.svg')))} SVGs to {OUT.relative_to(HERE.parent.parent)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
