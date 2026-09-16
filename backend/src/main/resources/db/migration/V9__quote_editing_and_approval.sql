ALTER TABLE quotes
    ADD COLUMN ai_items_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN ai_subtotal DOUBLE PRECISION NOT NULL DEFAULT 0,
    ADD COLUMN ai_total DOUBLE PRECISION NOT NULL DEFAULT 0,
    ADD COLUMN client_note TEXT,
    ADD COLUMN internal_note TEXT,
    ADD COLUMN approved_items_json JSONB,
    ADD COLUMN approved_subtotal DOUBLE PRECISION,
    ADD COLUMN approved_total DOUBLE PRECISION,
    ADD COLUMN approved_at TIMESTAMPTZ;

-- Backfill: for quotes created before this column existed, the current (AI-only,
-- never-yet-edited) items/totals are the AI baseline.
UPDATE quotes SET ai_items_json = items_json, ai_subtotal = subtotal, ai_total = total;

CREATE TABLE quote_change_log (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies (id),
    quote_id BIGINT NOT NULL REFERENCES quotes (id),
    summary TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_quote_change_log_quote_id ON quote_change_log (quote_id);
