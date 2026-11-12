# Side-by-side audit

TODO Phase 7 and section 3. The same screens from iOS, Android and the desktop, taken the same
way, so any difference shows up next to the others. iOS is the reference.

## Shot list

Every app takes these screens, with the same fake vault where it has one:

| Key | Screen |
| --- | --- |
| `01-welcome` | First launch, language screen |
| `02-tour` | First launch, tour page 2 (sign-up demo, once it has finished playing) |
| `03-name` | First launch, name screen |
| `04-create` | Choose your main password |
| `05-unlock` | Unlock (locked vault) |
| `06-home` | Home |
| `07-passwords` | Passwords list |
| `08-detail` | A login's detail (Gmail) |
| `09-editor` | New login editor |
| `10-generator` | Password generator |
| `11-alerts` | Alerts / breached passwords |
| `12-health` | Password health |
| `13-you` | You |
| `14-settings` | Settings (top) |
| `15-collections` | Collections |
| `16-add-menu` | The add-item menu (sheet or popover) |

Variants: `en-light`, `en-dark`, `ar-light`, `ckb-dark` (both themes, both scripts, both
directions). A screen an app doesn't have is skipped and noted in `NOTES.md`.

## Files

`design/parity/<app>/<key>-<lang>-<theme>.webp`, where `<app>` is `ios`, `android` or
`desktop`. Each file is a WebP about 500 px wide, quality about 80 (full-size PNGs stay out of
the repo). Phones are the whole screen. The desktop is the app window at 1100 × 760.

## Findings

`NOTES.md` lists every difference found, per screen, with what was fixed and what is left to
decide.
