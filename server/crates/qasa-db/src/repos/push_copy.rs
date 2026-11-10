//! Multilingual, friendly push / inbox copy (en / ckb / ar).

use std::borrow::Cow;

/// Push / inbox text in every app language. The fixed notices below borrow static copy;
/// admin broadcasts build one from the stored notification (`Cow::Owned`).
#[derive(Debug, Clone)]
pub struct LocalizedNotice {
    pub kind: Cow<'static, str>,
    pub title_en: Cow<'static, str>,
    pub title_ckb: Cow<'static, str>,
    pub title_ar: Cow<'static, str>,
    pub body_en: Cow<'static, str>,
    pub body_ckb: Cow<'static, str>,
    pub body_ar: Cow<'static, str>,
}

/// Premium users get a friendlier alert (details available in-app).
pub const BREACH_ALERT_PREMIUM: LocalizedNotice = LocalizedNotice {
    kind: Cow::Borrowed("breach_alert_premium"),
    title_en: Cow::Borrowed("🚨 Breach found"),
    title_ckb: Cow::Borrowed("🚨 دزەکردن دۆزرایەوە"),
    title_ar: Cow::Borrowed("🚨 تم اكتشاف تسريب"),
    body_en: Cow::Borrowed("One of your monitored emails popped up in a leak. Open Qasa to see sources and change that password ASAP 🔐"),
    body_ckb: Cow::Borrowed("یەکێک لە ئیمەیڵەکانت لە دزەکردندا دەرکەوت. قاسە بکەرەوە بۆ بینینی سەرچاوەکان و گۆڕینی وشەی نهێنی 🔐"),
    body_ar: Cow::Borrowed("ظهر أحد بريدك المراقَب في تسريب. افتح Qasa لعرض المصادر وغيّر كلمة المرور فوراً 🔐"),
};

/// Auto-renew OFF — expires in ~3 days.
pub const SUB_EXPIRING_3D: LocalizedNotice = LocalizedNotice {
    kind: Cow::Borrowed("sub_expiring_3d"),
    title_en: Cow::Borrowed("⏳ Premium is almost up"),
    title_ckb: Cow::Borrowed("⏳ پرێمیۆم نزیکە تەواو ببێت"),
    title_ar: Cow::Borrowed("⏳ اشتراك Premium على وشك الانتهاء"),
    body_en: Cow::Borrowed("Your Qasa Premium wraps up in about 3 days. Tap to renew with FIB, FastPay, or SuperQi and keep VPN + multi-device humming ✨"),
    body_ckb: Cow::Borrowed("پرێمیۆمی قاسە لە نزیکەی ٣ ڕۆژدا کۆتایی دێت. نوێیبکەرەوە لەگەڵ FIB / FastPay / SuperQi بۆ مانەوەی VPN و چەند ئامێر ✨"),
    body_ar: Cow::Borrowed("ينتهي Qasa Premium خلال نحو ٣ أيام. جدِّد عبر FIB أو FastPay أو SuperQi لتبقى VPN والأجهزة المتعددة معك ✨"),
};

/// Auto-renew ON but renewal couldn't complete.
pub const SUB_RENEW_FAILED: LocalizedNotice = LocalizedNotice {
    kind: Cow::Borrowed("sub_renew_failed"),
    title_en: Cow::Borrowed("💙 We couldn't renew Premium"),
    title_ckb: Cow::Borrowed("💙 نەمانتوانی پرێمیۆم نوێ بکەینەوە"),
    title_ar: Cow::Borrowed("💙 تعذّر تجديد Premium"),
    body_en: Cow::Borrowed("We tried to auto-renew your Qasa Premium but it didn't go through. No stress — tap here to pay with FIB, FastPay, or SuperQi and you're back online in a snap 🚀"),
    body_ckb: Cow::Borrowed("هەوڵماندا پرێمیۆم خۆکار نوێ بکەینەوە بەڵام سەرکەوتوو نەبوو. کێشە نییە — لێرەوە بپارە لەگەڵ FIB / FastPay / SuperQi و دەستبەجێ دەگەڕێیتەوە 🚀"),
    body_ar: Cow::Borrowed("حاولنا تجديد Qasa Premium تلقائياً لكن الدفع لم يكتمل. لا تقلق — اضغط للدفع عبر FIB أو FastPay أو SuperQi وعد خلال لحظات 🚀"),
};

