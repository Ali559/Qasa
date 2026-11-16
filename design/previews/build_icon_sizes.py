#!/usr/bin/env python3
"""Build icon-sizes.html from icon-sizes.src.html, inlining the icons it uses from assets/icons/svg."""
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
SVG = HERE.parent.parent / "assets" / "icons" / "svg"
NAMES = ["search", "card", "note", "login", "copy", "more-vertical", "add", "you", "alerts",
         "passwords.fill", "home", "generate", "health", "auto-lock", "shield", "vpn", "backup",
         "email-monitor"]

icons = {n: (SVG / f"{n}.svg").read_text().strip() for n in NAMES}
src = (HERE / "icon-sizes.src.html").read_text()
(HERE / "icon-sizes.html").write_text(src.replace("/*ICONS*/", json.dumps(icons)))
print("wrote icon-sizes.html")
