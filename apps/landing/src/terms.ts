// The terms of use (/terms, /ckb/terms, /ar/terms), shown by the same page as the privacy
// policy. DRAFT: written from what the apps and server actually do (prices, Wayl, devices,
// the VPN allowance), awaiting the owner's legal review before release (TODO.md). Keep it in
// step with src/privacy.ts and bump TERMS_UPDATED when it changes.
//
// Paragraphs and list items are trusted HTML. In ckb and ar, Latin runs are wrapped in
// FSI/PDI (`L`), as in the rest of the site's copy. The ckb and ar text awaits native review.

import type { Lang } from "./i18n";
import type { Policy } from "./privacy";

/** ISO date of the current text; shown localized in `updated`. */
export const TERMS_UPDATED = "2026-11-10";

const EMAIL = "privacy@qasa-app.com";
const FSI = "⁨";
const PDI = "⁩";
const L = (s: string) => `${FSI}${s}${PDI}`;
const mail = `<a href="mailto:${EMAIL}" lang="en">${EMAIL}</a>`;
const mailRtl = `<a href="mailto:${EMAIL}" lang="en">${L(EMAIL)}</a>`;

const en: Policy = {
  pageTitle: "Terms of use · Qasa",
  pageDescription: "The terms for using Qasa: your vault and main password, the Free and Premium plans, payments through Wayl, and fair use.",
  kicker: "Terms of use",
  title: "The terms for using Qasa",
  updated: "Last updated: 10 November 2026",
  contents: "On this page",
  sections: [
    {
      id: "short",
      title: "The short version",
      body: [
        [
          "Qasa keeps your passwords and other private details encrypted on your devices. Only your main password opens them.",
          "We can't see your vault, so we can't recover it or reset your main password. Keep a backup.",
          "Free covers one device. Premium (5,000 IQD a month or 50,000 IQD a year) adds more devices, Wi‑Fi sync, the breached-password check and, where available, the VPN.",
          "You pay on Wayl's page. Nothing is charged automatically.",
          "Use Qasa lawfully and don't try to break or abuse the service.",
        ],
      ],
    },
    {
      id: "agreement",
      title: "Who these terms are with",
      body: [
        "Qasa is made and run by Noovio, based in Sulaymaniyah, Iraq. These terms are an agreement between you and Noovio for the Qasa apps, the browser extension, the Qasa server (api.qasa-app.com) and this website. Here, “Qasa”, “we” and “us” mean Noovio and the Qasa service.",
        "By installing or using Qasa you accept these terms. Our <a href=\"/privacy\">privacy policy</a> explains what data we handle and why, and is part of these terms.",
      ],
    },
    {
      id: "vault",
      title: "Your vault and your main password",
      body: [
        "Everything you save in Qasa is yours. It is encrypted on your device with a key made from your main password, and we never receive it.",
        "You are responsible for your main password and for the devices you unlock Qasa on. We never ask for your main password, and no one from Qasa ever will.",
        "Because we have no copy of your vault or your main password, we can't recover either. If you forget your main password, or lose every device without a backup, your vault can't be restored. Keep an encrypted backup somewhere safe, such as iCloud Drive or Google Drive.",
      ],
    },
    {
      id: "plans",
      title: "Free and Premium",
      body: [
        "Free lets you use Qasa on one device. Premium lets you link more devices and adds Wi‑Fi sync between them, the breached-password check and, where it is available, the VPN on your phone.",
        "Premium costs 5,000 IQD a month or 50,000 IQD a year. Prices may change; a new price applies only from your next payment, and we tell you before then.",
        "You pay through Wayl's checkout page. Wayl can't charge you automatically: if auto-renew is on, we send you a payment link before your plan ends, and Premium continues only if you pay it. If you don't, your plan returns to Free and your vault stays on your devices.",
        `If something went wrong with a payment, write to us at ${mail} with your account ID and we'll look into it.`,
      ],
    },
    {
      id: "use",
      title: "Using Qasa fairly",
      body: [
        "Use Qasa only for lawful purposes and only for your own data or data you're allowed to keep. Don't:",
        [
          "try to get into other people's accounts or data, or into our server beyond what the apps do;",
          "attack, overload or reverse-engineer the service to harm it or others;",
          "resell Qasa or share one Premium plan beyond its own devices;",
          "use the VPN to break the law or to harm others.",
        ],
        "The VPN comes with a monthly data allowance, shown in the app. We may limit or block an account that breaks these terms or puts the service or other people at risk.",
      ],
    },
    {
      id: "service",
      title: "The service",
      body: [
        "We work to keep Qasa available and secure, but we can't promise it will always be available or free of errors. Your vault keeps working on your devices even when our server is unreachable; linking devices, Premium and the VPN need the server.",
        "We may update the apps, and add, change or remove features. If a change takes away something you paid for, we'll tell you in advance.",
      ],
    },
    {
      id: "liability",
      title: "Our responsibility",
      body: [
        "Qasa is provided as it is. As far as the law allows, we aren't responsible for loss caused by a forgotten main password, a lost device without a backup, someone else using your unlocked device, or services we don't run (such as your backup location or Wayl).",
        "Nothing in these terms limits rights you have under the law that can't be limited.",
      ],
    },
    {
      id: "ending",
      title: "Ending your use",
      body: [
        "You can stop using Qasa at any time, and delete your account in Settings in any Qasa app. Deleting the account ends any paid time left and erases the vault on that device; backup files you saved yourself stay where you keep them.",
        "We may close an account that seriously breaks these terms. Your vault stays on your devices either way.",
      ],
    },
    {
      id: "changes",
      title: "Changes and contact",
      body: [
        "We may update these terms. The date at the top shows the latest version, and we'll tell you in the app before an important change takes effect.",
        "These terms are governed by the laws of Iraq.",
        `Questions: write to ${mail}.`,
      ],
    },
  ],
};

