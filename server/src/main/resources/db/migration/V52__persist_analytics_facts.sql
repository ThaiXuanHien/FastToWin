-- Only this private link contains account identity. Historical facts never reference users.
CREATE TABLE analytics_subject_links (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    subject_key UUID NOT NULL UNIQUE
);

CREATE TABLE analytics_registrations (
    subject_key UUID PRIMARY KEY,
    registered_at TIMESTAMPTZ NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX analytics_registrations_time_idx ON analytics_registrations (registered_at);

CREATE TABLE analytics_item_purchases (
    event_id UUID PRIMARY KEY,
    subject_key UUID NOT NULL,
    item_id VARCHAR(128) NOT NULL,
    currency VARCHAR(8) NOT NULL CHECK (currency IN ('GOLD', 'GEMS')),
    amount BIGINT NOT NULL CHECK (amount > 0),
    purchased_at TIMESTAMPTZ NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX analytics_item_purchases_time_idx ON analytics_item_purchases (purchased_at);
CREATE INDEX analytics_item_purchases_currency_time_idx ON analytics_item_purchases (currency, purchased_at);
CREATE INDEX analytics_item_purchases_item_time_idx ON analytics_item_purchases (item_id, purchased_at);

-- Stable exact ledger contract for verified adapters/aggregate fixtures. No source is enabled here.
-- Event IDs are source-issued; refunds refer to the original transaction, not a user identity.
CREATE TABLE analytics_financial_events (
    source VARCHAR(32) NOT NULL,
    environment VARCHAR(8) NOT NULL CHECK (environment IN ('LIVE', 'SANDBOX')),
    event_id VARCHAR(256) NOT NULL,
    event_type VARCHAR(8) NOT NULL CHECK (event_type IN ('PAYMENT', 'REFUND')),
    transaction_id VARCHAR(256) NOT NULL,
    product_id VARCHAR(128) NOT NULL,
    subject_key UUID NOT NULL,
    amount_minor NUMERIC(38, 0) NOT NULL CHECK (amount_minor > 0),
    currency VARCHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    currency_exponent SMALLINT NOT NULL CHECK (currency_exponent BETWEEN 0 AND 6),
    occurred_at TIMESTAMPTZ NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (source, environment, event_id)
);
CREATE UNIQUE INDEX analytics_financial_payment_unique_idx
    ON analytics_financial_events (source, environment, transaction_id) WHERE event_type = 'PAYMENT';
CREATE INDEX analytics_financial_events_time_idx ON analytics_financial_events (occurred_at);
CREATE INDEX analytics_financial_events_source_time_idx ON analytics_financial_events (source, environment, occurred_at);
CREATE INDEX analytics_financial_events_currency_time_idx ON analytics_financial_events (currency, occurred_at);
CREATE INDEX analytics_financial_events_product_time_idx ON analytics_financial_events (product_id, occurred_at);

CREATE TABLE analytics_source_coverage (
    metric VARCHAR(32) NOT NULL,
    source VARCHAR(32) NOT NULL,
    environment VARCHAR(8) NOT NULL CHECK (environment IN ('LIVE', 'SANDBOX')),
    status VARCHAR(16) NOT NULL CHECK (status IN ('COMPLETE', 'PARTIAL', 'UNAVAILABLE')),
    coverage_start TIMESTAMPTZ,
    freshness TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (metric, source, environment),
    CHECK (status = 'UNAVAILABLE' OR (coverage_start IS NOT NULL AND freshness IS NOT NULL))
);
-- Trusted collection starts now; no historical dates, prices or registration events are invented.
INSERT INTO analytics_source_coverage (metric, source, environment, status, coverage_start, freshness)
VALUES ('REGISTRATIONS', 'SERVER', 'LIVE', 'COMPLETE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
       ('ITEM_PURCHASES', 'SERVER', 'LIVE', 'COMPLETE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
       ('FINANCIAL', 'GOOGLE_PLAY', 'LIVE', 'UNAVAILABLE', NULL, NULL),
       ('FINANCIAL', 'APP_STORE', 'LIVE', 'UNAVAILABLE', NULL, NULL);
