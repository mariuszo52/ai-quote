ALTER TABLE company_pricing_profiles
    ADD COLUMN manually_edited_at TIMESTAMPTZ;

ALTER TABLE company_pricing_profiles
    DROP COLUMN human_summary;
