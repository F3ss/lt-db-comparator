CREATE TABLE IF NOT EXISTS processed_data
(
    id             BIGSERIAL PRIMARY KEY,
    source_key     VARCHAR(255),
    payload        TEXT        NOT NULL,
    processed_value TEXT       NOT NULL,
    created_at     TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS audit_log
(
    id             BIGSERIAL PRIMARY KEY,
    event_type     VARCHAR(100) NOT NULL,
    description    TEXT         NOT NULL,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- index for fast lookups by key
CREATE INDEX IF NOT EXISTS idx_processed_data_source_key ON processed_data (source_key);

-- index for time-range queries
CREATE INDEX IF NOT EXISTS idx_audit_log_created_at ON audit_log (created_at);
