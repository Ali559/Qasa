// Landing page copy. Product terms (tab names, "main password", "breached passwords", item
// types, د.ع) follow l10n/strings.json so the page uses the same words as the app.
// The name in running text is localized like the app: Qasa / قاسە (ckb) / قاصة (ar). The logo
// stays the Latin "Qasa" wordmark in every language, matching brand.name.

export type Lang = "en" | "ckb" | "ar";

export const languages: { lang: Lang; label: string; href: string }[] = [
  { lang: "en", label: "EN", href: "/" },
  { lang: "ckb", label: "کوردی", href: "/ckb/" },
  { lang: "ar", label: "العربية", href: "/ar/" },
];

const en = {
  meta: {
    title: "Qasa: your passwords, kept safe on your phone",
    description:
      "Qasa remembers your passwords, cards and IDs, encrypted on your phone. In English, Kurdish and Arabic. Free to start; Premium paid with FIB, FastPay or SuperQi.",
  },
  nav: { features: "Features", privacy: "Privacy", pricing: "Pricing", faq: "FAQ", cta: "Get Qasa", language: "Language" },
  hero: {
    eyebrow: "Free on iPhone and Android",
    h1: "Your passwords, kept safe",
    h1Accent: "on your phone",
    lede: "Qasa saves and fills your passwords, cards and IDs. Everything is encrypted on your phone with one main password, and never leaves it.",
    freeDevice: "Free on 1 device",
    neverServer: "Your vault never touches our server",
    screenAlt: "The Qasa app showing a list of saved passwords",
    leakTitle: "1 breached password",
    leakSub: "Change your Gmail password",
  },
  stores: { appleSmall: "Download on the", playSmall: "Get it on" },
  strip: "Premium is paid the way you already pay",
  features: {
    kicker: "What Qasa does",
    title: 'No more sticky notes, screenshots or "forgot password"',
    lede: "Passwords, payment cards, secure notes, personal info and ID documents, in one encrypted vault on your phone.",
    types: ["Logins", "Payment cards", "Secure notes", "Personal info", "ID documents"],
    autofillTitle: "Fills your login inside apps and browsers",
    autofillBody: "When a sign-in screen opens, Qasa suggests the matching login. One tap fills the username and password.",
    fillWith: "Fill with Qasa",
    leaksTitle: "Checks every saved password against known leaks",
    leaksBody: "Qasa compares your passwords with Have I Been Pwned's list of leaked passwords and shows which ones to change. Only a 5-character code leaves your phone.",
    alertsAlt: "The Breached passwords screen in Qasa, showing a Gmail password seen 119,167 times in breaches",
    healthTitle: "Shows which passwords are reused or easy to guess",
    healthBody: "A score out of 100, with the weak, reused and leaked logins listed so you can fix them one by one.",
    healthAlt: "The Password health screen in Qasa, with a score of 39 out of 100",
    alsoKeeps: "Also keeps",
    langsLine: "In English, Kurdish and Arabic, light or dark.",
  },
  privacy: {
    kicker: "Privacy, built in",
    title: "Your passwords are encrypted on your phone, and only you hold the key.",
    lede: "Your vault is encrypted on your phone and stays there. Our server only counts your devices and handles payments. It never receives what's inside.",
    points: [
      { title: "Stored on your phone", body: "Not in someone else's cloud." },
      { title: "One main password", body: "Only you know it. It unlocks everything." },
      { title: "Nothing to leak", body: "If our server is ever attacked, your vault isn't on it." },
    ],
    server: "Qasa server",
    serverSub: "Devices & payments only",
    vault: "Your vault, encrypted",
    spec: "AES‑256‑GCM, with a key made from your main password (PBKDF2, 310,000 rounds). The key never leaves your phone.",
    never: "No one from Qasa will ever ask for your main password, not by phone, WhatsApp or email.",
  },
  pricing: {
    kicker: "Simple pricing",
    title: "Start free. Upgrade when you need more.",
    lede: "Prices in Iraqi dinar, paid with the wallet you already have.",
    currency: "IQD",
    free: "Free",
    freeDesc: "Everything you need on one phone.",
    freePrice: "0",
    freeFeatures: [
      "Unlimited passwords, cards, notes and IDs",
      "One-tap autofill in apps and browsers",
      "Password generator, health check and breach check",
      "English, Kurdish and Arabic",
      "Use it on 1 device",
    ],
    freeCta: "Download free",
    premium: "Premium",
    premiumDesc: "For your phone, tablet and the rest of the family's devices.",
    monthPrice: "5,000",
    perMonth: "/ month",
    yearPrefix: "or",
    yearPrice: "50,000 IQD / year",
    yearSuffix: ", two months free",
    premiumFeatures: [
      "Everything in Free",
      "Use it on up to 5 devices",
      "Sync your devices on Wi‑Fi",
      "Pay with FIB, FastPay or SuperQi",
    ],
    premiumCta: "Get Premium in the app",
    best: "Best value · yearly",
  },
  faq: {
    kicker: "Questions",
    title: "Good to know",
    items: [
      {
        q: "Can Qasa see my passwords?",
        a: "No. Your passwords are encrypted on your phone with your main password, and they stay there. Our server never receives them, so there is nothing for us, or anyone who breaks into our server, to read.",
      },
      {
        q: "What happens if I forget my main password?",
        a: "Because only you know it, we can't reset it for you. Pick something memorable and keep a written copy somewhere safe at home.",
      },
      {
        q: "How do I pay for Premium?",
        a: "Right in the app, with FIB, FastPay or SuperQi. No international card needed. Premium is 5,000 IQD a month or 50,000 IQD a year.",
      },
      {
        q: "Does it work in Kurdish and Arabic?",
        a: "Yes. The whole app is available in English, Kurdish (Sorani) and Arabic, with proper right-to-left layouts, and in light or dark.",
      },
      {
        q: "How does the breached-password check work?",
        a: "Qasa turns each saved password into a code (a hash) on your phone and sends only the first 5 characters to Have I Been Pwned, a public database of leaked passwords. The match happens on your phone, so your passwords never leave it. It's free for everyone.",
      },
    ],
  },
  cta: {
    title: "Give your passwords a safe home",
    lede: "Download Qasa and set your main password. It takes about a minute.",
  },
};

