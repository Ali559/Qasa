// The privacy policy (/privacy, /ckb/privacy, /ar/privacy). It describes what the apps and
// the server actually do: check it against server/migrations, server/crates/qasa-db/src/repos/
// account_deletion.rs and apps/extension/store/listing.md whenever data handling changes, and
// bump `updated` (and the in-app summary, legal.privacyBody in l10n/strings.json).
//
// Paragraphs and list items are trusted HTML written here (links, <strong>). In ckb and ar,
// Latin runs are wrapped in FSI/PDI (`L`), as in the rest of the site's copy.
// The ckb and ar text is a draft awaiting native review (TODO.md).

import type { Lang } from "./i18n";

/** A paragraph, or a bulleted list. */
export type Block = string | string[];

export interface Section {
  id: string;
  title: string;
  body: Block[];
}

export interface Policy {
  pageTitle: string;
  pageDescription: string;
  kicker: string;
  title: string;
  updated: string;
  contents: string;
  sections: Section[];
}

/** ISO date of the current text; shown localized in `updated`. */
export const PRIVACY_UPDATED = "2026-11-09";

/** Placeholder until the owner confirms the inbox (TODO.md). */
const EMAIL = "privacy@qasa-app.com";

const FSI = "⁨";
const PDI = "⁩";
/** Isolates a Latin run inside Kurdish or Arabic text. */
const L = (s: string) => `${FSI}${s}${PDI}`;

const mail = `<a href="mailto:${EMAIL}" lang="en">${EMAIL}</a>`;
const mailRtl = `<a href="mailto:${EMAIL}" lang="en">${L(EMAIL)}</a>`;
const hibp = `<a href="https://haveibeenpwned.com/Passwords" lang="en">Have I Been Pwned</a>`;
const hibpRtl = `<a href="https://haveibeenpwned.com/Passwords" lang="en">${L("Have I Been Pwned")}</a>`;
const wayl = `<a href="https://wayl.io" lang="en">Wayl</a>`;
const waylRtl = `<a href="https://wayl.io" lang="en">${L("Wayl")}</a>`;

const en: Policy = {
  pageTitle: "Privacy policy · Qasa",
  pageDescription:
    "How Qasa handles your data: your vault stays encrypted on your devices, and our server keeps only what it needs to link devices, run Premium and send notices.",
  kicker: "Privacy policy",
  title: "Your vault is yours. Here is everything else.",
  updated: "Last updated: 9 November 2026",
  contents: "On this page",
  sections: [
    {
      id: "who",
      title: "Who we are",
      body: [
        "Qasa is a password manager for iPhone, Android, macOS, Windows and Linux, with a browser extension for Chrome, Edge and Firefox. It is made and run by Ali Barznji, who is responsible for your data under this policy. Here, “Qasa”, “we” and “us” mean him and the Qasa service.",
        "This policy covers the Qasa apps, the browser extension, the Qasa server (api.qasa-app.com) and this website (qasa-app.com).",
      ],
    },
    {
      id: "short",
      title: "The short version",
      body: [
        [
          "Your vault (passwords, cards, notes, IDs and 2FA codes) is encrypted on your device with a key made from your main password. It never reaches our server, and we can't read it.",
          "There's no sign-up. We never ask for your name, email or phone number. Your account is a random ID that your app creates.",
          "Our server keeps only what it needs to run your account: your linked devices, your plan and payments, and the notices we send you.",
          "You pay on Wayl's checkout page. We never see your card or wallet details.",
          "No ads, no analytics, no trackers. We never sell your data.",
          "You can delete your account, and what our server holds about you, at any time.",
        ],
      ],
    },
    {
      id: "devices",
      title: "What stays on your devices",
      body: [
        "Everything you save in Qasa: logins, payment cards and bank accounts, secure notes, personal info, ID documents and 2FA codes. It is stored only on your phone or computer.",
        "Each item is encrypted with AES‑256‑GCM. The key is made from your main password on your device (PBKDF2‑HMAC‑SHA256, 310,000 rounds, with a random salt). Your main password is never stored or sent anywhere. The app keeps only a check value, so it knows when you've typed it correctly. Search also works on your device, with scrambled search tokens instead of readable text.",
        "Face ID, Touch ID, fingerprint and Windows Hello unlock are handled by your device's operating system. Qasa never receives your face or fingerprint data, only a yes or no.",
        "Because we have no copy, we can't restore your vault or reset your main password. If you lose every device and have no export, the vault can't be recovered.",
        "When you export your vault to a CSV file, that file is not encrypted. Keep it somewhere safe, and delete it when you're done.",
      ],
    },
    {
      id: "server",
      title: "What our server stores, and why",
      body: [
        "The apps talk to our server to link your devices, check your plan, take payments and deliver notices. This is everything it keeps about you:",
        [
          "<strong>Your account:</strong> a random account ID made by your app, your plan (Free or Premium), and when the account was created. If we block an account for abuse, we also keep the reason.",
          "<strong>Each linked device:</strong> its name as set on the device (for example “Ali's iPhone”), its type (iOS, Android, macOS, Windows or Linux), a random device ID made by the app, the app's language, and when it was linked and last used. The iPhone app also sends a region label taken from your phone's region setting (for example “Iraq”), shown in Linked Devices. It does not come from GPS or your IP address.",
          "<strong>Notifications:</strong> if you allow them, a push token for that phone, and the notices in your Qasa notifications (such as renewal reminders and service news), with whether you've read or deleted them.",
          "<strong>Plan and payments:</strong> see <a href=\"#payments\">Payments</a>.",
          "<strong>VPN use</strong>, if you use it: see <a href=\"#vpn\">The VPN</a>.",
        ],
        "We use this to link your devices and apply your plan's device limit, show you your Linked Devices, give you Premium, send the notices you asked for, and stop abuse. Nothing else.",
        "<strong>Your IP address.</strong> Like any server, ours sees the IP address of each request. We use it only for short-lived counters that stop one address from flooding device registration, and we delete those within a day. We don't store IP addresses with your account, put them in our logs, or use them to find where you are.",
        "<strong>Logs.</strong> The server logs each request's time, address and result, so we can keep the service running and fix problems. The logs are rotated automatically.",
        "<strong>Staff access.</strong> A small number of staff can see these records in an internal dashboard, for support and to stop abuse. Every change staff make is recorded.",
      ],
    },
    {
      id: "payments",
      title: "Payments",
      body: [
        `Premium costs 5,000 IQD a month or 50,000 IQD a year. You pay through ${wayl}, a payment company whose checkout page accepts FIB, Qi Card and other wallets.`,
        "You enter your wallet or card details on Wayl's page, not in Qasa. We never see or store card numbers, wallet logins or PINs. Wayl handles them under its own privacy policy.",
        "We keep a record of each payment: the amount, plan, date, status and Wayl's reference for it, along with the messages Wayl sends us to confirm it. We also keep your subscription's dates, whether auto-renew is on, and a cancellation reason if you give one.",
        "Wayl can't charge you automatically. If auto-renew is on, we send you a new payment link before your plan ends, and you decide whether to pay it.",
      ],
    },
    {
      id: "breach",
      title: "The breached-password check",
      body: [
        `When Qasa checks your passwords against known leaks, your device turns each password into a SHA‑1 hash and sends only its first 5 characters to ${hibp}'s Pwned Passwords service. It gets back a padded list of the leaked hashes that start with those characters, and your device looks for a match itself.`,
        "This goes straight from your device to Have I Been Pwned, not through our server. They see your IP address and the 5-character prefixes, each shared by hundreds of different passwords. Your passwords never leave your device.",
      ],
    },
    {
      id: "vpn",
      title: "The VPN",
      body: [
        "Where it's available, Premium includes a VPN on your phone. It uses WireGuard. The VPN key pair is made on your phone, and the private key never leaves it.",
        "When you connect, our server records which device connected and to which VPN server, the private address it got inside the tunnel, when the session started and ended, when the tunnel was last active, and how much data it used. We use this to run the connection, end stale sessions and apply the monthly data allowance.",
        "We don't record the websites you visit, the content of your traffic or your DNS lookups. Our server doesn't store the IP address you connect from; the VPN server uses it only while you're connected, to send your traffic back to you. While you're connected, your DNS lookups go to the public resolvers of Cloudflare (1.1.1.1) and Google (8.8.8.8).",
        "Session records stay with your account until you delete it. Your data count starts again each month.",
      ],
    },
    {
      id: "sync",
      title: "Wi‑Fi sync and the browser extension",
      body: [
        "Wi‑Fi sync (Premium) copies your vault between your own devices directly, over your local network. Your devices find each other on the network, and while a computer waits for a phone to join, it shows its name to other devices on that network. You confirm with a short one-time code. The connection is end-to-end encrypted (SPAKE2 with Argon2id, then AES‑256‑GCM), and your vault never passes through our server or the internet. Linking a new device to your account still registers it with our server, as described above.",
        "The browser extension talks only to the Qasa desktop app on the same computer. It sends nothing to us or anyone else, and makes no network requests. It reads the page you're on to find sign-in fields, on your computer. It keeps a pairing key and your language in the browser's extension storage and, during a sign-in split across two pages, the username in memory for up to 10 minutes.",
        "The desktop app checks for updates at qasa-app.com, which forwards the request to GitHub, where the installers are published. Downloads from this site come from GitHub too, so GitHub sees your IP address.",
      ],
    },
    {
      id: "partners",
      title: "Companies that help us run Qasa",
      body: [
        [
          "<strong>Contabo</strong> hosts our server and its database.",
          "<strong>Cloudflare</strong> delivers this website and our server's traffic, and protects them from attacks. Every request passes through Cloudflare, which sees your IP address.",
          "<strong>Wayl</strong> processes payments.",
          "<strong>Google Firebase Cloud Messaging</strong> delivers push notifications to Android, and to iPhone through Apple's push service. It receives your phone's push token and the text of each notice.",
          "<strong>Have I Been Pwned</strong> answers the breached-password check.",
          "<strong>GitHub</strong> hosts the desktop app's downloads and updates.",
          "<strong>Apple, Google, Microsoft and Mozilla</strong> distribute the apps and the extension through their stores, under their own privacy policies.",
        ],
        "Our server and these services may be outside Iraq, so your data may be processed in other countries.",
      ],
    },
    {
      id: "dont",
      title: "What we don't do",
      body: [
        [
          "We don't sell, rent or trade your data.",
          "We don't show ads.",
          "The apps contain no analytics, crash-reporting or tracking tools. The only outside code that uses the network is Firebase Cloud Messaging, for push notifications.",
          "This website has no analytics, ads or tracking cookies. Cloudflare may set a cookie it needs to block attacks.",
          "We never ask for your main password, by phone, WhatsApp, email or any other way.",
        ],
      ],
    },
    {
      id: "retention",
      title: "How long we keep data",
      body: [
        [
          "<strong>Your vault:</strong> on your devices, until you delete it.",
          "<strong>Account, device, notification and VPN records:</strong> while your account exists. An unlinked device stays on the list, marked unlinked and without its push token, until you delete the account.",
          "<strong>IP address counters:</strong> deleted within a day.",
          "<strong>Backups:</strong> the database is backed up every night, and each backup is kept for 14 days. Data you delete is gone from our backups within 14 days.",
          "<strong>Payment and subscription records:</strong> kept after you delete your account, as financial records, for as long as accounting and the law require.",
        ],
      ],
    },
    {
      id: "rights",
      title: "Your choices and rights",
      body: [
        [
          "<strong>See your data.</strong> The app shows your linked devices, plan, payments and notifications. For anything else we hold, email us.",
          "<strong>Export.</strong> Export your vault to a CSV file in Settings at any time.",
          "<strong>Delete.</strong> Delete your account in Settings (in the desktop app now, and coming to the phone apps), or email us. This removes your devices (names, IDs, region labels and push tokens), your VPN records and your notifications from our server, and erases the vault on that device. Any paid time left ends. We keep the account ID, marked deleted so it can never be used again, and your payment records, linked only to that ID.",
          "<strong>Control.</strong> In the app you can unlink devices, turn off notifications, turn off auto-renew or cancel Premium.",
        ],
        "We know you only by your account ID, which your app holds, so when you write to us we may ask for it. You can also complain to the data-protection authority where you live.",
      ],
    },
    {
      id: "children",
      title: "Children",
      body: [
        "Qasa is not made for children under 13. We never ask for anyone's name, age or contact details, so we can't tell how old a user is. A parent who wants an account removed can delete it in the app or contact us.",
      ],
    },
    {
      id: "security",
      title: "Security",
      body: [
        "Your vault is protected by encryption on your device, and only your main password unlocks it. It is not on our server, so it can't leak from there.",
        "Our server can be reached only through Cloudflare, over HTTPS. Its database accepts connections only from the server itself, and staff tools are restricted.",
        "No system is perfectly secure. If a breach of our server affects you, we will tell you in the app's notifications and on this website.",
      ],
    },
    {
      id: "changes",
      title: "Changes to this policy",
      body: [
        "When we change this policy, we update the date at the top. If a change matters, we'll also tell you in the app's notifications before it takes effect.",
      ],
    },
    {
      id: "contact",
      title: "Contact",
      body: [`Questions or requests: ${mail}. You can write in English, Kurdish or Arabic.`],
    },
  ],
};

