-- Premium drops to 5,000 IQD a month / 50,000 IQD a year, and email leak alerts leave the
-- plan (parked until there is a breach data source; todo email-leak-alerts).
-- Existing subscriptions keep what they paid; only new purchases use the new amounts.
UPDATE plans
SET amount_iqd = 5000,
    features_json = '["5 devices","VPN"]'::jsonb,
    updated_at = now()
WHERE id = 'premium_monthly';

UPDATE plans
SET amount_iqd = 50000,
    features_json = '["5 devices","VPN","Save vs monthly"]'::jsonb,
    updated_at = now()
WHERE id = 'premium_yearly';