export type Dict = typeof en;

const ckb: Dict = {
  meta: {
    title: "قاسە: وشە نهێنییەکانت، بە سەلامەتی لەسەر مۆبایلەکەت",
    description:
      "قاسە وشەی نهێنی، کارت و ناسنامەکانت بە شفرەکراوی لەسەر مۆبایلەکەت هەڵدەگرێت. بە ئینگلیزی، کوردی و عەرەبی. بە خۆڕایی دەست پێبکە؛ پارەی پریمیۆم بە FIB، FastPay یان SuperQi بدە.",
  },
  nav: { features: "تایبەتمەندییەکان", privacy: "نهێنیپارێزی", pricing: "نرخەکان", faq: "پرسیارەکان", cta: "قاسە دابەزێنە", language: "زمان" },
  hero: {
    eyebrow: "بە خۆڕایی بۆ iPhone و Android",
    h1: "وشە نهێنییەکانت، بە سەلامەتی",
    h1Accent: "لەسەر مۆبایلەکەت",
    lede: "قاسە وشەی نهێنی، کارت و ناسنامەکانت هەڵدەگرێت و بۆت پڕدەکاتەوە. هەموو شتێک بە یەک وشەی نهێنی سەرەکی لەسەر مۆبایلەکەت شفرە دەکرێت و هەرگیز بەجێی ناهێڵێت.",
    freeDevice: "بە خۆڕایی لەسەر ١ ئامێر",
    neverServer: "کۆگاکەت هەرگیز ناگاتە سێرڤەرەکەمان",
    screenAlt: "ئەپی قاسە کە لیستی وشە نهێنییە پاشەکەوتکراوەکان پیشان دەدات",
    leakTitle: "١ وشەی نهێنیی دزراو",
    leakSub: "وشەی نهێنی Gmail بگۆڕە",
  },
  stores: { appleSmall: "دابەزاندن لە", playSmall: "دابەزاندن لە" },
  strip: "پارەی پریمیۆم بەو جزدانانە بدە کە هەتە",
  features: {
    kicker: "قاسە چی دەکات",
    title: "چیتر نە کاغەزی چەسپاو، نە سکرینشۆت، نە «وشەی نهێنیم لەبیرچوو»",
    lede: "وشەی نهێنی، کارتی بانکی، تێبینیی پارێزراو، زانیاری کەسی و ناسنامە، لە یەک کۆگای شفرەکراودا لەسەر مۆبایلەکەت.",
    types: ["چوونەژوورەوەکان", "کارتی بانکی", "تێبینیی پارێزراو", "زانیاری کەسی", "ناسنامەکان"],
    autofillTitle: "چوونەژوورەوەکەت لە ئەپ و وێبگەڕەکاندا پڕدەکاتەوە",
    autofillBody: "کاتێک پەڕەی چوونەژوورەوە دەکرێتەوە، قاسە چوونەژوورەوەی گونجاو پێشنیار دەکات. بە یەک لێدان ناوی بەکارهێنەر و وشەی نهێنی پڕدەکرێنەوە.",
    fillWith: "پڕکردنەوە بە قاسە",
    leaksTitle: "هەموو وشە نهێنییە پاشەکەوتکراوەکانت لەگەڵ دزەکردنە ناسراوەکان بەراورد دەکات",
    leaksBody: "قاسە وشە نهێنییەکانت لەگەڵ لیستی وشە نهێنییە دزراوەکانی Have I Been Pwned بەراورد دەکات و پیشانت دەدات کامیان بگۆڕیت. تەنها کۆدێکی ٥ پیتی مۆبایلەکەت بەجێدەهێڵێت.",
    alertsAlt: "پەڕەی وشە نهێنییە دزراوەکان لە قاسە، کە وشەی نهێنیی Gmail پیشان دەدات کە ١١٩٬١٦٧ جار لە دزەکاندا بینراوە",
    healthTitle: "پیشانت دەدات کام وشەی نهێنی دووبارەیە یان بە ئاسانی دەزانرێت",
    healthBody: "خاڵێک لە ١٠٠، لەگەڵ لیستی چوونەژوورەوە لاواز، دووبارە و دزراوەکان، بۆ ئەوەی یەک بە یەک چاکیان بکەیت.",
    healthAlt: "پەڕەی تەندروستیی وشە نهێنییەکان لە قاسە، بە خاڵی ٣٩ لە ١٠٠",
    alsoKeeps: "هەروەها هەڵدەگرێت",
    langsLine: "بە ئینگلیزی، کوردی و عەرەبی، ڕووناک یان تاریک.",
  },
  privacy: {
    kicker: "نهێنیپارێزی لە بنەڕەتەوە",
    title: "وشە نهێنییەکانت لەسەر مۆبایلەکەت شفرە دەکرێن، و تەنها تۆ کلیلەکەت لایە.",
    lede: "کۆگاکەت لەسەر مۆبایلەکەت شفرە دەکرێت و هەر لەوێ دەمێنێتەوە. سێرڤەرەکەمان تەنها ئامێرەکانت دەژمێرێت و پارەدان ڕێکدەخات. هەرگیز ناوەڕۆکەکەی پێناگات.",
    points: [
      { title: "لەسەر مۆبایلەکەت هەڵدەگیرێت", body: "نەک لە هەوری کەسێکی تر." },
      { title: "یەک وشەی نهێنی سەرەکی", body: "تەنها تۆ دەیزانیت. هەموو شتێک دەکاتەوە." },
      { title: "هیچ شتێک نییە بۆ دزەکردن", body: "ئەگەر هێرش کرایە سەر سێرڤەرەکەمان، کۆگاکەت لەوێ نییە." },
    ],
    server: "سێرڤەری قاسە",
    serverSub: "تەنها ئامێر و پارەدان",
    vault: "کۆگاکەت، شفرەکراو",
    spec: "شفرەکردنی ⁨AES‑256‑GCM⁩، بە کلیلێک کە بە ⁨PBKDF2⁩ و ٣١٠٬٠٠٠ جار لە وشەی نهێنی سەرەکییەکەت دروست دەکرێت. کلیلەکە هەرگیز مۆبایلەکەت بەجێناهێڵێت.",
    never: "هیچ کەسێک لە قاسەوە هەرگیز داوای وشەی نهێنی سەرەکییەکەت ناکات، نە بە تەلەفۆن، نە بە WhatsApp، نە بە ئیمەیڵ.",
  },
  pricing: {
    kicker: "نرخی ساکار",
    title: "بە خۆڕایی دەست پێبکە. کاتێک زیاترت پێویست بوو بەرزی بکەرەوە.",
    lede: "نرخەکان بە دیناری عێراقین و بەو جزدانەی کە هەتە دەدرێن.",
    currency: "د.ع",
    free: "خۆڕایی",
    freeDesc: "هەموو ئەوەی پێویستتە لەسەر یەک مۆبایل.",
    freePrice: "٠",
    freeFeatures: [
      "وشەی نهێنی، کارت، تێبینی و ناسنامەی بێسنوور",
      "پڕکردنەوەی خۆکار بە یەک لێدان لە ئەپ و وێبگەڕەکان",
      "دروستکەری وشەی نهێنی، پشکنینی تەندروستی و پشکنینی دزەکردن",
      "ئینگلیزی، کوردی و عەرەبی",
      "بەکارهێنان لەسەر ١ ئامێر",
    ],
    freeCta: "بە خۆڕایی دابەزێنە",
    premium: "پریمیۆم",
    premiumDesc: "بۆ مۆبایل و تابلێتەکەت و ئامێرەکانی خێزانەکەت.",
    monthPrice: "٥٬٠٠٠",
    perMonth: "/ مانگ",
    yearPrefix: "یان",
    yearPrice: "٥٠٬٠٠٠ د.ع / ساڵ",
    yearSuffix: "، دوو مانگ بە خۆڕایی",
    premiumFeatures: [
      "هەموو شتەکانی پلانی خۆڕایی",
      "بەکارهێنان لەسەر تا ٥ ئامێر",
      "هاوکاتکردنی ئامێرەکانت لەسەر Wi‑Fi",
      "پارەدان بە FIB، FastPay یان SuperQi",
    ],
    premiumCta: "پریمیۆم لە ئەپەکەدا وەربگرە",
    best: "باشترین نرخ · ساڵانە",
  },
  faq: {
    kicker: "پرسیارەکان",
    title: "باشە بیزانیت",
    items: [
      {
        q: "ئایا قاسە دەتوانێت وشە نهێنییەکانم ببینێت؟",
        a: "نەخێر. وشە نهێنییەکانت لەسەر مۆبایلەکەت بە وشەی نهێنی سەرەکییەکەت شفرە دەکرێن و هەر لەوێ دەمێنن. سێرڤەرەکەمان هەرگیز وەریان ناگرێت، بۆیە هیچ شتێک نییە کە ئێمە یان هەر کەسێک بچێتە نێو سێرڤەرەکەمان بیخوێنێتەوە.",
      },
      {
        q: "ئەگەر وشەی نهێنی سەرەکیم لەبیر چوو چی دەبێت؟",
        a: "لەبەر ئەوەی تەنها تۆ دەیزانیت، ناتوانین بۆت ڕێکبخەینەوە. شتێک هەڵبژێرە کە لەبیرت بمێنێت و کۆپییەکی نووسراو لە شوێنێکی سەلامەت لە ماڵەوە دابنێ.",
      },
      {
        q: "چۆن پارەی پریمیۆم بدەم؟",
        a: "ڕاستەوخۆ لە ئەپەکەدا، بە FIB، FastPay یان SuperQi. پێویستت بە کارتی نێودەوڵەتی نییە. نرخی پریمیۆم مانگانە ٥٬٠٠٠ د.ع یان ساڵانە ٥٠٬٠٠٠ د.ع دەبێت.",
      },
      {
        q: "ئایا بە کوردی و عەرەبی کار دەکات؟",
        a: "بەڵێ. هەموو ئەپەکە بە ئینگلیزی، کوردی (سۆرانی) و عەرەبی بەردەستە، لەگەڵ ڕووکاری ڕاست-بۆ-چەپی دروست، و بە ڕووناک یان تاریک.",
      },
      {
        q: "پشکنینی وشەی نهێنیی دزراو چۆن کار دەکات؟",
        a: "قاسە هەر وشەیەکی نهێنیی پاشەکەوتکراو لەسەر مۆبایلەکەت دەگۆڕێت بۆ کۆدێک (هاش) و تەنها ٥ پیتی یەکەمی دەنێرێت بۆ Have I Been Pwned، کە بنکەدراوەیەکی گشتیی وشە نهێنییە دزراوەکانە. بەراوردکردنەکە لەسەر مۆبایلەکەت دەکرێت، بۆیە وشە نهێنییەکانت هەرگیز بەجێی ناهێڵن. بۆ هەمووان خۆڕاییە.",
      },
    ],
  },
  cta: {
    title: "ماڵێکی سەلامەت بدە بە وشە نهێنییەکانت",
    lede: "قاسە دابەزێنە و وشەی نهێنی سەرەکییەکەت دابنێ. نزیکەی یەک خولەک دەخایەنێت.",
  },
};

