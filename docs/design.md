# Qasa — Design System & Platform Parity Spec

> **Superseded for values (2026-11-11).** Sizes, radii, gaps, type, motion and colors now live
> in [`design/tokens.json`](../design/tokens.json), generated into every app by
> `design/generate.py`; the rules are in [`design/README.md`](../design/README.md). The color,
> type-scale and radius tables below predate the redesign and are stale. The behaviour notes
> are still useful history.

Normative reference for the Qasa visual design system **and** the behavioural contract that
`apps/android/` must satisfy. Companion to [`ARCHITECTURE.md`](../ARCHITECTURE.md) (what exists) and
`application-flow.md` (what happens).

**Source of truth: the iOS app.** `apps/ios/qasa_ios/` defines every token, every component
recipe, and every product behaviour. Android is a port, not a reinterpretation. When this
document and the iOS code disagree, the iOS code wins and this document is the bug.

> Current state: Android is a **partial port**. The color layer is a byte-exact copy; the
> type, spacing, and localization layers are largely bypassed; roughly half the feature and
> logic surface is missing. Section 14 is the punch list.

---

## 0. How to read this document

| Marker | Meaning |
| --- | --- |
| **MUST** | Required for parity. Deviation is a defect. |
| **SHOULD** | Follow unless there is a platform-forced reason; document the reason. |
| **DO NOT** | Known iOS defect or anti-pattern. Fix it on both platforms; do not port. |
| `path:line` | Reference into the iOS source, the authority for that rule. |

---

## 1. Source of truth map

| Concern | Authority | Android counterpart |
| --- | --- | --- |
| Color tokens | `qasa_ios/Theme/Theme.swift:5` | `theme/QasaTheme.kt:22` |
| Typography | `qasa_ios/Services/AppTypography.swift:5` | `theme/QasaTheme.kt:117` |
| Spacing / shape | `qasa_ios/Theme/Theme.swift:157` | `theme/QasaTheme.kt:107` |
| Reusable UI | `qasa_ios/Components/` | `theme/` |
| Screen chrome | `qasa_ios/Components/AppMenuHeader.swift:3` | `theme/chrome/AppChromeHeaders.kt:103` |
| App state machine | `qasa_ios/qasaApp.swift` + `Services/AppSession.swift` | `MainActivity.kt` + `screens/RootNav.kt` |
| Crypto constants | `qasa_ios/Services/Vault/{MasterKeyDeriver,VaultCrypto}.swift` | `vault/crypto/{MasterKeyDeriver,VaultCrypto}.kt` |
| Strings | `l10n/strings.json` → generated | `res/values*/strings.xml` |

`l10n/strings.json` is the single source for user-visible copy. Both platforms' resource
files are **generated** — never hand-edited. `python3 l10n/generate.py --check` must pass.

---

## 2. Color

### 2.1 Tokens

The brand palette is four colors: **purple `#722F99`** (actions), **grey `#C5C1C1`** (strokes,
secondary text), **beige `#FEE7C8`** (canvas, warm wash, gold) and **peach `#FF9292`** (VPN
accent, danger in dark). Every token below is derived from them, in light and dark.

The values live in [`design/tokens.json`](../design/tokens.json) and are generated into every
app (`QasaTokens.swift` + `ThemePalette+Tokens.swift`, `QasaTokens.kt`, `tokens.css`); this table
is copied from it. Change a color there, never here or in an app.

| Token | Light | Dark | Role |
| --- | --- | --- | --- |
| `background` | `#FBF6F0` | `#1C1D1F` | App canvas. The only root background. |
| `chrome` | `#FBF6F0` | `#1C1D1F` | Headers, sheets, sticky bars (same as background since the redesign) |
| `cardBackground` | `#FFFFFF` | `#26272A` | Card and text-field fill |
| `cardBackgroundLight` | `#FFF3E3` | `#2C2D30` | Inset / secondary surface, warm beige in light |
| `cardMuted` | `#F3EAF7` | `#323337` | Recessed surface: badges, chips, icon tiles |
| `stroke` | `#E6E1E1` | `#38393D` | Card and field border. Most-used token. |
| `strokeChrome` | `#DCD7D7` | `#434448` | Border on chrome surfaces |
| `accent` | `#722F99` | `#B67FDB` | Brand purple: primary action, focus, selection, links |
| `accentDark` | `#5C2580` | `#722F99` | Deeper accent: pressed / selected fills |
| `onAccent` | `#FFFFFF` | `#22102F` | Text and icons on an `accent` fill |
| `onAccentChip` | `#4A1A66` | `#F1E3FA` | Text on a pale `cardMuted` accent chip |
| `gold` | `#B7701E` | `#FEE7C8` | Premium accent, best-value badge, lock icon (brand beige in dark) |
| `goldMuted` | `#94591A` | `#F0C48C` | Softer gold: warnings, "primary" badge |
| `cyan` | `#E2707A` | `#FF9292` | Brand peach: VPN accent, generator regenerate |
| `info` | `#8A4FB3` | `#C9A3E6` | Informational banner |
| `success` | `#2F9E6C` | `#5CCB97` | Connected, resolved, active |
| `danger` | `#C8323C` | `#FF6B6B` | Destructive fill and text (text on it is white) |
| `dangerBackground` | `#FFE6E6` | `#4A1F28` | Danger banner fill, icon disc |
| `dangerText` | `#9E2F35` | `#FFC0C0` | Danger text on a light surface |
| `dangerTextMuted` | `#B83C44` | `#FFD9D9` | Softer danger text |
| `textPrimary` | `#2A1F33` | `#F2F1F3` | Body and headings |
| `textSecondary` | `#6E6475` | `#C5C1C1` | Sub-labels, descriptions (brand grey in dark) |
| `textTertiary` | `#A39DA3` | `#8D8D92` | Disabled, chevrons, timestamps |
| `textPlaceholder` | `#A39DA3` | `#8D8D92` | Placeholder text |
| `textOnChrome` | `#3E3346` | `#DCDBDF` | Text drawn on `chrome` |
| `warm` | `#FEE7C8` | `#332C27` | Warm wash behind Home when nothing needs attention (brand beige in light) |
| `calm` | `#EDE9E9` | `#242527` | Cool wash behind Home when something does |

### 2.2 Rules

