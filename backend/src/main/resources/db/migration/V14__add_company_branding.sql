ALTER TABLE companies
    ADD COLUMN display_name VARCHAR(255),
    ADD COLUMN logo_storage_key VARCHAR(512),
    ADD COLUMN logo_content_type VARCHAR(100),
    ADD COLUMN primary_color VARCHAR(7),
    ADD COLUMN welcome_text TEXT,
    ADD COLUMN contact_email VARCHAR(255),
    ADD COLUMN contact_phone VARCHAR(64),
    ADD COLUMN address TEXT;