const ckb: Policy = {
  pageTitle: "مەرجەکانی بەکارهێنان · قاسە",
  pageDescription: "مەرجەکانی بەکارهێنانی قاسە: گەنجینەکەت و وشەی نهێنیی سەرەکی، پلانی بەخۆڕایی و پریمیەم، پارەدان لە ڕێگەی وەیل، و بەکارهێنانی دادپەروەرانە.",
  kicker: "مەرجەکانی بەکارهێنان",
  title: "مەرجەکانی بەکارهێنانی قاسە",
  updated: "دوایین نوێکردنەوە: ١٠ی تشرینی دووەمی ٢٠٢٦",
  contents: "لەم پەڕەیەدا",
  sections: [
    {
      id: "short",
      title: "بە کورتی",
      body: [
        [
          "قاسە وشە نهێنی و زانیارییە تایبەتەکانت بە شێوەی کۆدکراو لەسەر ئامێرەکانت دەپارێزێت. تەنها وشەی نهێنیی سەرەکیت دەیانکاتەوە.",
          "ئێمە گەنجینەکەت نابینین، بۆیە ناتوانین بیگەڕێنینەوە یان وشەی نهێنیی سەرەکیت ڕێکبخەینەوە. پاشەکەوتێک هەڵبگرە.",
          "پلانی بەخۆڕایی بۆ یەک ئامێرە. پریمیەم (٥٬٠٠٠ د.ع مانگانە یان ٥٠٬٠٠٠ د.ع ساڵانە) ئامێری زیاتر، هاوکاتکردن بە وای‌فای، پشکنینی وشە نهێنییە دزراوەکان و، لەو شوێنانەی هەیە، VPN زیاد دەکات.",
          `لە پەڕەی ${L("Wayl")} پارە دەدەیت. هیچ پارەیەک بە خۆکاری لێت نابڕدرێت.`,
          "قاسە بە شێوەیەکی یاسایی بەکاربهێنە و هەوڵی تێکدان یان خراپ بەکارهێنانی خزمەتگوزارییەکە مەدە.",
        ],
      ],
    },
    {
      id: "agreement",
      title: "ئەم مەرجانە لەگەڵ کێن",
      body: [
        "قاسە لەلایەن نوڤیۆ (Noovio)ـەوە دروستکراوە و بەڕێوەدەبرێت، کە بارەگاکەی لە سلێمانییە، عێراق. ئەم مەرجانە ڕێککەوتنێکن لە نێوان تۆ و نوڤیۆ بۆ ئەپەکانی قاسە، پێوەکراوی وێبگەڕ، ڕاژەکاری قاسە و ئەم ماڵپەڕە.",
        "بە دامەزراندن یان بەکارهێنانی قاسە ئەم مەرجانە قبوڵ دەکەیت. <a href=\"/ckb/privacy\">سیاسەتی تایبەتمەندی</a> ڕوونی دەکاتەوە چ زانیارییەک بەکاردەهێنین و بۆچی، و بەشێکە لەم مەرجانە.",
      ],
    },
    {
      id: "vault",
      title: "گەنجینەکەت و وشەی نهێنیی سەرەکی",
      body: [
        "هەموو ئەوەی لە قاسەدا پاشەکەوتی دەکەیت هی خۆتە. لەسەر ئامێرەکەت بە کلیلێک کۆددەکرێت کە لە وشەی نهێنیی سەرەکیتەوە دروستدەبێت، و هەرگیز ناگاتە ئێمە.",
        "تۆ بەرپرسیاری وشەی نهێنیی سەرەکیت و ئەو ئامێرانەی قاسەیان لەسەر دەکەیتەوە. ئێمە هەرگیز داوای وشەی نهێنیی سەرەکیت ناکەین.",
        "چونکە هیچ کۆپییەکی گەنجینەکەت یان وشەی نهێنیی سەرەکیت لای ئێمە نییە، ناتوانین هیچیان بگەڕێنینەوە. ئەگەر وشەی نهێنیی سەرەکیت لەبیر بچێت، یان هەموو ئامێرەکانت بێ پاشەکەوت لەدەست بدەیت، گەنجینەکەت ناگەڕێتەوە. پاشەکەوتێکی کۆدکراو لە شوێنێکی پارێزراو هەڵبگرە، وەک iCloud Drive یان Google Drive.",
      ],
    },
    {
      id: "plans",
      title: "بەخۆڕایی و پریمیەم",
      body: [
        "پلانی بەخۆڕایی ڕێگەت دەدات قاسە لەسەر یەک ئامێر بەکاربهێنیت. پریمیەم ڕێگەت دەدات ئامێری زیاتر ببەستیتەوە و هاوکاتکردن بە وای‌فای، پشکنینی وشە نهێنییە دزراوەکان و، لەو شوێنانەی هەیە، VPN لەسەر مۆبایلەکەت زیاد دەکات.",
        "پریمیەم ٥٬٠٠٠ د.ع مانگانە یان ٥٠٬٠٠٠ د.ع ساڵانەیە. نرخەکان دەکرێت بگۆڕێن؛ نرخی نوێ تەنها لە پارەدانی داهاتووتەوە دەست پێدەکات، و پێشتر پێت دەڵێین.",
        `لە ڕێگەی پەڕەی پارەدانی ${L("Wayl")}ـەوە پارە دەدەیت. ${L("Wayl")} ناتوانێت بە خۆکاری پارەت لێ ببڕێت: ئەگەر نوێکردنەوەی خۆکار چالاک بێت، پێش کۆتایی پلانەکەت بەستەرێکی پارەدانت بۆ دەنێرین، و پریمیەم تەنها ئەگەر پارەکەی بدەیت بەردەوام دەبێت. ئەگەر نەیدەیت، پلانەکەت دەگەڕێتەوە بۆ بەخۆڕایی و گەنجینەکەت لەسەر ئامێرەکانت دەمێنێتەوە.`,
        `ئەگەر کێشەیەک لە پارەدانێکدا هەبوو، بە ناسنامەی هەژمارەکەتەوە بۆ ${mailRtl} بنووسە و سەیری دەکەین.`,
      ],
    },
    {
      id: "use",
      title: "بەکارهێنانی دادپەروەرانەی قاسە",
      body: [
        "قاسە تەنها بۆ مەبەستی یاسایی و تەنها بۆ زانیاریی خۆت یان ئەو زانیارییانەی ڕێگەت پێدراوە هەڵیانبگریت بەکاربهێنە. ئەمانە مەکە:",
        [
          "هەوڵدان بۆ چوونە ناو هەژمار یان زانیاریی کەسانی تر، یان ناو ڕاژەکارەکەمان زیاتر لەوەی ئەپەکان دەیکەن؛",
          "هێرشکردن، قورسکردن یان هەڵوەشاندنەوەی خزمەتگوزارییەکە بۆ زیان گەیاندن بەو یان بە کەسانی تر؛",
          "فرۆشتنەوەی قاسە یان بەشکردنی یەک پلانی پریمیەم زیاتر لە ئامێرەکانی خۆی؛",
          "بەکارهێنانی VPN بۆ شکاندنی یاسا یان زیان گەیاندن بە کەسانی تر.",
        ],
        "VPN بڕێکی داتای مانگانەی هەیە کە لە ئەپەکەدا نیشان دەدرێت. دەکرێت هەژمارێک سنووردار یان بلۆک بکەین کە ئەم مەرجانە دەشکێنێت یان خزمەتگوزارییەکە یان کەسانی تر دەخاتە مەترسییەوە.",
      ],
    },
    {
      id: "service",
      title: "خزمەتگوزارییەکە",
      body: [
        "هەوڵ دەدەین قاسە بەردەست و پارێزراو بێت، بەڵام ناتوانین بەڵێن بدەین هەمیشە بەردەست و بێ هەڵە بێت. گەنجینەکەت لەسەر ئامێرەکانت کاردەکات تەنانەت کاتێک ڕاژەکارەکەمان بەردەست نییە؛ بەستنەوەی ئامێر، پریمیەم و VPN پێویستیان بە ڕاژەکارەکەیە.",
        "دەکرێت ئەپەکان نوێ بکەینەوە و تایبەتمەندی زیاد، گۆڕین یان لابەرین. ئەگەر گۆڕانکارییەک شتێک لاببات کە پارەت بۆی داوە، پێشوەخت پێت دەڵێین.",
      ],
    },
    {
      id: "liability",
      title: "بەرپرسیارێتیی ئێمە",
      body: [
        "قاسە وەک خۆی پێشکەش دەکرێت. تا ئەو ڕادەیەی یاسا ڕێگە دەدات، بەرپرسیار نین لە زیانێک کە لە لەبیرچوونی وشەی نهێنیی سەرەکی، ونبوونی ئامێرێک بێ پاشەکەوت، بەکارهێنانی ئامێرە کراوەکەت لەلایەن کەسێکی ترەوە، یان ئەو خزمەتگوزارییانەی ئێمە بەڕێوەیان نابەین (وەک شوێنی پاشەکەوتەکەت یان Wayl) دروست دەبێت.",
        "هیچ شتێک لەم مەرجانەدا ئەو مافانەت سنووردار ناکات کە یاسا ڕێگە نادات سنووردار بکرێن.",
      ],
    },
    {
      id: "ending",
      title: "کۆتاییهێنان بە بەکارهێنان",
      body: [
        "هەر کاتێک بتەوێت دەتوانیت واز لە قاسە بهێنیت، و لە ڕێکخستنەکانی هەر ئەپێکی قاسەدا هەژمارەکەت بسڕیتەوە. سڕینەوەی هەژمار کاتی پارەدراوی ماوە کۆتایی پێدەهێنێت و گەنجینەی سەر ئەو ئامێرە دەسڕێتەوە؛ ئەو فایلە پاشەکەوتانەی خۆت هەڵتگرتوون لە شوێنی خۆیان دەمێننەوە.",
        "دەکرێت هەژمارێک دابخەین کە بە توندی ئەم مەرجانە دەشکێنێت. لە هەردوو حاڵەتدا گەنجینەکەت لەسەر ئامێرەکانت دەمێنێتەوە.",
      ],
    },
    {
      id: "changes",
      title: "گۆڕانکاری و پەیوەندی",
      body: [
        "دەکرێت ئەم مەرجانە نوێ بکەینەوە. بەرواری سەرەوە دوایین وەشان نیشان دەدات، و پێش جێبەجێبوونی گۆڕانکارییەکی گرنگ لە ئەپەکەدا پێت دەڵێین.",
        "ئەم مەرجانە ملکەچی یاساکانی عێراقن.",
        `پرسیار: بۆ ${mailRtl} بنووسە.`,
      ],
    },
  ],
};

