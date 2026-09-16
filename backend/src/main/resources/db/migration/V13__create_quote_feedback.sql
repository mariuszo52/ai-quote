CREATE TABLE quote_feedback (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies (id),
    quote_id BIGINT NOT NULL UNIQUE REFERENCES quotes (id),
    ai_total DOUBLE PRECISION NOT NULL,
    final_total DOUBLE PRECISION NOT NULL,
    diff_amount DOUBLE PRECISION NOT NULL,
    diff_percentage DOUBLE PRECISION,
    ai_items_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    final_items_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    changed_items_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    added_items_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    removed_items_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    reason VARCHAR(32),
    note TEXT,
    reason_submitted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_quote_feedback_company_id ON quote_feedback (company_id);