1. **MUST** access colors through one mechanism per platform — `@Environment(\.themePalette)`
   on iOS, `qasaColors` / `LocalQasaColors` on Android. **DO NOT** ship both a static facade
   and an environment accessor. iOS is mid-migration here (`Theme.swift:107` says "prefer
   the environment" while 26 of 29 views still read the static `Theme.*`); Android should not
   reproduce that split.
2. **MUST** resolve the palette exactly once at the root from the user's `SYSTEM | LIGHT |
   DARK` preference (`qasaApp.swift:136`, `QasaTheme.kt:136`). Never branch on
   `isSystemInDarkTheme()` inside a screen.
3. **MUST** derive tints from tokens, not new literals: `accent.copy(alpha = 0.16f)`,
   `stroke.copy(alpha = 0.5f)`. Alphas in use: 0.08, 0.1, 0.12, 0.14, 0.16, 0.18, 0.2, 0.3,
   0.35, 0.4, 0.45, 0.55, 0.7, 0.92, 0.95.
4. **MUST** have a light and a dark value for every color. Third-party brand colors
   (payment gateways) are the only exception and must live in an explicit `BrandColors`
   object with a comment.
5. **MUST NOT** use `Color.black` for scrims without a scheme-aware alpha — iOS hardcodes
   `Color.black.opacity(0.45)` at `SideMenuView.swift:14`; this is a defect. Define
   `scrim` as a token and use it on both platforms.
6. **DO NOT** carry iOS's four hardcoded escapes into Android: `Components.swift:334`
   (`#2563EB` in a accent toggle), `LinkedDevicesView.swift:187` (`#2A2A2B`), `VPNView.swift:181`
   (`#001B21`), `VaultEnums.swift:230` (`#3B82F6`, `#A855F7`). Android currently mirrors the
   first one at `QasaListWidgets.kt:239` — replace with a token.
7. **MUST** use one root background token per screen. iOS flips between `Theme.background`
   and `Theme.chrome` across 6 screens (`GenerateView`, `VPNView`, `LinkedDevicesView`,
   `ManageSubscriptionView`, `PremiumUpgradeView`), producing a visible seam on page change.
   **Fix this on both platforms:** the root is always `background`; only chrome surfaces
   (header, sheet, sticky bar, drawer) use `chrome`.

### 2.3 Android defect — border weight is half what iOS renders

`CardContainer` applies `.border()` **then** `.background()` (`QasaComponents.kt:124-125`).
`Modifier.border` draws centered on the bounds, so the background paints over the inner half
of the stroke. Every bordered surface in the Android app renders at **~0.5dp instead of
1dp** — a systematic, app-wide weight difference. The same ordering bug exists in
`VaultItemRow`, `ThemedTextField`, `CollectionsScreen`, and `VaultItemDetailScreen`.

**MUST** reverse the order so the stroke is fully visible:

```kotlin
// theme/QasaComponents.kt — CardContainer
Column(
    modifier = modifier
        .fillMaxWidth()
        .background(background ?: c.cardBackground, RoundedCornerShape(QasaDimens.cornerRadius))
        .border(1.dp, c.stroke, RoundedCornerShape(QasaDimens.cornerRadius))
        .padding(QasaDimens.cardPadding),
    content = content,
)
```

iOS is correct — `Components.swift:109-113` uses `.background` then `.overlay(stroke)`.
Apply the fix to every `.border().background()` pair in `theme/` and `screens/`.

---

## 3. Typography

### 3.1 Families

| Role | iOS | Android |
| --- | --- | --- |
| UI / body | `Font.qasa(size, weight:)` → `AppTypography.font` (`Theme.swift:188`) | Type scale, locale-aware family |
| Mono / label | `Font.kasoMono(size, weight:)` → `.system(design: .monospaced)` (`Theme.swift:183`) | `FontFamily.Monospace` |

Every app uses one family for every language: **IBM Plex Sans Arabic** (SIL OFL 1.1), which
covers Latin, Arabic and Sorani Kurdish. It is bundled with each app in four static weights
(Regular 400, Medium 500, SemiBold 600, Bold 700); nothing loads it from Google Fonts.

| App | Where |
| --- | --- |
| iOS (app + AutoFill + payment-notification extensions) | `qasa_ios/Resources/IBMPlexSansArabic-*.ttf`, mapped by weight in `SharedAutofill/QasaFontFace.swift`; `AppTypography.font` and `Font.qasa` go through it |
| Android | `res/font/ibm_plex_sans_arabic_{regular,medium,semibold,bold}.ttf`, one `FontFamily` in `theme/QasaTypography.kt` |
| Desktop, extension | `apps/desktop/src/assets/fonts/`, `@font-face` in `theme/base.css`; the extension build copies the files into `dist/*/fonts/` |
| Landing, dashboard | bundled locally with `@font-face`, same four weights |

Requested ExtraBold / Black weights fall back to Bold, the heaviest bundled file. The only
character in the string catalog the font lacks is U+2011 (non-breaking hyphen), which falls
back to the system font.

**MUST** keep these per-script rules (one decision, in the typography layer: `QasaText.arabicScript`
on Android, `QasaFontFace` / `AppTypography` on iOS):

- For `ckb`/`ar` the mono family is **dropped** in favour of the body family and
  `letterSpacing` is **zeroed**. Letter spacing breaks cursive joining in Arabic script, so
  this is a correctness requirement, not a preference. Passwords stay monospaced in `en`.
- **MUST NOT** force `.uppercase()` on `ckb`/`ar`; those scripts have no case.
- **MUST** pass the requested weight through. A bold title must render bold in every language.

### 3.2 Scale

| Size | Weight | Role |
| --- | --- | --- |
| 9 | bold | Payment gateway wordmark |
| 10 | regular | Inline chip icon, account-tier icon |
| 11 | regular / semibold / bold | Toggle checkmark, date parse error |
| **12** | medium / semibold | **The label size.** Section labels, status pills, badges, stat labels — 22 uses |
| 13 | regular / medium / semibold | Secondary and error body, chip labels |
| **14** | regular / medium / semibold | **Body standard.** Row subtitles, toggles, feature rows, detail labels |
| 15 | regular / medium / semibold | Legal body, secret values (mono), shield text |
| **16** | regular / semibold | **Default body.** Text fields, row titles, toggle titles |
| 17 | regular / semibold | Modal CTAs, drawer display name, chrome bar buttons |
| 18 | regular / semibold / bold | Primary button label, breach card title |
| 20 | regular / semibold / bold | Page titles, stat values, premium sheet title |
| 22 | bold / heavy | Section headlines, drawer brand, plan price |
| 24 | bold | Standalone brand header, legal title |
| 26 | bold | Store-error title |
| **28** | heavy / bold | **Screen hero.** Auth headers, VPN status, plan price, health stats |
| 34 | semibold | Nav large title (UIKit only) |
| 36 | medium | Detail hero icon, VPN power glyph |
| 40 | semibold | Store-error icon |
| 44 | semibold / bold | Health score numeral, shield icon |
| 88 | bold | Create-vault shield watermark |

Android's `QasaTypography` (`QasaTheme.kt:117`) only populates 9 of 12 M3 slots, and
**99 raw `fontSize = N.sp` literals in `screens/` bypass the scale entirely** — 33 uses of
`13.sp`, 30 of `14.sp`, 5 inline copies of `28.sp` that duplicate a token which already
exists. Android also **never sets `lineHeight`** outside the token, so every raw size gets
the M3 default (16sp → 24sp), looser than iOS and destroying vertical rhythm.

**MUST** define the Android scale to mirror the table above, expose it as
`QasaTypography` + named role accessors (`displayLarge`, `pageTitle`, `bodyMedium`,
`sectionLabel`, `bodyMono`, …), set explicit `lineHeight` per slot, and delete the 99 raw
literals.

### 3.3 Tracking and line height

| Value | Use |
| --- | --- |
| `0.6` | The house mono-label tracking — `SectionLabel`, `StatusPill`, `PillBadge`, stat labels, diagnostics |
| `0.8` | Drawer section labels |
| `1.2` | "BEST VALUE" eyebrow |
| `1.5` / `2.8` | Generated password, compact / full |
| `lineHeight 4` | Legal body — the only line-height override in the app |

`DO NOT` port iOS's 17 remaining semantic SwiftUI fonts (`.caption` ×7, `.body`, `.title2`
×2, `.subheadline` ×2, `.caption2`). They scale with the OS Dynamic Type setting that this
app never opts into, so they render at inconsistent sizes against the fixed scale.
`NavChevronRow` mixes `.subheadline` and `.caption` with `.qasa` in one component
(`Components.swift:274-289`) — Android's `NavChevronRow` correctly uses 15sp/12sp; keep that.

---

## 4. Spacing, shape, elevation

### 4.1 Tokens

iOS defines exactly three (`Theme.swift:157-159`):

| Token | Value |
| --- | --- |
| `cornerRadius` | 12 |
| `cornerRadiusLarge` | 16 |
| `screenPadding` | 20 |

Android's `QasaDimens` (`QasaTheme.kt:107`) matches and adds `headerHeight = 64` and
`stickyButtonHeight = 56`, which iOS hardcodes. Keep the additions; they are correct.

**MUST** promote the remaining de-facto constants into `QasaDimens` and use them everywhere:

| New token | Value | Currently |
| --- | --- | --- |
| `cardPadding` | 17 | 4 sites; the single most-repeated magic number |
| `headerHeight` | 64 | already a token |
| `iconSize*` | 8/10/11/12/16/18/20/24/28/36/40/44/56/88 | 17 raw sizes |
| `radiusSmall` | 8 | 20 raw uses |
| `radiusTiny` | 4 | 2 raw uses (`PillBadge`) |
| `radiusMedium` | 10 | drawer row, folder tile |
| `sectionGap` | 24 | 21 raw uses, split 20/24 |
| `contentGap` | 8 | 81 raw uses |
| `scrollTail` | 40 | 10 raw uses, plus 72/80/96 variants on Android |
| `fabClearance` | 96 | 96 and 72 on Android — pick one |
| `sheetMaxHeight` | `screenHeightDp × 0.92` | triplicated at `SettingsScreen.kt:151`, `VaultItemEditorScreen.kt:51`, `AddItemMenuScreen.kt:58` |

**MUST NOT** mix radii outside this set. Android currently has a `16.dp` item-tile radius
(`VaultItemDetailScreen.kt:92`) that collides numerically with `cornerRadiusLarge` but means
something else.

### 4.2 Screen rhythm

Every top-level screen follows the same shape:

```
VStack(spacing: 0) {
    AppMenuHeader / BrandMenuHeader      // 64pt, chrome, 1px strokeChrome underline
    ScrollView {
        VStack(alignment: .leading, spacing: 16 or 24) { ... }
            .padding(.horizontal, screenPadding)
            .padding(.top, 20 | 24 | 28)
            .padding(.bottom, scrollTail)
    }
}
.background(background.ignoresSafeArea())
```

Android's `ScreenScaffold` (`ScreenScaffold.kt:19`) already encodes this and is used at 17
sites — **keep it, and route every new screen through it.** It is a genuine improvement over
iOS, where every view hand-rolls the same `VStack`.

**MUST** add `WindowInsets` handling to `ScreenScaffold` so callers cannot get it wrong.
Today a header that forgets `statusBarsPadding()` renders under the status bar.

**MUST NOT** mix `Column` padding bottom `24` with no `navigationBarsPadding()` — the last
card sits under the gesture bar on the 13 `ScreenScaffold` screens with no sticky bar.

### 4.3 Elevation

iOS uses elevation in exactly two places. Android should match both and add nothing:

| Element | Recipe |
| --- | --- |
| `PrimaryButton` (accent only) | `shadow(color: accent @ 30%, radius: 10, y: 0)` — `Components.swift:90`, `QasaComponents.kt:87-92` ✅ |
| `MintFab` | `shadow(color: black @ 20%, radius: 8, y: 4)` + 1dp `accent @ 50%` border + 56×56 — `Components.swift:440-447` |

Android's `MintFab` (`QasaComponents.kt:246`) uses M3's `elevation(8.dp)`, which applies a
tonal surface tint instead of a black shadow, and **omits the accent border entirely**. Fix it.

Everything else — cards, chips, headers, drawer, sticky bar — is **flat**. No elevation.

---

## 5. Iconography

- **MUST** use vector drawables only, at these sizes: 8 (status dot), 10 (chip icon), 11
  (check), 12, 14 (lock), 16 (standard), 17 (menu), 18 (standard), 20, 22, 24, 28, 36, 40,
  44, 56, 88.
- **16 semibold is the default size.** 18 semibold is the norm for feature/checkmark icons.
- **MUST** replace every text glyph currently standing in for an icon:
  - `NavChevronRow`'s `"›"` (`QasaListWidgets.kt:74`) — points the wrong way in RTL.
  - `VaultItemRow`'s `"⧉"` (`QasaListWidgets.kt:213`) — no `contentDescription`, so
    TalkBack announces nothing.
  - `CheckToggle`'s `"✓"` (`QasaListWidgets.kt:243`) — use a vector.
- **MUST** use `Icons.AutoMirrored.*` for every directional glyph. Android currently has 57
  `Icons.Default.*` and 4 `AutoMirrored.*`; nothing prevents a future regression.
