#!/usr/bin/env python3
"""One-shot / re-import: Localizable.xcstrings → l10n/strings.json."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "qasa/qasa_ios/Resources/Localizable.xcstrings"
DEST = Path(__file__).resolve().parent / "strings.json"
KEYISH = re.compile(r"^[a-z][a-zA-Z0-9]*(\.[a-z][a-zA-Z0-9]*)+$")
LANGS = ("en", "ar", "ckb")


def extract(catalog: dict) -> dict[str, dict[str, str]]:
    out: dict[str, dict[str, str]] = {}
    for key, entry in catalog.get("strings", {}).items():
        if not key or not KEYISH.match(key):
            continue
        locs = entry.get("localizations") or {}
        row: dict[str, str] = {}
        for lang in LANGS:
            unit = (locs.get(lang) or {}).get("stringUnit") or {}
            val = unit.get("value")
            if isinstance(val, str):
                row[lang] = val
        if set(row) >= set(LANGS):
            out[key] = row
    return out


def main() -> int:
    if not SRC.is_file():
        print(f"missing {SRC}", file=sys.stderr)
        return 1
    catalog = json.loads(SRC.read_text(encoding="utf-8"))
    strings = extract(catalog)
    ordered = {k: strings[k] for k in sorted(strings)}
    DEST.write_text(
        json.dumps(ordered, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"wrote {len(ordered)} keys → {DEST.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
