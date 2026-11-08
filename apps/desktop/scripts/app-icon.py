#!/usr/bin/env python3
"""Makes the desktop app icon from the iOS one (apps/ios/.../AppIcon.png, a full-bleed
1024 square). macOS and Windows draw no mask of their own, so the desktop icon carries
it: Apple's 1024 grid puts an 824 px rounded square (radius ~185) in the middle with a
soft shadow under it. Writes src-tauri/icons/app-icon.png; then run
`bunx tauri icon src-tauri/icons/app-icon.png` (see `bun run icons`)."""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[3]
SOURCE = ROOT / "apps/ios/qasa_ios/Assets.xcassets/AppIcon.appiconset/AppIcon.png"
OUT = ROOT / "apps/desktop/src-tauri/icons/app-icon.png"
CANVAS, BODY, RADIUS = 1024, 824, 185


def main() -> None:
    art = Image.open(SOURCE).convert("RGBA").resize((BODY, BODY), Image.LANCZOS)
    mask = Image.new("L", (BODY, BODY), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, BODY - 1, BODY - 1), RADIUS, fill=255)
    offset = (CANVAS - BODY) // 2
    shadow = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    shadow_mask = Image.new("L", (CANVAS, CANVAS), 0)
    ImageDraw.Draw(shadow_mask).rounded_rectangle(
        (offset, offset + 12, offset + BODY, offset + BODY + 12), RADIUS, fill=90
    )
    shadow.putalpha(shadow_mask.filter(ImageFilter.GaussianBlur(14)))
    icon = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    icon.alpha_composite(shadow)
    icon.paste(art, (offset, offset), mask)
    icon.save(OUT)
    print(f"wrote {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