- **MUST** use the Qasa mark (the Qaf Keyhole) for the brand, never a stock shield:
  `QasaMark` on iOS (`Assets.xcassets/QasaMark`), `R.drawable.ic_qasa_mark` on Android,
  `QasaMark.vue` on desktop and in the extension. Every icon and mark is generated from one
  geometry by `assets/brand/generate.py`; change the shapes there, never in an app.
  `Icons.Default.Menu` stays for the drawer.
- Icon container frames: 14×14, 16×20 (brand shield), 24×24, 28×28, 32×32, 36×36, 40×40,
  44×44, 48×48, 56×56, 88×88.

---

## 6. Component catalog

Anything in this section exists on iOS. Android status is given for each.

### 6.1 Chrome

#### `AppMenuHeader` — `AppMenuHeader.swift:3` / `AppChromeHeaders.kt:103` ✅
Height 64, `chrome` background, 16pt horizontal padding, 1pt `strokeChrome` bottom border.
Menu button: 40×40 circle, `cardBackground` fill, 1pt `strokeChrome` border, 17pt glyph.
Title: **20 semibold**, `textPrimary`, single line. Optional trailing slot.

#### `BrandMenuHeader` — `AppMenuHeader.swift:41` / `AppChromeHeaders.kt:71` ✅
Same chrome, title slot replaced by the Qasa mark (22pt, accent) + brand
wordmark at `titleSize` (default 20) heavy, `accent`.

#### `KasoBrandHeader` — `Components.swift:4` / `AppChromeHeaders.kt:37` ✅
Centered brand variant for auth screens, `titleSize` default 24.

**DO NOT** port iOS's duplication: `BrandMenuHeader` copies 28 of `AppMenuHeader`'s 39 lines
(`AppMenuHeader.swift:41-81`). Android's shared `HeaderChrome` primitive is correct — keep it.
Android has two header title sizes too many (`BackChromeHeader` 18, `SheetChromeHeader` 18)
with no iOS counterpart; iOS uses `ToolbarItem(placement: .cancellationAction)` for sheet
cancellation. **Pick one approach and apply it to all four header variants.**

#### `StatusPill` — `Components.swift:34` / `QasaComponents.kt:57` ⚠ partial
13/5 padding, capsule, 1pt `strokeChrome` border, `cardBackgroundLight` fill, 12 mono +
0.6 tracking, `textOnChrome`. Optional 8×8 leading dot.
**Android is missing the dot and the color overrides.**

#### `MintFab` — `Components.swift:428` / `QasaComponents.kt:246` ⚠
56×56, `cornerRadiusLarge`, `accent` fill, `onAccent` glyph at 18 bold, 1pt `accent @ 50%`
border, black-20% shadow r8 y4.
**Android is missing the border and uses the wrong shadow; the icon is hardcoded to `Add`
and there is no `modifier` parameter.**

#### `StickyBottomBar` — iOS inline / `ScreenScaffold.kt:40` ✅
`chrome @ 92%`, 1pt `stroke` top divider, 17pt padding, `navigationBarsPadding()`.
**MUST** add `imePadding()` — with `enableEdgeToEdge()` the keyboard currently overlaps the
primary action in Unlock, Create, ChangeMasterPassword and Pair.

#### Drawer / `SideMenuView` — `SideMenuView.swift:3` / `AppShellScreen.kt:138-269` ⚠
Scrim `black @ 45%`; panel 320pt wide, `chrome`, `strokeChrome` trailing 1pt border;
header with brand at 22 heavy `accent`, display name 17 semibold, tier 13; three labelled
sections (primary / tools / account); rows 12/12 padding, 10pt radius, 16pt icon in a 22pt
slot, 14pt gap, 15pt label, gold lock glyph on premium-gated rows. Open/close
`.easeOut(0.22 / 0.2)`.

**Android's drawer is not RTL-aware** — fixed 320dp, `slideInHorizontally { -it }` always
from the left. **MUST** key the slide direction off `LocalLayoutDirection.current`.
**DO NOT** port iOS's `ZStack` sibling drawer (`AppShellView.swift:61-72`) — it gets no focus
trap, no swipe-to-dismiss, and no accessibility escape. Android's `AnimatedVisibility` +
`BackHandler` is closer; keep it, and add a scrim `clickable` sink that actually consumes
taps.

### 6.2 Buttons

| Component | iOS | Android | Recipe |
| --- | --- | --- | --- |
| `PrimaryButton` | `Components.swift:64` | `QasaComponents.kt:71` | Full width, 16pt vertical padding, `cornerRadius`, `accent` fill, `onAccent` label **18 semibold**, optional 16 semibold leading icon, accent-30% glow r10 |
| Secondary | **none** | `QasaComponents.kt:275` | Android-only. 56 tall, `cardBackgroundLight`, 1pt `stroke` border, 16 semibold |
| Destructive | **none** | `QasaComponents.kt:296` | Android-only. `danger` text, 15 semibold, 8pt radius — **no ripple, no role, ~38dp target** |
| Ghost | **none** | — | Bare button + colored text |
| Icon | **none** | — | 11 raw sites on iOS |

**MUST** add a disabled state to `PrimaryButton` and **MUST NOT** let callers hand-roll
`opacity(0.5)`. iOS has no disabled variant and 8 call sites fake it — Android's
`accent @ 45%` is the right call; keep it and extend to every button.

**MUST** give every button a `Button` (not a `Text` with `.clickable`) so ripples and roles
work. `DestructiveTextButton` is a bare `Text` — it has no indication at all.

**MUST** enforce a 48dp minimum touch target. Current violations: `DestructiveTextButton`
(~38dp), `FilterChipButton` (26–30dp for short labels like `"5m"`), `VaultItemRow` copy
`IconButton` (36dp), `HamburgerButton` (40dp — iOS parity, but still under-spec on Android).

### 6.3 Containers

#### `CardContainer` — `Components.swift:96` / `QasaComponents.kt:115` ✅
Padding 17, `cardBackground` fill, 1pt `stroke` border, `cornerRadius`, `fillMaxWidth`.
Overridable `background` and `borderColor`.
**Fix the border order per §2.3.** iOS omits the border on the store-error remedy card
(`VaultStoreErrorView.swift:61-70`) — a defect; Android should not repeat it.

#### `SectionLabel` — `Components.swift:119` / `QasaComponents.kt:132` ✅
Uppercased for `en` only, 12pt — mono + 0.6 tracking for `en`, 12 semibold + 0 tracking for
`ckb`/`ar` — `textSecondary`.

**DO NOT** repeat the Arabic-script check at call sites. One component, one decision.

**MUST** bake the following gap into `SectionLabel` rather than making 15 call sites repeat
`Spacer(8)` + `Spacer(24)`: a leading 24pt section gap, and expose an 8pt trailing slot.

#### `StatBox` — `Components.swift:186` / `QasaListWidgets.kt:139` ✅
Icon 18, 8pt gap, uppercased mono-12 label + 0.6 tracking, value 20 semibold with unit 14
on the same baseline. iOS's copy is **dead code** (0 call sites) — Android actually uses it
in `VPNScreen`. Keep Android's version, delete iOS's if it stays unused.

#### `FeatureRow` — `PremiumUpgradeView.swift:398` ❌ **missing on Android**
18pt accent checkmark-circle + 14pt text, 12pt gap. Android's `PremiumUpgradeScreen` has **no
feature list at all** — it uses one hardcoded bullet string
(`"${days}-day access · Multi-device · Breach · VPN"`, `PremiumUpgradeScreen.kt:221`).
iOS shows per-plan localized bullets plus a yearly "Save" line. Port the component and the
per-plan bullets.

### 6.4 Inputs

#### `ThemedTextField` — `Components.swift:131` / `QasaComponents.kt:142` ✅
16/18 padding, `cardBackground` fill, 1pt `stroke` border, `cornerRadius`, optional 20pt
leading icon at 12pt gap, accent cursor, 16pt text.
**Android is missing** the accent cursor on all fields other than by convention, an
`imeAction`/`keyboardActions` wiring (0 call sites pass `keyboardOptions`), a `label`, and a
`contentDescription`.

**DO NOT** port iOS's habit of relying on the inherited font: `ThemedTextField` sets no
`.font` and inherits 16pt from `qasaApp.swift:139`. Android's inline `fontSize = 16.sp` is
more robust — keep it, but move it into the type scale.

#### `FormTextField` — `FormHelpers.swift:19` ❌ **missing on Android**
Label above + 14pt padding + `.qasa(16)`. This is the form-field component, and Android
`VaultItemEditorScreen` uses `ThemedTextField` for everything. **Port it** and use it for all
editor fields.

#### `ValidatedDateField` — `ValidatedDateField.swift:4` ❌ **missing on Android**
Typed field + calendar button + graphical `DatePicker` on demand, with parse validation.
Parsers, in order, with `en_US_POSIX`, `isLenient = false` (`ValidatedDateField.swift:21-29`):
`"MMM d yyyy"`, `"MMM d, yyyy"`, `"yyyy-MM-dd"`, `"dd/MM/yyyy"`, `"MM/dd/yyyy"`, then a
locale-aware `.medium` fallback. Error message at 11pt `danger`, trailing-aligned.
Android uses a plain text field for `Expiry` (`VaultItemEditorScreen.kt:106`). **Port it.**

#### `ToggleRow` — `Components.swift:219` / `QasaListWidgets.kt:80` ✅
40×40 circular `iconColor @ 20%` disc with a 20pt icon, 12pt gap, title 16, subtitle 14,
accent switch, 8pt vertical padding. iOS allows an `iconBackground` override; Android always
uses `@ 20%`, so the VPN kill-switch tile differs. **Add the parameter.**

#### `LocalizedToggleRow` — `Components.swift:351` ❌ **missing on Android**
14pt title, `CheckToggleStyle`, real `disabled` state (title → `textTertiary`, 0.7 opacity),
8pt vertical padding. Used 5× in the generator settings card. Android has the two halves
(`ToggleRow`, `CheckToggle`) but never the combination.