const ckb: Policy = {
  pageTitle: "سیاسەتی تایبەتمەندی · قاسە",
  pageDescription:
    "قاسە چۆن مامەڵە لەگەڵ داتاکانت دەکات: کۆگاکەت لەسەر ئامێرەکانت شفرەکراو دەمێنێتەوە، و سێرڤەرەکەمان تەنها ئەوە هەڵدەگرێت کە بۆ بەستنەوەی ئامێر، پریمیۆم و ناردنی ئاگاداری پێویستە.",
  kicker: "سیاسەتی تایبەتمەندی",
  title: "کۆگاکەت هی خۆتە. ئەمەش هەموو شتەکانی ترە.",
  updated: "دوایین نوێکردنەوە: ٩ی تشرینی دووەمی ٢٠٢٦",
  contents: "لەم پەڕەیەدا",
  sections: [
    {
      id: "who",
      title: "ئێمە کێین",
      body: [
        `قاسە بەڕێوەبەری وشەی نهێنییە بۆ ${L("iPhone")}، ${L("Android")}، ${L("macOS")}، ${L("Windows")} و ${L("Linux")}، لەگەڵ زیادکراوێکی وێبگەڕ بۆ ${L("Chrome")}، ${L("Edge")} و ${L("Firefox")}. عەلی بەرزنجی دروستی کردووە و بەڕێوەی دەبات، و بەپێی ئەم سیاسەتە بەرپرسە لە داتاکانت. لێرەدا «قاسە» و «ئێمە» واتە ئەو و خزمەتگوزاریی قاسە.`,
        `ئەم سیاسەتە ئەپەکانی قاسە، زیادکراوی وێبگەڕ، سێرڤەری قاسە (${L("api.qasa-app.com")}) و ئەم ماڵپەڕە (${L("qasa-app.com")}) دەگرێتەوە.`,
      ],
    },
    {
      id: "short",
      title: "بە کورتی",
      body: [
        [
          `کۆگاکەت (وشەی نهێنی، کارت، تێبینی، ناسنامە و کۆدی ${L("2FA")}) لەسەر ئامێرەکەت بە کلیلێک شفرە دەکرێت کە لە وشەی نهێنی سەرەکییەکەت دروست دەکرێت. هەرگیز ناگاتە سێرڤەرەکەمان و ناتوانین بیخوێنینەوە.`,
          "خۆتۆمارکردن نییە. هەرگیز داوای ناو، ئیمەیڵ یان ژمارەی مۆبایلت ناکەین. هەژمارەکەت ناسنامەیەکی هەڕەمەکییە کە ئەپەکەت دروستی دەکات.",
          "سێرڤەرەکەمان تەنها ئەوە هەڵدەگرێت کە بۆ کارپێکردنی هەژمارەکەت پێویستە: ئامێرە بەستراوەکانت، پلان و پارەدانەکانت، و ئەو ئاگادارییانەی بۆتی دەنێرین.",
          `پارە لە پەڕەی پارەدانی ${L("Wayl")} دەدەیت. هەرگیز زانیاریی کارت یان جزدانەکەت نابینین.`,
          "نە ڕیکلام، نە شیکاری، نە شوێنکەوتن. هەرگیز داتاکانت نافرۆشین.",
          "هەر کاتێک بتەوێت دەتوانیت هەژمارەکەت و ئەوەی سێرڤەرەکەمان دەربارەت هەیەتی بسڕیتەوە.",
        ],
      ],
    },
    {
      id: "devices",
      title: "ئەوەی لەسەر ئامێرەکانت دەمێنێتەوە",
      body: [
        `هەموو ئەوەی لە قاسەدا پاشەکەوتی دەکەیت: چوونەژوورەوەکان، کارتی بانکی و هەژماری بانکی، تێبینیی پارێزراو، زانیاری کەسی، ناسنامەکان و کۆدی ${L("2FA")}. تەنها لەسەر مۆبایل یان کۆمپیوتەرەکەت هەڵدەگیرێت.`,
        `هەر بڕگەیەک بە ${L("AES‑256‑GCM")} شفرە دەکرێت. کلیلەکە لەسەر ئامێرەکەت لە وشەی نهێنی سەرەکییەکەت دروست دەکرێت (${L("PBKDF2‑HMAC‑SHA256")}، ٣١٠٬٠٠٠ جار، لەگەڵ خوێیەکی هەڕەمەکی). وشەی نهێنی سەرەکییەکەت هەرگیز هەڵناگیرێت و بۆ هیچ شوێنێک نانێردرێت. ئەپەکە تەنها بەهایەکی پشکنین هەڵدەگرێت، بۆ ئەوەی بزانێت کەی بە دروستی نووسیوتە. گەڕانیش لەسەر ئامێرەکەت دەکرێت، بە نیشانەی تێکدراو لە جیاتی دەقی خوێندراوە.`,
        `کردنەوە بە ${L("Face ID")}، ${L("Touch ID")}، پەنجەمۆر و ${L("Windows Hello")} لەلایەن سیستەمی ئامێرەکەتەوە دەکرێت. قاسە هەرگیز داتای ڕووخسار یان پەنجەمۆرت وەرناگرێت، تەنها بەڵێ یان نەخێر.`,
        "لەبەر ئەوەی هیچ کۆپییەکمان لا نییە، ناتوانین کۆگاکەت بگەڕێنینەوە یان وشەی نهێنی سەرەکییەکەت ڕێکبخەینەوە. ئەگەر هەموو ئامێرەکانت لەدەست بدەیت و هەناردەت نەکردبێت، کۆگاکە ناگەڕێتەوە.",
        `کاتێک کۆگاکەت بۆ فایلی ${L("CSV")} هەناردە دەکەیت، ئەو فایلە شفرەکراو نییە. لە شوێنێکی سەلامەت دایبنێ و کە تەواو بوویت بیسڕەوە.`,
      ],
    },
    {
      id: "server",
      title: "سێرڤەرەکەمان چی هەڵدەگرێت، و بۆچی",
      body: [
        "ئەپەکان پەیوەندی بە سێرڤەرەکەمانەوە دەکەن بۆ بەستنەوەی ئامێرەکانت، پشکنینی پلانەکەت، وەرگرتنی پارە و گەیاندنی ئاگاداری. ئەمە هەموو ئەوەیە کە دەربارەت هەڵیدەگرێت:",
        [
          "<strong>هەژمارەکەت:</strong> ناسنامەیەکی هەڕەمەکیی هەژمار کە ئەپەکەت دروستی کردووە، پلانەکەت (خۆڕایی یان پریمیۆم)، و کاتی دروستکردنی هەژمارەکە. ئەگەر هەژمارێک بەهۆی خراپ بەکارهێنانەوە ڕابگرین، هۆکارەکەشی هەڵدەگرین.",
          `<strong>هەر ئامێرێکی بەستراو:</strong> ناوەکەی وەک لەسەر ئامێرەکە دانراوە (بۆ نموونە «${L("Ali's iPhone")}»)، جۆرەکەی (${L("iOS")}، ${L("Android")}، ${L("macOS")}، ${L("Windows")} یان ${L("Linux")})، ناسنامەیەکی هەڕەمەکیی ئامێر کە ئەپەکە دروستی کردووە، زمانی ئەپەکە، و کاتی بەستنەوە و دوایین بەکارهێنان. ئەپی ${L("iPhone")} هەروەها ناوی ناوچەیەک دەنێرێت کە لە ڕێکخستنی ناوچەی مۆبایلەکەتەوە وەردەگیرێت (بۆ نموونە «عێراق»)، و لە ئامێرە بەستراوەکاندا پیشان دەدرێت. لە ${L("GPS")} یان ناونیشانی ${L("IP")} ـەکەتەوە نایەت.`,
          "<strong>ئاگادارییەکان:</strong> ئەگەر ڕێگەت پێدابن، نیشانەیەکی پوش بۆ ئەو مۆبایلە، و ئەو ئاگادارییانەی لە بەشی ئاگادارییەکانی قاسەدان (وەک بیرخستنەوەی نوێکردنەوە و هەواڵی خزمەتگوزاری)، لەگەڵ ئەوەی خوێندووتنەتەوە یان سڕیوتنەتەوە.",
          "<strong>پلان و پارەدان:</strong> بڕوانە <a href=\"#payments\">پارەدان</a>.",
          "<strong>بەکارهێنانی ⁨VPN⁩</strong>، ئەگەر بەکاری بهێنیت: بڕوانە <a href=\"#vpn\">⁨VPN⁩</a>.",
        ],
        "ئەمانە بەکاردەهێنین بۆ بەستنەوەی ئامێرەکانت و جێبەجێکردنی سنووری ئامێری پلانەکەت، پیشاندانی ئامێرە بەستراوەکانت، پێدانی پریمیۆم، ناردنی ئەو ئاگادارییانەی داوات کردوون، و ڕێگرتن لە خراپ بەکارهێنان. هیچی تر.",
        `<strong>ناونیشانی ${L("IP")} ـەکەت.</strong> وەک هەر سێرڤەرێک، هی ئێمەش ناونیشانی ${L("IP")} ـی هەر داواکارییەک دەبینێت. تەنها بۆ ژمێرەری کورتخایەن بەکاری دەهێنین کە ڕێگە نادەن یەک ناونیشان تۆمارکردنی ئامێر لافاو بکات، و لە ماوەی یەک ڕۆژدا دەیانسڕینەوە. ناونیشانی ${L("IP")} لەگەڵ هەژمارەکەت هەڵناگرین، نایخەینە تۆمارەکانمانەوە، و بۆ دۆزینەوەی شوێنەکەت بەکاری ناهێنین.`,
        "<strong>تۆمارەکان.</strong> سێرڤەرەکە کات، ناونیشان و ئەنجامی هەر داواکارییەک تۆمار دەکات، بۆ ئەوەی خزمەتگوزارییەکە کار بکات و کێشەکان چارەسەر بکەین. تۆمارەکان بە شێوەی خۆکار دەسوڕێنرێنەوە.",
        "<strong>دەستگەیشتنی کارمەندان.</strong> ژمارەیەکی کەم لە کارمەندان دەتوانن ئەم تۆمارانە لە داشبۆردێکی ناوخۆییدا ببینن، بۆ پشتگیری و ڕێگرتن لە خراپ بەکارهێنان. هەر گۆڕانکارییەک کە کارمەندان بیکەن تۆمار دەکرێت.",
      ],
    },
    {
      id: "payments",
      title: "پارەدان",
      body: [
        `نرخی پریمیۆم مانگانە ٥٬٠٠٠ د.ع یان ساڵانە ٥٠٬٠٠٠ د.ع ـە. پارە لە ڕێگەی ${waylRtl} ـەوە دەدەیت، کۆمپانیایەکی پارەدان کە پەڕەی پارەدانەکەی ${L("FIB")}، ${L("Qi Card")} و جزدانی تر وەردەگرێت.`,
        `زانیاریی جزدان یان کارتەکەت لە پەڕەی ${L("Wayl")} دەنووسیت، نەک لە قاسەدا. هەرگیز ژمارەی کارت، چوونەژوورەوەی جزدان یان ${L("PIN")} نابینین و هەڵیان ناگرین. ${L("Wayl")} بەپێی سیاسەتی تایبەتمەندیی خۆی مامەڵەیان لەگەڵ دەکات.`,
        `تۆمارێکی هەر پارەدانێک هەڵدەگرین: بڕەکە، پلانەکە، بەروار، دۆخ و ژمارەی ئاماژەی ${L("Wayl")} بۆی، لەگەڵ ئەو نامانەی ${L("Wayl")} بۆ پشتڕاستکردنەوەی دەینێرێت. هەروەها بەروارەکانی بەشداربوونەکەت، ئایا نوێبوونەوەی خۆکار چالاکە، و هۆکاری هەڵوەشاندنەوە ئەگەر پێمانی بڵێیت، هەڵدەگرین.`,
        `${L("Wayl")} ناتوانێت بە شێوەی خۆکار پارەت لێ وەربگرێت. ئەگەر نوێبوونەوەی خۆکار چالاک بێت، پێش کۆتاییهاتنی پلانەکەت بەستەرێکی پارەدانی نوێت بۆ دەنێرین، و خۆت بڕیار دەدەیت بیدەیت یان نا.`,
      ],
    },
    {
      id: "breach",
      title: "پشکنینی وشەی نهێنیی دزراو",
      body: [
        `کاتێک قاسە وشە نهێنییەکانت لەگەڵ دزەکردنە ناسراوەکان بەراورد دەکات، ئامێرەکەت هەر وشەیەکی نهێنی دەگۆڕێت بۆ هاشی ${L("SHA‑1")} و تەنها ٥ پیتی یەکەمی دەنێرێت بۆ خزمەتگوزاریی ${L("Pwned Passwords")} ـی ${hibpRtl}. لیستێکی پڕکراوە لەو هاشە دزراوانە وەردەگرێتەوە کە بەو پیتانە دەست پێدەکەن، و ئامێرەکەت خۆی بەدوای هاوتادا دەگەڕێت.`,
        `ئەمە ڕاستەوخۆ لە ئامێرەکەتەوە دەچێت بۆ ${L("Have I Been Pwned")}، نەک بە سێرڤەرەکەماندا. ئەوان ناونیشانی ${L("IP")} ـەکەت و پێشگرە ٥ پیتییەکان دەبینن، کە هەریەکەیان لە نێوان سەدان وشەی نهێنیی جیاوازدا هاوبەشە. وشە نهێنییەکانت هەرگیز ئامێرەکەت بەجێناهێڵن.`,
      ],
    },
    {
      id: "vpn",
      title: "⁨VPN⁩",
      body: [
        `لەو شوێنانەی بەردەستە، پریمیۆم ${L("VPN")} ـێک لەسەر مۆبایلەکەت لەخۆ دەگرێت. ${L("WireGuard")} بەکاردەهێنێت. جووتە کلیلی ${L("VPN")} لەسەر مۆبایلەکەت دروست دەکرێت، و کلیلە تایبەتەکە هەرگیز بەجێی ناهێڵێت.`,
        `کاتێک پەیوەندی دەکەیت، سێرڤەرەکەمان تۆمار دەکات کام ئامێر بە کام سێرڤەری ${L("VPN")} ـەوە پەیوەست بوو، ئەو ناونیشانە تایبەتەی لەناو تونێلەکەدا پێی درا، کاتی دەستپێک و کۆتاییی دانیشتنەکە، دوایین کاتی چالاکبوونی تونێلەکە، و چەند داتای بەکارهێنا. ئەمانە بۆ کارپێکردنی پەیوەندییەکە، کۆتاییهێنان بە دانیشتنە کۆنەکان و جێبەجێکردنی بڕی داتای مانگانە بەکاردەهێنین.`,
        `ئەو ماڵپەڕانەی سەردانیان دەکەیت، ناوەڕۆکی هاتوچۆکەت یان گەڕانەکانی ${L("DNS")} تۆمار ناکەین. سێرڤەرەکەمان ئەو ناونیشانی ${L("IP")} ـەی لێوەی پەیوەندی دەکەیت هەڵناگرێت؛ سێرڤەری ${L("VPN")} تەنها لە کاتی پەیوەستبووندا بەکاری دەهێنێت، بۆ ئەوەی هاتوچۆکەت بۆت بگەڕێنێتەوە. لە کاتی پەیوەستبووندا، گەڕانەکانی ${L("DNS")} ـت دەچن بۆ چارەسەرکەرە گشتییەکانی ${L("Cloudflare")} (${L("1.1.1.1")}) و ${L("Google")} (${L("8.8.8.8")}).`,
        "تۆماری دانیشتنەکان لەگەڵ هەژمارەکەت دەمێننەوە تا دەیسڕیتەوە. ژمارەی داتاکەت هەموو مانگێک لە سەرەتاوە دەست پێدەکاتەوە.",
      ],
    },
    {
      id: "sync",
      title: `هاوکاتکردن لەسەر ${L("Wi‑Fi")} و زیادکراوی وێبگەڕ`,
      body: [
        `هاوکاتکردن لەسەر ${L("Wi‑Fi")} (پریمیۆم) کۆگاکەت ڕاستەوخۆ لە نێوان ئامێرەکانی خۆتدا، لە ڕێگەی تۆڕی ناوخۆییەکەتەوە کۆپی دەکات. ئامێرەکانت لەسەر تۆڕەکە یەکتر دەدۆزنەوە، و کاتێک کۆمپیوتەرێک چاوەڕێی بەشداربوونی مۆبایلێک دەکات، ناوەکەی بە ئامێرەکانی تری ئەو تۆڕە پیشان دەدات. بە کۆدێکی کورتی یەکجاری پشتڕاستی دەکەیتەوە. پەیوەندییەکە سەرتاسەر شفرەکراوە (${L("SPAKE2")} لەگەڵ ${L("Argon2id")}، پاشان ${L("AES‑256‑GCM")})، و کۆگاکەت هەرگیز بە سێرڤەرەکەمان یان ئینتەرنێتدا تێناپەڕێت. بەستنەوەی ئامێرێکی نوێ بە هەژمارەکەتەوە هێشتا لە سێرڤەرەکەماندا تۆماری دەکات، وەک لە سەرەوە باس کراوە.`,
        "زیادکراوی وێبگەڕ تەنها لەگەڵ ئەپی قاسەی کۆمپیوتەر لەسەر هەمان کۆمپیوتەر قسە دەکات. هیچ شتێک بۆ ئێمە یان هیچ کەسێکی تر نانێرێت، و هیچ داواکارییەکی تۆڕ ناکات. ئەو پەڕەیەی لەسەریت دەخوێنێتەوە بۆ دۆزینەوەی خانەکانی چوونەژوورەوە، لەسەر کۆمپیوتەرەکەت. کلیلێکی بەستنەوە و زمانەکەت لە بیرگەی زیادکراوی وێبگەڕدا هەڵدەگرێت و، لە کاتی چوونەژوورەوەیەکدا کە دابەشی دوو پەڕە بووە، ناوی بەکارهێنەر بۆ ماوەی تا ١٠ خولەک لە بیرگەدا ڕادەگرێت.",
        `ئەپی کۆمپیوتەر لە ${L("qasa-app.com")} بەدوای نوێکردنەوەدا دەگەڕێت، کە داواکارییەکە دەگوازێتەوە بۆ ${L("GitHub")}، کە دامەزرێنەرەکانی لێ بڵاو دەکرێنەوە. دابەزاندنەکانی ئەم ماڵپەڕەش لە ${L("GitHub")} ـەوە دێن، بۆیە ${L("GitHub")} ناونیشانی ${L("IP")} ـەکەت دەبینێت.`,
      ],
    },
    {
      id: "partners",
      title: "ئەو کۆمپانیایانەی یارمەتیمان دەدەن قاسە بەڕێوە ببەین",
      body: [
        [
          `<strong>${L("Contabo")}</strong> سێرڤەرەکەمان و بنکەدراوەکەی هەڵدەگرێت.`,
          `<strong>${L("Cloudflare")}</strong> ئەم ماڵپەڕە و هاتوچۆی سێرڤەرەکەمان دەگەیەنێت و لە هێرش دەیانپارێزێت. هەموو داواکارییەک بە ${L("Cloudflare")} دا تێدەپەڕێت، کە ناونیشانی ${L("IP")} ـەکەت دەبینێت.`,
          `<strong>${L("Wayl")}</strong> پارەدانەکان جێبەجێ دەکات.`,
          `<strong>${L("Google Firebase Cloud Messaging")}</strong> ئاگاداریی پوش دەگەیەنێتە ${L("Android")}، و بە خزمەتگوزاریی پوشی ${L("Apple")} دا دەیگەیەنێتە ${L("iPhone")}. نیشانەی پوشی مۆبایلەکەت و دەقی هەر ئاگادارییەک وەردەگرێت.`,
          `<strong>${L("Have I Been Pwned")}</strong> وەڵامی پشکنینی وشەی نهێنیی دزراو دەداتەوە.`,
          `<strong>${L("GitHub")}</strong> دابەزاندن و نوێکردنەوەکانی ئەپی کۆمپیوتەر هەڵدەگرێت.`,
          `<strong>${L("Apple")}، ${L("Google")}، ${L("Microsoft")} و ${L("Mozilla")}</strong> ئەپەکان و زیادکراوەکە لە ڕێگەی فرۆشگاکانیانەوە بڵاو دەکەنەوە، بەپێی سیاسەتی تایبەتمەندیی خۆیان.`,
        ],
        "لەوانەیە سێرڤەرەکەمان و ئەم خزمەتگوزارییانە لە دەرەوەی عێراق بن، بۆیە لەوانەیە داتاکانت لە وڵاتانی تردا مامەڵەیان لەگەڵ بکرێت.",
      ],
    },
    {
      id: "dont",
      title: "ئەوەی نایکەین",
      body: [
        [
          "داتاکانت نافرۆشین، بە کرێ نادەین و ئاڵوگۆڕیان پێناکەین.",
          "ڕیکلام پیشان نادەین.",
          `ئەپەکان هیچ ئامرازێکی شیکاری، ڕاپۆرتی تێکچوون یان شوێنکەوتنیان تێدا نییە. تاکە کۆدی دەرەکی کە تۆڕ بەکاردەهێنێت ${L("Firebase Cloud Messaging")} ـە، بۆ ئاگاداریی پوش.`,
          `ئەم ماڵپەڕە هیچ شیکاری، ڕیکلام یان کووکیی شوێنکەوتنی نییە. ${L("Cloudflare")} لەوانەیە کووکییەک دابنێت کە بۆ ڕێگرتن لە هێرش پێویستیەتی.`,
          `هەرگیز داوای وشەی نهێنی سەرەکییەکەت ناکەین، نە بە تەلەفۆن، نە بە ${L("WhatsApp")}، نە بە ئیمەیڵ، نە بە هیچ ڕێگەیەکی تر.`,
        ],
      ],
    },
    {
      id: "retention",
      title: "چەندە داتا هەڵدەگرین",
      body: [
        [
          "<strong>کۆگاکەت:</strong> لەسەر ئامێرەکانت، تا دەیسڕیتەوە.",
          `<strong>تۆماری هەژمار، ئامێر، ئاگاداری و ${L("VPN")}:</strong> تا هەژمارەکەت هەبێت. ئامێرێکی لابراو لە لیستەکەدا دەمێنێتەوە، وەک لابراو و بێ نیشانەی پوش، تا هەژمارەکە دەسڕیتەوە.`,
          `<strong>ژمێرەرەکانی ناونیشانی ${L("IP")}:</strong> لە ماوەی یەک ڕۆژدا دەسڕدرێنەوە.`,
          "<strong>یەدەگەکان:</strong> هەموو شەوێک یەدەگی بنکەدراوەکە دەگیرێت، و هەر یەدەگێک ١٤ ڕۆژ دەمێنێتەوە. ئەو داتایەی دەیسڕیتەوە لە ماوەی ١٤ ڕۆژدا لە یەدەگەکانیشمان نامێنێت.",
          "<strong>تۆماری پارەدان و بەشداربوون:</strong> دوای سڕینەوەی هەژمارەکەت وەک تۆماری دارایی دەمێننەوە، بۆ ئەو ماوەیەی ژمێریاری و یاسا داوای دەکەن.",
        ],
      ],
    },
    {
      id: "rights",
      title: "هەڵبژاردن و مافەکانت",
      body: [
        [
          "<strong>بینینی داتاکانت.</strong> ئەپەکە ئامێرە بەستراوەکان، پلان، پارەدان و ئاگادارییەکانت پیشان دەدات. بۆ هەر شتێکی تر کە لامانە، ئیمەیڵمان بۆ بنێرە.",
          `<strong>هەناردەکردن.</strong> هەر کاتێک بتەوێت لە ڕێکخستنەکاندا کۆگاکەت بۆ فایلی ${L("CSV")} هەناردە بکە.`,
          `<strong>سڕینەوە.</strong> لە ڕێکخستنەکاندا هەژمارەکەت بسڕەوە (ئێستا لە ئەپی کۆمپیوتەردا، و بەم زووانە لە ئەپەکانی مۆبایلدا)، یان ئیمەیڵمان بۆ بنێرە. ئەمە ئامێرەکانت (ناو، ناسنامە، ناوی ناوچە و نیشانەی پوش)، تۆمارەکانی ${L("VPN")} و ئاگادارییەکانت لە سێرڤەرەکەمان لادەبات، و کۆگای سەر ئەو ئامێرە دەسڕێتەوە. هەر ماوەیەکی پارەدراو کە مابێت کۆتایی دێت. ناسنامەی هەژمارەکە هەڵدەگرین، وەک سڕاوە نیشانکراو بۆ ئەوەی هەرگیز دووبارە بەکارنەهێنرێتەوە، لەگەڵ تۆماری پارەدانەکانت کە تەنها بەو ناسنامەیەوە بەستراون.`,
          "<strong>کۆنترۆڵ.</strong> لە ئەپەکەدا دەتوانیت ئامێرەکان لاببەیت، ئاگادارییەکان بکوژێنیتەوە، نوێبوونەوەی خۆکار ناچالاک بکەیت یان پریمیۆم هەڵبوەشێنیتەوە.",
        ],
        "تەنها بە ناسنامەی هەژمارەکەت دەتناسین، کە لە ئەپەکەتدایە، بۆیە کاتێک بۆمان دەنووسیت لەوانەیە داوای بکەین. هەروەها دەتوانیت سکاڵا بکەیتە لای دەسەڵاتی پاراستنی داتا لەو شوێنەی تێیدا دەژیت.",
      ],
    },
    {
      id: "children",
      title: "منداڵان",
      body: [
        "قاسە بۆ منداڵانی خوار ١٣ ساڵ دروست نەکراوە. هەرگیز داوای ناو، تەمەن یان زانیاریی پەیوەندیی هیچ کەسێک ناکەین، بۆیە ناتوانین بزانین بەکارهێنەرێک تەمەنی چەندە. دایک یان باوکێک کە بیەوێت هەژمارێک لاببرێت، دەتوانێت لە ئەپەکەدا بیسڕێتەوە یان پەیوەندیمان پێوە بکات.",
      ],
    },
    {
      id: "security",
      title: "ئاسایش",
      body: [
        "کۆگاکەت بە شفرەکردن لەسەر ئامێرەکەت پارێزراوە، و تەنها وشەی نهێنی سەرەکییەکەت دەیکاتەوە. لەسەر سێرڤەرەکەمان نییە، بۆیە لەوێوە دزە ناکات.",
        `سێرڤەرەکەمان تەنها لە ڕێگەی ${L("Cloudflare")} و بە ${L("HTTPS")} دەگاتێ. بنکەدراوەکەی تەنها پەیوەندی لە خودی سێرڤەرەکەوە وەردەگرێت، و ئامرازەکانی کارمەندان سنووردارن.`,
        "هیچ سیستەمێک بە تەواوی سەلامەت نییە. ئەگەر دزەکردنێک لە سێرڤەرەکەمان کاری لە تۆ کرد، لە ئاگادارییەکانی ئەپەکە و لەسەر ئەم ماڵپەڕە پێت دەڵێین.",
      ],
    },
    {
      id: "changes",
      title: "گۆڕانکاری لەم سیاسەتەدا",
      body: [
        "کاتێک ئەم سیاسەتە دەگۆڕین، بەرواری سەرەوە نوێ دەکەینەوە. ئەگەر گۆڕانکارییەک گرنگ بێت، پێش ئەوەی جێبەجێ بکرێت لە ئاگادارییەکانی ئەپەکەشدا پێت دەڵێین.",
      ],
    },
    {
      id: "contact",
      title: "پەیوەندی",
      body: [`پرسیار یان داواکاری: ${mailRtl}. دەتوانیت بە ئینگلیزی، کوردی یان عەرەبی بنووسیت.`],
    },
  ],
};

