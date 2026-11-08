# Store listings: Chrome Web Store, Edge Add-ons, Firefox Add-ons

Copy for the three store dashboards. Upload files come from `bun run package` (see
[`README.md`](../README.md#store-packages)). Store locales are English (default) and Arabic
only: the stores have no Sorani (`ckb`) locale, so Kurdish users see the English listing
(plan §8.3). The extension itself is in English, Kurdish and Arabic.

The name and short description shown by the browser come from
`static/_locales/{en,ar}/messages.json`; keep this file and those in step.

---

## 1. Name and descriptions

### English (default)

**Name:** Qasa

**Short description** (Chrome summary, 132 characters max; this is the manifest
`extDescription`, 119 characters):

> Fill and save your Qasa logins on websites. Works with the Qasa app on your computer; nothing is stored in the browser.

**Long description:**

> Qasa is a password manager that keeps your vault encrypted on your own devices. This
> extension brings your Qasa logins to the browser.
>
> It works together with the Qasa desktop app for macOS, Windows and Linux. The extension
> stores no passwords: when you pick a login, it asks the desktop app on the same computer
> for it, fills it in, and forgets it.
>
> What it does:
> • Shows the saved logins for the site you're on, and fills the username and password when
>   you choose one.
> • Fills 2FA codes into one-time-code fields, or copies the code after a fill.
> • Offers to save a new login or update a changed password after you sign in. You confirm
>   in the Qasa app.
> • Search your logins, copy a username or password, and generate a strong password from the
>   toolbar.
> • Works with login, sign-up and change-password forms, including multi-step sign-ins and
>   forms inside frames.
>
> Built to be careful:
> • Nothing is filled without a click from you. Nothing happens on page load.
> • The fill menu is protected against hidden-overlay (clickjacking) tricks.
> • A login is only offered on the site it belongs to; a frame from another site can only
>   get logins for its own address.
> • The connection to the desktop app is encrypted, and the app asks you to approve the
>   browser the first time it connects.
> • No accounts, no analytics, no servers: the extension talks only to the Qasa app on your
>   computer.
>
> Requires the Qasa desktop app, installed and running on the same computer:
> https://qasa-app.com/download
>
> In English, Kurdish (Sorani) and Arabic.

### Arabic

**Name:** Qasa

**Short description** (the manifest `extDescription`, 112 characters):

> املأ بيانات تسجيل الدخول في قاصة واحفظها على المواقع. تعمل مع تطبيق قاصة على حاسوبك، ولا يُخزَّن شيء في المتصفح.

**Long description:**

> قاصة مدير كلمات مرور يحفظ خزنتك مشفّرة على أجهزتك أنت. تجلب هذه الإضافة تسجيلات دخولك
> في قاصة إلى المتصفح.
>
> تعمل الإضافة مع تطبيق قاصة لسطح المكتب على macOS وWindows وLinux. لا تحفظ الإضافة أي
> كلمة مرور: عندما تختار تسجيل دخول، تطلبه من تطبيق سطح المكتب على الحاسوب نفسه، وتملؤه، ثم
> تنساه.
>
> ماذا تفعل:
> • تعرض تسجيلات الدخول المحفوظة للموقع الذي تتصفحه، وتملأ اسم المستخدم وكلمة المرور عندما
>   تختار أحدها.
> • تملأ رموز التحقق بخطوتين في حقول الرمز لمرة واحدة، أو تنسخ الرمز بعد التعبئة.
> • تعرض حفظ تسجيل دخول جديد أو تحديث كلمة مرور تغيّرت بعد تسجيل الدخول، وتؤكد ذلك في تطبيق
>   قاصة.
> • ابحث في تسجيلات دخولك، وانسخ اسم المستخدم أو كلمة المرور، وأنشئ كلمة مرور قوية من شريط
>   الأدوات.
> • تعمل مع نماذج تسجيل الدخول وإنشاء الحساب وتغيير كلمة المرور، بما فيها تسجيل الدخول على
>   عدة خطوات والنماذج داخل الإطارات.
>
> مصمّمة بحذر:
> • لا يُملأ أي شيء دون نقرة منك، ولا يحدث شيء عند تحميل الصفحة.
> • قائمة التعبئة محمية من حيل الطبقات المخفية (clickjacking).
> • لا يُعرض تسجيل الدخول إلا على الموقع الذي يخصّه، والإطار القادم من موقع آخر لا يحصل إلا
>   على تسجيلات دخول عنوانه هو.
> • الاتصال بتطبيق سطح المكتب مشفّر، ويطلب منك التطبيق الموافقة على المتصفح في أول اتصال.
> • لا حسابات ولا تحليلات ولا خوادم: تتحدث الإضافة فقط مع تطبيق قاصة على حاسوبك.
>
> تحتاج إلى تطبيق قاصة لسطح المكتب مثبّتًا ومفتوحًا على الحاسوب نفسه:
> https://qasa-app.com/ar/download
>
> بالإنجليزية والكردية (السورانية) والعربية.

**Arabic store copy needs a native speaker's review** before submitting, like the app's
drafted ar/ckb strings.

### Per-store fields

| Field | Chrome Web Store | Edge Add-ons | Firefox Add-ons (AMO) |
| --- | --- | --- | --- |
| Upload | `qasa-chrome-<ver>.zip` | `qasa-chrome-<ver>.zip` (same file) | `qasa-firefox-<ver>.zip`, plus `qasa-extension-source-<ver>.zip` as the source code |
| Name | From the manifest (`extName`) | From the manifest | From the manifest |
| Summary | Manifest `extDescription` (132 max) | "Short description" field: same text | "Summary" (250 max): same text |
| Description | Long description above | Long description above (250 characters min) | Long description above |
| Category | Productivity → Tools (new dashboard: "Productivity") | Productivity | Privacy & Security |
| Language | English, Arabic | English, Arabic | English (default), Arabic |
| Homepage / support URL | https://qasa-app.com/ | https://qasa-app.com/ | https://qasa-app.com/ |
| Privacy policy URL | Required (see §5) | Required | Recommended |

---

## 2. Single purpose (Chrome Web Store)

> Fill and save website logins from the user's Qasa password manager desktop app. The
> extension is a bridge between web pages and the Qasa app on the same computer: it detects
> login forms, fills a login the user picks, and offers to save new or changed logins in the
> app.

---

## 3. Permission justifications

The manifest requests exactly `nativeMessaging`, `storage`, `activeTab` and `scripting`,
plus a content script on `<all_urls>` (no `host_permissions` entry).

**nativeMessaging**

> Qasa keeps the user's passwords in the Qasa desktop app, not in the browser. Native
> messaging is the only channel the extension uses: it connects to the Qasa app's native
> messaging host (com.qasa_app.desktop) to look up the logins for the current site, fill or
> copy one the user picks, generate a password, and send a new or changed login to the app
> for the user to confirm. Messages are end-to-end encrypted with a key agreed during a
> one-time pairing that the user approves in the app.

**storage**

> Stores two small values in chrome.storage.local: the pairing with the desktop app (a
> random client id and the key for the encrypted channel) and the user's chosen display
> language for the popup. No passwords or browsing data are stored. During a sign-in that
> asks for the username and password on separate pages, the username from the first page
> is held in chrome.storage.session (in memory, cleared when the browser closes) for at
> most 10 minutes, so the second page can offer to save both together.

**activeTab**

> When the user opens the toolbar popup, activeTab lets the extension reach the current tab
> to show that site's logins and fill the one the user chooses. It is only used after the
> user clicks the toolbar button, and only on that tab.

**scripting**

> Tabs that were already open when the extension was installed or updated don't have its
> content script yet. When the user opens the popup on such a tab, the extension injects its
> own packaged content script (content.js) into that tab, using the activeTab grant from the
> click, so the user can fill without reloading the page. It never injects remote or
> generated code.

**Host permission: content script on all URLs (`<all_urls>`, all frames)**

> Users sign in on any website, so the content script must run on every page to find login,
> sign-up, change-password and one-time-code fields and show the small Qasa fill icon in
> them. It runs in every frame because many sites put the sign-in form in an iframe; each
> frame only ever gets logins for its own address. The script reads form fields only to
> detect them and to offer saving a login after the user submits a form. It fills nothing
> without a click from the user, sends nothing to any server, and talks only to the
> extension's own background script, which talks only to the Qasa desktop app.

**Remote code (Chrome: "Are you using remote code?")**

> No. All code is in the package. The extension loads no external scripts, uses no eval,
> and makes no network requests.

---

## 4. Privacy practices (Chrome "Privacy practices" tab; Edge and AMO ask the same)

**Data collected:** none. Leave every data-type box unticked.

Why: "collected" in the Chrome Web Store sense means sent off the user's device to the
developer or a third party. The extension sends nothing to Qasa or anyone else and makes no
network requests. Logins it fills come from, and logins it saves go to, the Qasa desktop
app on the same computer, over native messaging. The only things it stores are the pairing
key and the popup language, in the browser's local extension storage, and, during a
multi-step sign-in, that page's username in memory for at most 10 minutes.

If a reviewer pushes back (the extension does read and fill passwords), the fallback is to
tick **Authentication information** and **Website content**, with "handled on the device
only, never transmitted". Don't tick anything else.

**Certifications** (tick all three):

- I do not sell or transfer user data to third parties, outside of the approved use cases.
- I do not use or transfer user data for purposes that are unrelated to my item's single
  purpose.
- I do not use or transfer user data to determine creditworthiness or for lending purposes.

**Firefox AMO data collection:** no data collected or transmitted. AMO now asks new
extensions to declare this in the manifest as
`browser_specific_settings.gecko.data_collection_permissions: { required: ["none"] }`.
The Firefox manifest (`scripts/manifest.ts`) declares exactly that, with `strict_min_version`
140.0, the first Firefox (and the current ESR) that reads the key.

---

## 5. Still needed before submitting

- **Privacy policy URL.** The Chrome Web Store and Edge require one for an extension that
  handles passwords. The site has no privacy page yet (the policy lives in the apps). Publish
  it at `https://qasa-app.com/privacy` (en/ar/ckb) and use that URL.
- **Support contact** email for the developer accounts.
- **Store ids.** After the first upload, add the Chrome Web Store and Edge Add-ons ids to the
  desktop's native host manifest (see [`EXTENSION_IDS.md`](../EXTENSION_IDS.md)). Firefox
  keeps `extension@qasa-app.com`.

---

## 6. Notes to reviewers

Chrome ("Test instructions"), Edge ("Notes for certification") and AMO ("Notes to
Reviewer") all have a field for this:

> The extension works only with the Qasa desktop app, which holds the vault. To test:
> 1. Install the Qasa desktop app (https://qasa-app.com/download), open it, create a vault
>    with any main password, and add a login for a test site (for example a login for
>    https://example.com).
> 2. Open the extension's toolbar popup and click Connect. The popup and the app show the
>    same code; approve the browser in the app.
> 3. Visit the site: the Qasa icon appears in the username and password fields; click it and
>    pick the login to fill. Submitting a form with a new password shows a save prompt in
>    the app.
>
> Without the desktop app the popup shows "Qasa isn't running" with a download link; that is
> expected.
>
> AMO only: the code is bundled with Vite. The source archive (qasa-extension-source-<ver>.zip)
> has SOURCE-BUILD.md with the exact build steps (Bun, `bun install --frozen-lockfile`,
> `bun run build:store`); the output is byte-identical to the submitted package.

---

## 7. Images (not made yet)

| Asset | Chrome Web Store | Edge Add-ons | Firefox AMO |
| --- | --- | --- | --- |
| Store icon | 128×128 PNG (96×96 artwork, 16 px transparent padding); taken from the package | 300×300 PNG logo (1:1, 128×128 minimum) | Taken from the package (`icons/icon-128.png`) |
| Screenshots | 1–5 at **1280×800** (or 640×400), PNG or JPEG, square corners, no padding | Up to 10 at **1280×800** (or 640×480) | 1 or more, **1280×800** recommended (shown at 700×437); PNG or JPEG |
| Small promo tile | **440×280**, required | 440×280, optional | n/a |
| Marquee promo tile | 1400×560, optional (needed to be featured) | 1400×560, optional | n/a |

Suggested screenshots, each in English and Arabic (Chrome and Edge allow per-locale images),
light theme, on a neutral sign-in page:

1. The fill menu open on a login form, with one matching login.
2. The toolbar popup, unlocked, showing the current site's logins.
3. The save prompt after signing in with a new password (desktop app beside the browser).
4. The popup's password generator.
5. The pairing step: the matching code in the popup and in the desktop app.