#### `CheckToggle` — `Components.swift:321` / `QasaListWidgets.kt:220` ⚠
48×24 track (accent on / `stroke` off), 24pt thumb, check glyph 11 bold.
**Android's `#2563EB` thumb is a port of an iOS defect — replace with a token.** Android
also snaps with no animation; iOS uses
`.spring(response: 0.25, dampingFraction: 0.8)`. **MUST** add the animation, and **MUST**
use `Modifier.toggleable` — Android uses `.clickable`, so TalkBack announces a button with no
on/off state.

#### `FilterChip` — `Components.swift:387` / `QasaComponents.kt:219` ✅
17/7 padding, capsule, 1pt border (`accent` selected / `stroke` unselected), 14pt label
(semibold when selected). Selected fill: `accent @ 16%` light, `accentDark` dark.
**MUST** add `Modifier.selectable` — Android uses `.clickable`, so selection is invisible to
TalkBack.

**DO NOT** port iOS's three divergent chip implementations. Only `FilterChip` survives.

**`FlowLayout`** — `BreachCard.swift:136` ❌ **missing on Android**
Wrapping layout for chips. Android uses `Row` + `horizontalScroll` (clips, and starts
scrolled the wrong way in RTL) or a fixed 3-column `Row`. **Port it**; breach cards need it.

#### `CountryPickerField` / `CountryMenuPicker` — `FormHelpers.swift:49,71` ❌ **missing**
Label + menu picker, 12pt padding, card chrome, accent tint. Backed by `Models/Country.swift`
(60 lines of country/dial-code data). Android has **no country data at all** and uses a
plain text field.

#### `CollectionPickerField` — `FormHelpers.swift:87` ❌ **missing**
Label + "none" picker + a accent `+` button, 12pt padding, card chrome, with an inline
create-collection alert. This is how items get assigned to a collection — **Android cannot
assign an item to a collection.**

#### `NoteColorPicker` — `FormHelpers.swift:135` ❌ **missing**
Row of 28×28 circles, 2pt white ring on the selected one, 12pt gap. Used for secure notes.
**DO NOT** port the iOS implementation's `onTapGesture` — Android **MUST** use real
buttons with a 48dp target and a selected-state semantic.

#### `PasswordGeneratorModal` — `PasswordGeneratorModal.swift:6` ❌ **missing on Android**
Reachable from the vault editor, not just the standalone generator screen. Presented at
`[.fraction(0.92), .large]` with a visible drag indicator and a 20pt corner radius. Contains
the generated-password card (gradient `accent @ 10%` → clear, mono 22/28 bold, tracking
1.5/2.8, single line, `minimumScaleFactor(0.4)`, copy button), the strength card and the
settings card. Android has `GenerateScreen` only — **the editor has no generator entry
point**, and the editor has no show/hide password toggle either.

**DO NOT** port the permanently disabled `pronounceable` toggle
(`PasswordGeneratorModal.swift:243-247`).

**`StrengthBar`** is used with 3 segments in `CreateVaultView` and 5 at height 8 in the
generator (`PasswordGeneratorModal.swift:181-186`). That is intentional — keep it.

### 6.5 Rows

#### `VaultItemRow` — `VaultItemRow.swift` / `QasaListWidgets.kt:165` ⚠ major gap
Shell: 17pt padding, 12pt radius, 1pt `stroke` border, `cardBackground`; 48×48 tile with 8pt
radius, `chrome` fill, `strokeChrome` border, 18pt icon; 16pt gap; title 16 semibold
(1 line, ellipsis), subtitle 14 (1 line, ellipsis).
Overflow: a 36×36 `ellipsis` glyph rotated 90° opening a `Menu` with **per-field copy
actions**, "Open website" via `openURL`, Edit, and Delete behind a confirm alert. Copying
shows a accent toast.

**Android has one copy button and no menu, no edit, no delete, no website open, no toast.**
Delete is unreachable from the row on both platforms — on iOS it is three taps deep, and
neither has swipe-to-delete. **MUST** port the `Menu`; **SHOULD** add a long-press
equivalent, since Android users expect it more than iOS users do.

#### `NavChevronRow` — `Components.swift:257` / `QasaListWidgets.kt:35` ✅
10pt vertical padding, 28pt icon, 12pt gap, title 15 medium, subtitle 12, trailing 12,
chevron 12 `textTertiary`, hidden when destructive. Omit the chevron in RTL automatically —
**use `AutoMirrored`, not the `"›"` glyph.**

### 6.6 Status and feedback

| Component | iOS | Android |
| --- | --- | --- |
| `PillBadge` — 4pt radius, 8/2 padding, `@ 10%` fill + `@ 20%` border or solid | `Components.swift:298` | `QasaListWidgets.kt:116` ✅ |
| Banner (info / limit-exceeded) | `LinkedDevicesView.swift:458-520` | **missing** — 5 ad-hoc copies on iOS, **0 on Android** |
| Empty state | `ContentUnavailableView`, 4 sites | `EmptyState` ✅ Android-only, keep it |
| Toast | `VaultItemRow.swift:105-117` | **missing** — 3 copy-paste copy-and-flash snippets on Android |
| Progress | bare `ProgressView`, 5 sites | bare `CircularProgressIndicator` |
| `BreachCard` | `BreachCard.swift:3` | ❌ **missing entirely** |
| `HalfCircleGauge` | `PasswordHealthView.swift:267` | `QasaHeroWidgets.kt:31` ⚠ |
| `VpnPowerDial` | `VPNView.swift:115-190` | `QasaHeroWidgets.kt:82` ⚠ |

**`BreachCard`** is the largest single component gap. It renders one card per breach: 21pt
padding, 12pt radius, 1pt accent border (`danger` unresolved / `success` resolved), a **2pt
accent top bar with an `UnevenRoundedRectangle` mask**, title at 18 semibold + `PillBadge`,
the breach date at 14, a `FlowLayout` of compromised-field chips (9/5 padding, 4pt radius,
10pt per-field icon, mono 12), and an action button — outlined danger "Rotate password" when
unresolved, a disabled "Action taken" when resolved — with `opacity(resolved ? 0.8 : 1)`.
Android's `BreachMonitorScreen` lists **monitored email addresses only**; the breach objects
the API returns are never surfaced at all.

**`DO NOT`** port the empty action on iOS's "Rotate password" button
(`BreachCard.swift:88-89`) — it is a `Button {}` with no body.

**`HalfCircleGauge`**: iOS strokes the trimmed arc with an
`AngularGradient([danger, gold, accent], 180°→360°)`, animates `.easeOut(1.1)`, and puts no
number inside. Android uses a **flat stepped color** (`≥80 success / ≥50 gold / else
danger`), no animation, and a 42sp Black numeral inside. **Pick one.** iOS's gradient is the
better design; Android's numeral is genuinely useful. Recommendation: gradient arc +
animated trim + the numeral in `textPrimary`, plus a `contentDescription`.

**`VpnPowerDial`**: iOS outer ring `cyan @ 30%` 1pt at 160, inner ring `cyan @ 55%` 1pt at
140, **opaque** `cardBackground` core at 128 with a `accent @ 40%` r20 shadow when connected,
`"power"` glyph at 36 medium, and a `ProgressView` busy state. Android uses 2dp/3dp rings,
a translucent `accent @ 18%` core, no shadow, `PowerSettingsNew`, and prints `"ON"`/`"OFF"`
text. Also the iOS callout: the kill-switch and auto-connect toggles are **local `@State` and
never persisted** (`VPNView.swift:10-11`) — a bug. **Persist them.**

### 6.7 Screens

#### `VaultStoreErrorView` — `VaultStoreErrorView.swift:9` ❌ **missing on Android**
Shown when the local store cannot be opened. Icon 40 `danger`, title 26 bold, body 16,
remedy 16 on a card, then a selectable 12pt monospaced diagnostics block with the store path.
Content is capped at `maxWidth: 520`. **Deliberately offers no retry and no reset** — it
never deletes a store to "recover" (`VaultStoreErrorView.swift:17-22`).

Android's `AppModule.kt:20-25` has a **debug-only `fallbackToDestructiveMigration`**, so a
production migration failure either crashes or silently destroys the vault. **Port the error
screen; remove the destructive fallback from release builds.**

#### `VaultPrivacyShield` — `VaultPrivacyShield.swift:12` ❌ **missing on Android**
`background`-filled overlay: `lock.shield.fill` at 44, "hidden" at 15 medium, and an OK
button. Driven by `AutoLockMonitor`, not by `scenePhase`.

**This is a security regression, not a cosmetic one.** Android has **no `FLAG_SECURE`
anywhere** — every vault screen is captured by Recents thumbnails, screenshots and screen
recording. **MUST** set `WindowManager.LayoutParams.FLAG_SECURE` in `MainActivity.onCreate`
and add the shield for the `inactive` equivalent.

**DO NOT** port two iOS defects: `.resistsUnauthorized(true)` is claimed in a comment at
`VaultPrivacyShield.swift:45-46` but never set, and `.accessibilityHidden(true)` hides the
OK button from VoiceOver.

#### `CSVDocument` — `CSVDocument.swift` ❌ **missing on Android**
`Transferable` + `FileDocument` that spills plaintext to
`tmp/qasa-export/qasa-vault-backup.csv`, marked `isExcludedFromBackup`, shared via `ShareLink`.
The header comment (`:4-10`) records that the previous `UIPasteboard.general` path leaked
every password to any app and to clipboard history. **Never use the clipboard for bulk vault
export on either platform.**

#### `VaultFormChrome` — `FormHelpers.swift:156` ❌ **missing on Android**
`NavigationStack` + `ScrollView` + `screenPadding`, inline navigation title, cancel action
and a save/update action disabled on `canSave` and tinted `accent`.

