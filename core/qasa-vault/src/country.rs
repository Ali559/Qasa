//! The country list used by phone, address, bank and ID items. Mirrors iOS `Country.swift`.

pub struct Country {
    pub code: &'static str,
    pub name: &'static str,
    pub dial_code: &'static str,
}

/// Sorted by English name, as on iOS.
pub const COUNTRIES: &[Country] = &[
    Country { code: "AU", name: "Australia", dial_code: "+61" },
    Country { code: "BH", name: "Bahrain", dial_code: "+973" },
    Country { code: "BR", name: "Brazil", dial_code: "+55" },
    Country { code: "CA", name: "Canada", dial_code: "+1" },
    Country { code: "CN", name: "China", dial_code: "+86" },
    Country { code: "EG", name: "Egypt", dial_code: "+20" },
    Country { code: "FR", name: "France", dial_code: "+33" },
    Country { code: "DE", name: "Germany", dial_code: "+49" },
    Country { code: "IN", name: "India", dial_code: "+91" },
    Country { code: "IR", name: "Iran", dial_code: "+98" },
    Country { code: "IQ", name: "Iraq", dial_code: "+964" },
    Country { code: "IT", name: "Italy", dial_code: "+39" },
    Country { code: "JP", name: "Japan", dial_code: "+81" },
    Country { code: "JO", name: "Jordan", dial_code: "+962" },
    Country { code: "KW", name: "Kuwait", dial_code: "+965" },
    Country { code: "MX", name: "Mexico", dial_code: "+52" },
    Country { code: "NL", name: "Netherlands", dial_code: "+31" },
    Country { code: "NO", name: "Norway", dial_code: "+47" },
    Country { code: "PK", name: "Pakistan", dial_code: "+92" },
    Country { code: "PL", name: "Poland", dial_code: "+48" },
    Country { code: "QA", name: "Qatar", dial_code: "+974" },
    Country { code: "SA", name: "Saudi Arabia", dial_code: "+966" },
    Country { code: "KR", name: "South Korea", dial_code: "+82" },
    Country { code: "ES", name: "Spain", dial_code: "+34" },
    Country { code: "SE", name: "Sweden", dial_code: "+46" },
    Country { code: "CH", name: "Switzerland", dial_code: "+41" },
    Country { code: "TR", name: "Turkey", dial_code: "+90" },
    Country { code: "AE", name: "United Arab Emirates", dial_code: "+971" },
    Country { code: "GB", name: "United Kingdom", dial_code: "+44" },
    Country { code: "US", name: "United States", dial_code: "+1" },
];

/// `+964` for `IQ`; an unknown code becomes `+CODE`, as on iOS.
pub fn dial_code(code: &str) -> String {
    COUNTRIES
        .iter()
        .find(|c| c.code == code)
        .map(|c| c.dial_code.to_string())
        .unwrap_or_else(|| format!("+{code}"))
}

/// The English name, or the code itself when unknown.
pub fn country_name(code: &str) -> String {
    COUNTRIES.iter().find(|c| c.code == code).map(|c| c.name.to_string()).unwrap_or_else(|| code.to_string())
}