const ar: Policy = {
  pageTitle: "شروط الاستخدام · قاصة",
  pageDescription: "شروط استخدام قاصة: خزنتك وكلمة المرور الرئيسية، والخطة المجانية وبريميوم، والدفع عبر وايل، والاستخدام العادل.",
  kicker: "شروط الاستخدام",
  title: "شروط استخدام قاصة",
  updated: "آخر تحديث: 10 تشرين الثاني 2026",
  contents: "في هذه الصفحة",
  sections: [
    {
      id: "short",
      title: "باختصار",
      body: [
        [
          "تحفظ قاصة كلمات مرورك وبياناتك الخاصة مشفّرة على أجهزتك. كلمة المرور الرئيسية وحدها تفتحها.",
          "لا نرى خزنتك، لذلك لا نستطيع استعادتها أو إعادة تعيين كلمة مرورك الرئيسية. احتفظ بنسخة احتياطية.",
          "الخطة المجانية لجهاز واحد. بريميوم (5,000 د.ع شهريًا أو 50,000 د.ع سنويًا) تضيف أجهزة أكثر، والمزامنة عبر الواي فاي، وفحص كلمات المرور المسرّبة، و VPN حيث يتوفر.",
          `تدفع على صفحة ${L("Wayl")}. لا يُخصم أي مبلغ تلقائيًا.`,
          "استخدم قاصة بشكل قانوني ولا تحاول تعطيل الخدمة أو إساءة استخدامها.",
        ],
      ],
    },
    {
      id: "agreement",
      title: "مع من هذه الشروط",
      body: [
        "تصنع قاصة وتديرها شركة نوفيو (Noovio) ومقرها في السليمانية، العراق. هذه الشروط اتفاق بينك وبين نوفيو بشأن تطبيقات قاصة وإضافة المتصفح وخادم قاصة وهذا الموقع.",
        "بتثبيت قاصة أو استخدامها فإنك تقبل هذه الشروط. توضّح <a href=\"/ar/privacy\">سياسة الخصوصية</a> البيانات التي نتعامل معها ولماذا، وهي جزء من هذه الشروط.",
      ],
    },
    {
      id: "vault",
      title: "خزنتك وكلمة المرور الرئيسية",
      body: [
        "كل ما تحفظه في قاصة ملك لك. يُشفَّر على جهازك بمفتاح مشتق من كلمة مرورك الرئيسية، ولا يصل إلينا أبدًا.",
        "أنت مسؤول عن كلمة مرورك الرئيسية وعن الأجهزة التي تفتح عليها قاصة. لن نطلب منك كلمة مرورك الرئيسية أبدًا.",
        "لأننا لا نملك نسخة من خزنتك أو من كلمة مرورك الرئيسية، لا نستطيع استعادة أي منهما. إذا نسيت كلمة مرورك الرئيسية، أو فقدت كل أجهزتك دون نسخة احتياطية، فلا يمكن استعادة خزنتك. احتفظ بنسخة احتياطية مشفّرة في مكان آمن، مثل iCloud Drive أو Google Drive.",
      ],
    },
    {
      id: "plans",
      title: "المجانية وبريميوم",
      body: [
        "تتيح لك الخطة المجانية استخدام قاصة على جهاز واحد. تتيح لك بريميوم ربط أجهزة أكثر، وتضيف المزامنة عبر الواي فاي بينها، وفحص كلمات المرور المسرّبة، و VPN على هاتفك حيث يتوفر.",
        "سعر بريميوم 5,000 د.ع شهريًا أو 50,000 د.ع سنويًا. قد تتغير الأسعار؛ ويُطبَّق السعر الجديد من دفعتك التالية فقط، ونخبرك قبل ذلك.",
        `تدفع عبر صفحة الدفع لدى ${L("Wayl")}. لا يستطيع ${L("Wayl")} أن يخصم منك تلقائيًا: إذا كان التجديد التلقائي مفعّلًا، نرسل لك رابط دفع قبل انتهاء خطتك، وتستمر بريميوم فقط إذا دفعته. وإن لم تدفع، تعود خطتك إلى المجانية وتبقى خزنتك على أجهزتك.`,
        `إذا حدثت مشكلة في عملية دفع، راسلنا على ${mailRtl} مع معرّف حسابك وسنتحقق منها.`,
      ],
    },
    {
      id: "use",
      title: "الاستخدام العادل لقاصة",
      body: [
        "استخدم قاصة لأغراض قانونية فقط، ولبياناتك أو البيانات المسموح لك بحفظها فقط. لا تقم بما يلي:",
        [
          "محاولة الوصول إلى حسابات الآخرين أو بياناتهم، أو إلى خادمنا بما يتجاوز ما تفعله التطبيقات؛",
          "مهاجمة الخدمة أو إثقالها أو تفكيكها للإضرار بها أو بالآخرين؛",
          "إعادة بيع قاصة أو مشاركة خطة بريميوم واحدة خارج أجهزتها؛",
          "استخدام VPN لمخالفة القانون أو للإضرار بالآخرين.",
        ],
        "يأتي VPN مع حصة بيانات شهرية تظهر في التطبيق. قد نقيّد أو نحظر حسابًا يخالف هذه الشروط أو يعرّض الخدمة أو الآخرين للخطر.",
      ],
    },
    {
      id: "service",
      title: "الخدمة",
      body: [
        "نعمل على إبقاء قاصة متاحة وآمنة، لكن لا يمكننا أن نعد بأن تكون متاحة دائمًا أو خالية من الأخطاء. تبقى خزنتك تعمل على أجهزتك حتى عندما لا يمكن الوصول إلى خادمنا؛ أما ربط الأجهزة وبريميوم و VPN فتحتاج إلى الخادم.",
        "قد نحدّث التطبيقات، ونضيف ميزات أو نغيّرها أو نزيلها. إذا أزال تغيير ما شيئًا دفعت مقابله، فسنخبرك مسبقًا.",
      ],
    },
    {
      id: "liability",
      title: "مسؤوليتنا",
      body: [
        "تُقدَّم قاصة كما هي. في الحدود التي يسمح بها القانون، لسنا مسؤولين عن خسارة سببها نسيان كلمة المرور الرئيسية، أو فقدان جهاز دون نسخة احتياطية، أو استخدام شخص آخر لجهازك المفتوح، أو خدمات لا نديرها (مثل مكان نسختك الاحتياطية أو Wayl).",
        "لا شيء في هذه الشروط يقيّد حقوقًا لك بموجب القانون لا يجوز تقييدها.",
      ],
    },
    {
      id: "ending",
      title: "إنهاء الاستخدام",
      body: [
        "يمكنك التوقف عن استخدام قاصة في أي وقت، وحذف حسابك من الإعدادات في أي تطبيق لقاصة. حذف الحساب ينهي أي مدة مدفوعة متبقية ويمسح الخزنة على ذلك الجهاز؛ أما ملفات النسخ الاحتياطي التي حفظتها بنفسك فتبقى حيث تحتفظ بها.",
        "قد نغلق حسابًا يخالف هذه الشروط بشكل جسيم. وفي كل الأحوال تبقى خزنتك على أجهزتك.",
      ],
    },
    {
      id: "changes",
      title: "التغييرات والتواصل",
      body: [
        "قد نحدّث هذه الشروط. يظهر التاريخ في الأعلى أحدث نسخة، وسنخبرك في التطبيق قبل أن يسري أي تغيير مهم.",
        "تخضع هذه الشروط لقوانين العراق.",
        `للأسئلة: راسلنا على ${mailRtl}.`,
      ],
    },
  ],
};

export const termsByLang: Record<Lang, Policy> = { en, ckb, ar };
