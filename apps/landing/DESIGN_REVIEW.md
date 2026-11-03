# Landing page design review (2026-11-03)

A research pass before any redesign, following the "research, report, wait" workflow. Nothing
here is built yet. "Generic" below is design judgement, not a test.

## How the research was done

The Mobbin connector needs a paid plan, so this used the free method instead: real, shipped
sites opened in a browser, looking at the hero and one or two sections each.

| Site | Why it's relevant | Usable? |
| --- | --- | --- |
| [Proton Pass](https://proton.me/pass) | Consumer password manager | Yes |
| [Bitwarden](https://bitwarden.com/) | Password manager | Partly (now mostly business) |
| [1Password](https://1password.com/) | Password manager | Barely (business pivot, cookie wall) |
| [Signal](https://signal.org/) | Privacy-first consumer app | Yes |
| [FIB](https://fib.iq/) | Iraqi fintech, same audience | Yes |
| [Tabby (Arabic)](https://tabby.ai/ar-AE) | Gulf fintech, right-to-left | Yes |
| [Things](https://culturedcode.com/things/) | Calm, restrained consumer app | Yes |

Apple's Passwords page returned a 404. Seven sites is a small set. Widen it before building
if one site starts dominating the plan.

## 1. Where the current page feels generic

**The page has the right ingredients, but its hero says "premium security app" through
decoration rather than through the product.**

- **Hero: too many devices competing.**
  - Three blurred colour blobs.
  - Three chips that float on a loop.
  - An eyebrow pill and an accent-coloured headline phrase.
  - A phone mockup, then a payment strip straight after.
  - None of the references do more than two of these. The chips repeat what the phone
    could show itself.
- **The product preview is a drawing.** The phone is an HTML imitation of the Passwords
  screen (sample user "Sara", Facebook, Gmail). It's readable, but it isn't the app. Every
  strong reference shows real screens.
- **The features section is a five-tile bento.** Every tile has the same weight and the same
  icon-title-body shape, so nothing leads. Item types and language support get as much room
  as autofill and breach checks, which are the reasons to install.
- **Headlines share one rhythm.**
  - "We can't see your passwords. Not even if we wanted to."
  - "Start free. Upgrade when you need more."
  - "Give your passwords a safe home."
  - Each is a short statement plus a contrast or sentiment, which reads as a
    template.
- **Some lines are mood words, not facts.** "one calm, organized home", "its own tidy space",
  "whatever feels like yours".
- **Stale icons (bug).**
  - The hero's third chip uses `mail-exclamation`.
  - The breach feature tile uses a mail icon.
  - Both still point at the parked email alerts.

## 2. What the strong references have in common

1. **One upright product image.** Proton, Signal and FIB each show real app screens, flat,
   with no tilt and no orbiting cards. FIB puts the phone in a real hand. Things goes
   further: one icon, one sentence, one link.
2. **A trust row right under the call to action, made only of things you can check.**
   - Proton shows app-store rating, users and press logos.
   - Tabby shows rating counts.
   - FIB shows 1M+ users and 1,000+ cash points.
   - Qasa has no numbers yet, so the row can only use facts that are true today. Nobody
     in the set uses invented testimonials.
3. **Each section headline names one concrete benefit, often with a number.**
   - Tabby: "up to 3% cashback", "send money with no transfer fees".
   - Proton: "Log in faster with autofill".
   - The benefit is in the headline, not saved for the body text.
4. **Security is shown through mechanisms or authorities, not reassurance.**
   - Signal names its protocol and says it's open source.
   - Tabby cites its central-bank regulator.
   - FIB lists concrete measures: biometrics, MFA, real-time notifications, user-controlled
     settings.
5. **Local trust signals.**
   - FIB opens with a banner saying "FIB never asks for your card details".
   - FIB and Tabby show people and places from the region.
   - In Iraq, scam calls are a real worry, and this speaks to it directly.
6. **Proper right-to-left with its own type.** Tabby's Arabic site is fully mirrored with
   Arabic-first typography. Qasa already does this, and it's a genuine edge to keep.
7. **A path from desktop to phone.** Tabby puts a QR code next to its download button, so a
   desktop visitor can install on their phone in one step.

## 3. Proposed direction

**"Show the vault, don't decorate it."** The app's own screens lead every section. The
theme tokens stay as they are (Theme.swift is the design system). Decoration is removed,
and each headline says one specific thing.

## 4. Change list

**Progress:** hero items 1–6 and features items 7–8 are done (2026-11-03). The autofill row
still uses a drawn fill card: a real shot needs the AutoFill extension driven from Safari. The wallet strip now uses the real FIB,
FastPay and SuperQi logos (`assets/payment-logos/`).

### Hero

1. **Remove the three blobs.** Use the plain background, or one soft wash at most. Decoration
   adds no information.
2. **Stop the chip animation, and keep at most one chip:** "1 breached password", because it
   shows the free feature. Floating cards are the most common AI-made tell, and they compete
   with the phone.
3. **Replace the HTML phone with real screenshots** from the simulator, one set per language
   (en, ckb, ar). Use a clean test vault with realistic local entries such as FIB, Asiacell,
   Korek and Instagram, and no real data. Real product evidence is the strongest pattern
   above, and a properly right-to-left Kurdish screenshot is something competitors can't
   show.
4. **Fix the stale mail icons** on the hero chip and the breach tile. They're wrong today.
5. **Rewrite the line under the headline to say what Qasa does.** For example: "Saves and
   fills your passwords, cards and IDs. Everything is encrypted on your phone with one main
   password." The headline itself can stay. Pattern 3: concrete over mood.
6. **Turn the "No card needed" line into a trust row of true facts:** "Free on 1 device ·
   English, کوردی, العربية · Your vault never touches our server". Pattern 2, with no
   invented numbers.

### Features

7. **Replace the five-tile bento with three alternating rows**, each with a real screenshot:
   - autofill (the fill sheet),
   - breached passwords (the Alerts tab),
   - password health.

   Item types and languages shrink to one compact line. Patterns 1 and 3: one idea per
   block, ordered by why people install.
8. **Headlines that state the benefit.** For example: "Fills your login inside apps and
   browsers", "Checks every saved password against known leaks, free", "Shows which passwords
   are reused or easy to guess". The mood words go.

### Privacy

9. **Name the mechanism in one line:** "AES-256-GCM, with a key made from your main password
   (PBKDF2, 310,000 rounds). The key never leaves your phone." Taken from ARCHITECTURE.md §5.
   Pattern 4: mechanisms over reassurance. Keep the server diagram, simplified.
10. **Add a local trust line:** "Qasa will never ask for your main password: not by phone,
    WhatsApp or email." Pattern 5, the FIB banner idea. It needs ckb and ar copy.
11. **Rewrite the headline** so it isn't the "statement. Contrast." beat. For example: "Your
    passwords are encrypted before they're saved, and only on your phone."

### Pricing, FAQ and closing

12. **Move the FIB / FastPay / SuperQi strip into the Premium card**, next to the price. It
    lightens the area under the hero, and the wallets matter most at the moment someone is
    looking at the price.
13. **Rewrite the closing headline** ("Give your passwords a safe home") into something
    concrete, such as "Set up Qasa in a minute. Free on your phone."
14. **Keep the pricing cards and FAQ as they are.** The prices are real, there's no invented
    social proof, and the FAQ answers real questions.

### Later, not now

15. **A QR code next to the store buttons** for desktop visitors (pattern 7), once the store
    links exist. The download buttons stay as they are for now.

## Risks and open questions

- **Screenshots take work.** They mean running the app in the simulator (and ideally the
  Android emulator) with a seeded test vault in three languages, plus a refresh process when
  the UI changes.
- **Every rewritten line needs Kurdish and Arabic copy**, and a native speaker should review it.
- **The reference set is small** (seven sites, two of them weak), so treat the patterns as
  directional.
- **Mobile layout hasn't been re-checked** for these changes. It gets checked after the build.

## Suggested build order

Build in fenced passes, each touching one section only: hero → features → privacy →
pricing strip and closing. Check mobile and right-to-left after each pass.
