-- Trial/subscription state for a company. One row per company — no new table needed,
-- same reasoning as the branding fields added in V14.
ALTER TABLE companies ADD COLUMN plan VARCHAR(32) NOT NULL DEFAULT 'TRIAL';
ALTER TABLE companies ADD COLUMN stripe_customer_id VARCHAR(255);
ALTER TABLE companies ADD COLUMN stripe_subscription_id VARCHAR(255);
ALTER TABLE companies ADD COLUMN subscription_status VARCHAR(32) NOT NULL DEFAULT 'NONE';
ALTER TABLE companies ADD COLUMN current_period_start TIMESTAMPTZ;
ALTER TABLE companies ADD COLUMN current_period_end TIMESTAMPTZ;
