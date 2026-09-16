CREATE TABLE leads (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies (id),
    conversation_id BIGINT NOT NULL REFERENCES conversations (id),
    client_name VARCHAR(255) NOT NULL,
    client_phone VARCHAR(64) NOT NULL,
    client_email VARCHAR(255),
    ai_summary TEXT,
    estimated_price_min DOUBLE PRECISION,
    estimated_price_max DOUBLE PRECISION,
    currency VARCHAR(8),
    uncertain_notes TEXT,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_leads_company_id ON leads (company_id);
