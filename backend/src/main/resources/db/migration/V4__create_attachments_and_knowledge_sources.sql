CREATE TABLE attachments (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies (id),
    conversation_id BIGINT REFERENCES conversations (id),
    message_id BIGINT REFERENCES messages (id),
    kind VARCHAR(32) NOT NULL,
    storage_key VARCHAR(512) NOT NULL,
    original_filename VARCHAR(255),
    mime_type VARCHAR(128),
    size_bytes BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_attachments_company_id ON attachments (company_id);

CREATE TABLE knowledge_sources (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies (id),
    type VARCHAR(32) NOT NULL,
    attachment_id BIGINT NOT NULL REFERENCES attachments (id),
    status VARCHAR(32) NOT NULL,
    extracted_summary TEXT,
    failure_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_knowledge_sources_company_id ON knowledge_sources (company_id);