const ar: Dict = {
  meta: {
    title: "قاصة: كلمات مرورك في أمان على هاتفك",
    description:
      "تحفظ قاصة كلمات مرورك وبطاقاتك وهوياتك مشفّرة على هاتفك. بالإنجليزية والكردية والعربية. ابدأ مجانًا، وادفع اشتراك بريميوم عبر FIB أو FastPay أو SuperQi.",
  },
  nav: { features: "الميزات", privacy: "الخصوصية", pricing: "الأسعار", faq: "الأسئلة", cta: "حمّل قاصة", language: "اللغة" },
  hero: {
    eyebrow: "مجاني على iPhone و Android",
    h1: "كلمات مرورك في أمان",
    h1Accent: "على هاتفك",
    lede: "تحفظ قاصة كلمات مرورك وبطاقاتك وهوياتك وتملؤها لك. كل شيء مشفّر على هاتفك بكلمة مرور رئيسية واحدة، ولا يغادره أبدًا.",
    freeDevice: "مجاني على جهاز واحد",
    neverServer: "خزنتك لا تصل إلى خادمنا أبدًا",
    screenAlt: "تطبيق قاصة يعرض قائمة كلمات المرور المحفوظة",
    leakTitle: "كلمة مرور واحدة مسرّبة",
    leakSub: "غيّر كلمة مرور Gmail",
  },
  stores: { appleSmall: "حمّله من", playSmall: "احصل عليه من" },
  strip: "ادفع اشتراك بريميوم بالمحفظة التي تستخدمها أصلًا",
  features: {
    kicker: "ماذا تفعل قاصة",
    title: "وداعًا للأوراق اللاصقة ولقطات الشاشة و«نسيت كلمة المرور»",
    lede: "كلمات المرور والبطاقات المصرفية والملاحظات الآمنة والمعلومات الشخصية والهويات، في خزنة مشفّرة واحدة على هاتفك.",
    types: ["تسجيلات الدخول", "بطاقات الدفع", "ملاحظات آمنة", "معلومات شخصية", "الهويات"],
    autofillTitle: "تملأ بيانات الدخول داخل التطبيقات والمتصفحات",
    autofillBody: "عندما تظهر شاشة تسجيل الدخول، تقترح قاصة الحساب المناسب. بلمسة واحدة يُملأ اسم المستخدم وكلمة المرور.",
    fillWith: "التعبئة عبر قاصة",
    leaksTitle: "تفحص كل كلمة مرور محفوظة مقابل التسريبات المعروفة",
    leaksBody: "تقارن قاصة كلمات مرورك بقائمة Have I Been Pwned لكلمات المرور المسرّبة، وتُظهر لك أيّها يجب تغييره. لا يغادر هاتفك سوى رمز من 5 أحرف.",
    alertsAlt: "شاشة كلمات المرور المسرّبة في قاصة، تُظهر كلمة مرور Gmail ظهرت 119,167 مرة في التسريبات",
    healthTitle: "تُظهر كلمات المرور المكرّرة أو السهلة التخمين",
    healthBody: "درجة من 100، مع قائمة بالحسابات الضعيفة والمكرّرة والمسرّبة لتصلحها واحدًا تلو الآخر.",
    healthAlt: "شاشة صحة كلمات المرور في قاصة، بدرجة 39 من 100",
    alsoKeeps: "وتحفظ أيضًا",
    langsLine: "بالإنجليزية والكردية والعربية، بالوضع الفاتح أو الداكن.",
  },
  privacy: {
    kicker: "الخصوصية في الأساس",
    title: "كلمات مرورك مشفّرة على هاتفك، والمفتاح معك وحدك.",
    lede: "خزنتك مشفّرة على هاتفك وتبقى هناك. خادمنا يعدّ أجهزتك ويتولى الدفع فقط، ولا يستلم محتواها أبدًا.",
    points: [
      { title: "مخزّنة على هاتفك", body: "وليس في سحابة شخص آخر." },
      { title: "كلمة مرور رئيسية واحدة", body: "أنت وحدك تعرفها، وهي تفتح كل شيء." },
      { title: "لا شيء ليتسرّب", body: "إذا تعرّض خادمنا لهجوم، فخزنتك ليست عليه." },
    ],
    server: "خادم قاصة",
    serverSub: "الأجهزة والدفع فقط",
    vault: "خزنتك، مشفّرة",
    spec: "تشفير ⁨AES‑256‑GCM⁩، بمفتاح مشتق من كلمة مرورك الرئيسية عبر ⁨PBKDF2⁩ بـ 310,000 دورة. المفتاح لا يغادر هاتفك أبدًا.",
    never: "لن يطلب منك أي أحد من قاصة كلمة مرورك الرئيسية أبدًا، لا عبر الهاتف ولا واتساب ولا البريد الإلكتروني.",
  },
  pricing: {
    kicker: "أسعار بسيطة",
    title: "ابدأ مجانًا. وقم بالترقية عندما تحتاج المزيد.",
    lede: "الأسعار بالدينار العراقي، وتُدفع بالمحفظة التي لديك.",
    currency: "د.ع",
    free: "مجاني",
    freeDesc: "كل ما تحتاجه على هاتف واحد.",
    freePrice: "0",
    freeFeatures: [
      "كلمات مرور وبطاقات وملاحظات وهويات بلا حدود",
      "تعبئة تلقائية بلمسة في التطبيقات والمتصفحات",
      "مولّد كلمات المرور وفحص صحتها وفحص التسريبات",
      "الإنجليزية والكردية والعربية",
      "الاستخدام على جهاز واحد",
    ],
    freeCta: "حمّل مجانًا",
    premium: "بريميوم",
    premiumDesc: "لهاتفك وجهازك اللوحي وأجهزة العائلة.",
    monthPrice: "5,000",
    perMonth: "/ شهر",
    yearPrefix: "أو",
    yearPrice: "50,000 د.ع / سنة",
    yearSuffix: "، أي شهران مجانًا",
    premiumFeatures: [
      "كل ما في الخطة المجانية",
      "الاستخدام على ما يصل إلى 5 أجهزة",
      "مزامنة أجهزتك عبر Wi‑Fi",
      "الدفع عبر FIB أو FastPay أو SuperQi",
    ],
    premiumCta: "احصل على بريميوم من التطبيق",
    best: "أفضل قيمة · سنوي",
  },
  faq: {
    kicker: "أسئلة",
    title: "معلومات تهمّك",
    items: [
      {
        q: "هل تستطيع قاصة رؤية كلمات مروري؟",
        a: "لا. كلمات مرورك مشفّرة على هاتفك بكلمة مرورك الرئيسية وتبقى هناك. لا يستلمها خادمنا أبدًا، لذا لا يوجد ما يمكن أن نقرأه نحن أو أي شخص يخترق خادمنا.",
      },
      {
        q: "ماذا يحدث إذا نسيت كلمة المرور الرئيسية؟",
        a: "لأنك وحدك تعرفها، لا يمكننا إعادة تعيينها لك. اختر شيئًا يسهل تذكّره، واحتفظ بنسخة مكتوبة في مكان آمن في منزلك.",
      },
      {
        q: "كيف أدفع اشتراك بريميوم؟",
        a: "من داخل التطبيق مباشرةً، عبر FIB أو FastPay أو SuperQi. لا حاجة إلى بطاقة دولية. سعر بريميوم 5,000 د.ع شهريًا أو 50,000 د.ع سنويًا.",
      },
      {
        q: "هل يعمل بالكردية والعربية؟",
        a: "نعم. التطبيق كاملًا متوفر بالإنجليزية والكردية (السورانية) والعربية، باتجاه صحيح من اليمين إلى اليسار، وبالوضع الفاتح أو الداكن.",
      },
      {
        q: "كيف يعمل فحص كلمات المرور المسرّبة؟",
        a: "تحوّل قاصة كل كلمة مرور محفوظة إلى رمز (hash) على هاتفك، ولا ترسل سوى أول 5 أحرف منه إلى Have I Been Pwned، وهي قاعدة بيانات عامة لكلمات المرور المسرّبة. تتم المطابقة على هاتفك، فلا تغادره كلمات مرورك أبدًا. الفحص مجاني للجميع.",
      },
    ],
  },
  cta: {
    title: "امنح كلمات مرورك مكانًا آمنًا",
    lede: "حمّل قاصة واختر كلمة مرورك الرئيسية. الأمر يستغرق دقيقة تقريبًا.",
  },
};

export const dictionaries: Record<Lang, Dict> = { en, ckb, ar };
