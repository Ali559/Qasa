//! Multilingual, friendly push / inbox copy (en / ckb / ar).

#[derive(Debug, Clone)]
pub struct LocalizedNotice {
    pub kind: &'static str,
    pub title_en: &'static str,
    pub title_ckb: &'static str,
    pub title_ar: &'static str,
    pub body_en: &'static str,
    pub body_ckb: &'static str,
    pub body_ar: &'static str,
}

/// Free-tier conversion: email hash found in a leak dump.
pub const BREACH_ALERT_FREE: LocalizedNotice = LocalizedNotice {
    kind: "breach_alert_free",
    title_en: "🚨 Security alert",
    title_ckb: "🚨 ئاگاداری ئاسایش",
    title_ar: "🚨 تنبيه أمني",
    body_en: "Heads up — an email you monitor showed up in a new data leak. Open Qasa, upgrade to Premium, and lock it down for 10,000 IQD via FIB, FastPay, or SuperQi. You've got this 💪",
    body_ckb: "سڵاو — ئیمەیڵێکت لە دزەکردنی نوێدا دۆزرایەوە. قاسە بکەرەوە، بۆ پرێمیۆم بەرزبکەرەوە و بیپارێزە بە ١٠٬٠٠٠ دینار لە ڕێگەی FIB / FastPay / SuperQi. تۆ دەتوانیت 💪",
    body_ar: "تنبيه ودي — ظهر بريد تراقبه في تسريب جديد. افتح Qasa، ترقَّ إلى Premium، وأمِّنه بـ ١٠٬٠٠٠ دينار عبر FIB أو FastPay أو SuperQi. أنت قدّها 💪",
};

/// Premium users also get a friendlier alert (details available in-app).
pub const BREACH_ALERT_PREMIUM: LocalizedNotice = LocalizedNotice {
    kind: "breach_alert_premium",
    title_en: "🚨 Breach found",
    title_ckb: "🚨 دزەکردن دۆزرایەوە",
    title_ar: "🚨 تم اكتشاف تسريب",
    body_en: "One of your monitored emails popped up in a leak. Open Qasa to see sources and change that password ASAP 🔐",
    body_ckb: "یەکێک لە ئیمەیڵەکانت لە دزەکردندا دەرکەوت. قاسە بکەرەوە بۆ بینینی سەرچاوەکان و گۆڕینی وشەی نهێنی 🔐",
    body_ar: "ظهر أحد بريدك المراقَب في تسريب. افتح Qasa لعرض المصادر وغيّر كلمة المرور فوراً 🔐",
};

/// Auto-renew OFF — expires in ~3 days.
pub const SUB_EXPIRING_3D: LocalizedNotice = LocalizedNotice {
    kind: "sub_expiring_3d",
    title_en: "⏳ Premium is almost up",
    title_ckb: "⏳ پرێمیۆم نزیکە تەواو ببێت",
    title_ar: "⏳ اشتراك Premium على وشك الانتهاء",
    body_en: "Your Qasa Premium wraps up in about 3 days. Tap to renew with FIB, FastPay, or SuperQi and keep VPN + multi-device humming ✨",
    body_ckb: "پرێمیۆمی قاسە لە نزیکەی ٣ ڕۆژدا کۆتایی دێت. نوێیبکەرەوە لەگەڵ FIB / FastPay / SuperQi بۆ مانەوەی VPN و چەند ئامێر ✨",
    body_ar: "ينتهي Qasa Premium خلال نحو ٣ أيام. جدِّد عبر FIB أو FastPay أو SuperQi لتبقى VPN والأجهزة المتعددة معك ✨",
};

/// Auto-renew ON but renewal couldn't complete.
pub const SUB_RENEW_FAILED: LocalizedNotice = LocalizedNotice {
    kind: "sub_renew_failed",
    title_en: "💙 We couldn't renew Premium",
    title_ckb: "💙 نەمانتوانی پرێمیۆم نوێ بکەینەوە",
    title_ar: "💙 تعذّر تجديد Premium",
    body_en: "We tried to auto-renew your Qasa Premium but it didn't go through. No stress — tap here to pay with FIB, FastPay, or SuperQi and you're back online in a snap 🚀",
    body_ckb: "هەوڵماندا پرێمیۆم خۆکار نوێ بکەینەوە بەڵام سەرکەوتوو نەبوو. کێشە نییە — لێرەوە بپارە لەگەڵ FIB / FastPay / SuperQi و دەستبەجێ دەگەڕێیتەوە 🚀",
    body_ar: "حاولنا تجديد Qasa Premium تلقائياً لكن الدفع لم يكتمل. لا تقلق — اضغط للدفع عبر FIB أو FastPay أو SuperQi وعد خلال لحظات 🚀",
};