/// Auto-renew ON and renewal is by link: the link for the next period is ready.
/// ckb/ar await native review (2026-11-04 strings).
pub const SUB_RENEWAL_LINK: LocalizedNotice = LocalizedNotice {
    kind: Cow::Borrowed("sub_renewal_link"),
    title_en: Cow::Borrowed("💳 Your Premium renewal is ready"),
    title_ckb: Cow::Borrowed("💳 نوێکردنەوەی پرێمیۆمەکەت ئامادەیە"),
    title_ar: Cow::Borrowed("💳 تجديد Premium جاهز"),
    body_en: Cow::Borrowed("Your Qasa Premium ends in a few days. Tap to pay for the next period in one step; it adds on to the time you have left."),
    body_ckb: Cow::Borrowed("پرێمیۆمی قاسە لە چەند ڕۆژێکدا کۆتایی دێت. دەستی لێ بدە بۆ پارەدانی ماوەی داهاتوو بە یەک هەنگاو؛ دەچێتە سەر ئەو کاتەی ماوەتەوە."),
    body_ar: Cow::Borrowed("ينتهي Qasa Premium خلال أيام. اضغط لدفع الفترة التالية بخطوة واحدة؛ تُضاف إلى المدة المتبقية لديك."),
};

/// 80% of the month's VPN data used. ckb/ar await native review (2026-11-02 strings).
pub const VPN_USAGE_80: LocalizedNotice = LocalizedNotice {
    kind: Cow::Borrowed("vpn_usage_80"),
    title_en: Cow::Borrowed("📶 VPN data almost used up"),
    title_ckb: Cow::Borrowed("📶 داتای VPN نزیکە تەواو ببێت"),
    title_ar: Cow::Borrowed("📶 بيانات VPN على وشك النفاد"),
    body_en: Cow::Borrowed("You've used 80% of this month's VPN data. It resets on the 1st."),
    body_ckb: Cow::Borrowed("٨٠٪ی داتای VPN ی ئەم مانگەت بەکارهێناوە. لە یەکی مانگ نوێ دەبێتەوە."),
    body_ar: Cow::Borrowed("استخدمت 80٪ من بيانات VPN لهذا الشهر. يتجدد الرصيد في أول الشهر."),
};

/// Monthly VPN quota used up; speed limited until the period resets.
pub const VPN_THROTTLED: LocalizedNotice = LocalizedNotice {
    kind: Cow::Borrowed("vpn_throttled"),
    title_en: Cow::Borrowed("🐢 VPN slowed down"),
    title_ckb: Cow::Borrowed("🐢 خێرایی VPN کەم کرایەوە"),
    title_ar: Cow::Borrowed("🐢 تم تخفيض سرعة VPN"),
    body_en: Cow::Borrowed("You've used this month's VPN data, so speed is limited to 5 Mbps until the 1st."),
    body_ckb: Cow::Borrowed("داتای VPN ی ئەم مانگەت تەواو بوو، بۆیە خێرایی تا یەکی مانگ بە ٥ مێگابیت سنووردار کراوە."),
    body_ar: Cow::Borrowed("استهلكت بيانات VPN لهذا الشهر، لذا السرعة محدودة بـ 5 ميغابت حتى أول الشهر."),
};

/// A device restored a backup and took the place of the device that made it
/// (`POST /v1/devices/register` with a restore key). Sent to the whole account before the
/// old device is unlinked, so the old phone gets the push too. ckb/ar await native review
/// (2026-11-10 strings).
pub fn device_replaced(old_device: &str, new_device: &str) -> LocalizedNotice {
    LocalizedNotice {
        kind: Cow::Borrowed("device_replaced"),
        title_en: Cow::Borrowed("🔁 A device was replaced"),
        title_ckb: Cow::Borrowed("🔁 ئامێرێک گۆڕدرا"),
        title_ar: Cow::Borrowed("🔁 تم استبدال جهاز"),
        body_en: Cow::Owned(format!(
            "“{new_device}” restored a Qasa backup and took the place of “{old_device}”, which is now signed out. If this wasn't you, change your main password on a device you trust and contact us."
        )),
        body_ckb: Cow::Owned(format!(
            "«{new_device}» باکئەپێکی قاسەی گەڕاندەوە و جێگەی «{old_device}»ی گرتەوە، کە ئێستا دەرچووە. ئەگەر تۆ نەبوویت، لەسەر ئامێرێک کە متمانەت پێیەتی وشەی نهێنی سەرەکییەکەت بگۆڕە و پەیوەندیمان پێوە بکە."
        )),
        body_ar: Cow::Owned(format!(
            "استعاد «{new_device}» نسخة احتياطية من قاصة وحلّ محل «{old_device}»، الذي سُجّل خروجه الآن. إذا لم تكن أنت، فغيّر كلمة مرورك الرئيسية من جهاز تثق به وتواصل معنا."
        )),
    }
}