const ar: Policy = {
  pageTitle: "سياسة الخصوصية · قاصة",
  pageDescription:
    "كيف تتعامل قاصة مع بياناتك: تبقى خزنتك مشفّرة على أجهزتك، ولا يحتفظ خادمنا إلا بما يلزم لربط الأجهزة وتشغيل بريميوم وإرسال الإشعارات.",
  kicker: "سياسة الخصوصية",
  title: "خزنتك ملكك. وهذا كل ما عدا ذلك.",
  updated: "آخر تحديث: 9 تشرين الثاني 2026",
  contents: "في هذه الصفحة",
  sections: [
    {
      id: "who",
      title: "من نحن",
      body: [
        `قاصة مدير كلمات مرور لأجهزة ${L("iPhone")} و${L("Android")} و${L("macOS")} و${L("Windows")} و${L("Linux")}، مع إضافة للمتصفح لـ ${L("Chrome")} و${L("Edge")} و${L("Firefox")}. صنعها ويديرها علي برزنجي، وهو المسؤول عن بياناتك بموجب هذه السياسة. وهنا تعني «قاصة» و«نحن» هو وخدمة قاصة.`,
        `تشمل هذه السياسة تطبيقات قاصة وإضافة المتصفح وخادم قاصة (${L("api.qasa-app.com")}) وهذا الموقع (${L("qasa-app.com")}).`,
      ],
    },
    {
      id: "short",
      title: "باختصار",
      body: [
        [
          `خزنتك (كلمات المرور والبطاقات والملاحظات والهويات ورموز ${L("2FA")}) مشفّرة على جهازك بمفتاح مشتق من كلمة مرورك الرئيسية. لا تصل إلى خادمنا أبدًا، ولا نستطيع قراءتها.`,
          "لا يوجد تسجيل. لا نطلب أبدًا اسمك أو بريدك الإلكتروني أو رقم هاتفك. حسابك معرّف عشوائي ينشئه تطبيقك.",
          "لا يحتفظ خادمنا إلا بما يلزم لتشغيل حسابك: أجهزتك المرتبطة، وخطتك ومدفوعاتك، والإشعارات التي نرسلها إليك.",
          `تدفع في صفحة الدفع الخاصة بـ ${L("Wayl")}. لا نرى أبدًا تفاصيل بطاقتك أو محفظتك.`,
          "لا إعلانات، ولا تحليلات، ولا تتبّع. ولا نبيع بياناتك أبدًا.",
          "يمكنك حذف حسابك، وما يحتفظ به خادمنا عنك، في أي وقت.",
        ],
      ],
    },
    {
      id: "devices",
      title: "ما يبقى على أجهزتك",
      body: [
        `كل ما تحفظه في قاصة: تسجيلات الدخول، وبطاقات الدفع والحسابات المصرفية، والملاحظات الآمنة، والمعلومات الشخصية، والهويات، ورموز ${L("2FA")}. يُخزَّن على هاتفك أو حاسوبك فقط.`,
        `يُشفَّر كل عنصر بـ ${L("AES‑256‑GCM")}. ويُشتق المفتاح من كلمة مرورك الرئيسية على جهازك (${L("PBKDF2‑HMAC‑SHA256")}، بـ 310,000 دورة، مع ملح عشوائي). لا تُخزَّن كلمة مرورك الرئيسية ولا تُرسَل إلى أي مكان أبدًا. يحتفظ التطبيق بقيمة تحقق فقط، ليعرف متى كتبتها بشكل صحيح. ويعمل البحث على جهازك أيضًا، برموز بحث مموّهة بدل النص المقروء.`,
        `يتولى نظام تشغيل جهازك الفتح بـ ${L("Face ID")} و${L("Touch ID")} وبصمة الإصبع و${L("Windows Hello")}. لا تستلم قاصة أبدًا بيانات وجهك أو بصمتك، بل «نعم» أو «لا» فقط.`,
        "لأنه لا توجد لدينا نسخة، لا يمكننا استعادة خزنتك أو إعادة تعيين كلمة مرورك الرئيسية. إذا فقدت كل أجهزتك ولم تكن لديك نسخة مُصدَّرة، فلا يمكن استرجاع الخزنة.",
        `عند تصدير خزنتك إلى ملف ${L("CSV")}، يكون هذا الملف غير مشفّر. احفظه في مكان آمن، واحذفه عند الانتهاء.`,
      ],
    },
    {
      id: "server",
      title: "ما يحفظه خادمنا، ولماذا",
      body: [
        "تتصل التطبيقات بخادمنا لربط أجهزتك، والتحقق من خطتك، واستلام المدفوعات، وإيصال الإشعارات. وهذا كل ما يحفظه عنك:",
        [
          "<strong>حسابك:</strong> معرّف حساب عشوائي أنشأه تطبيقك، وخطتك (المجانية أو بريميوم)، وتاريخ إنشاء الحساب. إذا أوقفنا حسابًا بسبب إساءة الاستخدام، نحتفظ بالسبب أيضًا.",
          `<strong>كل جهاز مرتبط:</strong> اسمه كما هو مضبوط على الجهاز (مثلًا «${L("Ali's iPhone")}»)، ونوعه (${L("iOS")} أو ${L("Android")} أو ${L("macOS")} أو ${L("Windows")} أو ${L("Linux")})، ومعرّف جهاز عشوائي أنشأه التطبيق، ولغة التطبيق، ووقت ربطه وآخر استخدام له. ويرسل تطبيق ${L("iPhone")} أيضًا اسم منطقة مأخوذًا من إعداد المنطقة في هاتفك (مثلًا «العراق»)، ويظهر في الأجهزة المرتبطة. وهو لا يأتي من ${L("GPS")} ولا من عنوان ${L("IP")} الخاص بك.`,
          "<strong>الإشعارات:</strong> إذا سمحت بها، رمز إشعارات لذلك الهاتف، والإشعارات الموجودة في قسم الإشعارات في قاصة (مثل تذكيرات التجديد وأخبار الخدمة)، مع معرفة ما إذا كنت قد قرأتها أو حذفتها.",
          "<strong>الخطة والمدفوعات:</strong> انظر <a href=\"#payments\">الدفع</a>.",
          "<strong>استخدام ⁨VPN⁩</strong>، إن استخدمته: انظر <a href=\"#vpn\">⁨VPN⁩</a>.",
        ],
        "نستخدم هذا لربط أجهزتك وتطبيق حد الأجهزة في خطتك، وعرض أجهزتك المرتبطة، ومنحك بريميوم، وإرسال الإشعارات التي طلبتها، ومنع إساءة الاستخدام. لا شيء غير ذلك.",
        `<strong>عنوان ${L("IP")} الخاص بك.</strong> مثل أي خادم، يرى خادمنا عنوان ${L("IP")} لكل طلب. نستخدمه فقط في عدّادات قصيرة الأمد تمنع عنوانًا واحدًا من إغراق تسجيل الأجهزة، ونحذفها خلال يوم. لا نحفظ عناوين ${L("IP")} مع حسابك، ولا نضعها في سجلاتنا، ولا نستخدمها لمعرفة مكانك.`,
        "<strong>السجلات.</strong> يسجّل الخادم وقت كل طلب وعنوانه ونتيجته، لنُبقي الخدمة تعمل ونصلح المشكلات. وتُدوَّر السجلات تلقائيًا.",
        "<strong>وصول الموظفين.</strong> يستطيع عدد قليل من الموظفين رؤية هذه السجلات في لوحة تحكم داخلية، للدعم ولمنع إساءة الاستخدام. ويُسجَّل كل تغيير يجريه الموظفون.",
      ],
    },
    {
      id: "payments",
      title: "الدفع",
      body: [
        `سعر بريميوم 5,000 د.ع شهريًا أو 50,000 د.ع سنويًا. تدفع عبر ${waylRtl}، وهي شركة دفع تقبل صفحتها ${L("FIB")} و${L("Qi Card")} ومحافظ أخرى.`,
        `تُدخل تفاصيل محفظتك أو بطاقتك في صفحة ${L("Wayl")}، لا في قاصة. لا نرى ولا نحفظ أبدًا أرقام البطاقات أو بيانات دخول المحافظ أو رموز ${L("PIN")}. وتتعامل ${L("Wayl")} معها وفق سياسة الخصوصية الخاصة بها.`,
        `نحتفظ بسجل لكل عملية دفع: المبلغ والخطة والتاريخ والحالة ورقم ${L("Wayl")} المرجعي لها، مع الرسائل التي ترسلها ${L("Wayl")} لتأكيدها. ونحتفظ أيضًا بتواريخ اشتراكك، وما إذا كان التجديد التلقائي مفعّلًا، وسبب الإلغاء إن ذكرته.`,
        `لا تستطيع ${L("Wayl")} خصم المبلغ منك تلقائيًا. إذا كان التجديد التلقائي مفعّلًا، نرسل إليك رابط دفع جديدًا قبل انتهاء خطتك، وأنت تقرر ما إذا كنت ستدفعه.`,
      ],
    },
    {
      id: "breach",
      title: "فحص كلمات المرور المسرّبة",
      body: [
        `عندما تفحص قاصة كلمات مرورك مقابل التسريبات المعروفة، يحوّل جهازك كل كلمة مرور إلى بصمة ${L("SHA‑1")} (${L("hash")})، ولا يرسل سوى أول 5 أحرف منها إلى خدمة ${L("Pwned Passwords")} التابعة لـ ${hibpRtl}. ويستلم قائمة مُبطَّنة بالبصمات المسرّبة التي تبدأ بهذه الأحرف، ويبحث جهازك بنفسه عن تطابق.`,
        `ينتقل هذا مباشرة من جهازك إلى ${L("Have I Been Pwned")}، لا عبر خادمنا. يرون عنوان ${L("IP")} الخاص بك والبادئات المكوّنة من 5 أحرف، وكل منها مشتركة بين مئات كلمات المرور المختلفة. ولا تغادر كلمات مرورك جهازك أبدًا.`,
      ],
    },
    {
      id: "vpn",
      title: "⁨VPN⁩",
      body: [
        `حيثما يتوفر، يتضمن بريميوم خدمة ${L("VPN")} على هاتفك. وهي تستخدم ${L("WireGuard")}. يُنشأ زوج مفاتيح ${L("VPN")} على هاتفك، ولا يغادره المفتاح الخاص أبدًا.`,
        `عند الاتصال، يسجّل خادمنا أي جهاز اتصل وبأي خادم ${L("VPN")}، والعنوان الخاص الذي حصل عليه داخل النفق، ووقت بدء الجلسة وانتهائها، وآخر وقت كان فيه النفق نشطًا، وكمية البيانات المستخدمة. نستخدم هذا لتشغيل الاتصال، وإنهاء الجلسات المتوقفة، وتطبيق حصة البيانات الشهرية.`,
        `لا نسجّل المواقع التي تزورها، ولا محتوى حركة بياناتك، ولا استعلامات ${L("DNS")}. لا يحفظ خادمنا عنوان ${L("IP")} الذي تتصل منه؛ ويستخدمه خادم ${L("VPN")} فقط أثناء اتصالك، ليعيد إليك حركة بياناتك. وأثناء الاتصال، تذهب استعلامات ${L("DNS")} إلى الخوادم العامة لـ ${L("Cloudflare")} (${L("1.1.1.1")}) و${L("Google")} (${L("8.8.8.8")}).`,
        "تبقى سجلات الجلسات مع حسابك حتى تحذفه. ويبدأ عدّاد بياناتك من جديد كل شهر.",
      ],
    },
    {
      id: "sync",
      title: `المزامنة عبر ${L("Wi‑Fi")} وإضافة المتصفح`,
      body: [
        `تنسخ المزامنة عبر ${L("Wi‑Fi")} (بريميوم) خزنتك بين أجهزتك مباشرة، عبر شبكتك المحلية. تجد أجهزتك بعضها على الشبكة، وعندما ينتظر حاسوب انضمام هاتف، يُظهر اسمه للأجهزة الأخرى على تلك الشبكة. وتؤكّد برمز قصير يُستخدم مرة واحدة. الاتصال مشفّر من طرف إلى طرف (${L("SPAKE2")} مع ${L("Argon2id")}، ثم ${L("AES‑256‑GCM")})، ولا تمرّ خزنتك أبدًا عبر خادمنا أو الإنترنت. أما ربط جهاز جديد بحسابك فيسجّله على خادمنا، كما هو موضّح أعلاه.`,
        "لا تتواصل إضافة المتصفح إلا مع تطبيق قاصة لسطح المكتب على الحاسوب نفسه. لا ترسل شيئًا إلينا أو إلى أي أحد، ولا تُجري أي طلبات عبر الشبكة. تقرأ الصفحة التي أنت فيها لتجد حقول تسجيل الدخول، على حاسوبك. وتحفظ مفتاح الربط ولغتك في مساحة تخزين الإضافات في المتصفح، وأثناء تسجيل دخول مقسّم على صفحتين، تُبقي اسم المستخدم في الذاكرة لمدة أقصاها 10 دقائق.",
        `يتحقق تطبيق سطح المكتب من التحديثات عبر ${L("qasa-app.com")}، الذي يحوّل الطلب إلى ${L("GitHub")} حيث تُنشر ملفات التثبيت. والتنزيلات من هذا الموقع تأتي من ${L("GitHub")} أيضًا، لذا يرى ${L("GitHub")} عنوان ${L("IP")} الخاص بك.`,
      ],
    },
    {
      id: "partners",
      title: "الشركات التي تساعدنا في تشغيل قاصة",
      body: [
        [
          `<strong>${L("Contabo")}</strong> تستضيف خادمنا وقاعدة بياناته.`,
          `<strong>${L("Cloudflare")}</strong> توصل هذا الموقع وحركة خادمنا وتحميهما من الهجمات. يمرّ كل طلب عبر ${L("Cloudflare")}، التي ترى عنوان ${L("IP")} الخاص بك.`,
          `<strong>${L("Wayl")}</strong> تعالج المدفوعات.`,
          `<strong>${L("Google Firebase Cloud Messaging")}</strong> توصل الإشعارات إلى ${L("Android")}، وإلى ${L("iPhone")} عبر خدمة الإشعارات من ${L("Apple")}. وتستلم رمز الإشعارات الخاص بهاتفك ونص كل إشعار.`,
          `<strong>${L("Have I Been Pwned")}</strong> تجيب عن فحص كلمات المرور المسرّبة.`,
          `<strong>${L("GitHub")}</strong> يستضيف تنزيلات تطبيق سطح المكتب وتحديثاته.`,
          `<strong>${L("Apple")} و${L("Google")} و${L("Microsoft")} و${L("Mozilla")}</strong> توزّع التطبيقات والإضافة عبر متاجرها، وفق سياسات الخصوصية الخاصة بها.`,
        ],
        "قد يكون خادمنا وهذه الخدمات خارج العراق، لذا قد تُعالَج بياناتك في بلدان أخرى.",
      ],
    },
    {
      id: "dont",
      title: "ما لا نفعله",
      body: [
        [
          "لا نبيع بياناتك ولا نؤجّرها ولا نتاجر بها.",
          "لا نعرض إعلانات.",
          `لا تحتوي التطبيقات على أي أدوات تحليلات أو تقارير أعطال أو تتبّع. والشيفرة الخارجية الوحيدة التي تستخدم الشبكة هي ${L("Firebase Cloud Messaging")}، للإشعارات.`,
          `لا يحتوي هذا الموقع على تحليلات أو إعلانات أو ملفات تعريف ارتباط للتتبّع. وقد تضع ${L("Cloudflare")} ملف تعريف ارتباط تحتاجه لصدّ الهجمات.`,
          "لن نطلب منك كلمة مرورك الرئيسية أبدًا، لا عبر الهاتف ولا واتساب ولا البريد الإلكتروني ولا بأي طريقة أخرى.",
        ],
      ],
    },
    {
      id: "retention",
      title: "مدة احتفاظنا بالبيانات",
      body: [
        [
          "<strong>خزنتك:</strong> على أجهزتك، حتى تحذفها.",
          `<strong>سجلات الحساب والأجهزة والإشعارات و${L("VPN")}:</strong> ما دام حسابك موجودًا. يبقى الجهاز الذي أُلغي ربطه في القائمة، معلَّمًا بأنه غير مرتبط وبلا رمز إشعارات، حتى تحذف الحساب.`,
          `<strong>عدّادات عناوين ${L("IP")}:</strong> تُحذف خلال يوم.`,
          "<strong>النسخ الاحتياطية:</strong> تُنسخ قاعدة البيانات احتياطيًا كل ليلة، وتُحفظ كل نسخة 14 يومًا. والبيانات التي تحذفها تختفي من نسخنا الاحتياطية خلال 14 يومًا.",
          "<strong>سجلات الدفع والاشتراك:</strong> تبقى بعد حذف حسابك كسجلات مالية، للمدة التي تتطلبها المحاسبة والقانون.",
        ],
      ],
    },
    {
      id: "rights",
      title: "خياراتك وحقوقك",
      body: [
        [
          "<strong>الاطلاع على بياناتك.</strong> يعرض التطبيق أجهزتك المرتبطة وخطتك ومدفوعاتك وإشعاراتك. ولأي شيء آخر نحتفظ به، راسلنا بالبريد الإلكتروني.",
          `<strong>التصدير.</strong> صدّر خزنتك إلى ملف ${L("CSV")} من الإعدادات في أي وقت.`,
          `<strong>الحذف.</strong> احذف حسابك من الإعدادات (في تطبيق سطح المكتب الآن، وقريبًا في تطبيقات الهاتف)، أو راسلنا بالبريد الإلكتروني. يزيل هذا أجهزتك (الأسماء والمعرّفات وأسماء المناطق ورموز الإشعارات)، وسجلات ${L("VPN")}، وإشعاراتك من خادمنا، ويمسح الخزنة على ذلك الجهاز. وتنتهي أي مدة مدفوعة متبقية. نحتفظ بمعرّف الحساب، معلَّمًا بأنه محذوف كي لا يُستخدم مرة أخرى أبدًا، وبسجلات مدفوعاتك، مرتبطة بهذا المعرّف فقط.`,
          "<strong>التحكم.</strong> يمكنك من التطبيق إلغاء ربط الأجهزة، وإيقاف الإشعارات، وإيقاف التجديد التلقائي، أو إلغاء بريميوم.",
        ],
        "لا نعرفك إلا بمعرّف حسابك، الموجود في تطبيقك، لذا قد نطلبه منك عندما تراسلنا. ويمكنك أيضًا تقديم شكوى إلى سلطة حماية البيانات في مكان إقامتك.",
      ],
    },
    {
      id: "children",
      title: "الأطفال",
      body: [
        "قاصة ليست مصممة للأطفال دون 13 عامًا. لا نطلب أبدًا اسم أي شخص أو عمره أو بيانات التواصل معه، لذا لا يمكننا معرفة عمر المستخدم. ويمكن للوالدين اللذين يريدان إزالة حساب حذفه من التطبيق أو التواصل معنا.",
      ],
    },
    {
      id: "security",
      title: "الأمان",
      body: [
        "خزنتك محمية بالتشفير على جهازك، ولا يفتحها إلا كلمة مرورك الرئيسية. وهي ليست على خادمنا، فلا يمكن أن تتسرّب منه.",
        `لا يمكن الوصول إلى خادمنا إلا عبر ${L("Cloudflare")} وباستخدام ${L("HTTPS")}. ولا تقبل قاعدة بياناته الاتصالات إلا من الخادم نفسه، وأدوات الموظفين مقيّدة.`,
        "لا يوجد نظام آمن تمامًا. إذا أثّر اختراق لخادمنا عليك، فسنخبرك في إشعارات التطبيق وعلى هذا الموقع.",
      ],
    },
    {
      id: "changes",
      title: "التغييرات على هذه السياسة",
      body: [
        "عندما نغيّر هذه السياسة، نحدّث التاريخ في الأعلى. وإذا كان التغيير مهمًا، فسنخبرك أيضًا في إشعارات التطبيق قبل أن يسري.",
      ],
    },
    {
      id: "contact",
      title: "التواصل",
      body: [`للأسئلة أو الطلبات: ${mailRtl}. يمكنك الكتابة بالإنجليزية أو الكردية أو العربية.`],
    },
  ],
};

export const policies: Record<Lang, Policy> = { en, ckb, ar };
