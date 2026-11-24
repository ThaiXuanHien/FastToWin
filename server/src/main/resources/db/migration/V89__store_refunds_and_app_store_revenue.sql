-- Refunded store purchases take back their Gems once, never below a zero balance (owner policy 2026-11-24).
ALTER TABLE store_purchases
    ADD COLUMN refunded_at TIMESTAMPTZ,
    ADD COLUMN gems_revoked INTEGER NOT NULL DEFAULT 0 CHECK (gems_revoked >= 0);

-- App Store Server Notifications V2 replay guard. No payload, token or user ID is stored.
CREATE TABLE app_store_notifications (
    notification_uuid UUID PRIMARY KEY,
    notification_type VARCHAR(64) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

-- App Store revenue now comes from Apple-signed transactions, so its sandbox can report too.
INSERT INTO analytics_source_coverage (metric, source, environment, status, coverage_start, freshness)
VALUES ('FINANCIAL', 'APP_STORE', 'SANDBOX', 'UNAVAILABLE', NULL, NULL)
ON CONFLICT DO NOTHING;