---

## 7. Screen chrome & navigation

### 7.1 Shell

iOS `AppShellView.swift:4-102` is a single-stack page swap — **there is no `TabView`**;
`MainTabView.swift` is a 12-line tombstone. 11 pages, 3 wrapped in a `NavigationStack`,
8 not. `selectPage` gates premium: a premium page redirects to the upgrade screen
(`AppShellView.swift:86-95`).

Android's `RootNav.kt:50-55` + `AppShellScreen.kt:111-136` mirror this and additionally
prefer `NavHost` for drill-down, which is the better choice. **Keep the NavHost.**

Two Android defects:

1. **`RootNav` checks `vaultExists` before `welcomeDone`** (`RootNav.kt:52-54`), so a user with
   an existing vault never sees the welcome screen. iOS orders it welcome-first.
2. **`welcomeDone` is held in `remember` only** (`RootNav.kt:48,53`) and never read from
   `AppPreferences.hasCompletedWelcome`, so Welcome reappears after process death for anyone
   who hasn't created a vault yet. `WelcomePreferencesScreen.kt:123` does persist it — the
   shell just doesn't read it.

**DO NOT** port iOS's mixed `NavigationStack` wrapping. **MUST** use a `NavHost` for
drill-down on both.

**Delete** `RootRoutes` (`Routes.kt:3-9`) and `VaultRoutes.ADD`/`EDIT` (`Routes.kt:14,15`) —
all dead.

### 7.2 Presentation

| Type | iOS | Android |
| --- | --- | --- |
| Add-item type picker | `.sheet` `[.medium, .large]` | `QasaModalBottomSheet` ✅ |
| Vault editor | `.sheet` full height | `QasaModalBottomSheet` ✅ |
| Generator | `.sheet` `[.fraction(0.92), .large]`, drag indicator, 20pt radius | `QasaModalBottomSheet`, no detents |
| Settings sub-pages | `.sheet` full height | `QasaModalBottomSheet` via `SettingsSheet` |
| Pair existing vault | `.fullScreenCover` — the only one | `QasaModalBottomSheet` |

**MUST** set `tonalElevation = 0.dp` and `shadowElevation` explicitly on
`QasaModalBottomSheet`. M3's default surface tint derives from the partial `colorScheme.surface`
and drifts from `background`.

**MUST NOT** port the 350ms `DispatchQueue.main.asyncAfter` that iOS uses to chain the
add-menu sheet into the editor sheet (`VaultListView.swift:107`). Use a dismissal callback.

**MUST** apply the locale override inside every sheet, on both platforms. iOS's
`.appLocalization()` is missing from two sheets (`CollectionsView.swift:230`,
`PasswordHealthView.swift:258`) — a bug.

### 7.3 Add-item menu

iOS `AddItemMenuView.swift:8-96` is a **hierarchical, tab-aware drill-down**: a top-level
type list that navigates into subtypes, seeded from the calling tab. Android
`AddItemMenuScreen.kt:71-85` is a **flat list of all 13 subtypes**. Port the drill-down.

**Bug to fix on iOS:** `AddItemMenuView.swift:66` renders `Button("common.cancel", …)` — a
raw localization key, not `L10n.tr(...)`. Users see the literal string `common.cancel`.

### 7.4 Precedence

For a premium-gated page, the shell redirects to the upgrade screen. Android does this in
`navigate()` (`AppShellScreen.kt:97-100`) — correct. iOS additionally gates
`PairExistingVaultView` on `checkLinkEligibility` with a distinct "device limit exceeded"
message. Port that distinction.

---

## 8. Motion and haptics

### 8.1 Timing

iOS uses five durations. Define them as tokens; Android currently has none.

| Token | Value | Use |
| --- | --- | --- |
| `motionFast` | 180ms | Selection, language/theme change |
| `motionStandard` | 200ms | Drawer close, plan dot, plan page |
| `motionDrawer` | 220ms | Drawer open |
| `motionToggle` | spring(response 0.25, dampingFraction 0.8) | `CheckToggle` thumb |
| `motionGauge` | 1100ms | Health gauge trim + score count-up |

**MUST** honor `Settings.Global.ANIMATOR_DURATION_SCALE` / iOS
`accessibilityReduceMotion`. iOS runs the 1100ms gauge and the toggle spring regardless —
a defect. **MUST** wrap the `PremiumUpgradeView` `TabView` page swipe in `withAnimation`;
iOS animates the dots but snaps the page.

### 8.2 Haptics

iOS has exactly one haptic: a light impact on copy (`FormHelpers.swift:6-9`), and it
allocates a fresh `UIImpactFeedbackGenerator` each call. Everything else — toggles,
generator regenerate, chip selection, delete confirmation, biometric auth, drawer
open/close — is silent.

**MUST** add haptics on both platforms for: copy (light), delete (medium), destructive
confirm (heavy), toggle flip (selection), generator regenerate (light), biometric success
(failure). Cache the generators; do not allocate per call.

**SHOULD** add a copy toast everywhere, not just in `VaultItemRow`. Android has three
copy-pasted copy-and-flash snippets (`VaultListScreen.kt:254`, `GenerateScreen.kt:117`,
`VaultItemDetailScreen.kt:150`) and no shared helper.

### 8.3 Press states

**No `ButtonStyle` is defined anywhere in the iOS codebase.** Some buttons use
`.buttonStyle(.plain)` (no highlight), others omit it and get the grey system flash. The
result is inconsistent, and there is no press feedback, no press-scale, and no haptic on
press.

**MUST** define one `ButtonStyle` on each platform and apply it uniformly. Recommended:
scale to 0.97 with a 120ms ease-out, plus the haptic from §8.2.

---

## 9. Accessibility

iOS accessibility is thin; Android's is thinner still — **zero `Modifier.semantics` calls in
the entire app.** Android is a worse case on essentially every axis.

**MUST** on both platforms:

1. 48dp / 44pt minimum touch target. Android violations: `DestructiveTextButton` (~38dp),
   `FilterChipButton` (26–30dp), `VaultItemRow` copy (36dp), `HamburgerButton` (40dp).
2. `Modifier.toggleable` on `CheckToggle`; `Modifier.selectable` on `FilterChipButton` and
   the language/theme pickers; `Role.Button` on `DestructiveTextButton`.
3. `contentDescription` on every icon that is the sole label — `MintFab` currently hardcodes
   `"Add"`, `HamburgerButton` hardcodes `"Menu"`, and `VaultItemRow`'s copy button has none.
   **All of these MUST be localized**; iOS uses `L10n.tr("nav.openMenu")`.
4. `contentDescription` for the gauge score ("Password health score 72 of 100"), the
   strength bar, and the VPN dial's on/off state.
5. `LiveRegion` on error messages — `UnlockScreen.kt:106`, `CreateVaultScreen.kt:165`,
   `ChangeMasterPasswordScreen.kt:113`, `ManageSubscriptionScreen.kt:113` are currently
   silent to TalkBack.
6. **MUST NOT** use a text glyph as an icon. `QasaListWidgets.kt:74` (`"›"`) and `:213`
   (`"⧉"`) are read aloud as punctuation.
7. `ThemedTextField` **MUST** carry a label. Android's placeholder is a sibling `Text`, so a
   TalkBack user hears only typed characters and never the field name.
8. Selected rows in the drawer and the language/theme pickers **MUST** expose selected state.

---

## 10. Localization and RTL

### 10.1 The catalog

`l10n/strings.json` holds **559 keys**. `l10n/generate.py` emits `Localizable.xcstrings`
for iOS and `res/values{,-ar,-ckb}/strings.xml` for Android (561 entries each, including
platform overlays). All three Arabic/Kurdish files are complete and correct.

**The generation pipeline is not the problem. The Android UI does not read it.**

- `stringResource(...)` — **0 uses** in the entire Android app.
- `R.string.*` — **4 uses**, all in `PremiumUpgradeScreen.kt:115,137,146,152`.
- Every other string on every other screen is an English literal.

Consequence: an Arabic or Sorani user who selects their language in Settings sees **an
entirely English app**, including a language picker that appears to work.

### 10.2 Runtime switching is broken on Android

`AppPreferences.setLanguage` (`AppPreferences.kt:43-46`) writes SharedPreferences and updates
a `MutableStateFlow`. It does **not** call `AppCompatDelegate.setApplicationLocales`, does
not touch `LocaleManager`, does not recreate the activity, and does not override
configuration. `AndroidManifest.xml` has **no `android:localeConfig`**. Selecting a language
changes zero pixels.

**MUST**, on Android:

1. Add `res/xml/locales_config.xml` and `android:localeConfig="@xml/locales_config"`.
2. Call `AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))`
   from `setLanguage`, or wrap `attachBaseContext` with a configuration override.
3. Replace **every** hardcoded literal with `stringResource(R.string.x)`. Start with the
   drawer labels (`AppShellScreen.kt:271-298`), the settings rows
   (`SettingsScreen.kt:181-349`), the vault list (`VaultListScreen.kt:208-241`), the breach
   monitor (`BreachMonitorScreen.kt:81-200`), the VPN screen, and the two form screens with
   30+ labels each (`VaultItemDetailScreen.kt:177-241`,
   `VaultItemEditorScreen.kt:91-156`).
4. Resolve drawer labels from a `localizationKey` on the page enum, as iOS does
   (`AppSession.swift:70-84`), rather than a hardcoded `menuLabel`.

**MUST NOT** use `t.name.lowercase().replaceFirstChar { it.titlecase() }` for enum-derived
labels (`WelcomePreferencesScreen.kt:104`, `SettingsScreen.kt:281`) — it can only ever
produce English. Use catalog keys.

### 10.3 Font and case

