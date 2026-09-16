CREATE TABLE quotes (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies (id),
    conversation_id BIGINT NOT NULL REFERENCES conversations (id),
    lead_id BIGINT NOT NULL REFERENCES leads (id),
    status VARCHAR(32) NOT NULL,
    currency VARCHAR(8) NOT NULL,
    items_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    subtotal DOUBLE PRECISION NOT NULL DEFAULT 0,
    total DOUBLE PRECISION NOT NULL DEFAULT 0,
    ai_confidence DOUBLE PRECISION,
    ai_reasoning TEXT,
    uncertain_factors_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_quotes_company_id ON quotes (company_id);
CREATE UNIQUE INDEX idx_quotes_lead_id ON quotes (lead_id);
