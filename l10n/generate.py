#!/usr/bin/env python3
"""Generate iOS .xcstrings, Android strings.xml and the desktop/extension JSON catalogs
from l10n/strings.json."""

from __future__ import annotations

import argparse
import json
import re
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
L10N = Path(__file__).resolve().parent
LANGS = ("en", "ar", "ckb")

IOS_MAIN = ROOT / "apps/ios/qasa_ios/Resources/Localizable.xcstrings"
IOS_CREDENTIAL = ROOT / "apps/ios/QasaCredentialProvider/Localizable.xcstrings"
IOS_PAYMENT = ROOT / "apps/ios/QasaPaymentNotification/Localizable.xcstrings"
ANDROID_RES = ROOT / "apps/android/app/src/main/res"
# Desktop app catalogs; the browser extension imports the same files.
WEB_DIR = ROOT / "apps/desktop/src/i18n"

CREDENTIAL_KEYS = [
    "auth.unlock.placeholder",
    "autofill.bank",
    "autofill.biometricFailed",
    "autofill.card",
    "autofill.cardNumber",
    "autofill.copied",
    "autofill.copy",
    "autofill.cvv",
    "autofill.decryptFailed",
    "autofill.expiresIn",
    "autofill.expiryDate",
    "autofill.nameOnCard",
    "autofill.noMatches",
    "autofill.noMatchesHint",
    "autofill.search",
    "autofill.title",
    "autofill.unlock",
    "autofill.unlockTitle",
    "autofill.unlocking",
    "autofill.useBiometrics",
    "autofill.wrongPassword",
    "common.cancel",
    "field.accountHolder",
    "field.accountNumber",
    "field.bankName",
    "field.expiryDate",
    "field.number",
    "field.routingNumber",
    "field.securityCode",
]

PAYMENT_KEYS = [
    "autofill.cardNumber",
    "autofill.copied",
    "autofill.copy",
    "autofill.cvv",
    "autofill.expiresIn",
    "autofill.expiryDate",
    "autofill.nameOnCard",
]

ANDROID_DIR = {
    "en": ANDROID_RES / "values",
    "ar": ANDROID_RES / "values-ar",
    "ckb": ANDROID_RES / "values-ckb",
}

_PLACEHOLDER_RE = re.compile(
    r"%(?P<pos>\d+\$)?(?P<spec>lld|ld|d|f|@|s)"
)


def load_json(path: Path) -> dict:
    if not path.is_file():
        return {}
    data = json.loads(path.read_text(encoding="utf-8"))
    return data if isinstance(data, dict) else {}


def merge_tables(*tables: dict) -> dict[str, dict[str, str]]:
    out: dict[str, dict[str, str]] = {}
    for table in tables:
        for key, row in table.items():
            if not isinstance(row, dict):
                continue
            cleaned = {lang: row[lang] for lang in LANGS if lang in row}
            if cleaned:
                # Optional explicit Android resource name (see android_name).
                if isinstance(row.get("android"), str):
                    cleaned["android"] = row["android"]
                out[key] = cleaned
    return out


def android_name(key: str, row: dict | None = None) -> str:
    """Android resource name for a key: the row's `"android"` override when it has one
    (for keys whose Android name cannot be derived, e.g. `home.allgoodBody` →
    `home_allgood_body`), otherwise derived (`auth.create.button` → `auth_create_button`)."""
    if row and isinstance(row.get("android"), str):
        return row["android"]
    if re.fullmatch(r"[a-z][a-z0-9_]*", key):
        return key
    name = key.replace(".", "_")
    name = re.sub(r"[^a-zA-Z0-9_]", "_", name)
    name = name.lower()
    if not name or name[0].isdigit():
        name = "s_" + name
    return name


def ios_to_android_placeholders(text: str) -> str:
    def repl(match: re.Match[str]) -> str:
        pos = match.group("pos") or ""
        spec = match.group("spec")
        if spec in ("@", "s"):
            mapped = "s"
        elif spec in ("lld", "ld", "d"):
            mapped = "d"
        else:
            mapped = spec
        return f"%{pos}{mapped}"

    return _PLACEHOLDER_RE.sub(repl, text)


def ios_to_web_placeholders(text: str) -> str:
    """`%@` / `%lld` → `{0}`, `{1}`, … in argument order; `%2$@` → `{1}`."""
    counter = 0

    def repl(match: re.Match[str]) -> str:
        nonlocal counter
        pos = match.group("pos")
        if pos:
            index = int(pos[:-1]) - 1
        else:
            index = counter
            counter += 1
        return "{" + str(index) + "}"

    return _PLACEHOLDER_RE.sub(repl, text)


def web_paths(dest_root: Path | None) -> list[Path]:
    base = WEB_DIR if dest_root is None else dest_root / WEB_DIR.relative_to(ROOT)
    return [base / f"{lang}.json" for lang in LANGS]