Covered in §3.1: one bundled family (IBM Plex Sans Arabic) for every language; drop mono and
zero the tracking for `ckb`/`ar`, and suppress `.uppercase()`.

**MUST** add `displayName` to `AppLanguage`, matching iOS (`AppPreferences.swift:9-15`).
Android currently inlines a `when` in `WelcomePreferencesScreen.kt:92-96` and uses
`l.code.uppercase()` in `SettingsScreen.kt:299`, so **the two language pickers look nothing
alike.** One implementation, one appearance.

### 10.4 RTL

`android:supportsRtl="true"` is declared, but because the app never overrides the locale,
picking `ckb` does not flip the app to RTL. Fixing §10.2 fixes most of this.

Additional **MUST** items:

- Drawer slide direction keyed off `LocalLayoutDirection.current` (§6.1).
- `horizontalScroll` chip strips must reverse in RTL. `VaultListScreen.kt:214` does not, so
  the last chip is unreachable in Sorani.
- `NavChevronRow` and every other directional icon uses `AutoMirrored`.
- **Number and date formatting must be locale-aware.** Android hardcodes US formats:
  `"%,d".format(plan.amount_iqd)` (`PremiumUpgradeScreen.kt:204`),
  `"${days}-day access"` (`:221`), `"${atRisk.size} of $limit slots used"`
  (`BreachMonitorScreen.kt:111`), `"Last updated: Aug 25, 2026"`
  (`LegalPlaceholderScreen.kt:28`). Use `NumberFormat` / `DateFormat` with the active locale,
  or format-string resources.
- **Pluralization.** Android hand-rolls `"1 item"`/`"$count items"`
  (`CollectionsScreen.kt:112`) and `"address"`/`"addresses"`
  (`BreachMonitorScreen.kt:134`). Use `plurals.xml` — there are currently **zero** plurals
  in the repo.
- **iOS bug:** `PremiumUpgradeView.swift:317` embeds a hardcoded 3-line
  `"First\nIraqi\nBank"`, and `LANSyncView.swift:66` hardcodes `"6-digit code"`. Both must
  go to the catalog.
- **iOS bug:** `PremiumUpgradeView` compares `interval == "yearly"` case-sensitively while
  Android uses `contains("year", ignoreCase = true)`. If the API returns `"YEARLY"`, iOS
  labels it monthly. Normalise the comparison on both.

---

## 11. Theming

`AppThemeMode { SYSTEM, LIGHT, DARK }`, persisted under `"qasa.theme"`, default `SYSTEM`,
resolved with `isSystemInDarkTheme()` / `ColorScheme`. iOS and Android agree.

**MUST** fix on Android: system bar icon appearance is never updated.
`enableEdgeToEdge()` is called unconditionally (`MainActivity.kt:26`) and nothing sets
`isAppearanceLightStatusBars`, so **in light theme the status bar icons are light-on-light
and effectively invisible.** Add a `SideEffect` in `QasaTheme`:

```kotlin
val view = LocalView.current
val dark = LocalConfiguration.current.isSystemInDarkTheme()
if (!view.isInEditMode) {
    SideEffect {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}
```

**SHOULD** set `android:forceDarkAllowed="false"` explicitly in both `themes.xml` variants
and add `values-night/colors.xml` for the XML-consumed tokens.

`FilterChipButton` branches on `c.isDark` — a boolean carried inside the color object
(`QasaComponents.kt:222`). iOS uses `@Environment(\.colorScheme)`. **MUST** branch on the
resolved scheme, not on a field inside the palette.

`PrimaryButton`'s disabled state `accent @ 45%` composites differently over `background` in
light vs dark. Pick alphas that hold contrast in both.

---

## 12. Logic parity contract

The design system is the visible half. This section is the behavioural half, and it is where
Android diverges most.

### 12.1 App state machine

iOS states: `launching → (welcomePreferences) → (createVault | login) → unlocked shell`,
plus `vaultStoreFailure` and an `obscured` privacy-shield overlay. Transitions are driven by
`qasaApp.swift:96-218` and `AppSession.swift`.

| Transition | iOS | Android | Action |
| --- | --- | --- | --- |
| First launch | Welcome (language + theme) | Welcome, but `welcomeDone` is `remember`-only | Read from prefs; fix order |
| Create vault | Derive keys, store verifier, seed session | ✅ | — |
| Unlock | Derive, **constant-time** compare verifier | `ByteArray.contentEquals` — timing-variable | Use `MessageDigest.isEqual` |
| Auto-lock | Timeout + background duration | ✅ equivalent | — |
| Manual lock | **Confirmation alert** (`SettingsView.swift:228-235`) | Immediate, no confirm | Add the alert |
| Lock side effects | Lock + clear repository cache + reset model context | `session.lock()` only | Port the cache clear |
| Bootstrap error | Surfaced alert + a config-error banner in a `safeAreaInset` | `catch (_: Exception) {}` — silent | Surface errors |
| Config validation | Release requires HTTPS; banner if invalid | None | Port the check |
| Obscured | Privacy shield on `.inactive` | **None** | `FLAG_SECURE` + shield |

**Bootstrap sequence** (`AppBootstrap.swift`) — Android must match the order:
configure/migrate the vault → warm the cache → bootstrap the license → register the device →
refresh the license → seed debug data → register for push → sync locale to the server. On
logout, reverse it. `syncLocaleToServer` must also fire on **language change**, not only on
unlock (iOS `AppPreferences.swift:73`).

### 12.2 Crypto — byte-exact

Android and iOS currently **agree** on all of this. It must not drift.

| Parameter | Value |
| --- | --- |
| KDF | PBKDF2-HMAC-SHA256 |
| Iterations | **310 000** |
| Salt | 32 bytes (all-zero when absent) |
| Root key | 32 bytes |
| Verifier | `SHA-256(rootKey)` |
| Subkey derivation | HKDF-SHA256 |
| `enc` subkey info | `"qasa-enc"` |
| `search` subkey info | `"qasa-search"` |
| Cipher | AES-256-GCM |
| Nonce | 12 bytes, prefix |
| Tag | 16 bytes, suffix |
| Blob layout | `nonce ‖ ciphertext ‖ tag` |
| kdfVersion | big-endian `Int32`; absent = 1 |
| Metadata prefs file | `io.kaso.qasa.vault` |

Gaps to close on Android:

1. **kdfVersion is not persisted.** iOS stores it, refuses newer versions
   (`unsupportedKdfVersion`), and treats a legacy absent value as 1.
   **MUST** implement — otherwise a future KDF bump locks Android users out.
2. **No salt-length validation.** iOS requires `>= 16`.
3. **No constant-time verifier compare.** Use `MessageDigest.isEqual` (Android's
   `contentEquals` short-circuits).

Android's explicit key zeroization (`VaultSession`) is *better* than iOS and should be kept.

CSV column order, for byte-identical exports
(`VaultCSV.swift`, columns at `:2-10`):
`type, title, username, password, url, email, account_holder, number, security_code, expiry,
account_number, routing_number, bank_name, content, notes, fields`.

### 12.3 Data model vocabulary

The payload envelope is portable across platforms — both use identical AES-GCM — but the
JSON *content* vocabulary diverges, so filters and search can disagree:

| Value | iOS | Android |
| --- | --- | --- |
| Personal email type | `"Personal"` | `"personal"` |
| Personal phone type | `"Cell"` | `"cell"` |
| Honorific | Capitalized | lowercase |
| ID subtype | `idCard` | `nationalId` |
| Legacy payloads | `legacyPayloadData` | absent |

**MUST** pick one spelling per platform and normalise on read — accept both, write one.
**MUST** add the `legacyPayloadData` column for forward compatibility.

The item-type taxonomy must match exactly:
`login`, `secureNote`, `payment` → `card` | `bankAccount`,
`personalInfo` → `identity` | `personal`,
`idDocument` → `passport` | `nationalId` | `driverLicense` | `other`.

### 12.4 Search index

Both build an on-device index of word tokens, 2–3 character n-grams, and HMAC-SHA256 hashes.

**Android's tokenizer is ASCII-only** — `[^a-z0-9]+` (`VaultSearchIndex.kt:6-32`) — while
iOS uses Unicode `CharacterSet.alphanumerics` (`VaultSearchIndex.swift:5-35`). Android
therefore produces **zero tokens for Arabic and Sorani text**. Two of the three shipped
languages are unsearchable.

**MUST** switch to `Regex("[^\\p{L}\\p{N}]+", RegexOption.UNICODE_CHARACTER_CLASS)`.
**MUST** add a test with Arabic and Sorani input.

### 12.5 Autofill

iOS ships three things. Android ships one and a half.

| Capability | iOS | Android |
| --- | --- | --- |
| Classic Autofill / Credential Provider | ✅ | ✅ (returns `null` while locked) |
| **Extension-local unlock** (password + biometric inside the provider UI) | ✅ `AutofillUnlock.swift` | ❌ |
| **Context ranking** — exact host +100, subdomain +80, same registrable domain +60, substring +30, title/subtitle +20, kind/hint base 10, tie-break case-insensitive title | ✅ `AutofillContextMatcher.swift:11-49` | ❌ |
| **Credential Identity Store** | ✅ `AutofillIdentitySync.swift` | ❌ |
| **Ephemeral payment session** — 30s TTL, per-field drawer, countdown, `•••• last4` masking, clears the pasteboard only if still owned | ✅ `EphemeralSecretDrawer.swift:7-31` | ❌ |
| Payment notification with a 30s expiry and a content extension | ✅ | ❌ |
| Malicious-`PendingIntent` guard | n/a | ⚠ `QasaCredentialProviderService.kt:21-40` uses a **mutable** `PendingIntent` — make it immutable |

