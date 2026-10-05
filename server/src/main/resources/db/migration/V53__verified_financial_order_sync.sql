-- Operational recheck registry. No user ID, raw receipt, purchase token or provider response.
-- Retained after identity deletion so late refunds use the original anonymous subject.
CREATE TABLE store_financial_orders (
    source VARCHAR(32) NOT NULL CHECK (source = 'GOOGLE_PLAY'),
    environment VARCHAR(8) NOT NULL CHECK (environment IN ('LIVE', 'SANDBOX')),
    order_id VARCHAR(256) NOT NULL,
    package_name VARCHAR(256) NOT NULL,
    product_id VARCHAR(128) NOT NULL,
    receipt_fingerprint CHAR(64) NOT NULL,
    subject_key UUID NOT NULL,
    captured_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    next_check_at TIMESTAMPTZ NOT NULL DEFAULT '-infinity',
    checked_at TIMESTAMPTZ,
    last_event_at TIMESTAMPTZ,
    sync_status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        CHECK (sync_status IN ('PENDING', 'OK', 'FAILED', 'CONFLICT')),
    PRIMARY KEY (source, environment, order_id),
    UNIQUE (source, receipt_fingerprint)
);
CREATE INDEX store_financial_orders_due_idx ON store_financial_orders(next_check_at);