def write_web_json(table: dict[str, dict[str, str]], paths: list[Path]) -> None:
    for lang, path in zip(LANGS, paths):
        path.parent.mkdir(parents=True, exist_ok=True)
        catalog = {
            key: ios_to_web_placeholders(row[lang])
            for key, row in sorted(table.items())
            if lang in row
        }
        path.write_text(
            json.dumps(catalog, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )


def escape_android_xml(text: str) -> str:
    text = text.replace("\\", "\\\\")
    text = text.replace("'", r"\'")
    text = text.replace('"', r"\"")
    text = text.replace("&", "&amp;")
    text = text.replace("<", "&lt;")
    text = text.replace(">", "&gt;")
    text = text.replace("\n", r"\n")
    return text


def build_xcstrings(table: dict[str, dict[str, str]]) -> dict:
    strings: dict = {}
    for key in sorted(table):
        row = table[key]
        locs = {}
        for lang in LANGS:
            if lang not in row:
                continue
            locs[lang] = {
                "stringUnit": {
                    "state": "translated",
                    "value": row[lang],
                }
            }
        strings[key] = {"localizations": locs}
    return {
        "sourceLanguage": "en",
        "strings": strings,
        "version": "1.0",
    }


def write_xcstrings(path: Path, table: dict[str, dict[str, str]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    catalog = build_xcstrings(table)
    path.write_text(
        json.dumps(catalog, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


def subset(table: dict[str, dict[str, str]], keys: list[str]) -> dict:
    missing = [k for k in keys if k not in table]
    if missing:
        raise KeyError(f"missing shared keys for extension: {missing}")
    return {k: table[k] for k in keys}


def write_android_xml(
    table: dict[str, dict[str, str]],
    overlay: dict[str, dict[str, str]],
    out_dirs: dict[str, Path],
) -> None:
    merged = merge_tables(table, overlay)
    # Overlay wins on name collision after android_name transform.
    by_lang: dict[str, list[tuple[str, str]]] = {lang: [] for lang in LANGS}
    seen: dict[str, str] = {}
    for key in sorted(merged):
        name = android_name(key, merged[key])
        if name in seen and seen[name] != key:
            raise ValueError(f"Android name collision: {seen[name]} vs {key} → {name}")
        seen[name] = key
        row = merged[key]
        for lang in LANGS:
            if lang not in row:
                continue
            value = ios_to_android_placeholders(row[lang])
            by_lang[lang].append((name, escape_android_xml(value)))

    for lang, directory in out_dirs.items():
        directory.mkdir(parents=True, exist_ok=True)
        lines = [
            '<?xml version="1.0" encoding="utf-8"?>',
            "<!-- Generated by l10n/generate.py — do not edit. -->",
            "<resources>",
        ]
        for name, value in sorted(by_lang[lang], key=lambda x: x[0]):
            lines.append(f'    <string name="{name}">{value}</string>')
        lines.append("</resources>")
        lines.append("")
        (directory / "strings.xml").write_text("\n".join(lines), encoding="utf-8")


def generate_into(dest_root: Path | None = None) -> list[Path]:
    """Write all outputs. If dest_root is set, mirror under that root for --check."""
    shared = load_json(L10N / "strings.json")
    ios_overlay = load_json(L10N / "platform/ios.json")
    desktop_overlay = load_json(L10N / "platform/desktop.json")
    android_overlay = load_json(L10N / "platform/android.json")
    ios_table = merge_tables(shared, ios_overlay)

    def map_path(p: Path) -> Path:
        if dest_root is None:
            return p
        return dest_root / p.relative_to(ROOT)

    written: list[Path] = []

    main_path = map_path(IOS_MAIN)
    write_xcstrings(main_path, ios_table)
    written.append(main_path)

    cred_path = map_path(IOS_CREDENTIAL)
    write_xcstrings(cred_path, subset(ios_table, CREDENTIAL_KEYS))
    written.append(cred_path)

    pay_path = map_path(IOS_PAYMENT)
    write_xcstrings(pay_path, subset(ios_table, PAYMENT_KEYS))
    written.append(pay_path)

    if dest_root is None:
        android_dirs = ANDROID_DIR
    else:
        android_dirs = {
            lang: dest_root / path.relative_to(ROOT) for lang, path in ANDROID_DIR.items()
        }
    write_android_xml(shared, android_overlay, android_dirs)
    for lang, directory in android_dirs.items():
        written.append(directory / "strings.xml")

    paths = web_paths(dest_root)
    write_web_json(merge_tables(shared, desktop_overlay), paths)
    written.extend(paths)

    return written


def paths_equal(a: Path, b: Path) -> bool:
    if not a.is_file() or not b.is_file():
        return False
    return a.read_bytes() == b.read_bytes()


def check_drift() -> int:
    with tempfile.TemporaryDirectory(prefix="qasa-l10n-") as tmp:
        tmp_root = Path(tmp)
        generate_into(tmp_root)
        targets = [
            IOS_MAIN,
            IOS_CREDENTIAL,
            IOS_PAYMENT,
            *[d / "strings.xml" for d in ANDROID_DIR.values()],
            *web_paths(None),
        ]
        stale = []
        for path in targets:
            candidate = tmp_root / path.relative_to(ROOT)
            if not paths_equal(path, candidate):
                stale.append(path.relative_to(ROOT))
        if stale:
            print("l10n outputs are stale; run: python3 l10n/generate.py", file=sys.stderr)
            for rel in stale:
                print(f"  {rel}", file=sys.stderr)
            return 1
    print("l10n outputs up to date")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--check",
        action="store_true",
        help="exit 1 if generated outputs would change",
    )
    args = parser.parse_args()
    if args.check:
        return check_drift()
    written = generate_into()
    print(f"generated {len(written)} files")
    for path in written:
        print(f"  {path.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