**MUST** port the matcher. It is the entire value of an autofill service; without it Android
returns an unranked list. Port the index-rebuild-on-unlock contract, the Credential Identity
Store registration, and the 30-second ephemeral drawer for card/bank fields — including the
`UIPasteboard.changeCount` ownership check, which maps to
`ClipboardManager.addPrimaryClip` with a listener plus a wipe on expiry so Android does not
clear a clipboard entry the user has since replaced.

**Security fix, iOS:** `PaymentClipboard.swift:1-49` routes card/bank copies through the
ephemeral drawer with a 30s TTL. Android's `VaultItemDetailScreen.kt:150-160` and
`VaultListScreen.kt:254` copy card numbers to the clipboard with **no TTL**, so a copied card
number persists until the user overwrites it. **Port the TTL.**

### 12.6 Licensing — Android's most severe defect

iOS verifies the license JWT's **Ed25519 signature** against a pinned public key
(`LicenseTokenVerifier.swift:12-44`), caching it under `qasa.license.verifyingKey.b64` and
refreshing from `/.well-known/qasa-license-key`, and **fails closed** with no key
(`LicenseManager.swift:28-49`). It also **rejects** a bad token and preserves the good one
(`applyToken`).

Android's `LicenseManager` calls `parseJWT(token)` and trusts the claims
(`LicenseManager.kt:41-52`). **A locally forged token grants premium and raises the device
limit.** There is no signature check anywhere in the Android app.

**MUST** port `LicenseTokenVerifier`: Ed25519 verification, the pinned-then-fetched key
cache, fail-closed behaviour, "reject bad, preserve good" in `applyToken`, and the
retry-once-after-key-rotation path.

### 12.7 Biometrics — Android's second-severe defect

iOS stores the master password in the Keychain under
`kSecAttrAccessibleWhenUnlockedThisDeviceOnly` with `.biometryCurrentSet`, and gates reads
behind `LAContext.evaluatePolicy`. `unlockIfNeeded` **fails closed** — a
`canEvaluatePolicy` failure returns `completion(false)` (`FormHelpers.swift:203-220`).

Android stores the master password as a **plaintext string in
`EncryptedSharedPreferences`** (`VaultSecureStore.kt:19-29,68`) and
`UnlockScreen.kt:110-146` reads it back with **no `BiometricPrompt` anywhere in the app**
(grep confirmed zero hits). Anyone with the device unlocked, or with ADB/root access, gets
the master password.

**MUST** use `EncryptedSharedPreferences` with
`MasterKey.KeyScheme.AES256_GCM` **plus** a `BiometricPrompt`-gated read — or better,
`setUserAuthenticationRequired(true)` on the key with a 0-second timeout and
`setUserAuthenticationParameters(0, AUTH_BIOMETRIC_STRONG | AUTH_DEVICE_CREDENTIAL)` so the
master key itself is hardware-gated. **MUST** show a real `BiometricPrompt`. **MUST** fail
closed.

Also: `VaultItemDetailScreen.kt:111` reveals protected notes with **no biometric gate at
all** ("parity stub"). iOS gates on real biometrics. **MUST** port the gate.

**MUST** fix the iOS-side bug too: `VaultItemDetailView.swift:88-91` re-triggers
`evaluatePolicy` on **every `updatedAt` change** plus `onAppear`, so the user is prompted
repeatedly during a background cache refresh. Evaluate once per unlock.

### 12.8 CSV import and export

iOS has a working implementation: `VaultCSV.swift` (356 lines) with a fixed 16-column
header, import with fingerprint-based dedupe, and export through `ShareLink` over
`CSVDocument` (`SettingsView.swift:164-190`).

Android's two settings rows are `onClick = { }` (`SettingsScreen.kt:312,317`) — the export
row's own subtitle says "Empty export stub (iOS parity)", which is **not** parity; iOS's
works.

**MUST** port `VaultCSV` and the `ShareLink` equivalent (`ActivityResultContracts.CreateDocument`).
**MUST** spill to a cache file marked `excludeFromBackup`, never the clipboard (§6.7).

### 12.9 LAN sync

**iOS has a real implementation**: `NWListener`/`NWBrowser` over `_qasa-sync._tcp`, an
HMAC-SHA256 challenge keyed on the pair code, and a length-prefixed transfer
(`LANVaultSyncService.swift:139-143`).

**Android's `LANVaultSyncService` is a status-string placeholder with no networking at all**
(`LANVaultSyncService.kt:20-34`). The feature is entirely non-functional.

Both platforms transmit the literal string `qasa-vault-placeholder:{accountId}` instead of
vault data, and **both write a peer-supplied `accountId` into secure storage with only the
pair code as trust** (`LANSyncView.swift:102-108`, `LANSyncScreen.kt:105-110`). A hostile
LAN peer that learns the 6-digit code can rebind the device to another account. This is
live on iOS today.

**MUST** implement the transport and, before shipping it, fix the account-rebinding hole on
both platforms: derive the account id locally, or require a second authenticated channel.

### 12.10 Subscriptions and payments

Intent creation, polling (30 × 2s), license refresh, and release-build refusal to fall back
to a stub webhook all match. Remaining Android gaps:

1. **`ManageSubscriptionScreen` has two live buttons that both call `cancel()`**
   (`:105-134`). iOS has one cancel behind a confirm, then a reason sheet.
2. Pricing plans are not sorted by `sort_order` (`PricingService.swift:19`).
3. The pricing fallback lives in the screen, not the service, and does not preserve the last
   good value.
4. No "already premium" state — iOS disables the button and says so.
5. `statusMessage` and `errorMessage` are conflated into one field; iOS separates them
   (accent vs danger).
6. Feature bullets are one hardcoded string (§6.3).

### 12.11 Other services

| Service | Gap |
| --- | --- |
| **Breach monitor** | Android has **no `MonitoredEmail` table** — the list is in-memory and lost on restart. iOS persists, and has unresolved/resolved tabs, rescan, slot limit, duplicate check, and remove-with-confirm. **MUST** port persistence and the tabs. |
| **Notifications** | Android reads `prefs.language.value.code` **directly from the `StateFlow`** instead of `collectAsState()` (`NotificationsScreen.kt:56`), so the list does not re-localize live. No local notes, no detail sheet, no mark-all. |
| **Push** | Android's `onNewToken` stores the token but **never uploads it**. Port the iOS behaviour. |
| **Password health** | iOS requires length ≥ 10 and scores by character-class count, trims input and skips blanks. Android uses length < 8, a common-password list and all-digits, and does neither. **Align the rules** — the score is a user-visible number. |
| **Generator** | Android guarantees one character from each selected class and shuffles; iOS uses `pool.randomElement()` with no class guarantee. **Android's is better — port it to iOS.** Android allows length up to 256; iOS caps at 32 (`8...32`). Pick one cap. |
| **Linked devices** | Android's unlink has no confirm dialog; iOS distinguishes "log out device" from "unlink", shows the activation date and a platform icon, and blocks primary unlink when other devices exist. Port all of it. |
| **Auto-lock UI** | iOS uses a sheet with a stepper 1–60 plus 8 presets `[1,2,5,10,15,30,45,60]`. Android inlines 5 chips `[1,5,15,30,60]`. **Port `AutoLockSettingsView`.** |
| **Collections** | Android can create and delete only. iOS can rename and drill into contents. **Port `CollectionDetailView` and rename.** |
| **Config error banner** | iOS shows a banner when a release build cannot reach a valid HTTPS host. Android has none. |
| **FCM vs APNs** | `PushChannels.kt` needs review; iOS registers notification categories, Android does not. |
| **Save-to-vault from the generator** | A no-op on both (`GenerateView.swift:28-39`, `GenerateScreen.kt:205`). Implement it or disable the button. |

### 12.12 No-op buttons

An enabled control that does nothing reads as a bug. Every instance must be implemented or
disabled.

| Location | Action |
| --- | --- |
| `SettingsScreen.kt:312` | CSV import — `onClick = { }` |
| `SettingsScreen.kt:317` | CSV export — `onClick = { }` |
| `GenerateScreen.kt:206` | Save to vault — no-op on both platforms |
| `BreachCard.swift:89` (iOS) | Rotate password — empty `Button {}` |
| `AddItemMenuView.swift:66` (iOS) | Renders the raw key `"common.cancel"` |

### 12.13 No-ops that are intentional

Do **not** "fix" these — the absence is the design:

- `VaultStoreErrorView` offers no retry and no reset; it never deletes a store to recover
  (`VaultStoreErrorView.swift:17-22`).
- CSV export is deliberately a plaintext temp file, **not** the clipboard
  (`CSVDocument.swift:4-10` records the regression it replaced).
- The privacy shield is driven by `AutoLockMonitor`, not `scenePhase`.

---

## 13. iOS defects — do not port

Fix these on iOS while porting the correct behaviour to Android.

