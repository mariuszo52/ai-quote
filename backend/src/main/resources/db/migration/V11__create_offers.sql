CREATE TABLE offers (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies (id),
    quote_id BIGINT NOT NULL UNIQUE REFERENCES quotes (id),
    public_token VARCHAR(64) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL,
    currency VARCHAR(8) NOT NULL,
    items_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    total DOUBLE PRECISION NOT NULL DEFAULT 0,
    client_name VARCHAR(255) NOT NULL,
    client_phone VARCHAR(64) NOT NULL,
    client_email VARCHAR(255),
    job_description TEXT,
    estimated_timeline VARCHAR(255),
    valid_until DATE,
    pdf_storage_key VARCHAR(512) NOT NULL,
    sent_at TIMESTAMPTZ,
    last_send_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_offers_company_id ON offers (company_id);
CREATE UNIQUE INDEX idx_offers_public_token ON offers (public_token);
