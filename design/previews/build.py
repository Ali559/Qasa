#!/usr/bin/env python3
"""Build every <name>.html preview here from <name>.src.html, inlining the shared icons
(assets/icons/svg) where the source says /*ICONS*/ and the site marks
(site-marks.json) where it says /*SITES*/."""
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
SVG = HERE.parent.parent / "assets" / "icons" / "svg"

icons = {p.stem: p.read_text().strip() for p in sorted(SVG.glob("*.svg"))}
sites = json.loads((HERE / "site-marks.json").read_text())["sites"]
for src in sorted(HERE.glob("*.src.html")):
    out = HERE / src.name.replace(".src.html", ".html")
    out.write_text(src.read_text().replace("/*ICONS*/", json.dumps(icons)).replace("/*SITES*/", json.dumps(sites)))
    print("wrote", out.name)
