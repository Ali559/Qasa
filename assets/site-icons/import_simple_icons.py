#!/usr/bin/env python3
"""Refresh the title, colour and path of each site in sites.json from a Simple Icons
package, then rewrite sites.json. Only the fields Simple Icons owns are replaced; `id`,
`slug` and `domains` stay as written.

    npm pack simple-icons@16.37.0 && tar xzf simple-icons-16.37.0.tgz
    python3 assets/site-icons/import_simple_icons.py package/
    python3 assets/site-icons/generate.py
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
SITES = HERE / "sites.json"


def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__, file=sys.stderr)
        return 2
    package = Path(sys.argv[1])
    version = json.loads((package / "package.json").read_text())["version"]
    data = {i["slug"]: i for i in json.loads((package / "data/simple-icons.json").read_text())}
    source = json.loads(SITES.read_text())
    for site in source["sites"]:
        slug = site["slug"]
        if slug not in data:
            print(f"{slug}: not in Simple Icons {version}", file=sys.stderr)
            return 1
        svg = (package / "icons" / f"{slug}.svg").read_text()
        paths = re.findall(r'<path d="([^"]+)"', svg)
        if len(paths) != 1 or 'viewBox="0 0 24 24"' not in svg:
            print(f"{slug}: expected one path in a 24x24 viewBox", file=sys.stderr)
            return 1
        site["title"] = data[slug]["title"]
        site["hex"] = data[slug]["hex"]
        site["path"] = paths[0]
    source["source"] = f"simple-icons@{version}"
    SITES.write_text(json.dumps(source, indent=2, ensure_ascii=False) + "\n")
    print(f"{len(source['sites'])} sites from simple-icons@{version}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