| Defect | Location |
| --- | --- |
| Half-migrated theme: static `Theme.*` facade alongside `themePalette`; 26 of 29 views read the static one | `Theme.swift:107-108` |
| `themedBackground()` defined, zero call sites | `Theme.swift:193-205` |
| `StatBox`, `BilingualToggleRow`, `AppTypography.uiFont`, `textPlaceholder`, `dangerTextMuted`, and 4 legacy aliases all unused | `Components.swift:186,375`, `AppTypography.swift:22` |
| `.kasoMono` is never localized — passwords render in SF Mono in ckb/ar | `Theme.swift:183-185` |
| `Font.qasa` missing from 10 files, 76 sites — no Kurdish glyphs in the vault list, breach cards, settings, collections, or login | see §3.2 |
| 3 chip implementations, 3 text-field variants, 3 toggle styles, 4 hand-rolled headers, 4 section-label idioms | §6 |
| ~22 hand-rolled card copies of `CardContainer`'s exact modifier chain | §6.3 |
| No `ButtonStyle` anywhere; mixed plain/default flash | §8.3 |
| One haptic app-wide, allocating a generator per call | §8.2 |
| No `@FocusState`, no `scrollDismissesKeyboard` — the keyboard covers the CTA on 4 screens | §6.4 |
| No `contextMenu`, no `swipeActions` — delete is 3 taps deep on the vault list | §6.5 |
| 350ms `asyncAfter` to chain two sheets | §7.2 |
| `AddressPhonePicker` decrypts every vault row on every render | `VaultItemEditor.swift:733-738` |
| `LAContext` re-prompts on every cache update | `VaultItemDetailView.swift:88-91` |
| Drawer is a `ZStack` sibling — no focus trap, no swipe-dismiss | `AppShellView.swift:61-72` |
| Root background flips between `background` and `chrome` across 6 screens | §2.2 rule 7 |
| LAN sync writes a peer-supplied `accountId` | `LANSyncView.swift:102-108` |
| `Button("common.cancel")` leaks a raw key | `AddItemMenuView.swift:66` |
| Hardcoded `"6-digit code"`, `"First\nIraqi\nBank"` | `LANSyncView.swift:66`, `PremiumUpgradeView.swift:317` |
| VPN kill-switch and auto-connect toggles are `useState`, never persisted | `VPNView.swift:10-11` |
| `ContentView.swift` / `Item.swift` are Xcode template scaffolding | — |

**Dead code to delete:** iOS `MainTabView.swift`, `ContentView.swift`, `Item.swift`; Android
`Routes.kt:3-9` (`RootRoutes`), `Routes.kt:14,15` (`VaultRoutes.ADD`/`EDIT`),
`WelcomePreferencesScreen.kt:132-148` (`SelectRow`, private and single-use),
`QasaComponents.kt:204` (unused `val c`), and the unused imports in `QasaComponents.kt`.

---

## 14. Android gap ledger

Ordered by user impact. `docs/design.md` § references point at the rule that governs.

### P0 — security and data integrity

| # | Gap | Ref |
| --- | --- | --- |
| 1 | License JWT is never Ed25519-verified — a forged token grants premium | §12.6 |
| 2 | Master password cached in `EncryptedSharedPreferences` with no `BiometricPrompt` | §12.7 |
| 3 | No `FLAG_SECURE`, no privacy shield — vault contents in Recents and screenshots | §6.7 |
| 4 | Protected notes reveal with no biometric gate | §12.7 |
| 5 | Card/bank copies have no 30s ephemeral TTL | §12.5 |
| 6 | Destructive vault-store fallback in release builds | §6.7 |

### P1 — the app is broken for 2 of 3 languages

| # | Gap | Ref |
| --- | --- | --- |
| 7 | `setLanguage` is a no-op — no `localeConfig`, no locale override | §10.2 |
| 8 | 555 of 559 catalog keys unused; `stringResource` count is 0 | §10.1 |
| 9 | ~~No Kurdish font; ckb/ar render in Roboto~~ Fixed: IBM Plex Sans Arabic is bundled | §3.1 |
| 10 | Search index is ASCII-only — Arabic and Sorani are unsearchable | §12.4 |
| 11 | Drawer and chip strips are not RTL-aware | §10.4 |
| 12 | US-format number and date strings; no plurals | §10.4 |

### P2 — the app looks visibly wrong

| # | Gap | Ref |
| --- | --- | --- |
| 13 | Every border renders at ~0.5dp because of `.border().background()` ordering | §2.3 |
| 14 | 99 raw `fontSize` bypass the type scale; no `lineHeight` anywhere | §3.2 |
| 15 | 245 raw `.dp`; 5 `QasaDimens` tokens; `17.dp` magic number | §4.1 |
| 16 | No Arabic-script mono-swap or tracking zeroing; `.uppercase()` forced on ckb/ar | §3.1 |
| 17 | `MintFab` has no accent border and the wrong shadow; the icon is hardcoded | §6.1 |
| 18 | `CheckToggle` thumb is hardcoded `#2563EB`; no animation; no `toggleable` | §6.4 |
| 19 | `HalfCircleGauge` is a flat stepped color, unanimated, number inside | §6.6 |
| 20 | `VpnPowerDial` diverges in every dimension | §6.6 |
| 21 | 4 header title sizes (24/20/20/18) and 2 weights | §6.1 |
| 22 | Status bar icons are invisible in light theme | §11 |
| 23 | `ScreenScaffold` has no `WindowInsets`; `StickyBottomBar` has no `imePadding()` | §4.2, §6.1 |
| 24 | `QasaModalBottomSheet` does not zero `tonalElevation` | §7.2 |

### P3 — missing components and features

| # | Gap | Ref |
| --- | --- | --- |
| 25 | `BreachCard` + `FlowLayout` — breach objects are never surfaced at all | §6.6 |
| 26 | `VaultItemRow` overflow menu: per-field copy, open website, edit, delete, toast | §6.5 |
| 27 | `FormHelpers`: `FormTextField`, `CollectionPickerField`, `NoteColorPicker`, `CountryPickerField`, `VaultFormChrome` | §6.4, §6.7 |
| 28 | `ValidatedDateField` | §6.4 |
| 29 | `PasswordGeneratorModal` reachable from the editor; no show/hide password | §6.4 |
| 30 | `LocalizedToggleRow` with a real disabled state | §6.4 |
| 31 | `FeatureRow` and per-plan localized feature bullets | §6.3 |
| 32 | `VaultPrivacyShield` overlay and `VaultStoreErrorView` | §6.7 |
| 33 | `AutoLockSettingsView` sheet (stepper + 8 presets vs 5 inline chips) | §12.11 |
| 34 | `CollectionDetailView` and collection rename | §12.11 |
| 35 | Add-item menu drill-down (flat list vs hierarchical) | §7.3 |
| 36 | Status bar, banner, and toast components | §6.6 |
| 37 | Editor is missing ~40% of the model: collection, note colour/protect, structured address, ID issue/expiry/authority, honorific, middle name, birth date, email/phone type sub-variants | §12.3 |
| 38 | `CSVDocument` and the full `VaultCSV` implementation | §12.8 |

### P4 — logic and service parity

| # | Gap | Ref |
| --- | --- | --- |
| 39 | kdfVersion not persisted; no newer-KDF rejection; no salt-length validation; non-constant-time verifier compare | §12.2 |
| 40 | Subtype/string vocabulary drift from iOS; no `legacyPayloadData` | §12.3 |
| 41 | `LANVaultSyncService` has no networking — the feature does not work | §12.9 |
| 42 | Autofill: no extension-local unlock, no context matcher, no identity store, no ephemeral drawer | §12.5 |
| 43 | Mutable `PendingIntent` in the credential provider | §12.5 |
| 44 | Breach monitor has no persistence — the email list is in-memory | §12.11 |
| 45 | Push token stored but never uploaded on `onNewToken` | §12.11 |
| 46 | Password health rules differ from iOS | §12.11 |
| 47 | CSV import/export are no-ops | §12.8 |
| 48 | `ManageSubscriptionScreen` cancels twice; no plan sort; no already-premium state | §12.10 |
| 49 | Manual lock has no confirmation; lock does not clear the repository cache | §12.1 |
| 50 | Bootstrap errors are swallowed; no config validation banner | §12.1 |
| 51 | `RootNav` order wrong; `welcomeDone` is `remember`-only | §12.1 |
| 52 | Notifications read the `StateFlow` directly, so they do not re-localize | §12.11 |
| 53 | Generator is stronger than iOS — port it back to iOS; length cap 256 vs 32 | §12.11 |

### P5 — accessibility and polish

| # | Gap | Ref |
| --- | --- | --- |
| 54 | Zero `Modifier.semantics` calls in the app | §9 |
| 55 | No `toggleable` / `selectable` / `Role` on any control | §9 |
| 56 | Touch targets under 48dp in 4 components | §9 |
| 57 | Unlocalized `contentDescription`s; `"›"` / `"⧉"` / `"✓"` text glyphs | §9, §5 |
| 58 | `ThemedTextField` has no label, no `imeAction` wiring | §6.4, §9 |
| 59 | No live region on error messages | §9 |
| 60 | No shared copy helper or toast; 3 copy-paste snippets | §8.2 |
| 61 | 5 copy-pasted "requires premium" upsell cards with inconsistent alpha | §6.3 |
| 62 | 2 independent alert-dialog implementations | §6.6 |
| 63 | 5 copies of the circular-bordered icon tile at 5 sizes and 3 radii | §6.1 |
| 64 | No `ButtonStyle` — mixed press feedback | §8.3 |
| 65 | No motion tokens; no reduced-motion support | §8.1 |

---

## 15. Acceptance checklist

Before any Android change is called done:

- [ ] `python3 l10n/generate.py --check` passes; **zero** new hardcoded user-visible strings
- [ ] No `Color(0x…)` outside `QasaTheme.kt` and an explicitly-commented `BrandColors` object
- [ ] No raw `fontSize = N.sp` in `screens/`; every size comes from the scale
- [ ] No raw `N.dp` outside `QasaDimens`
- [ ] Every `.border()` precedes nothing — background is painted first, then the stroke
- [ ] Every direction-bearing icon uses `Icons.AutoMirrored`
- [ ] Every interactive control is ≥48dp and carries a role
- [ ] Every icon-only control has a localized `contentDescription`
- [ ] Every new screen goes through `ScreenScaffold` + `StickyBottomBar`, with insets handled
- [ ] Every new component is added to §6 with both platforms' implementations
- [ ] Crypto constants match §12.2; `MasterKeyDeriverTest` and `PasswordGeneratorTest` pass
- [ ] A cross-platform round trip is verified: create a vault on iOS, open it on Android, and
      read every item kind
- [ ] No enabled control has an empty `onClick` (§12.12)
- [ ] `dangerTextMuted` exists in `QasaColors`
- [ ] Status bar icon appearance tracks the resolved theme
