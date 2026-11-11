#!/usr/bin/env python3
"""Fail when app code breaks a design-system or language rule from design/README.md.

Each rule is a pattern that must not appear in the listed app sources. Run from anywhere:
`python3 design/check.py`. CI runs it next to `design/generate.py --check`.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
IOS = ROOT / "apps/ios/qasa_ios"

# (glob root, file suffix, pattern, why)
RULES: list[tuple[Path, str, re.Pattern[str], str]] = [
    (
        IOS,
        ".swift",
        re.compile(r"ToolbarItem\(placement:\s*\.(cancellationAction|confirmationAction)\)"),
        "sheets have no Cancel/Done in the header: use .sheetHeader(title) (rule 4)",
    ),
    (
        IOS,
        ".swift",
        re.compile(r"\.presentationCornerRadius\(\s*\d"),
        "sheet corners come from QasaTokens.Radius.sheet (qasaSheetPresentation)",
    ),
    (
        IOS,
        ".swift",
        re.compile(r"\b\w*[eE]rror\.localizedDescription"),
        "system errors arrive in the device's language: use L10n.message(for:) (rule 7)",
    ),
    (
        IOS,
        ".swift",
        re.compile(r'UserDefaults\.standard\.string\(forKey:\s*"qasa\.language"\)'),
        "read the language through AppPreferences / AppLanguage.current so views update (rule 7)",
    ),
]

# Files allowed to break a rule, with the reason.
ALLOW = {
    IOS / "Services/L10n.swift": "defines L10n.message(for:)",
    IOS / "Services/Sync/QasaFFI.swift": "generated UniFFI bindings",
}


def main() -> int:
    failures = []
    for root, suffix, pattern, why in RULES:
        for path in sorted(root.rglob(f"*{suffix}")):
            if path in ALLOW:
                continue
            for n, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
                if pattern.search(line):
                    failures.append(f"{path.relative_to(ROOT)}:{n}: {why}\n    {line.strip()}")
    if failures:
        print("\n".join(failures), file=sys.stderr)
        print(f"\n{len(failures)} design/language rule violation(s)", file=sys.stderr)
        return 1
    print("design rules ok")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
